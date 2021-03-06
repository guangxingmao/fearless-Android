package jp.co.soramitsu.common.data.network.rpc

import com.google.gson.Gson
import com.neovisionaries.ws.client.WebSocketFactory
import com.neovisionaries.ws.client.WebSocketState
import io.reactivex.Completable
import io.reactivex.Observable
import io.reactivex.ObservableEmitter
import io.reactivex.Single
import io.reactivex.disposables.Disposable
import io.reactivex.schedulers.Schedulers
import io.reactivex.subjects.BehaviorSubject
import jp.co.soramitsu.common.data.network.rpc.mappers.ResponseMapper
import jp.co.soramitsu.common.data.network.rpc.mappers.nonNull
import jp.co.soramitsu.common.data.network.rpc.mappers.string
import jp.co.soramitsu.common.data.network.rpc.recovery.ExponentialReconnectStrategy
import jp.co.soramitsu.common.data.network.rpc.recovery.ReconnectStrategy
import jp.co.soramitsu.common.data.network.rpc.socket.RpcSocket
import jp.co.soramitsu.common.data.network.rpc.socket.RpcSocketListener
import jp.co.soramitsu.common.data.network.rpc.subscription.SubscriptionChange
import jp.co.soramitsu.common.utils.concurrentHashSet
import jp.co.soramitsu.common.utils.subscribeToError
import jp.co.soramitsu.fearless_utils.wsrpc.Logger
import jp.co.soramitsu.fearless_utils.wsrpc.request.runtime.RuntimeRequest
import jp.co.soramitsu.fearless_utils.wsrpc.response.RpcResponse
import java.util.concurrent.ArrayBlockingQueue
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ThreadPoolExecutor
import java.util.concurrent.TimeUnit

sealed class State {
    abstract class Attempting(val attempt: Int) : State()

    object Connected : State()

    class Waiting(attempt: Int) : Attempting(attempt)
    class Connecting(attempt: Int) : Attempting(attempt)

    object Disconnected : State()
}

class ConnectionClosedException : Exception()

enum class DeliveryType {
    /**
     * For idempotent requests will not produce error and try to to deliver after reconnect
     */
    AT_LEAST_ONCE,

    /**
     * For non-idempotent requests, will produce an error if fails to deliver/get response
     */
    AT_MOST_ONCE,

    /**
     * Similar to AT_LEAST_ONCE, but resend request on each reconnect regardless of success
     */
    ON_RECONNECT
}

class RequestMapEntry(
    val request: RuntimeRequest,
    val deliveryType: DeliveryType,
    val emitter: ObservableEmitter<RpcResponse>
)

class SubscriptionMapEntry(
    val request: RuntimeRequest,
    val emitter: ObservableEmitter<SubscriptionChange>
)

private val WAITING_EXECUTOR = ThreadPoolExecutor(1, 1, 5, TimeUnit.SECONDS, ArrayBlockingQueue(10))
private val WAITING_SCHEDULER = Schedulers.from(WAITING_EXECUTOR)

private val DEFAULT_RECONNECT_STRATEGY = ExponentialReconnectStrategy(initialTime = 300L, base = 2.0)

class SocketService(
    private val jsonMapper: Gson,
    private val logger: Logger,
    private val reconnectStrategy: ReconnectStrategy = DEFAULT_RECONNECT_STRATEGY
) : RpcSocketListener, ConnectionManager {
    private var socket: RpcSocket? = null

    private val requestsMap = ConcurrentHashMap<Int, RequestMapEntry>()

    private val pendingRequests = concurrentHashSet<RuntimeRequest>()
    private val waitingForResponseRequests = concurrentHashSet<RuntimeRequest>()

    private val subscriptions = ConcurrentHashMap<String, SubscriptionMapEntry>()

    private val socketFactory = WebSocketFactory()

    private val stateSubject = BehaviorSubject.create<State>()
    private val lifecycleConditionSubject = BehaviorSubject.createDefault(LifecycleCondition.FORBIDDEN)

    @Volatile
    private var currentReconnectAttempt = 0

    @Volatile
    private var reconnectWaitDisposable: Disposable? = null

    @Volatile
    private var resendPendingDisposable: Disposable? = null

    @Volatile
    private var state: State = State.Disconnected

    override fun switchUrl(url: String) {
        stop()

        start(url)
    }

    override fun started() = state !is State.Disconnected

    override fun setLifecycleCondition(condition: LifecycleCondition) {
        lifecycleConditionSubject.onNext(condition)
    }

    override fun observeLifecycleCondition(): Observable<LifecycleCondition> = lifecycleConditionSubject

    override fun getLifecycleCondition() = lifecycleConditionSubject.value!!

    @Synchronized
    override fun start(url: String) {
        if (state !is State.Disconnected) return

        currentReconnectAttempt = 0
        updateState(State.Connecting(currentReconnectAttempt))

        socket = createSocket(url)

        socket!!.connectAsync()
    }

    @Synchronized
    override fun stop() {
        if (state is State.Disconnected) return

        resendPendingDisposable?.dispose()

        pendingRequests.clear()
        waitingForResponseRequests.clear()

        requestsMap.values.forEach { entry -> entry.emitter.onComplete() }
        requestsMap.clear()

        socket!!.clearListeners()
        socket!!.disconnect()

        reconnectWaitDisposable?.dispose()
        reconnectWaitDisposable = null

        subscriptions.values.forEach { entry -> entry.emitter.onComplete() }
        subscriptions.clear()

        socket = null

        currentReconnectAttempt = 0
        updateState(State.Disconnected)
    }

    override fun observeNetworkState() = stateSubject

    fun subscribe(request: RuntimeRequest): Observable<SubscriptionChange> {
        return executeRequestMultiResponse(request, DeliveryType.ON_RECONNECT)
            .map { string().nonNull().map(it, jsonMapper) }
            .switchMap { subscriptionId ->
                Observable.create<SubscriptionChange> { emitter ->
                    subscriptions[subscriptionId] = SubscriptionMapEntry(request, emitter)
                }.doOnComplete {
                    subscriptions.remove(subscriptionId)
                }
            }
    }

    fun <R> executeRequest(
        request: RuntimeRequest,
        responseType: ResponseMapper<R>,
        deliveryType: DeliveryType = DeliveryType.AT_LEAST_ONCE
    ): Single<R> {
        return executeRequest(request, deliveryType)
            .map { responseType.map(it, jsonMapper) }
    }

    fun executeRequest(
        runtimeRequest: RuntimeRequest,
        deliveryType: DeliveryType = DeliveryType.AT_LEAST_ONCE
    ): Single<RpcResponse> {
        return executeRequestMultiResponse(runtimeRequest, deliveryType)
            .firstOrError()
    }

    private fun executeRequestMultiResponse(
        runtimeRequest: RuntimeRequest,
        deliveryType: DeliveryType = DeliveryType.AT_LEAST_ONCE
    ): Observable<RpcResponse> {
        return Observable.create<RpcResponse> {
            synchronized<Unit>(this) {
                requestsMap[runtimeRequest.id] = RequestMapEntry(runtimeRequest, deliveryType, it)

                if (state is State.Connected) {
                    socket!!.sendRpcRequest(runtimeRequest)
                    waitingForResponseRequests.add(runtimeRequest)
                } else {
                    if (deliveryType == DeliveryType.AT_MOST_ONCE) {
                        it.tryOnError(ConnectionClosedException())
                        requestsMap.remove(runtimeRequest.id)
                        return@create
                    }

                    logger.log("[PENDING REQUEST] ${runtimeRequest.method}")

                    pendingRequests.add(runtimeRequest)

                    if (state is State.Waiting) forceReconnect()
                }
            }
        }.doOnDispose { cancelRequest(runtimeRequest) }
    }

    @Synchronized
    override fun onResponse(rpcResponse: RpcResponse) {
        val requestMapEntry = requestsMap[rpcResponse.id]

        requestMapEntry?.let {
            if (it.deliveryType != DeliveryType.ON_RECONNECT) requestsMap.remove(rpcResponse.id)

            waitingForResponseRequests.remove(it.request)

            it.emitter.onNext(rpcResponse)
        }
    }

    override fun onResponse(subscriptionChange: SubscriptionChange) {
        val subscriptionEntry = subscriptions[subscriptionChange.params.subscription]

        subscriptionEntry?.emitter?.onNext(subscriptionChange)
    }

    @Synchronized
    override fun onStateChanged(newState: WebSocketState) {
        if (newState == WebSocketState.CLOSED) {
            reconnectDelayed()

            reportErrorToAtMostOnceAndForget()

            moveWaitingResponseToPending()

            addOnReconnectToPending()

            subscriptions.clear()
        }
    }

    private fun addOnReconnectToPending() {
        val requests = requestsMap.values.filter { it.deliveryType == DeliveryType.ON_RECONNECT }
            .map { it.request }

        pendingRequests.addAll(requests)
    }

    override fun onConnected() {
        connectionEstablished()
    }

    private fun cancelRequest(runtimeRequest: RuntimeRequest) = synchronized<Unit>(this) {
        requestsMap.remove(runtimeRequest.id)
        waitingForResponseRequests.remove(runtimeRequest)
        pendingRequests.remove(runtimeRequest)
    }

    private fun moveWaitingResponseToPending() {
        pendingRequests.addAll(waitingForResponseRequests)
        waitingForResponseRequests.clear()
    }

    private fun reportErrorToAtMostOnceAndForget() {
        requestsMap.values.filter { it.deliveryType == DeliveryType.AT_MOST_ONCE }
            .forEach {
                it.emitter.tryOnError(ConnectionClosedException())

                waitingForResponseRequests.remove(it.request)
                requestsMap.remove(it.request.id)
            }
    }

    @Synchronized
    private fun connectionEstablished() {
        currentReconnectAttempt = 0
        updateState(State.Connected)

        resendPendingDisposable = Completable.fromAction {
            pendingRequests.forEach {
                socket!!.sendRpcRequest(it)
                waitingForResponseRequests.add(it)

                pendingRequests.remove(it)
            }
        }
            .subscribeOn(Schedulers.io())
            .doFinally { resendPendingDisposable = null }
            .subscribeToError {
                // suspend send errors
            }
    }

    @Synchronized
    private fun reconnectDelayed() {
        if (reconnectWaitDisposable != null) return

        socket!!.clearListeners()

        currentReconnectAttempt++
        updateState(State.Waiting(currentReconnectAttempt))

        val waitTime = reconnectStrategy.getTimeForReconnect(currentReconnectAttempt)

        logger.log("[WAITING FOR RECONNECT] $waitTime ms")

        reconnectWaitDisposable = Completable.timer(waitTime, TimeUnit.MILLISECONDS, WAITING_SCHEDULER)
            .subscribe {
                reconnectNow()
            }
    }

    @Synchronized
    private fun reconnectNow() {
        if (state is State.Connecting) return

        clearCurrentWaitingTask()

        socket = createSocket(socket!!.url)

        socket!!.connectAsync()

        updateState(State.Connecting(currentReconnectAttempt))
    }

    private fun clearCurrentWaitingTask() {
        if (reconnectWaitDisposable != null && !reconnectWaitDisposable!!.isDisposed) reconnectWaitDisposable!!.dispose()

        reconnectWaitDisposable = null
    }

    private fun forceReconnect() {
        currentReconnectAttempt = 0

        reconnectNow()
    }

    private fun createSocket(url: String) = RpcSocket(url, this, logger, socketFactory, jsonMapper)

    private fun updateState(newState: State) {
        state = newState

        stateSubject.onNext(newState)
    }
}