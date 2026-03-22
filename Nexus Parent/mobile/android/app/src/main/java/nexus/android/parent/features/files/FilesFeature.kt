package nexus.android.parent.features.files

import android.content.Context
import android.view.View
import android.view.ViewGroup
import nexus.android.parent.R
import nexus.android.parent.features.BaseFeature

/**
 * FilesFeature - File monitoring feature
 * This feature is only available on desktop app
 * Mobile app shows a message directing users to desktop
 */
class FilesFeature(context: Context) : BaseFeature(context) {

    override fun createView(container: ViewGroup): View {
        // The layout already contains the desktop-only message
        // No additional setup needed
        return container
    }

    override fun getTitle(): String = context.getString(R.string.feature_files)

    override fun getDescription(): String = context.getString(R.string.feature_files_desc)
}
