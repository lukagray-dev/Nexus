/**
 * Splash Screen Handler
 * Manages the animated splash screen on app startup
 */

(function() {
    'use strict';

    // Create floating particles
    function createParticles() {
        const particlesContainer = document.querySelector('.splash-particles');
        if (!particlesContainer) return;

        const particleCount = 20;
        
        for (let i = 0; i < particleCount; i++) {
            const particle = document.createElement('div');
            particle.className = 'splash-particle';
            
            // Random position
            particle.style.left = Math.random() * 100 + '%';
            particle.style.top = Math.random() * 100 + '%';
            
            // Random animation delay
            particle.style.animationDelay = Math.random() * 5 + 's';
            
            // Random animation duration
            particle.style.animationDuration = (8 + Math.random() * 4) + 's';
            
            // Random size
            const size = 2 + Math.random() * 2;
            particle.style.width = size + 'px';
            particle.style.height = size + 'px';
            
            particlesContainer.appendChild(particle);
        }
    }

    // Hide splash screen after delay
    function hideSplashScreen() {
        const splashScreen = document.getElementById('splash-screen');
        if (!splashScreen) return;

        // Wait 5 seconds then fade out
        setTimeout(() => {
            splashScreen.style.animation = 'splashFadeOut 0.5s ease-out forwards';
            
            // Remove from DOM after animation
            setTimeout(() => {
                splashScreen.remove();
                console.log('✅ Splash screen hidden');
            }, 500);
        }, 5000);
    }

    // Initialize splash screen
    function initSplashScreen() {
        console.log('🎨 Initializing splash screen');
        createParticles();
        hideSplashScreen();
    }

    // Run on DOM ready
    if (document.readyState === 'loading') {
        document.addEventListener('DOMContentLoaded', initSplashScreen);
    } else {
        initSplashScreen();
    }
})();
