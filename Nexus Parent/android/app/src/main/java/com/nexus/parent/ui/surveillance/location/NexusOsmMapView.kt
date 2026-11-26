package com.nexus.parent.ui.surveillance.location

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Point
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.nexus.parent.state.MapLayerMode
import org.osmdroid.config.Configuration
import org.osmdroid.tileprovider.MapTileProviderBasic
import org.osmdroid.tileprovider.tilesource.OnlineTileSourceBase
import org.osmdroid.util.GeoPoint
import org.osmdroid.util.MapTileIndex
import org.osmdroid.views.CustomZoomButtonsController
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Overlay
import org.osmdroid.views.overlay.TilesOverlay
import java.io.File
import kotlin.math.cos

private object NexusTileSources {
    val ESRI_WORLD_IMAGERY = object : OnlineTileSourceBase(
        "EsriWorldImagery",
        0,
        19,
        256,
        "",
        arrayOf("https://server.arcgisonline.com/ArcGIS/rest/services/World_Imagery/MapServer/tile/")
    ) {
        override fun getTileURLString(pMapTileIndex: Long): String {
            val z = MapTileIndex.getZoom(pMapTileIndex)
            val x = MapTileIndex.getX(pMapTileIndex)
            val y = MapTileIndex.getY(pMapTileIndex)
            return "$baseUrl$z/$y/$x"
        }
    }

    val ESRI_REFERENCE_BOUNDARIES = object : OnlineTileSourceBase(
        "EsriWorldBoundaries",
        0,
        19,
        256,
        "",
        arrayOf("https://server.arcgisonline.com/ArcGIS/rest/services/Reference/World_Boundaries_and_Places/MapServer/tile/")
    ) {
        override fun getTileURLString(pMapTileIndex: Long): String {
            val z = MapTileIndex.getZoom(pMapTileIndex)
            val x = MapTileIndex.getX(pMapTileIndex)
            val y = MapTileIndex.getY(pMapTileIndex)
            return "$baseUrl$z/$y/$x"
        }
    }

    val CARTO_DARK_MATTER = object : OnlineTileSourceBase(
        "CartoDbDarkMatter",
        0,
        19,
        256,
        ".png",
        arrayOf(
            "https://a.basemaps.cartocdn.com/dark_all/",
            "https://b.basemaps.cartocdn.com/dark_all/",
            "https://c.basemaps.cartocdn.com/dark_all/"
        )
    ) {
        override fun getTileURLString(pMapTileIndex: Long): String {
            val z = MapTileIndex.getZoom(pMapTileIndex)
            val x = MapTileIndex.getX(pMapTileIndex)
            val y = MapTileIndex.getY(pMapTileIndex)
            return "$baseUrl$z/$x/$y$mImageFilenameEnding"
        }
    }
}

private class TacticalGpsTargetOverlay : Overlay() {

    private var targetPoint: GeoPoint? = null
    private var accuracyMeters: Float = 15f
    private var isLive: Boolean = false

    private val haloFillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(36, 34, 197, 94)
        style = Paint.Style.FILL
    }
    private val haloStrokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(155, 34, 197, 94)
        style = Paint.Style.STROKE
        strokeWidth = 2.5f
    }
    private val ringPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        style = Paint.Style.FILL
    }
    private val dotPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.rgb(34, 197, 94)
        style = Paint.Style.FILL
    }
    private val idleDotPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.rgb(161, 161, 170)
        style = Paint.Style.FILL
    }
    private val reusableScreenPoint = Point()

    fun updateTarget(latitude: Double?, longitude: Double?, accuracy: Float, live: Boolean) {
        targetPoint = if (latitude != null && longitude != null) GeoPoint(latitude, longitude) else null
        accuracyMeters = accuracy.coerceAtLeast(5f)
        isLive = live
    }

    override fun draw(canvas: Canvas, mapView: MapView, shadow: Boolean) {
        if (shadow) return
        val point = targetPoint ?: return

        mapView.projection.toPixels(point, reusableScreenPoint)
        val cx = reusableScreenPoint.x.toFloat()
        val cy = reusableScreenPoint.y.toFloat()

        // Scale equator pixels to target latitude so GPS accuracy halo diameter matches real meters
        val latCos = cos(Math.toRadians(point.latitude)).coerceAtLeast(0.15)
        val equatorPx = mapView.projection.metersToEquatorPixels(accuracyMeters)
        val haloRadiusPx = (equatorPx / latCos.toFloat()).coerceIn(18f, 360f)

        canvas.drawCircle(cx, cy, haloRadiusPx, haloFillPaint)
        canvas.drawCircle(cx, cy, haloRadiusPx, haloStrokePaint)
        canvas.drawCircle(cx, cy, 18f, ringPaint)
        canvas.drawCircle(cx, cy, 12f, if (isLive) dotPaint else idleDotPaint)
    }
}

/**
 * NexusOsmMapView.kt - Native `osmdroid` interactive map matching Desktop Leaflet `map-manager.ts`.
 *
 * Hey friend! Supports live switching between Esri World Imagery + Reference Boundaries (Satellite)
 * and CartoDB Dark Matter vector-style streets, renders the `#22C55E` GPS target marker & accuracy
 * halo directly on the tile canvas, and handles smooth camera panning, center-target, and zoom.
 */
@Composable
fun NexusOsmMapView(
    mapLayer: MapLayerMode,
    latitude: Double?,
    longitude: Double?,
    accuracyMeters: Float,
    isTracking: Boolean,
    centerRequestToken: Long,
    zoomDeltaRequestId: Long,
    zoomDeltaSteps: Int,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    remember(context) {
        configureOsmdroid(context.applicationContext)
        true
    }

    val boundariesOverlay = remember(context) {
        TilesOverlay(
            MapTileProviderBasic(context.applicationContext, NexusTileSources.ESRI_REFERENCE_BOUNDARIES),
            context.applicationContext
        ).apply {
            loadingBackgroundColor = Color.TRANSPARENT
            loadingLineColor = Color.TRANSPARENT
        }
    }
    val targetOverlay = remember { TacticalGpsTargetOverlay() }
    val mapViewHolder = remember { arrayOfNulls<MapView>(1) }

    // Update marker & animate smoothly whenever fresh GPS coordinates arrive
    LaunchedEffect(latitude, longitude, accuracyMeters, isTracking) {
        targetOverlay.updateTarget(latitude, longitude, accuracyMeters, isTracking)
        val view = mapViewHolder[0] ?: return@LaunchedEffect
        if (latitude != null && longitude != null) {
            view.controller.animateTo(GeoPoint(latitude, longitude))
        }
        view.invalidate()
    }

    // Center Target action button handler
    LaunchedEffect(centerRequestToken) {
        if (centerRequestToken == 0L) return@LaunchedEffect
        val view = mapViewHolder[0] ?: return@LaunchedEffect
        val lat = latitude ?: return@LaunchedEffect
        val lng = longitude ?: return@LaunchedEffect
        view.controller.setZoom(16.0)
        view.controller.animateTo(GeoPoint(lat, lng))
    }

    // + / - Zoom button handler
    LaunchedEffect(zoomDeltaRequestId) {
        if (zoomDeltaRequestId == 0L || zoomDeltaSteps == 0) return@LaunchedEffect
        val view = mapViewHolder[0] ?: return@LaunchedEffect
        if (zoomDeltaSteps > 0) view.controller.zoomIn() else view.controller.zoomOut()
    }

    // Lifecycle resume/pause/detach management
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            val map = mapViewHolder[0] ?: return@LifecycleEventObserver
            when (event) {
                Lifecycle.Event.ON_RESUME -> map.onResume()
                Lifecycle.Event.ON_PAUSE -> map.onPause()
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            mapViewHolder[0]?.onDetach()
            mapViewHolder[0] = null
        }
    }

    AndroidView(
        modifier = modifier.fillMaxSize(),
        factory = { ctx ->
            MapView(ctx).apply {
                setMultiTouchControls(true)
                zoomController.setVisibility(CustomZoomButtonsController.Visibility.NEVER)
                setTilesScaledToDpi(true)
                controller.setZoom(15.0)
                val initLat = latitude ?: 22.572645
                val initLng = longitude ?: 88.363892
                controller.setCenter(GeoPoint(initLat, initLng))
                applyMapLayer(this, mapLayer, boundariesOverlay, targetOverlay)
                mapViewHolder[0] = this
            }
        },
        update = { view ->
            targetOverlay.updateTarget(latitude, longitude, accuracyMeters, isTracking)
            applyMapLayer(view, mapLayer, boundariesOverlay, targetOverlay)
            view.invalidate()
        }
    )
}

private fun applyMapLayer(
    mapView: MapView,
    layer: MapLayerMode,
    boundariesOverlay: TilesOverlay,
    targetOverlay: TacticalGpsTargetOverlay
) {
    val desiredSource = if (layer == MapLayerMode.SATELLITE) {
        NexusTileSources.ESRI_WORLD_IMAGERY
    } else {
        NexusTileSources.CARTO_DARK_MATTER
    }
    if (mapView.tileProvider.tileSource.name() != desiredSource.name()) {
        mapView.setTileSource(desiredSource)
    }

    val overlays = mapView.overlays
    if (layer == MapLayerMode.SATELLITE) {
        if (!overlays.contains(boundariesOverlay)) {
            overlays.add(0, boundariesOverlay)
        }
    } else {
        overlays.remove(boundariesOverlay)
    }

    if (!overlays.contains(targetOverlay)) {
        overlays.add(targetOverlay)
    }
}

private fun configureOsmdroid(appContext: Context) {
    val config = Configuration.getInstance()
    config.userAgentValue = appContext.packageName
    val basePath = File(appContext.cacheDir, "osmdroid").apply { mkdirs() }
    val tileCache = File(basePath, "tiles").apply { mkdirs() }
    config.osmdroidBasePath = basePath
    config.osmdroidTileCache = tileCache
}
