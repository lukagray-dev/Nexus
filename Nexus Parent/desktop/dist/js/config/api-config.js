/**
 * API Configuration
 * Centralized configuration for all API endpoints
 */

const API_CONFIG = {
    // Email Service API (Vercel deployment)
    EMAIL_SERVICE: {
        BASE_URL: 'https://e-mail-service.vercel.app',
        ENDPOINTS: {
            SEND_OTP: '/send-otp',
            VERIFY_OTP: '/verify-otp',
            SEND_PASSWORD_RESET: '/send-password-reset',
            HEALTH: '/'
        }
    },

    // For local development, uncomment this:
    // EMAIL_SERVICE: {
    //     BASE_URL: 'http://localhost:3000',
    //     ENDPOINTS: {
    //         SEND_OTP: '/send-otp',
    //         VERIFY_OTP: '/verify-otp',
    //         SEND_PASSWORD_RESET: '/send-password-reset',
    //         HEALTH: '/'
    //     }
    // },

    // Helper methods
    getEmailServiceUrl(endpoint) {
        return `${this.EMAIL_SERVICE.BASE_URL}${this.EMAIL_SERVICE.ENDPOINTS[endpoint]}`;
    }
};

// Export for use in other modules
window.API_CONFIG = API_CONFIG;
