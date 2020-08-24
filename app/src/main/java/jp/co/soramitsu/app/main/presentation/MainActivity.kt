package jp.co.soramitsu.app.main.presentation

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.lifecycle.Observer
import jp.co.soramitsu.app.R
import jp.co.soramitsu.app.main.di.MainApi
import jp.co.soramitsu.app.main.di.MainComponent
import jp.co.soramitsu.app.main.navigation.Destination
import jp.co.soramitsu.app.main.navigation.main.MainFragment
import jp.co.soramitsu.app.main.navigation.onboarding.OnboardingFragment
import jp.co.soramitsu.common.base.BaseActivity
import jp.co.soramitsu.common.di.FeatureUtils
import jp.co.soramitsu.common.interfaces.BackButtonListener

class MainActivity : BaseActivity<MainViewModel>() {

    companion object {

        fun start(context: Context) {
            val intent = Intent(context, MainActivity::class.java)
            context.startActivity(intent)
        }
    }

    override fun inject() {
        FeatureUtils.getFeature<MainComponent>(this, MainApi::class.java)
            .mainActivityComponentFactory()
            .create(this)
            .inject(this)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        processJsonOpenIntent()
    }

    private fun processJsonOpenIntent() {
        if (Intent.ACTION_VIEW == intent.action && intent.type != null) {
            if ("application/json" == intent.type) {
                val file = this.contentResolver.openInputStream(intent.data!!)
                val content = file?.reader(Charsets.UTF_8)?.readText()
                viewModel.jsonFileOpened(content)
            }
        }
    }

    override fun layoutResource(): Int {
        return R.layout.activity_main
    }

    override fun initViews() {
    }

    override fun subscribe(viewModel: MainViewModel) {
        viewModel.navigationDestinationLiveData.observe(this, Observer { destination ->
            val destinationFragment = if (Destination.ONBOARDING == destination) {
                OnboardingFragment.newInstance()
            } else {
                MainFragment.newInstance()
            }
            supportFragmentManager.beginTransaction()
                .replace(R.id.container, destinationFragment)
                .commit()
        })
    }

    override fun onDestroy() {
        super.onDestroy()
        /*navController?.let {
            navigator.detachNavController(it)
        }*/
    }

    override fun onBackPressed() {
        val navHostFragment = supportFragmentManager.findFragmentById(R.id.nav_host_fragment)
        navHostFragment?.childFragmentManager?.let {
            if (it.fragments.isNotEmpty()) {
                val currentFragment = it.fragments.last()
                if (currentFragment is BackButtonListener) {
                    currentFragment.onBackButtonPressed()
                } else {
                    super.onBackPressed()
                }
            }
        }
    }
}