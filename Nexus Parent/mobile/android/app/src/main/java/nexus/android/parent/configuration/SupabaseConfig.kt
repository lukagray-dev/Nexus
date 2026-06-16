package nexus.android.parent.configuration

import io.github.jan.supabase.createSupabaseClient
import io.github.jan.supabase.auth.Auth
import io.github.jan.supabase.postgrest.Postgrest

/**
 * Supabase Configuration
 * Shared configuration for Nexus Parent Android
 */
object SupabaseConfig {
    
    private const val SUPABASE_URL = "https://dxhvlbzodiiluysmluka.supabase.co"
    private const val SUPABASE_ANON_KEY = "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJpc3MiOiJzdXBhYmFzZSIsInJlZiI6ImR4aHZsYnpvZGlpbHV5c21sdWthIiwicm9sZSI6ImFub24iLCJpYXQiOjE3ODE2MTQ2ODksImV4cCI6MjA5NzE5MDY4OX0.MzmfWtsfSZHW933Uyfa0twQA88A22HR2G9RkxkXdnrg"
    
    val client = createSupabaseClient(
        supabaseUrl = SUPABASE_URL,
        supabaseKey = SUPABASE_ANON_KEY
    ) {
        install(Auth)
        install(Postgrest)
    }
}
