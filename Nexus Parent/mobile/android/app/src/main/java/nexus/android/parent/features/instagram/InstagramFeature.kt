package nexus.android.parent.features.instagram

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import nexus.android.parent.R
import nexus.android.parent.features.BaseFeature

class InstagramFeature(context: Context) : BaseFeature(context) {
    override fun createView(container: ViewGroup): View {
        return LayoutInflater.from(context).inflate(android.R.layout.simple_list_item_1, container, false).apply {
            findViewById<android.widget.TextView>(android.R.id.text1).text = "Instagram Feature - Implementation pending"
        }
    }
    override fun getTitle(): String = context.getString(R.string.feature_instagram)
    override fun getDescription(): String = context.getString(R.string.feature_instagram_desc)
}
