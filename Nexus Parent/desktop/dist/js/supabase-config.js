/**
 * Supabase Configuration
 * Shared configuration for Nexus Parent Desktop
 */

const supabaseConfig = {
  url: "https://dxhvlbzodiiluysmluka.supabase.co",
  anonKey: "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJpc3MiOiJzdXBhYmFzZSIsInJlZiI6ImR4aHZsYnpvZGlpbHV5c21sdWthIiwicm9sZSI6ImFub24iLCJpYXQiOjE3ODE2MTQ2ODksImV4cCI6MjA5NzE5MDY4OX0.MzmfWtsfSZHW933Uyfa0twQA88A22HR2G9RkxkXdnrg",
  projectId: "dxhvlbzodiiluysmluka"
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
