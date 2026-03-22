package nexus.android.parent.features.messenger

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import nexus.android.parent.R
import nexus.android.parent.features.BaseFeature

class MessengerFeature(context: Context) : BaseFeature(context) {
    override fun createView(container: ViewGroup): View {
        return LayoutInflater.from(context).inflate(android.R.layout.simple_list_item_1, container, false).apply {
            findViewById<android.widget.TextView>(android.R.id.text1).text = "Messenger Feature - Implementation pending"
        }
    }
    override fun getTitle(): String = context.getString(R.string.feature_messenger)
    override fun getDescription(): String = context.getString(R.string.feature_messenger_desc)
}
