package jp.co.soramitsu.feature_wallet_impl.domain

import io.reactivex.Completable
import io.reactivex.Observable
import io.reactivex.Single
import jp.co.soramitsu.common.interfaces.FileProvider
import jp.co.soramitsu.fearless_utils.encrypt.qr.QrSharing
import jp.co.soramitsu.feature_account_api.domain.interfaces.AccountRepository
import jp.co.soramitsu.feature_account_api.domain.model.Account
import jp.co.soramitsu.feature_wallet_api.domain.interfaces.NotEnoughFundsException
import jp.co.soramitsu.feature_wallet_api.domain.interfaces.WalletInteractor
import jp.co.soramitsu.feature_wallet_api.domain.interfaces.WalletRepository
import jp.co.soramitsu.feature_wallet_api.domain.model.Asset
import jp.co.soramitsu.feature_wallet_api.domain.model.CheckFundsStatus
import jp.co.soramitsu.feature_wallet_api.domain.model.Fee
import jp.co.soramitsu.feature_wallet_api.domain.model.Transaction
import jp.co.soramitsu.feature_wallet_api.domain.model.Transfer
import java.io.File
import java.math.BigDecimal

class WalletInteractorImpl(
    private val walletRepository: WalletRepository,
    private val accountRepository: AccountRepository,
    private val fileProvider: FileProvider
) : WalletInteractor {

    companion object {
        private const val QR_SHARE_PREFIX = "substrate"
    }

    override fun observeAssets(): Observable<List<Asset>> {
        return walletRepository.observeAssets()
    }

    override fun syncAssetsRates(): Completable {
        return walletRepository.syncAssetsRates()
    }

    override fun observeAsset(token: Asset.Token): Observable<Asset> {
        return walletRepository.observeAsset(token)
    }

    override fun syncAssetRates(token: Asset.Token): Completable {
        return walletRepository.syncAsset(token)
    }

    override fun observeCurrentAsset(): Observable<Asset> {
        return accountRepository.observeSelectedAccount()
            .map { Asset.Token.fromNetworkType(it.network.type) }
            .switchMap(walletRepository::observeAsset)
    }

    override fun observeTransactionsFirstPage(pageSize: Int): Observable<List<Transaction>> {
        return walletRepository.observeTransactionsFirstPage(pageSize)
            .distinctUntilChanged { previous, new -> areTransactionPagesTheSame(previous, new) }
    }

    private fun areTransactionPagesTheSame(previous: List<Transaction>, new: List<Transaction>): Boolean {
        if (previous.size != new.size) return false

        return previous.zip(new).all { (previousElement, currentElement) -> previousElement == currentElement }
    }

    override fun syncTransactionsFirstPage(pageSize: Int): Completable {
        return walletRepository.syncTransactionsFirstPage(pageSize)
    }

    override fun getTransactionPage(pageSize: Int, page: Int): Single<List<Transaction>> {
        return walletRepository.getTransactionPage(pageSize, page)
    }

    override fun observeSelectedAccount(): Observable<Account> {
        return accountRepository.observeSelectedAccount()
    }

    override fun getAddressId(address: String): Single<ByteArray> {
        return accountRepository.getAddressId(address)
    }

    override fun getContacts(query: String): Single<List<String>> {
        return accountRepository.getSelectedAccount()
            .flatMap { walletRepository.getContacts(query) }
    }

    override fun getMyAddresses(query: String): Single<List<String>> {
        return accountRepository.observeSelectedAccount().firstOrError()
            .flatMap { accountRepository.getMyAccounts(query, it.network.type) }
    }

    override fun validateSendAddress(address: String): Single<Boolean> {
        return getAddressId(address)
            .map { true }
            .onErrorReturn { false }
    }

    override fun getTransferFee(transfer: Transfer): Single<Fee> {
        return walletRepository.getTransferFee(transfer)
    }

    override fun performTransfer(transfer: Transfer, fee: BigDecimal): Completable {
        return walletRepository.checkEnoughAmountForTransfer(transfer)
            .flatMapCompletable {
                if (it == CheckFundsStatus.NOT_ENOUGH_FUNDS) {
                    throw NotEnoughFundsException()
                } else {
                    walletRepository.performTransfer(transfer, fee)
                }
            }
    }

    override fun checkEnoughAmountForTransfer(transfer: Transfer): Single<CheckFundsStatus> {
        return walletRepository.checkEnoughAmountForTransfer(transfer)
    }

    override fun getAccountsInCurrentNetwork(): Single<List<Account>> {
        return accountRepository.observeSelectedAccount().firstOrError()
            .flatMap {
                accountRepository.getAccountsByNetworkType(it.network.type)
            }
    }

    override fun selectAccount(address: String): Completable {
        return accountRepository.getAccount(address)
            .flatMapCompletable(accountRepository::selectAccount)
    }

    override fun getQrCodeSharingString(): Single<String> {
        return accountRepository.observeSelectedAccount()
            .firstOrError()
            .map(::formatQrAccountData)
    }

    private fun formatQrAccountData(account: Account): String {
        return with(account) {
            if (name.isNullOrEmpty()) {
                "$QR_SHARE_PREFIX:$address:$publicKey"
            } else {
                "$QR_SHARE_PREFIX:$address:$publicKey:$name"
            }
        }
    }

    override fun createFileInTempStorageAndRetrieveAsset(fileName: String): Single<Pair<File, Asset>> {
        return fileProvider.createFileInTempStorage(fileName)
            .flatMap { file ->
                observeCurrentAsset()
                    .firstOrError()
                    .map { Pair(file, it) }
            }
    }

    override fun getRecipientFromQrCodeContent(content: String): Single<String> {
        return Single.fromCallable { QrSharing.decode(content) }
            .map { it.address }
    }
}