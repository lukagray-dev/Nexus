package nexus.android.parent.configuration

import io.github.jan.supabase.createSupabaseClient
import io.github.jan.supabase.auth.Auth
import io.github.jan.supabase.postgrest.Postgrest

/**
 * Supabase Configuration
 * Shared configuration for Nexus Parent Android
 */
object SupabaseConfig {
    
    private const val SUPABASE_URL = "https://ddaakqjxqcrkmovefagx.supabase.co"
    private const val SUPABASE_ANON_KEY = "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJpc3MiOiJzdXBhYmFzZSIsInJlZiI6ImRkYWFrcWp4cWNya21vdmVmYWd4Iiwicm9sZSI6ImFub24iLCJpYXQiOjE3NzEyMDg0MjUsImV4cCI6MjA4Njc4NDQyNX0.AOq4smykTU9g3IfHiwEK90r8g-Qfu3VJl6wOaondrms"
    
    val client = createSupabaseClient(
        supabaseUrl = SUPABASE_URL,
        supabaseKey = SUPABASE_ANON_KEY
    ) {
        install(Auth)
        install(Postgrest)
    }
}
