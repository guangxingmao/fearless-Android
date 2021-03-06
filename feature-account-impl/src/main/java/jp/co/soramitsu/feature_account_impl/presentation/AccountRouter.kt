package jp.co.soramitsu.feature_account_impl.presentation

import jp.co.soramitsu.feature_account_api.domain.model.Node
import jp.co.soramitsu.feature_account_impl.presentation.exporting.json.confirm.ExportJsonConfirmPayload
import jp.co.soramitsu.feature_account_impl.presentation.mnemonic.confirm.ConfirmMnemonicPayload

interface AccountRouter {

    fun backToCreateAccountScreen()

    fun backToWelcomeScreen()

    fun openMain()

    fun openDestination(destination: Int)

    fun openCreatePincode()

    fun openConfirmMnemonicOnCreate(confirmMnemonicPayload: ConfirmMnemonicPayload)

    fun openAboutScreen()

    fun openTermsScreen()

    fun openPrivacyScreen()

    fun backToBackupMnemonicScreen()

    fun backToProfileScreen()

    fun back()

    fun openAccounts()

    fun openNodes()

    fun openLanguages()

    fun openAddAccount()

    fun openAccountDetails(address: String)

    fun openEditAccounts()

    fun backToMainScreen()

    fun openNodeDetails(nodeId: Int, isSelected: Boolean)

    fun openAddNode()

    fun createAccountForNetworkType(networkType: Node.NetworkType)

    fun openExportMnemonic(accountAddress: String)

    fun openExportSeed(accountAddress: String)

    fun openConfirmMnemonicOnExport(mnemonic: List<String>)

    fun openExportJsonPassword(accountAddress: String)

    fun returnToMain()

    fun openExportJsonConfirm(payload: ExportJsonConfirmPayload)

    fun finishExportFlow()

    fun openChangePinCode()
}