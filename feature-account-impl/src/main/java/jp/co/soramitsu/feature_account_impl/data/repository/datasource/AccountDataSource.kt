package jp.co.soramitsu.feature_account_impl.data.repository.datasource

import io.reactivex.Observable
import jp.co.soramitsu.feature_account_api.domain.model.Account
import jp.co.soramitsu.feature_account_api.domain.model.AuthType
import jp.co.soramitsu.feature_account_api.domain.model.Node

interface AccountDataSource {

    fun saveAuthType(authType: AuthType)

    fun getAuthType(): AuthType

    fun saveSelectedLanguage(language: String)

    fun getSelectedLanguage(): String?

    fun savePinCode(pinCode: String)

    fun getPinCode(): String?

    fun saveSelectedNode(node: Node)

    fun getSelectedNode(): Node?

    fun setMnemonicIsBackedUp(backedUp: Boolean)

    fun getMnemonicIsBackedUp(): Boolean

    fun saveSeed(seed: ByteArray, address: String)

    fun getSeed(address: String): ByteArray?

    fun saveEntropy(entropy: ByteArray, address: String)

    fun getEntropy(address: String): ByteArray?

    fun saveDerivationPath(derivationPath: String, address: String)

    fun getDerivationPath(address: String): String?

    fun anyAccountSelected(): Boolean

    fun saveSelectedAccount(account: Account)

    fun observeSelectedAccount(): Observable<Account>
}