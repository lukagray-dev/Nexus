package nexus.android.parent.features.location

import android.content.Context
import android.graphics.Color
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import com.google.android.material.floatingactionbutton.FloatingActionButton
import kotlinx.coroutines.*
import nexus.android.parent.R
import nexus.android.parent.features.BaseFeature
import nexus.android.parent.webrtc.ConnectionManager
import org.json.JSONObject
import org.osmdroid.config.Configuration
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Marker

/**
 * LocationFeature - Real-time location tracking feature
 * Displays child device location on an interactive map
 */
class LocationFeature(context: Context) : BaseFeature(context) {

    private var mapView: MapView? = null
    private var marker: Marker? = null
    private var locationNameText: TextView? = null
    private var locationCoordsText: TextView? = null
    private var playPauseButton: FloatingActionButton? = null
    
    private var isTracking = false
    private var currentLocation: LocationData? = null
    private var confirmationTimeout: Job? = null
    
    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private var dataChannelJob: Job? = null

    override fun createView(container: ViewGroup): View {
        // Configure OSMDroid
        Configuration.getInstance().userAgentValue = context.packageName
        
        // Container is the panel root, find views from it
        mapView = container.findViewById(R.id.location_map)
        locationNameText = container.findViewById(R.id.location_name)
        locationCoordsText = container.findViewById(R.id.location_coords)
        playPauseButton = container.findViewById(R.id.location_play_pause_btn)
        
        setupMap()
        setupListeners()
        
        // Only start listening if not already listening
        if (dataChannelJob == null || dataChannelJob?.isActive == false) {
            listenForLocationUpdates()
        }
        
        // Restore state
        updateButton(isTracking)
        currentLocation?.let { loc ->
            updateLocation(loc.lat, loc.lng, loc.name)
        }
        
        return container
    }

    private fun setupMap() {
        mapView?.apply {
            setTileSource(TileSourceFactory.MAPNIK)
            setMultiTouchControls(true)
            controller.setZoom(15.0)
            
            // Set initial position (default location)
            val startPoint = GeoPoint(23.5, 87.3)
            controller.setCenter(startPoint)
            
            // Create marker
            marker = Marker(this).apply {
                position = startPoint
                setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
                title = "Waiting for location..."
                
                // Custom marker icon (golden pin)
                icon = context.getDrawable(R.drawable.ic_location_marker)
            }
            overlays.add(marker)
        }
    }

    private fun setupListeners() {
        playPauseButton?.setOnClickListener {
            toggleTracking()
        }
    }

    private fun listenForLocationUpdates() {
        dataChannelJob = scope.launch {
            ConnectionManager.dataChannelEvents.collect { message ->
                try {
                    val json = JSONObject(message)
                    val type = json.optString("type")
                    
                    when (type) {
                        "location", "LOCATION_UPDATE" -> {
                            // Handle location update
                            val lat: Double
                            val lng: Double
                            
                            // Handle both formats
                            if (json.has("coords")) {
                                val coords = json.getJSONArray("coords")
                                lat = coords.getDouble(0)
                                lng = coords.getDouble(1)
                            } else {
                                lat = json.getDouble("latitude")
                                lng = json.getDouble("longitude")
                            }
                            
                            val locationName = json.optString("locationName", null)
                            updateLocation(lat, lng, locationName)
                        }
                    }
                } catch (e: Exception) {
                    // Check for confirmation messages
                    when (message) {
                        "LOCATION_STARTED" -> handleConfirmation("LOCATION_STARTED")
                        "LOCATION_STOPPED" -> handleConfirmation("LOCATION_STOPPED")
                    }
                }
            }
        }
    }

    private fun toggleTracking() {
        if (isTracking) stopTracking() else startTracking()
    }

    private fun startTracking() {
        showLoading()
        ConnectionManager.sendCommand("LOCATE_CHILD")
        
        confirmationTimeout = scope.launch {
            delay(30000)
            hideLoading()
            showError("Location tracking start timeout")
        }
    }

    private fun stopTracking() {
        showLoading()
        ConnectionManager.sendCommand("LOCATE_CHILD_STOP")
        
        confirmationTimeout = scope.launch {
            delay(30000)
            hideLoading()
            showError("Location tracking stop timeout")
        }
    }

    private fun handleConfirmation(message: String) {
        confirmationTimeout?.cancel()
        when (message) {
            "LOCATION_STARTED" -> {
                isTracking = true
                updateButton(true)
                hideLoading()
            }
            "LOCATION_STOPPED" -> {
                isTracking = false
                updateButton(false)
                hideLoading()
            }
        }
    }

    private fun updateLocation(lat: Double, lng: Double, locationName: String?) {
        val geoPoint = GeoPoint(lat, lng)
        
        // Store current location
        currentLocation = LocationData(lat, lng, locationName)
        
        // Update marker
        marker?.apply {
            position = geoPoint
            title = locationName ?: "Unknown Location"
            snippet = "${lat.format(6)}, ${lng.format(6)}"
        }
        
        // Animate map to new location
        mapView?.controller?.animateTo(geoPoint, 15.0, 1500L)
        
        // Update UI
        locationNameText?.text = locationName ?: "Unknown Location"
        locationCoordsText?.text = "${lat.format(6)}, ${lng.format(6)}"
        
        // Refresh map
        mapView?.invalidate()
    }

    private fun showLoading() {
        playPauseButton?.setImageResource(R.drawable.loading_spinner)
        playPauseButton?.isEnabled = false
    }

    private fun hideLoading() {
        playPauseButton?.isEnabled = true
        updateButton(isTracking)
    }

    private fun updateButton(active: Boolean) {
        val iconRes = if (active) {
            android.R.drawable.ic_media_pause
        } else {
            android.R.drawable.ic_media_play
        }
        playPauseButton?.setImageResource(iconRes)
    }

    private fun showError(message: String) {
        locationNameText?.apply {
            text = "Error"
            setTextColor(Color.parseColor("#ef4444"))
        }
        locationCoordsText?.apply {
            text = message
            setTextColor(Color.parseColor("#ef4444"))
        }
        
        // Reset colors after 3 seconds
        scope.launch {
            delay(3000)
            locationNameText?.setTextColor(context.getColor(R.color.text_primary))
            locationCoordsText?.setTextColor(context.getColor(R.color.text_secondary))
        }
    }

    private fun Double.format(decimals: Int): String {
        return "%.${decimals}f".format(this)
    }

    override fun onStop() {
        super.onStop()
        mapView?.onPause()
        dataChannelJob?.cancel()
        confirmationTimeout?.cancel()
    }

    override fun onResume() {
        super.onResume()
        mapView?.onResume()
    }

    override fun getTitle(): String = context.getString(R.string.feature_location)
    override fun getDescription(): String = context.getString(R.string.feature_location_desc)

    data class LocationData(
        val lat: Double,
        val lng: Double,
        val name: String?
    )
}
