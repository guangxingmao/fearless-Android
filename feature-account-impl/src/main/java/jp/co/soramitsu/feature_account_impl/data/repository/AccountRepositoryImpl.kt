package jp.co.soramitsu.feature_account_impl.data.repository

import io.reactivex.Completable
import io.reactivex.Observable
import io.reactivex.Single
import jp.co.soramitsu.common.data.network.AppLinksProvider
import jp.co.soramitsu.core_db.dao.NodeDao
import jp.co.soramitsu.core_db.dao.UserDao
import jp.co.soramitsu.core_db.model.NodeLocal
import jp.co.soramitsu.core_db.model.UserLocal
import jp.co.soramitsu.fearless_utils.bip39.Bip39
import jp.co.soramitsu.fearless_utils.bip39.MnemonicLength
import jp.co.soramitsu.fearless_utils.encrypt.EncryptionType
import jp.co.soramitsu.fearless_utils.encrypt.KeypairFactory
import jp.co.soramitsu.fearless_utils.junction.JunctionDecoder
import jp.co.soramitsu.fearless_utils.ss58.AddressType
import jp.co.soramitsu.fearless_utils.ss58.SS58Encoder
import jp.co.soramitsu.feature_account_api.domain.interfaces.AccountRepository
import jp.co.soramitsu.feature_account_api.domain.model.AuthType
import jp.co.soramitsu.feature_account_api.domain.model.CryptoType
import jp.co.soramitsu.feature_account_api.domain.model.Node
import jp.co.soramitsu.feature_account_api.domain.model.SourceType
import jp.co.soramitsu.feature_account_api.domain.model.User
import jp.co.soramitsu.feature_account_impl.data.repository.datasource.AccountDataSource
import org.spongycastle.util.encoders.Hex

private val DEFAULT_CRYPTO_TYPE = CryptoType.SR25519

class AccountRepositoryImpl(
    private val accountDataSource: AccountDataSource,
    private val userDao: UserDao,
    private val nodeDao: NodeDao,
    private val bip39: Bip39,
    private val sS58Encoder: SS58Encoder,
    private val junctionDecoder: JunctionDecoder,
    private val keypairFactory: KeypairFactory,
    private val appLinksProvider: AppLinksProvider
) : AccountRepository {

    companion object {
        val DEFAULT_NODES_LIST = listOf(
            NodeLocal(
                0,
                "Kusama Parity Node",
                "wss://kusama-rpc.polkadot.io",
                Node.NetworkType.KUSAMA.ordinal,
                true
            ),
            NodeLocal(
                1,
                "Polkadot Parity Node", "wss://rpc.polkadot.io",
                Node.NetworkType.POLKADOT.ordinal,
                true
            ),
            NodeLocal(
                2,
                "Westend Parity Node",
                "wss://westend-rpc.polkadot.io",
                Node.NetworkType.WESTEND.ordinal,
                true
            )
        )
    }

    override fun getTermsAddress(): Single<String> {
        return Single.just(appLinksProvider.termsUrl)
    }

    override fun getPrivacyAddress(): Single<String> {
        return Single.just(appLinksProvider.privacyUrl)
    }

    override fun getEncryptionTypes(): Single<List<CryptoType>> {
        return Single.just(listOf(CryptoType.SR25519, CryptoType.ED25519, CryptoType.ECDSA))
    }

    override fun getNodes(): Observable<List<Node>> {
        return nodeDao.getNodes()
            .doOnNext {
                if (it.isEmpty()) nodeDao.insert(DEFAULT_NODES_LIST)
            }.map { it.map(::mapNodeLocalToNode) }
    }

    override fun getSelectedNode(): Single<Node> {
        return Single.fromCallable {
            accountDataSource.getSelectedNode() ?: mapNodeLocalToNode(DEFAULT_NODES_LIST.first())
        }
    }

    override fun saveNode(node: Node): Completable {
        return Completable.fromCallable {
            nodeDao.insert(mapNetworkToNodeLocal(node))
        }
    }

    override fun removeNode(node: Node): Completable {
        return Completable.fromCallable {
            nodeDao.remove(node.link)
        }
    }

    override fun selectNode(node: Node): Completable {
        return Completable.fromAction {
            accountDataSource.saveSelectedNode(node)
        }
    }

    private fun mapNodeLocalToNode(it: NodeLocal): Node {
        val networkType = Node.NetworkType.values()[it.networkType]

        return Node(it.name, networkType, it.link, it.isDefault)
    }

    private fun mapNetworkToNodeLocal(it: Node): NodeLocal {
        return NodeLocal(0, it.name, it.link, it.networkType.ordinal, it.isDefault)
    }

    override fun selectAccount(account: User): Completable {
        return Completable.fromCallable {
            accountDataSource.saveSelectedAccount(account)
        }
    }

    override fun getSelectedAccount(): Single<User> {
        return Single.fromCallable {
            accountDataSource.getSelectedAccount()
                ?: throw IllegalArgumentException("No account selected")
        }
    }

    override fun getPreferredCryptoType(): Single<CryptoType> {
        return Single.fromCallable {
            accountDataSource.getSelectedAccount()?.cryptoType ?: DEFAULT_CRYPTO_TYPE
        }
    }

    override fun isAccountSelected(): Single<Boolean> {
        return Single.fromCallable {
            accountDataSource.getSelectedAccount() != null
        }
    }

    override fun removeAccount(account: User): Completable {
        return Completable.fromCallable {
            userDao.remove(account.address)
        }
    }

    override fun createAccount(
        accountName: String,
        mnemonic: String,
        encryptionType: CryptoType,
        derivationPath: String,
        node: Node
    ): Completable {
        return saveAccountData(
            accountName,
            mnemonic,
            derivationPath,
            encryptionType,
            node.networkType
        ).flatMapCompletable(::selectAccount)
            .andThen(selectNode(node))
    }

    override fun getAccounts(): Single<List<User>> {
        return userDao.getUsers()
            .map {
                it.map {
                    mapUserLocalToUser(it)
                }
            }
    }

    private fun mapUserLocalToUser(it: UserLocal): User {
        return with(it) {
            User(
                address = address,
                username = username,
                publicKey = publicKey,
                cryptoType = CryptoType.values()[cryptoType],
                networkType = Node.NetworkType.values()[it.networkType]
            )
        }
    }

    override fun getSourceTypes(): Single<List<SourceType>> {
        return Single.just(
            listOf(
                SourceType.MNEMONIC_PASSPHRASE,
                SourceType.RAW_SEED,
                SourceType.KEYSTORE
            )
        )
    }

    override fun importFromMnemonic(
        keyString: String,
        username: String,
        derivationPath: String,
        selectedEncryptionType: CryptoType,
        node: Node
    ): Completable {
        return saveAccountData(
            username,
            keyString,
            derivationPath,
            selectedEncryptionType,
            node.networkType
        )
            .flatMapCompletable(::selectAccount)
            .andThen(selectNode(node))
    }

    override fun importFromSeed(
        keyString: String,
        username: String,
        derivationPath: String,
        selectedEncryptionType: CryptoType,
        node: Node
    ): Completable {
        return Single.fromCallable {
            val keys = keypairFactory.generate(
                mapCryptoTypeToEncryption(selectedEncryptionType),
                Hex.decode(keyString),
                derivationPath
            )
            val addressType = mapNetworkTypeToAddressType(node.networkType)
            val address = sS58Encoder.encode(keys.publicKey, addressType)

            accountDataSource.saveDerivationPath(derivationPath, address)
            accountDataSource.saveSeed(Hex.decode(keyString), address)
            accountDataSource.setMnemonicIsBackedUp(true)

            val publicKeyEncoded = Hex.toHexString(keys.publicKey)

            val userLocal = UserLocal(
                address = address,
                username = username,
                publicKey = publicKeyEncoded,
                cryptoType = selectedEncryptionType.ordinal,
                networkType = node.networkType.ordinal
            )

            userDao.insert(userLocal)

            User(address, username, publicKeyEncoded, selectedEncryptionType, node.networkType)
        }
            .flatMapCompletable(::selectAccount)
            .andThen(selectNode(node))
    }

    private fun mapCryptoTypeToEncryption(cryptoType: CryptoType): EncryptionType {
        return when (cryptoType) {
            CryptoType.SR25519 -> EncryptionType.SR25519
            CryptoType.ED25519 -> EncryptionType.ED25519
            CryptoType.ECDSA -> EncryptionType.ECDSA
        }
    }

    private fun mapNetworkTypeToAddressType(networkType: Node.NetworkType): AddressType {
        return when (networkType) {
            Node.NetworkType.KUSAMA -> AddressType.KUSAMA
            Node.NetworkType.POLKADOT -> AddressType.POLKADOT
            Node.NetworkType.WESTEND -> AddressType.WESTEND
        }
    }

    override fun importFromJson(
        json: String,
        password: String,
        networkType: Node.NetworkType
    ): Completable {
        return Completable.complete()
    }

    override fun isCodeSet(): Single<Boolean> {
        return Single.fromCallable {
            accountDataSource.getPinCode() != null
        }
    }

    override fun savePinCode(code: String): Completable {
        return Completable.fromCallable {
            accountDataSource.savePinCode(code)
        }
    }

    override fun isPinCorrect(code: String): Single<Boolean> {
        return Single.fromCallable {
            accountDataSource.getPinCode() == code
        }
    }

    override fun getPinCode(): String? {
        return accountDataSource.getPinCode()
    }

    override fun generateMnemonic(): Single<List<String>> {
        return Single.fromCallable {
            val mnemonic = bip39.generateMnemonic(MnemonicLength.TWELVE)
            mnemonic.split(" ")
        }
    }

    override fun getAddressId(): Single<ByteArray> {
        return Single.fromCallable {
            val user = accountDataSource.getSelectedAccount()
                ?: throw IllegalArgumentException("No selected account found")

            val addressType = mapNetworkTypeToAddressType(user.networkType)

            sS58Encoder.decode(user.address, addressType)
        }
    }

    override fun isBiometricEnabled(): Single<Boolean> {
        return Single.fromCallable {
            accountDataSource.getAuthType() == AuthType.BIOMETRY
        }
    }

    override fun setBiometricOn(): Completable {
        return Completable.fromAction {
            accountDataSource.saveAuthType(AuthType.BIOMETRY)
        }
    }

    override fun setBiometricOff(): Completable {
        return Completable.fromAction {
            accountDataSource.saveAuthType(AuthType.PINCODE)
        }
    }

    private fun saveAccountData(
        accountName: String,
        mnemonic: String,
        derivationPath: String,
        cryptoType: CryptoType,
        networkType: Node.NetworkType
    ): Single<User> {
        return Single.fromCallable {
            val entropy = bip39.generateEntropy(mnemonic)
            val password = junctionDecoder.getPassword(derivationPath)
            val seed = bip39.generateSeed(entropy, password)
            val keys =
                keypairFactory.generate(mapCryptoTypeToEncryption(cryptoType), seed, derivationPath)
            val addressType = mapNetworkTypeToAddressType(networkType)
            val address = sS58Encoder.encode(keys.publicKey, addressType)

            accountDataSource.saveDerivationPath(derivationPath, address)
            accountDataSource.saveSeed(seed, address)
            accountDataSource.saveEntropy(entropy, address)
            accountDataSource.setMnemonicIsBackedUp(true)

            val publicKeyEncoded = Hex.toHexString(keys.publicKey)

            val userLocal = UserLocal(
                address = address,
                username = accountName,
                publicKey = publicKeyEncoded,
                cryptoType = cryptoType.ordinal,
                networkType = networkType.ordinal
            )

            userDao.insert(userLocal)

            User(address, accountName, publicKeyEncoded, cryptoType, networkType)
        }
    }
}