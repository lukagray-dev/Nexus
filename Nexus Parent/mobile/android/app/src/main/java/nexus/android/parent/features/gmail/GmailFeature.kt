package nexus.android.parent.features.gmail

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import nexus.android.parent.R
import nexus.android.parent.features.BaseFeature

/**
 * GmailFeature - Gmail monitoring feature
 * Allows parents to monitor Gmail activity
 */
class GmailFeature(context: Context) : BaseFeature(context) {

    override fun createView(container: ViewGroup): View {
        // TODO: Implement Gmail feature UI
        return LayoutInflater.from(context).inflate(
            android.R.layout.simple_list_item_1,
            container,
            false
        ).apply {
            findViewById<android.widget.TextView>(android.R.id.text1).text =
                "Gmail Feature - Implementation pending"
        }
    }

    override fun getTitle(): String = context.getString(R.string.feature_gmail)

    override fun getDescription(): String = context.getString(R.string.feature_gmail_desc)
}
