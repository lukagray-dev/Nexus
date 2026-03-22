package nexus.android.parent.managers

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import nexus.android.parent.R

/**
 * PanelManager - Manages feature panels in the main content area
 * Handles panel creation, display, and removal with animations
 */
class PanelManager(
    private val context: Context,
    private val contentContainer: LinearLayout,
    private val welcomeSection: LinearLayout,
    private val uiManager: UIManager
) {

    // Track active panels to prevent duplicates
    private val activePanels = mutableMapOf<FeatureType, View>()

    /**
     * Add a feature panel to the content area
     * If panel already exists, scroll to it instead of creating duplicate
     */
    fun addPanel(featureType: FeatureType) {
        // Check if panel already exists
        if (activePanels.containsKey(featureType)) {
            // Scroll to existing panel
            val existingPanel = activePanels[featureType]
            existingPanel?.let { panel ->
                panel.post {
                    val scrollView = contentContainer.parent as? androidx.core.widget.NestedScrollView
                    scrollView?.smoothScrollTo(0, panel.top)
                }
            }
            return
        }

        // Hide welcome text when first panel is added
        if (activePanels.isEmpty()) {
            uiManager.fadeOut(welcomeSection)
        }

        // Create new panel
        val panelView = createPanelView(featureType)
        
        // Add to container
        contentContainer.addView(panelView)
        activePanels[featureType] = panelView

        // Animate panel entrance
        uiManager.fadeIn(panelView)

        // Scroll to new panel
        panelView.post {
            val scrollView = contentContainer.parent as? androidx.core.widget.NestedScrollView
            scrollView?.smoothScrollTo(0, panelView.top)
        }
    }

    /**
     * Remove a feature panel from the content area
     */
    fun removePanel(featureType: FeatureType) {
        val panelView = activePanels[featureType] ?: return
        
        uiManager.fadeOut(panelView) {
            contentContainer.removeView(panelView)
            activePanels.remove(featureType)
            
            // Show welcome text if no panels remain
            if (activePanels.isEmpty()) {
                uiManager.fadeIn(welcomeSection)
            }
        }
    }

    /**
     * Remove all panels
     */
    fun removeAllPanels() {
        activePanels.keys.toList().forEach { featureType ->
            removePanel(featureType)
        }
    }

    /**
     * Check if a panel is active
     */
    fun isPanelActive(featureType: FeatureType): Boolean {
        return activePanels.containsKey(featureType)
    }

    /**
     * Get count of active panels
     */
    fun getActivePanelCount(): Int {
        return activePanels.size
    }

    /**
     * Create a panel view for the given feature type
     */
    private fun createPanelView(featureType: FeatureType): View {
        val inflater = LayoutInflater.from(context)
        
        // Get the specific layout for this feature type
        val layoutRes = getPanelLayoutResource(featureType)
        val panelView = inflater.inflate(layoutRes, contentContainer, false)

        // Setup panel components
        val panelClose = panelView.findViewById<ImageView>(R.id.panel_close)
        val panelContent = panelView.findViewById<ViewGroup>(R.id.panel_content)

        // Setup close button
        panelClose.setOnClickListener {
            removePanel(featureType)
        }

        // Load feature content
        loadFeatureContent(featureType, panelContent)

        return panelView
    }

    /**
     * Get the layout resource for the given feature type
     */
    private fun getPanelLayoutResource(featureType: FeatureType): Int {
        return when (featureType) {
            // Core Features
            FeatureType.CAMERA -> R.layout.panel_camera
            FeatureType.SCREEN -> R.layout.panel_screen
            FeatureType.LOCATION -> R.layout.panel_location
            FeatureType.SMS -> R.layout.panel_sms
            FeatureType.CALLLOG -> R.layout.panel_calllog
            FeatureType.MIC -> R.layout.panel_mic
            FeatureType.NOTIFICATIONS -> R.layout.panel_notifications
            FeatureType.CHATS -> R.layout.panel_chats
            FeatureType.GMAIL -> R.layout.panel_gmail
            FeatureType.FILES -> R.layout.panel_files
            FeatureType.WELLBEING -> R.layout.panel_wellbeing
            FeatureType.APPLOCK -> R.layout.panel_applock
            FeatureType.WALLPAPER -> R.layout.panel_wallpaper
            FeatureType.VIBRATEFLASH -> R.layout.panel_vibrateflash
            // Social Media
            FeatureType.WHATSAPP -> R.layout.panel_whatsapp
            FeatureType.FACEBOOK -> R.layout.panel_facebook
            FeatureType.INSTAGRAM -> R.layout.panel_instagram
            FeatureType.MESSENGER -> R.layout.panel_messenger
            FeatureType.SNAPCHAT -> R.layout.panel_snapchat
            FeatureType.TELEGRAM -> R.layout.panel_telegram
            // AI Assistants
            FeatureType.CHATGPT -> R.layout.panel_chatgpt
            FeatureType.CLAUDE -> R.layout.panel_claude
            FeatureType.GROK -> R.layout.panel_grok
        }
    }

    /**
     * Load feature-specific content into panel
     */
    private fun loadFeatureContent(featureType: FeatureType, container: ViewGroup) {
        // Create feature instance and initialize it with the panel's root view
        val feature = nexus.android.parent.features.FeatureFactory.createFeature(context, featureType)
        // Pass the container's parent (the panel root) so feature can find its views
        val panelRoot = container.parent as View
        feature.createView(panelRoot as ViewGroup)
        // Don't add anything to container - the layout already has everything
    }
}
