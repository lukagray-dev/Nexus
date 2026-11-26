package com.nexus.parent

import android.app.Application
import com.nexus.parent.data.DeviceStateCoordinator
import com.nexus.parent.data.DeviceStorage
import com.nexus.parent.data.SharedPrefsStorageStore
import com.nexus.parent.state.ParentStateHolder
import com.nexus.parent.webrtc.WebRtcManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

/**
 * NexusParentApplication.kt - Application-scoped dependency container for Nexus Parent.
 *
 * Hey friend! Holding `WebRtcManager`, `DeviceStorage`, `DeviceStateCoordinator`, and
 * `ParentStateHolder` at the `Application` level means screen rotations, dark-mode toggles,
 * or minimizing the app into the background never tear down active WebRTC sockets or reset
 * reconnect countdown timers.
 */
class NexusParentApplication : Application() {

    private val appScope = CoroutineScope(Dispatchers.Main.immediate + SupervisorJob())

    val deviceStorage: DeviceStorage by lazy {
        DeviceStorage(SharedPrefsStorageStore(this))
    }

    val webRtcManager: WebRtcManager by lazy {
        WebRtcManager(this)
    }

    val coordinator: DeviceStateCoordinator by lazy {
        DeviceStateCoordinator(
            context = this,
            storage = deviceStorage,
            webRtcManager = webRtcManager,
            scope = appScope
        )
    }

    val stateHolder: ParentStateHolder by lazy {
        val initial = coordinator.buildInitialUiState()
        ParentStateHolder(
            initialState = initial,
            coordinator = coordinator
        ).also { holder ->
            coordinator.attachStateHolder(holder)
        }
    }
}
