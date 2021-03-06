package jp.co.soramitsu.common.di.modules

import com.google.gson.Gson
import dagger.Module
import dagger.Provides
import jp.co.soramitsu.common.BuildConfig
import jp.co.soramitsu.common.data.network.AndroidLogger
import jp.co.soramitsu.common.data.network.AppLinksProvider
import jp.co.soramitsu.common.data.network.ExternalAnalyzer
import jp.co.soramitsu.common.data.network.ExternalAnalyzerLinks
import jp.co.soramitsu.common.data.network.NetworkApiCreator
import jp.co.soramitsu.common.data.network.RxCallAdapterFactory
import jp.co.soramitsu.common.data.network.rpc.ConnectionManager
import jp.co.soramitsu.common.data.network.rpc.SocketService
import jp.co.soramitsu.common.data.network.rpc.SocketSingleRequestExecutor
import jp.co.soramitsu.common.di.scope.ApplicationScope
import jp.co.soramitsu.common.mixin.api.NetworkStateMixin
import jp.co.soramitsu.common.mixin.impl.NetworkStateProvider
import jp.co.soramitsu.common.resources.ResourceManager
import jp.co.soramitsu.fearless_utils.wsrpc.Logger
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import java.util.concurrent.TimeUnit

@Module
class NetworkModule {

    @Provides
    @ApplicationScope
    fun provideAppLinksProvider(): AppLinksProvider {
        val externalAnalyzerTemplates = mapOf(
            ExternalAnalyzer.POLKASCAN to ExternalAnalyzerLinks(
                transaction = BuildConfig.POLKSASCAN_TRANSACTION_TEMPLATE,
                account = BuildConfig.POLKSASCAN_ACCOUNT_TEMPLATE
            ),

            ExternalAnalyzer.SUBSCAN to ExternalAnalyzerLinks(
                transaction = BuildConfig.SUBSCAN_TRANSACTION_TEMPLATE,
                account = BuildConfig.SUBSCAN_ACCOUNT_TEMPLATE
            )
        )

        return AppLinksProvider(
            termsUrl = BuildConfig.TERMS_URL,
            privacyUrl = BuildConfig.PRIVACY_URL,
            externalAnalyzerTemplates = externalAnalyzerTemplates,
            roadMapUrl = BuildConfig.ROADMAP_URL,
            devStatusUrl = BuildConfig.DEV_STATUS_URL
        )
    }

    @Provides
    @ApplicationScope
    fun provideOkHttpClient(): OkHttpClient {
        val builder = OkHttpClient.Builder()
            .connectTimeout(10, TimeUnit.SECONDS)
            .writeTimeout(10, TimeUnit.SECONDS)
            .readTimeout(10, TimeUnit.SECONDS)
            .retryOnConnectionFailure(true)

        if (BuildConfig.DEBUG) {
            builder.addInterceptor(HttpLoggingInterceptor().setLevel(HttpLoggingInterceptor.Level.BODY))
        }

        return builder.build()
    }

    @Provides
    @ApplicationScope
    fun provideRxCallAdapterFactory(resourceManager: ResourceManager): RxCallAdapterFactory {
        return RxCallAdapterFactory(resourceManager)
    }

    @Provides
    @ApplicationScope
    fun provideLogger(): Logger = AndroidLogger()

    @Provides
    @ApplicationScope
    fun provideApiCreator(
        okHttpClient: OkHttpClient,
        rxCallAdapterFactory: RxCallAdapterFactory
    ): NetworkApiCreator {
        return NetworkApiCreator(okHttpClient, "https://placeholder.com", rxCallAdapterFactory)
    }

    @Provides
    @ApplicationScope
    fun provideSocketService(
        mapper: Gson,
        logger: Logger
    ): SocketService = SocketService(mapper, logger)

    @Provides
    @ApplicationScope
    fun provideConnectionManager(
        socketService: SocketService
    ): ConnectionManager = socketService

    @Provides
    @ApplicationScope
    fun provideSocketSingleRequestExecutor(
        mapper: Gson,
        logger: Logger,
        resourceManager: ResourceManager
    ) = SocketSingleRequestExecutor(mapper, logger, resourceManager)

    @Provides
    fun provideNetworkStateMixin(
        connectionManager: ConnectionManager
    ): NetworkStateMixin = NetworkStateProvider(connectionManager)

    @Provides
    @ApplicationScope
    fun provideJsonMapper() = Gson()
}