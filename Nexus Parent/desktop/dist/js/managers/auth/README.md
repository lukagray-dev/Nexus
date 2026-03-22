# Authentication Backend

Backend authentication system managing user sign-in, sign-up, and profile data.

## Architecture

```
auth-manager.js (Coordinator)
    ├── signin-handler.js (Sign-in logic)
    ├── signup-handler.js (Sign-up with OTP)
    └── user-info-handler.js (Profile management)
```

## Files

### auth-manager.js
Main coordinator that manages authentication state and flow.

**Responsibilities:**
- Initialize auth system
- Manage auth state changes
- Coordinate between handlers
- Control screen navigation
- Handle Firebase auth state listener

**Key Methods:**
- `init()` - Initialize system
- `showSignIn()` - Display sign-in screen
- `showSignUp()` - Display sign-up screen
- `showUserInfo()` - Display user profile
- `proceedToConnect()` - Navigate to connection screen
- `signOut()` - Sign out user
- `getCurrentUser()` - Get current user
- `isAuthenticated()` - Check auth status

### signin-handler.js
Handles user sign-in functionality.

**Features:**
- Email/password authentication
- Google OAuth sign-in
- Error handling
- Loading states

**Key Methods:**
- `handleEmailSignIn(e)` - Process email/password sign-in
- `handleGoogleSignIn()` - Process Google OAuth
- `handleSignInError(error)` - Handle and display errors
- `setLoading(loading)` - Update UI loading state

### signup-handler.js
Handles user registration with OTP verification.

**Features:**
- User registration form
- Email verification with OTP
- Password validation
- Firebase user creation
- User data storage

**Key Methods:**
- `handleSignUp(e)` - Process registration
- `sendOTP()` - Send verification code
- `verifyOTP()` - Verify entered code
- `startOTPTimer()` - Countdown timer
- `isValidEmail(email)` - Email validation

**OTP System:**
- 6-digit code generation
- 5-minute expiration
- Resend functionality
- Currently logs to console (dev mode)
- Production: integrate email service

### user-info-handler.js
Manages user profile data and display.

**Features:**
- Load user data from Firebase
- Display user information
- Update user profile
- Handle logout

**Key Methods:**
- `loadUserData(user)` - Fetch from database
- `updateUserInfoDisplay(user)` - Update UI
- `updateUserProfile(updates)` - Save changes
- `getUserData()` - Get cached data
- `handleLogout()` - Sign out user

## Data Structure

### User Data (Firebase Realtime Database)
```javascript
users/{uid}/
  ├── name: string
  ├── email: string
  ├── createdAt: timestamp
  └── subscription/
      ├── plan: "free" | "premium"
      └── status: "active" | "inactive"
```

## Authentication Flow

```
1. App Initializes
   ↓
2. AuthManager.init()
   ↓
3. Firebase Auth State Listener
   ↓
   ├─ User Signed In
   │  ├─ Load user data
   │  └─ Show user info screen
   │
   └─ No User
      └─ Show sign-in screen
```

## Error Handling

All handlers implement comprehensive error handling:
- Firebase auth errors
- Network errors
- Validation errors
- User-friendly error messages

## Usage

```javascript
// Access auth manager
const authManager = window.nexusApp.authManager;

// Get current user
const user = authManager.getCurrentUser();

// Check authentication
if (authManager.isAuthenticated()) {
  // User is signed in
}

// Sign out
await authManager.signOut();
```

## Firebase Configuration

Requires Firebase setup in `firebase-config.js`:
- Authentication enabled
- Realtime Database enabled
- Google OAuth provider (optional)

## Production Notes

**OTP Email Service:**
Currently logs OTP to console. For production:
1. Set up backend email API
2. Update `signup-handler.js` sendOTP()
3. Configure email templates
4. Add rate limiting

**Security:**
- Implement CAPTCHA for sign-up
- Add rate limiting
- Enable email verification
- Configure security rules in Firebase
