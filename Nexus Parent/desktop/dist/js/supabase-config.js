/**
 * Supabase Configuration
 * Shared configuration for Nexus Parent Desktop
 */

const supabaseConfig = {
  url: "https://ddaakqjxqcrkmovefagx.supabase.co",
  anonKey: "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJpc3MiOiJzdXBhYmFzZSIsInJlZiI6ImRkYWFrcWp4cWNya21vdmVmYWd4Iiwicm9sZSI6ImFub24iLCJpYXQiOjE3NzEyMDg0MjUsImV4cCI6MjA4Njc4NDQyNX0.AOq4smykTU9g3IfHiwEK90r8g-Qfu3VJl6wOaondrms",
  projectId: "ddaakqjxqcrkmovefagx"
};

// Export config to window
window.supabaseConfig = supabaseConfig;

// Initialize Supabase client
if (window.supabase && window.supabase.createClient) {
  window.supabaseClient = window.supabase.createClient(
    supabaseConfig.url,
    supabaseConfig.anonKey
  );
  console.log('✅ Supabase client initialized');
} else {
  console.error('❌ Supabase SDK not loaded');
}

console.log('✅ Supabase config loaded');
