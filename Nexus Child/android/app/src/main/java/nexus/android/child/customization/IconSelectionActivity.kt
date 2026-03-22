package nexus.android.child.customization

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import nexus.android.child.R
import nexus.android.child.utils.AppCustomizationManager

/**
 * Activity for selecting app icon from a grid of pre-made icons.
 */
class IconSelectionActivity : AppCompatActivity() {
    
    private lateinit var iconGrid: RecyclerView
    private var currentSelectedIcon: AppCustomizationManager.IconType = AppCustomizationManager.IconType.DEFAULT
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_icon_selection)
        
        currentSelectedIcon = AppCustomizationManager.getCurrentIconType(this)
        
        iconGrid = findViewById(R.id.icon_grid)
        iconGrid.layoutManager = GridLayoutManager(this, 3)
        iconGrid.adapter = IconAdapter(getAvailableIcons()) { iconType ->
            onIconSelected(iconType)
        }
    }
    
    private fun getAvailableIcons(): List<AppCustomizationManager.IconType> {
        return listOf(
            AppCustomizationManager.IconType.DEFAULT,
            AppCustomizationManager.IconType.GMAIL,
            AppCustomizationManager.IconType.WHATSAPP,
            AppCustomizationManager.IconType.INSTAGRAM,
            AppCustomizationManager.IconType.SETTINGS,
            AppCustomizationManager.IconType.YOUTUBE_MUSIC,
            AppCustomizationManager.IconType.CALCULATOR,
            AppCustomizationManager.IconType.TELEGRAM,
            AppCustomizationManager.IconType.SECURITY,
            AppCustomizationManager.IconType.CHATGPT,
            AppCustomizationManager.IconType.GOOGLE,
            AppCustomizationManager.IconType.GAME_BOOSTER,
            AppCustomizationManager.IconType.GEMINI,
            AppCustomizationManager.IconType.GPAY,
            AppCustomizationManager.IconType.MAPS,
            AppCustomizationManager.IconType.MESSAGES,
            AppCustomizationManager.IconType.GOOGLE_ONE,
            AppCustomizationManager.IconType.PLAY_STORE,
            AppCustomizationManager.IconType.SPOTIFY,
            AppCustomizationManager.IconType.X,
            AppCustomizationManager.IconType.YOUTUBE
        )
    }
    
    private fun onIconSelected(iconType: AppCustomizationManager.IconType) {
        // Show dialog to ask about fake UI
        showFakeUIChoiceDialog(iconType)
    }

    private fun showFakeUIChoiceDialog(iconType: AppCustomizationManager.IconType) {
        val dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_fake_ui_choice, null)
        
        val dialog = com.google.android.material.dialog.MaterialAlertDialogBuilder(this)
            .setView(dialogView)
            .setCancelable(true)
            .show()

        val btnIconOnly = dialogView.findViewById<com.google.android.material.button.MaterialButton>(R.id.btn_icon_only)
        val btnIconAndUI = dialogView.findViewById<com.google.android.material.button.MaterialButton>(R.id.btn_icon_and_ui)

        btnIconOnly.setOnClickListener {
            dialog.dismiss()
            applyIconChange(iconType, enableFakeUI = false)
        }

        btnIconAndUI.setOnClickListener {
            dialog.dismiss()
            applyIconChange(iconType, enableFakeUI = true)
        }
    }

    private fun applyIconChange(iconType: AppCustomizationManager.IconType, enableFakeUI: Boolean) {
        val success = AppCustomizationManager.changeAppIcon(this, iconType)
        if (success) {
            nexus.android.child.utils.FakeUIManager.setFakeUIEnabled(this, iconType, enableFakeUI)
            currentSelectedIcon = iconType
            iconGrid.adapter?.notifyDataSetChanged()
            val iconName = getIconDisplayName(iconType)
            val message = if (enableFakeUI) {
                "Icon and fake UI changed to \"$iconName\". Restart the app launcher to see the changes."
            } else {
                "App icon changed to \"$iconName\". Restart the app launcher to see the changes."
            }
            Toast.makeText(this, message, Toast.LENGTH_LONG).show()
        } else {
            Toast.makeText(this, "Failed to change app icon.", Toast.LENGTH_SHORT).show()
        }
    }
    
    private fun getIconDisplayName(iconType: AppCustomizationManager.IconType): String {
        return when (iconType) {
            AppCustomizationManager.IconType.DEFAULT -> "Child"
            AppCustomizationManager.IconType.GMAIL -> "Gmail"
            AppCustomizationManager.IconType.WHATSAPP -> "WhatsApp"
            AppCustomizationManager.IconType.INSTAGRAM -> "Instagram"
            AppCustomizationManager.IconType.SETTINGS -> "Settings"
            AppCustomizationManager.IconType.YOUTUBE_MUSIC -> "YouTube Music"
            AppCustomizationManager.IconType.CALCULATOR -> "Calculator"
            AppCustomizationManager.IconType.TELEGRAM -> "Telegram"
            AppCustomizationManager.IconType.SECURITY -> "Security"
            AppCustomizationManager.IconType.CHATGPT -> "ChatGPT"
            AppCustomizationManager.IconType.GOOGLE -> "Google"
            AppCustomizationManager.IconType.GAME_BOOSTER -> "Game Booster"
            AppCustomizationManager.IconType.GEMINI -> "Gemini"
            AppCustomizationManager.IconType.GPAY -> "Google Pay"
            AppCustomizationManager.IconType.MAPS -> "Maps"
            AppCustomizationManager.IconType.MESSAGES -> "Messages"
            AppCustomizationManager.IconType.GOOGLE_ONE -> "Google One"
            AppCustomizationManager.IconType.PLAY_STORE -> "Play Store"
            AppCustomizationManager.IconType.SPOTIFY -> "Spotify"
            AppCustomizationManager.IconType.X -> "X"
            AppCustomizationManager.IconType.YOUTUBE -> "YouTube"
        }
    }
    
    private inner class IconAdapter(
        private val icons: List<AppCustomizationManager.IconType>,
        private val onIconClick: (AppCustomizationManager.IconType) -> Unit
    ) : RecyclerView.Adapter<IconAdapter.IconViewHolder>() {
        
        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): IconViewHolder {
            val view = LayoutInflater.from(parent.context)
                .inflate(R.layout.item_icon_selection, parent, false)
            return IconViewHolder(view)
        }
        
        override fun onBindViewHolder(holder: IconViewHolder, position: Int) {
            val iconType = icons[position]
            holder.bind(iconType, iconType == currentSelectedIcon) {
                onIconClick(iconType)
            }
        }
        
        override fun getItemCount(): Int = icons.size
        
        inner class IconViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
            private val iconImage: ImageView = itemView.findViewById(R.id.icon_image)
            private val iconName: TextView = itemView.findViewById(R.id.icon_name)
            private val selectedIndicator: View = itemView.findViewById(R.id.selected_indicator)
            
            fun bind(iconType: AppCustomizationManager.IconType, isSelected: Boolean, onClick: () -> Unit) {
                val iconResId = getIconResourceId(iconType)
                iconImage.setImageResource(iconResId)
                iconName.text = getIconDisplayName(iconType)
                selectedIndicator.visibility = if (isSelected) View.VISIBLE else View.GONE
                
                itemView.setOnClickListener { onClick() }
            }
            
            private fun getIconResourceId(iconType: AppCustomizationManager.IconType): Int {
                return when (iconType) {
                    AppCustomizationManager.IconType.DEFAULT -> R.mipmap.ic_launcher
                    AppCustomizationManager.IconType.GMAIL -> R.mipmap.ic_app_icon_gmail
                    AppCustomizationManager.IconType.WHATSAPP -> R.mipmap.ic_app_icon_whatsapp
                    AppCustomizationManager.IconType.INSTAGRAM -> R.mipmap.ic_app_icon_instagram
                    AppCustomizationManager.IconType.SETTINGS -> R.mipmap.ic_app_icon_settings
                    AppCustomizationManager.IconType.YOUTUBE_MUSIC -> R.mipmap.ic_app_icon_youtube_music
                    AppCustomizationManager.IconType.CALCULATOR -> R.mipmap.ic_app_icon_calculator
                    AppCustomizationManager.IconType.TELEGRAM -> R.mipmap.ic_app_icon_telegram
                    AppCustomizationManager.IconType.SECURITY -> R.mipmap.ic_app_icon_security
                    AppCustomizationManager.IconType.CHATGPT -> R.mipmap.ic_app_icon_chatgpt
                    AppCustomizationManager.IconType.GOOGLE -> R.mipmap.ic_app_icon_google
                    AppCustomizationManager.IconType.GAME_BOOSTER -> R.mipmap.ic_app_icon_game_booster
                    AppCustomizationManager.IconType.GEMINI -> R.mipmap.ic_app_icon_gemini
                    AppCustomizationManager.IconType.GPAY -> R.mipmap.ic_app_icon_gpay
                    AppCustomizationManager.IconType.MAPS -> R.mipmap.ic_app_icon_maps
                    AppCustomizationManager.IconType.MESSAGES -> R.mipmap.ic_app_icon_messages
                    AppCustomizationManager.IconType.GOOGLE_ONE -> R.mipmap.ic_app_icon_google_one
                    AppCustomizationManager.IconType.PLAY_STORE -> R.mipmap.ic_app_icon_play_store
                    AppCustomizationManager.IconType.SPOTIFY -> R.mipmap.ic_app_icon_spotify
                    AppCustomizationManager.IconType.X -> R.mipmap.ic_app_icon_x
                    AppCustomizationManager.IconType.YOUTUBE -> R.mipmap.ic_app_icon_youtube
                }
            }
            
            private fun getIconDisplayName(iconType: AppCustomizationManager.IconType): String {
                return when (iconType) {
                    AppCustomizationManager.IconType.DEFAULT -> "Child"
                    AppCustomizationManager.IconType.GMAIL -> "Gmail"
                    AppCustomizationManager.IconType.WHATSAPP -> "WhatsApp"
                    AppCustomizationManager.IconType.INSTAGRAM -> "Instagram"
                    AppCustomizationManager.IconType.SETTINGS -> "Settings"
                    AppCustomizationManager.IconType.YOUTUBE_MUSIC -> "YouTube Music"
                    AppCustomizationManager.IconType.CALCULATOR -> "Calculator"
                    AppCustomizationManager.IconType.TELEGRAM -> "Telegram"
                    AppCustomizationManager.IconType.SECURITY -> "Security"
                    AppCustomizationManager.IconType.CHATGPT -> "ChatGPT"
                    AppCustomizationManager.IconType.GOOGLE -> "Google"
                    AppCustomizationManager.IconType.GAME_BOOSTER -> "Game Booster"
                    AppCustomizationManager.IconType.GEMINI -> "Gemini"
                    AppCustomizationManager.IconType.GPAY -> "Google Pay"
                    AppCustomizationManager.IconType.MAPS -> "Maps"
                    AppCustomizationManager.IconType.MESSAGES -> "Messages"
                    AppCustomizationManager.IconType.GOOGLE_ONE -> "Google One"
                    AppCustomizationManager.IconType.PLAY_STORE -> "Play Store"
                    AppCustomizationManager.IconType.SPOTIFY -> "Spotify"
                    AppCustomizationManager.IconType.X -> "X"
                    AppCustomizationManager.IconType.YOUTUBE -> "YouTube"
                }
            }
        }
    }
}

