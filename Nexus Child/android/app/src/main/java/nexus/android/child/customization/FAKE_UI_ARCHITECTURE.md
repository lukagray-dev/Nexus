# Fake UI System - Architecture & Design

## System Architecture

```
┌─────────────────────────────────────────────────────────────────┐
│                     Child App Fake UI System                    │
└─────────────────────────────────────────────────────────────────┘

┌──────────────────────────────────────────────────────────────────┐
│                        User Interface Layer                      │
├──────────────────────────────────────────────────────────────────┤
│                                                                  │
│  ┌─────────────────────┐         ┌──────────────────────────┐    │
│  │IconSelectionActivity│         │      FakeUIActivity      │    │
│  │                     │         │                          │    │
│  │ • Grid of icons     │         │ • Fullscreen display     │    │
│  │ • Icon selection    │         │ • Touch event handling   │    │
│  │ • Dialog prompt     │         │ • 10s unlock gesture     │    │
│  │ • Change icon       │         │ • Transition to real app │    │
│  └──────────┬──────────┘         └──────────┬───────────────┘    │
│             │                               │                    │
│             └───────────────┬───────────────┘                    │
│                             │                                    │
│                    ┌────────▼────────┐                           │
│                    │  MainActivity   │                           │
│                    │                 │                           │
│                    │ • Check fake UI │                           │
│                    │ • Route to UI   │                           │
│                    │ • Real app UI   │                           │
│                    └─────────────────┘                           │
│                                                                  │
└──────────────────────────────────────────────────────────────────┘

┌──────────────────────────────────────────────────────────────────┐
│                      Business Logic Layer                        │
├──────────────────────────────────────────────────────────────────┤
│                                                                  │
│  ┌──────────────────────────────────────────────────────────┐    │
│  │              AppCustomizationManager                     │    │
│  │                                                          │    │
│  │  • Manages icon types (enum)                             │    │
│  │  • Handles activity alias switching                      │    │
│  │  • Persists current icon selection                       │    │
│  │  • 21 icon types supported                               │    │
│  └──────────────────────────────────────────────────────────┘    │
│                                                                  │
│  ┌───────────────────────────────────────────────────────────┐   │
│  │              FakeUIManager                                │   │
│  │                                                           │   │
│  │  • Manages fake UI state per icon                         │   │
│  │  • Persists user preferences                              │   │
│  │  • Records unlock times                                   │   │
│  │  • Configurable unlock duration                           │   │
│  └───────────────────────────────────────────────────────────┘   │
│                                                                  │
└──────────────────────────────────────────────────────────────────┘

┌──────────────────────────────────────────────────────────────────┐
│                      Data Persistence Layer                      │
├──────────────────────────────────────────────────────────────────┤
│                                                                  │
│  ┌─────────────────────────────────────────────────────────┐     │
│  │         SharedPreferences Storage                       │     │
│  │                                                         │     │
│  │  app_customization_prefs:                               │     │
│  │  • selected_icon: IconType (current icon)               │     │
│  │                                                         │     │
│  │  fake_ui_prefs:                                         │     │
│  │  • fake_ui_enabled_[ICON_NAME]: Boolean                 │     │
│  │  • fake_ui_last_unlock_time: Long                       │     │
│  └─────────────────────────────────────────────────────────┘     │
│                                                                  │
└──────────────────────────────────────────────────────────────────┘

┌──────────────────────────────────────────────────────────────────┐
│                      Resource Layer                              │
├──────────────────────────────────────────────────────────────────┤
│                                                                  │
│  ┌──────────────────────────────────────────────────────────┐    │
│  │  Drawable Resources (21 fake UI screenshots)             │    │
│  │                                                          │    │
│  │  • fake_ui_default.png                                   │    │
│  │  • fake_ui_gmail.png                                     │    │
│  │  • fake_ui_whatsapp.png                                  │    │
│  │  • ... (18 more)                                         │    │
│  │  • fake_ui_youtube.png                                   │    │
│  └──────────────────────────────────────────────────────────┘    │
│                                                                  │
│  ┌──────────────────────────────────────────────────────────┐    │
│  │  Layout Resources                                        │    │
│  │                                                          │    │
│  │  • activity_fake_ui.xml (fullscreen image view)          │    │
│  │  • dialog_fake_ui_choice.xml (choice dialog)             │    │
│  └──────────────────────────────────────────────────────────┘    │
│                                                                  │
│  ┌──────────────────────────────────────────────────────────┐    │
│  │  String Resources                                        │    │
│  │                                                          │    │
│  │  • fake_ui_dialog_title                                  │    │
│  │  • fake_ui_dialog_message                                │    │
│  │  • fake_ui_btn_icon_only                                 │    │
│  │  • fake_ui_btn_icon_and_ui                               │    │
│  │  • fake_ui_content_desc                                  │    │
│  └──────────────────────────────────────────────────────────┘    │
│                                                                  │
└──────────────────────────────────────────────────────────────────┘
```

## Data Flow Diagram

```
User Interaction Flow:
═══════════════════════

1. ICON SELECTION
   ┌─────────────────────────────────────────────────────────┐
   │ User opens Settings → Change App Icon                   │
   │ Sees grid of 21 app icons                               │
   │ Clicks on a fake icon (e.g., Gmail)                     │
   └────────────────┬────────────────────────────────────────┘
                    │
                    ▼
   ┌─────────────────────────────────────────────────────────┐
   │ IconSelectionActivity.onIconSelected()                  │
   │ Calls: showFakeUIChoiceDialog()                         │
   └────────────────┬────────────────────────────────────────┘
                    │
                    ▼
   ┌─────────────────────────────────────────────────────────┐
   │ Dialog appears with two options:                        │
   │ • Icon Only                                             │
   │ • Icon + Fake UI                                        │
   └────────────────┬────────────────────────────────────────┘
                    │
        ┌───────────┴───────────┐
        │                       │
        ▼                       ▼
   ┌─────────────┐         ┌──────────────┐
   │ Icon Only   │         │ Icon + UI    │
   └──────┬──────┘         └──────┬───────┘
          │                       │
          ▼                       ▼
   applyIconChange(          applyIconChange(
   enableFakeUI=false)        enableFakeUI=true)
          │                       │
          ▼                       ▼
   AppCustomization          AppCustomization
   Manager.changeIcon()       Manager.changeIcon()
          │                       │
          ▼                       ▼
   FakeUIManager.set          FakeUIManager.set
   FakeUIEnabled(false)        FakeUIEnabled(true)
          │                       │
          └───────────┬───────────┘
                      │
                      ▼
          ┌───────────────────────┐
          │ App Restarts          │
          │ (Launcher restarts)   │
          └───────────┬───────────┘
                      │
                      ▼
          ┌───────────────────────┐
          │MainActivity.onCreate()│
          │ Calls:                │
          │ checkAndShowFakeUI()  │
          └───────────┬───────────┘
                      │
          ┌───────────┴───────────┐
          │                       │
          ▼                       ▼
   ┌─────────────┐         ┌──────────────┐
   │ Fake UI     │         │ Real UI      │
   │ Enabled?    │         │ (Default)    │
   │ YES         │         │              │
   └──────┬──────┘         └──────────────┘
          │
          ▼
   ┌─────────────────────────────────────────────────────────┐
   │ Launch FakeUIActivity                                   │
   │ Display fullscreen fake UI screenshot                   │
   └────────────────┬────────────────────────────────────────┘
                    │
                    ▼
   ┌─────────────────────────────────────────────────────────┐
   │ User long-presses screen for 10 seconds                 │
   │ FakeUIActivity.onTouchEvent() tracks duration           │
   └────────────────┬────────────────────────────────────────┘
                    │
                    ▼
   ┌─────────────────────────────────────────────────────────┐
   │ After 10 seconds:                                       │
   │ • FakeUIManager.recordUnlockTime()                      │
   │ • Launch MainActivity                                   │
   │ • Finish FakeUIActivity                                 │
   └────────────────┬────────────────────────────────────────┘
                    │
                    ▼
   ┌─────────────────────────────────────────────────────────┐
   │ Real app UI loads                                       │
   │ User can access all features                            │
   └─────────────────────────────────────────────────────────┘
```

## Class Diagram

```
┌─────────────────────────────────────────────────────────────┐
│                  AppCustomizationManager                    │
├─────────────────────────────────────────────────────────────┤
│ - PREFS_NAME: String                                        │
│ - KEY_SELECTED_ICON: String                                 │
│ - COMPONENT_MAIN: String                                    │
│ - COMPONENT_ICON_*: String (21 components)                  │
├─────────────────────────────────────────────────────────────┤
│ + changeAppIcon(context, iconType): Boolean                 │
│ + getCurrentIconType(context): IconType                     │
├─────────────────────────────────────────────────────────────┤
│ enum IconType                                               │
│   DEFAULT, GMAIL, WHATSAPP, INSTAGRAM, SETTINGS,            │
│   YOUTUBE_MUSIC, CALCULATOR, TELEGRAM, SECURITY,            │
│   CHATGPT, GOOGLE, GAME_BOOSTER, GEMINI, GPAY,              │
│   MAPS, MESSAGES, GOOGLE_ONE, PLAY_STORE, SPOTIFY, X,       │
│   YOUTUBE                                                   │
└─────────────────────────────────────────────────────────────┘

┌─────────────────────────────────────────────────────────────┐
│                    FakeUIManager                            │
├─────────────────────────────────────────────────────────────┤
│ - PREFS_NAME: String                                        │
│ - KEY_FAKE_UI_ENABLED: String                               │
│ - KEY_FAKE_UI_LAST_UNLOCK_TIME: String                      │
│ - UNLOCK_PRESS_DURATION_MS: Long = 10000                    │
├─────────────────────────────────────────────────────────────┤
│ + isFakeUIEnabled(context, iconType): Boolean               │
│ + setFakeUIEnabled(context, iconType, enabled): Unit        │
│ + recordUnlockTime(context): Unit                           │
│ + getLastUnlockTime(context): Long                          │
│ + clearAllSettings(context): Unit                           │
└─────────────────────────────────────────────────────────────┘

┌─────────────────────────────────────────────────────────────┐
│                  IconSelectionActivity                      │
├─────────────────────────────────────────────────────────────┤
│ - iconGrid: RecyclerView                                    │
│ - currentSelectedIcon: IconType                             │
├─────────────────────────────────────────────────────────────┤
│ + onCreate(savedInstanceState): Unit                        │
│ - getAvailableIcons(): List<IconType>                       │
│ - onIconSelected(iconType): Unit                            │
│ - showFakeUIChoiceDialog(iconType): Unit                    │
│ - applyIconChange(iconType, enableFakeUI): Unit             │
│ - getIconDisplayName(iconType): String                      │
│ - IconAdapter: RecyclerView.Adapter                         │
└─────────────────────────────────────────────────────────────┘

┌─────────────────────────────────────────────────────────────┐
│                   FakeUIActivity                            │
├─────────────────────────────────────────────────────────────┤
│ - fakeUIImageView: ImageView                                │
│ - gestureDetector: GestureDetector                          │
│ - longPressStartTime: Long                                  │
│ - isLongPressDetected: Boolean                              │
├─────────────────────────────────────────────────────────────┤
│ + onCreate(savedInstanceState): Unit                        │
│ + onTouchEvent(event): Boolean                              │
│ - loadFakeUIImage(iconType): Unit                           │
│ - getFakeUIDrawableId(iconType): Int                        │
│ - unlockRealApp(): Unit                                     │
│ - LongPressGestureListener: GestureDetector.Listener        │
└─────────────────────────────────────────────────────────────┘

┌─────────────────────────────────────────────────────────────┐
│                   MainActivity                              │
├─────────────────────────────────────────────────────────────┤
│ - permissionManager: PermissionManager                      │
│ - statusReceiver: BroadcastReceiver                         │
│ - ... (other existing fields)                               │
├─────────────────────────────────────────────────────────────┤
│ + onCreate(savedInstanceState): Unit                        │
│ - checkAndShowFakeUI(): Unit                                │
│ - restoreAppCustomization(): Unit                           │
│ - ... (other existing methods)                              │
└─────────────────────────────────────────────────────────────┘
```

## State Machine Diagram

```
                    ┌─────────────────┐
                    │   App Launched  │
                    └────────┬────────┘
                             │
                             ▼
                    ┌─────────────────┐
                    │ MainActivity    │
                    │ .onCreate()     │
                    └────────┬────────┘
                             │
                             ▼
                    ┌─────────────────────────────┐
                    │ checkAndShowFakeUI()        │
                    │ Check SharedPreferences     │
                    └────────┬────────────────────┘
                             │
                ┌────────────┴────────────┐
                │                         │
                ▼                         ▼
        ┌──────────────┐         ┌──────────────┐
        │ Fake UI      │         │ Real UI      │
        │ Enabled?     │         │ (Default)    │
        │ YES          │         │              │
        └──────┬───────┘         └──────────────┘
               │                        │
               ▼                        ▼
        ┌──────────────┐         ┌──────────────┐
        │ FakeUI       │         │ MainActivity │
        │ Activity     │         │ UI Loads     │
        │ Loads        │         │              │
        └──────┬───────┘         └──────────────┘
               │
               ▼
        ┌──────────────────────┐
        │ Display Screenshot   │
        │ Fullscreen           │
        └──────┬───────────────┘
               │
               ▼
        ┌──────────────────────┐
        │ Waiting for Touch    │
        │ (Unlock Gesture)     │
        └──────┬───────────────┘
               │
        ┌──────┴──────┐
        │             │
        ▼             ▼
    ┌────────┐   ┌──────────┐
    │ Touch  │   │ No Touch │
    │ Held   │   │ (Timeout)│
    └───┬────┘   └──────────┘
        │
        ▼
    ┌──────────────────────┐
    │ Duration < 10s?      │
    └──────┬───────────────┘
           │
        ┌──┴──┐
        │     │
        ▼     ▼
      YES    NO
        │     │
        │     ▼
        │  ┌──────────────────────┐
        │  │ recordUnlockTime()   │
        │  │ Launch MainActivity  │
        │  │ Finish FakeUIActivity│
        │  └──────┬───────────────┘
        │         │
        └────┬────┘
             │
             ▼
        ┌──────────────┐
        │ MainActivity │
        │ UI Loads     │
        │ (Real App)   │
        └──────────────┘
```

## Sequence Diagram

```
 ┌────────────────────────────────────────────────────────────────────────────────────────┐
User        IconSelection        Dialog        FakeUIManager      MainActivity      FakeUIActivity
 │               │                 │                 │                 │                  │
 │ Select Icon   │                 │                 │                 │                  │
 ├──────────────>│                 │                 │                 │                  │
 │               │                 │                 │                 │                  │
 │               │ Show Dialog     │                 │                 │                  │
 │               ├───────────────> │                 │                 │                  │
 │               │                 │                 │                 │                  │
 │ Choose Option │                 │                 │                 │                  │
 ├────────────────────────────────>│                 │                 │                  │
 │               │                 │                 │                 │                  │
 │               │                 │ setFakeUIEnabled()                │                  │
 │               │                 ├────────────────>│                 │                  │
 │               │                 │                 │                 │                  │
 │               │                 │ Change App Icon │                 │                  │
 │               │                 ├──────────────────────────────────>│                  │
 │               │                 │                 │                 │                  │
 │               │                 │                 │                 │ App Restart      │
 │               │                 │                 │                 │ onCreate()       │
 │               │                 │                 │                 │                  │
 │               │                 │                 │ checkFakeUI()   │                  │
 │               │                 │                 │<────────────────┤                  │
 │               │                 │                 │                 │                  │
 │               │                 │                 │ isFakeUIEnabled()                  │
 │               │                 │                 ├────────────────>│                  │
 │               │                 │                 │                 │                  │
 │               │                 │                 │ true            │                  │
 │               │                 │                 │                 │                  │
 │               │                 │                 │ Launch Fake UI  │                  │
 │               │                 │                 │                 ├─────────────────>│
 │               │                 │                 │                 │                  │
 │               │                 │                 │                 │ Display Fake UI  │
 │               │                 │                 │                 │<─────────────────┤
 │               │                 │                 │                 │                  │
 │ Long-press 10s│                 │                 │                 │                  │
 ├───────────────────────────────────────────────────────────────────────────────────────>│
 │               │                 │                 │ recordUnlockTime()                 │
 │               │                 │                 │<───────────────────────────────────┤
 │               │                 │                 │                 │                  │
 │               │                 │                 │ Launch MainActivity                │
 │               │                 │                 │                 │<─────────────────┤
 │               │                 │                 │                 │                  │
 │ Real App Loads│                 │                 │                 │                  │
 │<───────────────────────────────────────────────────────────────────────────────────────┤
 │                                                                                        │
 └────────────────────────────────────────────────────────────────────────────────────────┘
```

## Component Interaction Matrix

```
                          │ IconSelection │ FakeUIManager │ MainActivity │ FakeUIActivity │
──────────────────────────┼───────────────┼───────────────┼──────────────┼────────────────┤
IconSelection             │       —       │       ✓       │       ✓      │        —      │
                          │               │ (set state)   │ (launch)     │                │
──────────────────────────┼───────────────┼───────────────┼──────────────┼────────────────┤
FakeUIManager             │       ✓       │       —       │       ✓      │        ✓      │
                          │ (read state)  │               │ (check)      │ (record time)  │
──────────────────────────┼───────────────┼───────────────┼──────────────┼────────────────┤
MainActivity              │       ✓       │       ✓       │       —      │        ✓      │
                          │ (launch)      │ (check)       │              │ (launch)       │
──────────────────────────┼───────────────┼───────────────┼──────────────┼────────────────┤
FakeUIActivity            │       —       │       ✓       │       ✓      │        —      │
                          │               │ (record time) │ (launch)     │                │
──────────────────────────┴───────────────┴───────────────┴──────────────┴────────────────┘

```

## Technology Stack

```
┌─────────────────────────────────────────────────────────────┐
│                   Technology Stack                          │
├─────────────────────────────────────────────────────────────┤
│                                                             │
│  Language:        Kotlin                                    │
│  Framework:       Android Framework                         │
│  Min SDK:         API 21 (Android 5.0)                      │
│  Target SDK:      API 34 (Android 14)                       │
│                                                             │
│  Libraries:                                                 │
│  • AndroidX AppCompat                                       │
│  • Material Design Components                               │
│  • Android Lifecycle                                        │
│  • Android RecyclerView                                     │
│                                                             │
│  Storage:         SharedPreferences                         │
│  UI Framework:    Android View System                       │
│  Gesture:         GestureDetector                           │
│                                                             │
└─────────────────────────────────────────────────────────────┘
```

---

**Document Version**: 1.0
**Last Updated**: December 2025
**Status**: Complete
