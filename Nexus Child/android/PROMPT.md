# Comprehensive Unit Test Suite for Nexus Child Android App

## Objective
Create production-grade JVM unit tests for ALL components in the Nexus Child Android app. Each source file under `app/src/main/java/**` should have a corresponding sibling test file under `app/src/test/java/**` mirroring the package structure.

## Critical Constraints
1. **DO NOT modify any files under `app/src/main/java/**`**
2. **Add tests ONLY under `app/src/test/java/**` mirroring package paths**
3. **DO NOT refactor app code** - tests must adapt to existing behavior
4. **Keep tests deterministic and CI-safe** (no flaky timing/network dependencies)
5. **Use mocks/fakes** for Android runtime and hardware services
6. **Ensure `./gradlew testDebugUnitTest` passes**

## Test Dependencies (Already Added)
```gradle
testImplementation 'junit:junit:4.13.2'
testImplementation 'org.mockito:mockito-core:5.8.0'
testImplementation 'org.mockito.kotlin:mockito-kotlin:5.2.1'
testImplementation 'org.jetbrains.kotlinx:kotlinx-coroutines-test:1.10.2'
testImplementation 'org.robolectric:robolectric:4.11.1'
```

## Project Structure
- **Main source**: `app/src/main/java/nexus/android/child/`
- **Test directory**: `app/src/test/java/nexus/android/child/`
- **Build tool**: Gradle with Kotlin, JUnit 4
- **Android SDK**: Min 28, Target 36, Compile 36

## Components to Test (Priority Order)

### 1. Commands Module (`commands/`)
**Files to test:**
- `CommandHandler.kt` (CommandHandlerImpl class)
- `BackgroundServiceHandlers.kt` (BackgroundServicePermissionHandler, BackgroundServiceExtendedStealthHandler, BackgroundServiceSettingsHandler)
- `StorageContracts.kt` (FileListRequest, FileStreamRequest data classes) ✅ DONE

**Test focus:**
- Command parsing (JSON and plain text)
- All command types: CAMERA_ON/OFF, MIC_ON/OFF, SCREEN_RECORDING, LOCATE_CHILD, SMS, CALLLOG, NOTIFICATION, CHAT, KEYBOARD, STEALTH, SETTINGS, STORAGE, WELLBEING, PARENT_AUDIO, WALLPAPER, VIBRATE, FLASH, APP_LOCK
- DataChannel message sending
- Null controller handling
- Invalid JSON/commands
- Permission checks

### 2. Signaling Module (`signaling/`)
**Files to test:**
- `SignalingClient.kt` - Firebase WebRTC signaling
- `DeviceStatusManager.kt` - Device presence and heartbeat ✅ PARTIAL
- `WebRtcExtensions.kt` - Serialization helpers ✅ DONE

**Test focus:**
- Firebase path operations (sendAnswer, sendIceCandidate, clearSessionData)
- Listener setup and cleanup
- ICE candidate de-duplication
- Offer/answer handling
- Malformed data handling
- Status transitions and heartbeat

### 3. WebRTC Module (`webrtc/`)
**Files to test:**
- `PhantomPeerManager.kt` - PeerConnection lifecycle
- `PeerObserver.kt` - WebRTC event adapter ✅ DONE

**Test focus:**
- Factory initialization
- PeerConnection creation with ICE servers
- SDP operations (createOffer, createAnswer, setRemoteDescription)
- ICE candidate handling
- Suspend function behavior with coroutine test dispatcher
- Null PeerConnection edge cases

### 4. ID Module (`id/`)
**Files to test:**
- `DeviceIdManager.kt` - Device ID generation ✅ PARTIAL (format() tested, generateUniqueDeviceId needs mocking)

**Test focus:**
- Cached ID retrieval
- SharedPreferences storage/retrieval
- Firebase uniqueness check (mock)
- Retry logic with exponential backoff
- 12-digit ID generation
- Metadata storage

### 5. Permissions Module (`permissions/`)
**Files to test:**
- `PermissionHelper.kt` - OEM-specific settings ✅ DONE
- `PermissionManager.kt` - Permission workflow orchestration

**Test focus:**
- Permission flow (foreground → background → special)
- Android version-specific permissions (Q, R, Tiramisu)
- Permission result handling
- Rationale dialogs
- Special permissions (battery, accessibility, notification listener)
- Toast throttling

### 6. Service Module (`service/`)
**Files to test:**
- `BootReceiver.kt` - Boot/restart handler ✅ DONE
- `UnlockReceiver.kt` - Unlock handler ✅ DONE
- `BootServiceRestartReceiver.kt` - Service restart handler
- `PersistentJobService.kt` - JobScheduler for persistence
- `BackgroundService.kt` - Main background service (unit-testable parts)
- `ParentalAccessibilityService.kt` - Accessibility service (contract tests)

**Test focus:**
- Intent action handling
- Service start logic
- Retry mechanisms
- Job scheduling
- Lifecycle methods (onCreate, onStartCommand, onDestroy)
- Static helper methods

### 7. Component Controllers (`components/`)

#### Camera (`components/camera/`)
- `CameraController.kt` - Camera capture and streaming

**Test focus:**
- Permission checks
- Camera start/stop/switch
- Track enable/disable
- Camera enumeration (mock Camera2Enumerator)
- Capture settings validation

#### Location (`components/location/`)
- `LocationController.kt` - Location tracking

**Test focus:**
- Permission checks
- Location tracking start/stop
- Update filtering (distance, time)
- JSON serialization
- DataChannel communication
- FusedLocationProviderClient mocking

#### Microphone (`components/microphone/`)
- `MicrophoneController.kt` - Audio capture

**Test focus:**
- Permission checks
- Audio track management
- Start/stop logic
- WebRTC audio track integration

#### Notification (`components/notification/`)
- `NotificationController.kt` - Notification management
- `NotificationListener.kt` - Notification listener service

**Test focus:**
- Notification posting/cancellation
- Listener service lifecycle
- Notification extraction
- Permission checks

#### Screen (`components/screen/`)
- `ScreenRecordingController.kt` - Screen recording
- `OpusEncoder.kt` - Audio encoding
- `SystemAudioCapturer.kt` - System audio capture

**Test focus:**
- Unit-testable logic only (encoding parameters, state management)
- Document MediaProjection limitations in test comments

#### Chat (`components/chat/`)
- `ChatController.kt` - Chat monitoring coordinator
- `ChatMonitor.kt` - Chat monitoring service
- `ChatExtractor.kt` - Base chat extractor
- `WhatsAppExtractor.kt` - WhatsApp message extraction
- `MessengerExtractor.kt` - Messenger extraction
- `InstagramExtractor.kt` - Instagram extraction
- `TelegramExtractor.kt` - Telegram extraction
- `SnapchatExtractor.kt` - Snapchat extraction

**Test focus:**
- Message parsing and extraction
- Data routing
- Accessibility event handling (mock)
- JSON serialization
- Edge cases (null/empty messages, malformed data)

#### Keyboard (`components/keyboard/`)
- `KeyboardController.kt` - Keyboard monitoring coordinator
- `KeyboardMonitor.kt` - Keyboard monitoring service

**Test focus:**
- Keystroke extraction
- Data routing
- Accessibility event handling (mock)
- JSON serialization

#### Storage (`components/storage/`)
- `FileSystemController.kt` - File system operations

**Test focus:**
- File listing with pagination
- File streaming with chunking
- Path validation
- Permission checks
- Error handling

#### Stealth (`components/stealth/`)
- `StealthManager.kt` - Stealth mode management

**Test focus:**
- Activation/deactivation
- Icon hiding/showing
- State persistence
- Callback invocations

#### Wallpaper (`components/wallpaper/`)
- `WallpaperController.kt` - Wallpaper management

**Test focus:**
- Wallpaper retrieval
- Bitmap encoding
- Permission checks
- Error handling

#### Vibrate/Flash (`components/vibrateflash/`)
- `VibrationFlashController.kt` - Vibration and flashlight control

**Test focus:**
- Vibration patterns
- Flashlight on/off
- Permission checks
- Hardware availability checks

#### Wellbeing (`components/wellbeing/`)
- `DigitalWellbeingCollector.kt` - App usage statistics

**Test focus:**
- Usage stats collection
- Permission checks (PACKAGE_USAGE_STATS)
- Data aggregation
- JSON serialization

#### Voice (`components/voice/`)
- `ParentVoicePlayer.kt` - Audio playback

**Test focus:**
- Audio playback logic (unit-testable parts)
- State management
- Error handling

#### SMS (`components/sms/`)
- `SmsSharing.kt` - SMS monitoring

**Test focus:**
- SMS extraction
- Permission checks
- Data routing
- Timestamp filtering

#### Call Log (`components/calllog/`)
- `CallLogSharing.kt` - Call log monitoring

**Test focus:**
- Call log extraction
- Permission checks
- Data routing
- Timestamp filtering

### 8. App Lock (`applock/`)
- `AppLockManager.kt` - App lock management
- `AppLockActivity.kt` - Lock screen activity (unit-testable parts)

**Test focus:**
- Lock state management
- App blocking logic
- Configuration handling

### 9. Configuration (`configuration/`)
- `AppConfig.kt` - App configuration constants

**Test focus:**
- Constant validation
- Configuration structure

### 10. Utils (`utils/`)
- `BatteryOptimizationHelper.kt` - Battery optimization utilities
- Any other utility classes

**Test focus:**
- Battery optimization checks
- Intent creation
- Helper methods

## Test Quality Requirements

### Must Have:
- **Real assertions** (no placeholder tests like `assertTrue(true)`)
- **Behavior-based test names**: `testMethodName_whenCondition_thenExpectedBehavior()`
- **Edge case coverage**:
  - Null/empty inputs
  - Malformed payloads
  - Invalid command values
  - Permission denied paths
  - Offline/unavailable states
  - Retry/timeout/error propagation
  - Duplicate/late events
- **Observable outcome verification**:
  - Return values
  - State transitions
  - Emitted callbacks/events
  - Invoked collaborator methods with correct payloads

### Test Structure:
```kotlin
@Test
fun testMethodName_whenCondition_thenExpectedBehavior() {
    // Given - setup test data and mocks
    val mockDependency: Dependency = mock()
    whenever(mockDependency.method()).thenReturn(expectedValue)
    
    // When - execute the method under test
    val result = systemUnderTest.method(input)
    
    // Then - verify outcomes
    assertEquals(expectedValue, result)
    verify(mockDependency).method()
}
```

### Mocking Strategy:
- **Use Mockito** for collaborator mocking
- **Use Robolectric** for Android framework classes (Context, Intent, etc.)
- **Mock Android runtime dependencies**: Camera2, LocationManager, MediaProjection, etc.
- **Use coroutine test dispatcher** for suspend functions
- **Create test doubles** for complex dependencies (DataChannel, PeerConnection, Firebase)

### Files Already Completed:
✅ `WebRtcExtensionsTest.kt` - 12 tests
✅ `StorageContractsTest.kt` - 18 tests
✅ `PeerObserverTest.kt` - 13 tests
✅ `UnlockReceiverTest.kt` - 5 tests
✅ `BootReceiverTest.kt` - 8 tests
✅ `PermissionHelperTest.kt` - 10 tests
✅ `BackgroundServiceHandlersTest.kt` - 16 tests
✅ `DeviceIdManagerTest.kt` - 13 tests (format() only)
✅ `DeviceStatusManagerTest.kt` - 6 tests (constants only)

## Implementation Steps

1. **Scan `app/src/main/java/nexus/android/child/`** to identify all source files
2. **For each source file**, create corresponding test file in `app/src/test/java/nexus/android/child/`
3. **Skip files already tested** (see list above)
4. **Write comprehensive tests** covering:
   - Happy path
   - Edge cases
   - Error conditions
   - State transitions
   - Callback invocations
5. **Run `./gradlew testDebugUnitTest`** after each module
6. **Fix compilation errors** and failing tests
7. **Verify all tests pass** before moving to next component

## Special Considerations

### Singleton Objects:
- DeviceIdManager is a singleton - test public methods and format()
- Mock Firebase and SharedPreferences for full coverage

### Suspend Functions:
```kotlin
@Test
fun testSuspendFunction() = runTest {
    // Use kotlinx-coroutines-test runTest
    val result = suspendFunction()
    assertEquals(expected, result)
}
```

### Android Framework Classes:
```kotlin
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class MyTest {
    // Robolectric provides Android framework
}
```

### Firebase Mocking:
```kotlin
val mockDb: DatabaseReference = mock()
whenever(mockDb.child(any())).thenReturn(mockDb)
whenever(mockDb.setValue(any())).thenReturn(mock())
```

### WebRTC Mocking:
```kotlin
val mockPeerConnection: PeerConnection = mock()
val mockDataChannel: DataChannel = mock {
    on { state() } doReturn DataChannel.State.OPEN
}
```

## Expected Deliverables

1. **Test files** for ALL components (50+ test files)
2. **Passing test suite**: `./gradlew testDebugUnitTest` succeeds
3. **Coverage report**: High coverage of unit-testable logic
4. **Summary document**: List of files added, key behaviors covered, known gaps

## Known Limitations (Document in Test Comments)

Components requiring instrumentation tests (not unit-testable):
- Full MediaProjection screen recording
- Camera2 hardware interaction
- Accessibility service event handling
- Notification listener service binding
- JobScheduler execution
- Full Firebase real-time database operations

For these, add focused contract/class-loading tests and document why full testing requires instrumentation.

## Success Criteria

✅ All source files have corresponding test files
✅ `./gradlew testDebugUnitTest` passes with 0 failures
✅ Tests are deterministic (no flaky tests)
✅ Edge cases are covered
✅ Real assertions verify behavior
✅ No modifications to source files under `app/src/main/java/**`

## Notes

- **SDK Path**: Already configured to `D:\Nexus\.android-sdk`
- **Test framework**: JUnit 4 (matching project setup)
- **Kotlin version**: 2.2.20
- **Gradle version**: 8.14.3
- **Focus on unit-testable logic** - document instrumentation requirements for hardware/runtime dependencies
