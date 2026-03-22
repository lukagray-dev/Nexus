package nexus.android.parent

import android.app.Application
import com.google.firebase.FirebaseApp

/**
 * NexusParentApp - Application class for global initialization
 */
class NexusParentApp : Application() {
    
    override fun onCreate() {
        super.onCreate()
        
        // Initialize Firebase
        FirebaseApp.initializeApp(this)
    }
}
