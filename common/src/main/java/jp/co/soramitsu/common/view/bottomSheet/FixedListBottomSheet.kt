package jp.co.soramitsu.common.view.bottomSheet

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import androidx.annotation.DrawableRes
import androidx.annotation.LayoutRes
import androidx.annotation.StringRes
import com.google.android.material.bottomsheet.BottomSheetDialog
import jp.co.soramitsu.common.R
import jp.co.soramitsu.common.utils.inflateChild
import jp.co.soramitsu.common.utils.setDrawableStart
import kotlinx.android.synthetic.main.bottom_sheeet_fixed_list.fixedListSheetItemContainer
import kotlinx.android.synthetic.main.bottom_sheeet_fixed_list.fixedListSheetTitle
import kotlinx.android.synthetic.main.item_sheet_iconic_label.view.itemExternalActionContent

abstract class FixedListBottomSheet(context: Context) : BottomSheetDialog(context, R.style.BottomSheetDialog) {

    init {
        setContentView(LayoutInflater.from(context).inflate(R.layout.bottom_sheeet_fixed_list, null))
    }

    final override fun setContentView(view: View) {
        super.setContentView(view)
    }

    override fun setTitle(@StringRes titleRes: Int) {
        fixedListSheetTitle.setText(titleRes)
    }

    override fun setTitle(title: CharSequence?) {
        fixedListSheetTitle.text = title
    }

    fun item(@LayoutRes layoutRes: Int, builder: (View) -> Unit) {
        val view = fixedListSheetItemContainer.inflateChild(layoutRes)

        builder.invoke(view)

        fixedListSheetItemContainer.addView(view)
    }

    inline fun View.setDismissingClickListener(crossinline listener: (View) -> Unit) {
        setOnClickListener {
            listener.invoke(it)

            dismiss()
        }
    }
}

fun FixedListBottomSheet.item(@DrawableRes icon: Int, @StringRes titleRes: Int, onClick: (View) -> Unit) {
    item(R.layout.item_sheet_iconic_label) { view ->
        view.itemExternalActionContent.setText(titleRes)
        view.itemExternalActionContent.setDrawableStart(icon, widthInDp = 24, tint = R.color.white)

        view.setDismissingClickListener(onClick)
    }
}