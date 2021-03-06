package jp.co.soramitsu.feature_account_impl.presentation.common.mixin.api

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import io.reactivex.disposables.CompositeDisposable
import jp.co.soramitsu.common.utils.Event
import jp.co.soramitsu.feature_account_impl.presentation.view.advanced.network.NetworkChooserPayload
import jp.co.soramitsu.feature_account_impl.presentation.view.advanced.network.model.NetworkModel

interface NetworkChooserMixin {
    val networkDisposable: CompositeDisposable

    val selectedNetworkLiveData: MutableLiveData<NetworkModel>

    val networkChooserEvent: LiveData<Event<NetworkChooserPayload>>

    val isNetworkTypeChangeAvailable: Boolean

    fun chooseNetworkClicked()
}