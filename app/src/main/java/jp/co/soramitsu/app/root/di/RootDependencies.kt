package jp.co.soramitsu.app.root.di

import jp.co.soramitsu.feature_account_api.domain.interfaces.AccountRepository

interface RootDependencies {

    fun accountRepository(): AccountRepository
}