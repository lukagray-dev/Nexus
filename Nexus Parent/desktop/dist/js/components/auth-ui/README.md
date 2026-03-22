# Authentication UI Component

Frontend UI component for the authentication system with sign-in, sign-up, and user info screens.

## Files

- `auth-ui.html` - HTML templates for all auth screens
- `auth-ui.css` - Styling with golden theme and glassmorphism
- `auth-ui.js` - UI controller and loader

## Screens

### 1. Sign-In Screen
- Email/password input fields
- Google OAuth button
- Link to sign-up screen
- Error message display

### 2. Sign-Up Screen
- Full name input
- Email input with OTP verification section
- Password and confirm password fields
- OTP verification UI with timer
- Link to sign-in screen
- Error message display

### 3. User Info Screen
- Display user name and email with gradient styling
- Continue to dashboard button
- Logout button

## Styling

### Theme
- **Primary Color**: `#fbbf24` (Golden)
- **Background**: `#000` (Black)
- **Card Background**: `rgba(15, 15, 15, 0.85)`
- **Border**: `rgba(251, 191, 36, 0.2)`

### Effects
- Glassmorphism with backdrop blur
- Gradient text effects
- Smooth fade-in animations
- Particles background
- Responsive design

## Usage

The component is automatically loaded by `AuthUI` class:

```javascript
const authUI = new AuthUI();
await authUI.load();
```

This injects the HTML and loads the CSS into the page before the connect screen.

## DOM Structure

```html
<div id="auth-screen">
  <canvas id="auth-particles-canvas"></canvas>
  <div class="auth-container">
    <div id="auth-signin" class="auth-view">...</div>
    <div id="auth-signup" class="auth-view hidden">...</div>
    <div id="auth-userinfo" class="auth-view hidden">...</div>
  </div>
</div>
```

## CSS Classes

- `.auth-screen` - Full screen container
- `.auth-container` - Centered content wrapper
- `.auth-view` - Individual screen container
- `.glass-card` - Glassmorphism card
- `.brand` - Gradient title
- `.form` - Form container
- `.input` - Input field styling
- `.primary-btn` - Primary action button
- `.secondary-btn` - Secondary action button
- `.error-message` - Error display
- `.otp-section` - OTP verification UI
- `.user-info-content` - User profile display

## Responsive

- Mobile-first design
- Adjusts padding on small screens
- Maximum width: 480px
- Full width on mobile devices
