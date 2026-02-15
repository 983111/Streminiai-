# Stremini AI

**Package:** `Android.stremini_ai`
**Platform:** Android (Hybrid Flutter/Kotlin)
**Backend:** Cloudflare Workers

## Overview

Stremini AI is a **system-wide Android utility** designed to enhance digital safety and productivity through intelligent screen analysis and contextual AI assistance. The application leverages Android's powerful **Accessibility Services** and **System Alert Windows** to provide:

- **Real-time threat detection** for phishing attempts, scams, and malicious content
- **Persistent AI assistant overlay** accessible from any application
- **Context-aware assistance** based on on-screen content
- **AI-powered keyboard** for enhanced typing, translation, and text completion

The architecture implements a sophisticated hybrid approach, combining Flutter's cross-platform UI capabilities with native Kotlin services for deep system-level integration.

### Use Cases

- **Security-conscious users** who want real-time protection against phishing and scams
- **Productivity enthusiasts** seeking quick AI assistance without context switching
- **Multi-lingual users** requiring instant translation and text assistance
- **Accessibility needs** for users who benefit from AI-powered screen reading and analysis

---

## Key Features

### 🔍 Real-Time Screen Scanning

Stremini AI continuously monitors visible content to identify potential security threats:

- **Intelligent Text Extraction:** Parses all visible text and UI elements using Android's AccessibilityNodeInfo API
- **Threat Detection Engine:** Analyzes content for:
  - Phishing URLs and domains
  - Social engineering patterns
  - Suspicious payment requests
  - Fake authentication prompts
- **Visual Risk Indicators:** Color-coded overlay tags highlight threats directly on screen
  - 🔴 **Red:** High-risk elements (phishing, malware)
  - 🟡 **Yellow:** Suspicious content requiring attention
  - 🟢 **Green:** Verified safe content
- **Dismissible Banners:** Non-intrusive summary notifications with detailed scan results

### 💬 Floating Assistant

A persistent, always-accessible AI companion:

- **Draggable Bubble:** Minimalist floating icon that stays accessible across all apps
- **Radial Menu:** Quick-access circular menu with gesture controls
  - Trigger security scan
  - Open full chat interface
  - Access app settings
  - Toggle keyboard mode
- **Context-Aware Chat:** AI assistant receives screen context for relevant responses
- **Cross-App Functionality:** Works seamlessly across third-party applications

### ⌨️ AI Keyboard Integration

Intelligent typing assistance powered by AI:

- **Smart Text Completion:** Context-aware suggestions and auto-completion
- **Multi-Language Translation:** Instant translation while typing
- **Tone Adjustment:** Rewrite messages in different tones (professional, casual, friendly)
- **Grammar Correction:** Real-time grammar and spelling fixes
- **Template Expansion:** Shortcut-based text expansion for common phrases

### 🛡️ Privacy-First Design

- **Local Processing:** Screen analysis occurs on-device when possible
- **Encrypted Communication:** All backend API calls use TLS 1.3
- **No Data Retention:** Screen content is not stored or logged
- **Transparent Permissions:** Clear explanations for all permission requests

---

## Technical Architecture

Stremini AI implements a **three-layer hybrid architecture** that maximizes performance, security, and user experience.

### System Architecture Diagram

```
┌─────────────────────────────────────────────────────────────┐
│                        USER INTERFACE                        │
│  ┌────────────────┐  ┌────────────────┐  ┌───────────────┐ │
│  │  Home Screen   │  │  Chat Screen   │  │ Settings View │ │
│  └────────────────┘  └────────────────┘  └───────────────┘ │
│                    Flutter Layer (Dart)                      │
│  ┌─────────────────────────────────────────────────────┐   │
│  │  State Management (Riverpod)                        │   │
│  │  - Service State Providers                          │   │
│  │  - Permission State Providers                       │   │
│  │  - Chat Message Providers                           │   │
│  └─────────────────────────────────────────────────────┘   │
└──────────────────────┬──────────────────────────────────────┘
                       │ MethodChannel / EventChannel
┌──────────────────────▼──────────────────────────────────────┐
│                  Native Android Layer (Kotlin)               │
│  ┌──────────────────────────────────────────────────────┐  │
│  │                    MainActivity                       │  │
│  │  - MethodChannel Bridge                              │  │
│  │  - Permission Request Handler                        │  │
│  └──────────────────────────────────────────────────────┘  │
│                                                              │
│  ┌───────────────────┐  ┌───────────────────────────────┐  │
│  │ScreenReaderService│  │   ChatOverlayService          │  │
│  │(Accessibility)    │  │   (Foreground Service)        │  │
│  │                   │  │                               │  │
│  │- Node Tree Parse  │  │- Floating Bubble              │  │
│  │- Text Extraction  │  │- Radial Menu                  │  │
│  │- Threat Detection │  │- Chat Window                  │  │
│  │- Overlay Drawing  │  │- Gesture Handling             │  │
│  └───────────────────┘  └───────────────────────────────┘  │
│                                                              │
│  ┌──────────────────────────────────────────────────────┐  │
│  │              StreminiIME (Optional)                   │  │
│  │              (Input Method Service)                   │  │
│  └──────────────────────────────────────────────────────┘  │
└──────────────────────┬──────────────────────────────────────┘
                       │ HTTPS (TLS 1.3)
┌──────────────────────▼──────────────────────────────────────┐
│                   Backend (Cloudflare Workers)               │
│  ┌──────────────────────────────────────────────────────┐  │
│  │  /api/analyze-screen                                  │  │
│  │  - Screen Content Analysis                            │  │
│  │  - Threat Detection ML Models                         │  │
│  │  - Phishing URL Verification                          │  │
│  └──────────────────────────────────────────────────────┘  │
│  ┌──────────────────────────────────────────────────────┐  │
│  │  /api/chat                                            │  │
│  │  - Conversational AI (LLM Integration)                │  │
│  │  - Context-Aware Responses                            │  │
│  └──────────────────────────────────────────────────────┘  │
│  ┌──────────────────────────────────────────────────────┐  │
│  │  /api/keyboard-assist                                 │  │
│  │  - Text Completion                                    │  │
│  │  - Translation Services                               │  │
│  └──────────────────────────────────────────────────────┘  │
└─────────────────────────────────────────────────────────────┘
```

### 1. Flutter Layer (Dart)

The Flutter layer provides the application's UI and manages cross-platform logic.

#### State Management
- **Provider:** `flutter_riverpod`
- **Key Providers:**
  - `overlayServiceProvider`: Manages overlay service state and controls
  - `chatMessagesProvider`: Handles chat message history and state
  - `permissionsProvider`: Tracks permission grant status
  - `scanResultsProvider`: Stores and manages scan results

#### Networking
- **HTTP Client:** `http` package with custom interceptors
- **API Service:** `lib/services/api_service.dart`
  - Handles all backend communication
  - Implements request/response serialization
  - Manages authentication tokens
  - Includes retry logic and error handling

#### UI Components

**HomeScreen** (`lib/screens/home_screen.dart`)
- Dashboard for managing permissions
- Service state indicators
- Quick action buttons
- Recent scan results summary

**ChatScreen** (`lib/screens/chat_screen.dart`)
- Full-screen AI chat interface
- Message history with infinite scroll
- Context injection controls
- Typing indicators and loading states

**PermissionsScreen** (`lib/screens/permissions_screen.dart`)
- Step-by-step permission setup wizard
- Direct links to system settings
- Permission status indicators

#### Service Integration

**OverlayService** (`lib/services/overlay_service.dart`)
```dart
class OverlayService {
  static const MethodChannel _channel = 
    MethodChannel('stremini.chat.overlay');
  
  Future<void> startOverlay() async {
    await _channel.invokeMethod('startOverlay');
  }
  
  Future<void> stopOverlay() async {
    await _channel.invokeMethod('stopOverlay');
  }
  
  Future<bool> isOverlayActive() async {
    return await _channel.invokeMethod('isOverlayActive');
  }
}
```

**KeyboardService** (`lib/services/keyboard_service.dart`)
```dart
class KeyboardService {
  static const MethodChannel _channel = 
    MethodChannel('stremini.keyboard');
  
  Future<void> enableKeyboard() async {
    await _channel.invokeMethod('enableKeyboard');
  }
  
  Stream<String> get textSuggestions {
    return EventChannel('stremini.keyboard.suggestions')
      .receiveBroadcastStream()
      .map((event) => event as String);
  }
}
```

### 2. Native Android Layer (Kotlin)

The native layer handles system-level operations that require Android platform APIs.

#### ScreenReaderService (AccessibilityService)

**Location:** `android/app/src/main/kotlin/com/android/stremini_ai/ScreenReaderService.kt`

**Purpose:** Extracts screen content and detects security threats.

**Key Responsibilities:**
- Monitor accessibility events across all apps
- Parse AccessibilityNodeInfo trees to extract text and coordinates
- Send screen content to backend for threat analysis
- Render overlay warnings on detected threats
- Manage scan lifecycle and caching

**Implementation Details:**

```kotlin
class ScreenReaderService : AccessibilityService() {
    
    private lateinit var windowManager: WindowManager
    private val overlayViews = mutableListOf<View>()
    
    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        event?.source?.let { node ->
            val screenContent = extractScreenContent(node)
            analyzeContent(screenContent)
        }
    }
    
    private fun extractScreenContent(node: AccessibilityNodeInfo): ScreenContent {
        val textElements = mutableListOf<TextElement>()
        
        fun traverse(node: AccessibilityNodeInfo, depth: Int = 0) {
            // Extract text and bounds
            node.text?.let { text ->
                val bounds = Rect()
                node.getBoundsInScreen(bounds)
                textElements.add(TextElement(
                    text = text.toString(),
                    bounds = bounds,
                    className = node.className.toString()
                ))
            }
            
            // Recursively traverse children
            for (i in 0 until node.childCount) {
                node.getChild(i)?.let { traverse(it, depth + 1) }
            }
        }
        
        traverse(node)
        return ScreenContent(textElements)
    }
    
    private fun analyzeContent(content: ScreenContent) {
        CoroutineScope(Dispatchers.IO).launch {
            val result = ApiClient.analyzeScreen(content)
            withContext(Dispatchers.Main) {
                displayThreatOverlays(result.threats)
            }
        }
    }
    
    private fun displayThreatOverlays(threats: List<Threat>) {
        // Clear existing overlays
        overlayViews.forEach { windowManager.removeView(it) }
        overlayViews.clear()
        
        threats.forEach { threat ->
            val overlayView = createThreatOverlay(threat)
            val params = WindowManager.LayoutParams(
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
                PixelFormat.TRANSLUCENT
            ).apply {
                x = threat.bounds.left
                y = threat.bounds.top
            }
            
            windowManager.addView(overlayView, params)
            overlayViews.add(overlayView)
        }
    }
}
```

#### ChatOverlayService (ForegroundService)

**Location:** `android/app/src/main/kotlin/com/android/stremini_ai/ChatOverlayService.kt`

**Purpose:** Manages the floating bubble and chat interface.

**Key Features:**
- Persistent floating bubble with drag gestures
- Radial menu for quick actions
- Expandable chat window
- Lifecycle management tied to foreground service

**Implementation Details:**

```kotlin
class ChatOverlayService : Service() {
    
    private lateinit var windowManager: WindowManager
    private var bubbleView: View? = null
    private var chatView: View? = null
    private var radialMenuView: View? = null
    
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        createNotificationChannel()
        startForeground(NOTIFICATION_ID, createNotification())
        
        when (intent?.action) {
            ACTION_START_OVERLAY -> showFloatingBubble()
            ACTION_STOP_OVERLAY -> stopSelf()
            ACTION_OPEN_CHAT -> expandChatWindow()
        }
        
        return START_STICKY
    }
    
    private fun showFloatingBubble() {
        bubbleView = LayoutInflater.from(this)
            .inflate(R.layout.floating_bubble, null)
        
        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = 100
            y = 100
        }
        
        // Add drag gesture handling
        var initialX = 0
        var initialY = 0
        var initialTouchX = 0f
        var initialTouchY = 0f
        
        bubbleView?.setOnTouchListener { view, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    initialX = params.x
                    initialY = params.y
                    initialTouchX = event.rawX
                    initialTouchY = event.rawY
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    params.x = initialX + (event.rawX - initialTouchX).toInt()
                    params.y = initialY + (event.rawY - initialTouchY).toInt()
                    windowManager.updateViewLayout(bubbleView, params)
                    true
                }
                MotionEvent.ACTION_UP -> {
                    if (Math.abs(event.rawX - initialTouchX) < 5 &&
                        Math.abs(event.rawY - initialTouchY) < 5) {
                        showRadialMenu()
                    }
                    true
                }
                else -> false
            }
        }
        
        windowManager.addView(bubbleView, params)
    }
    
    private fun showRadialMenu() {
        radialMenuView = LayoutInflater.from(this)
            .inflate(R.layout.radial_menu, null)
        
        radialMenuView?.findViewById<View>(R.id.action_scan)
            ?.setOnClickListener {
                triggerScreenScan()
                hideRadialMenu()
            }
        
        radialMenuView?.findViewById<View>(R.id.action_chat)
            ?.setOnClickListener {
                expandChatWindow()
                hideRadialMenu()
            }
        
        // Add to window manager with animation
        windowManager.addView(radialMenuView, createMenuParams())
    }
    
    private fun expandChatWindow() {
        chatView = LayoutInflater.from(this)
            .inflate(R.layout.chat_window, null)
        
        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            (Resources.getSystem().displayMetrics.heightPixels * 0.7).toInt(),
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.BOTTOM
        }
        
        windowManager.addView(chatView, params)
    }
}
```

#### MainActivity (FlutterActivity)

**Location:** `android/app/src/main/kotlin/com/android/stremini_ai/MainActivity.kt`

**Purpose:** Bridges Flutter and native Android code.

**MethodChannel Implementation:**

```kotlin
class MainActivity: FlutterActivity() {
    
    private val OVERLAY_CHANNEL = "stremini.chat.overlay"
    private val KEYBOARD_CHANNEL = "stremini.keyboard"
    
    override fun configureFlutterEngine(flutterEngine: FlutterEngine) {
        super.configureFlutterEngine(flutterEngine)
        
        // Overlay Service Channel
        MethodChannel(flutterEngine.dartExecutor.binaryMessenger, OVERLAY_CHANNEL)
            .setMethodCallHandler { call, result ->
                when (call.method) {
                    "startOverlay" -> {
                        if (checkOverlayPermission()) {
                            startOverlayService()
                            result.success(true)
                        } else {
                            requestOverlayPermission()
                            result.error("NO_PERMISSION", 
                                "Overlay permission required", null)
                        }
                    }
                    "stopOverlay" -> {
                        stopOverlayService()
                        result.success(true)
                    }
                    "isOverlayActive" -> {
                        val isActive = isServiceRunning(ChatOverlayService::class.java)
                        result.success(isActive)
                    }
                    else -> result.notImplemented()
                }
            }
        
        // Keyboard Channel
        MethodChannel(flutterEngine.dartExecutor.binaryMessenger, KEYBOARD_CHANNEL)
            .setMethodCallHandler { call, result ->
                when (call.method) {
                    "enableKeyboard" -> {
                        openKeyboardSettings()
                        result.success(true)
                    }
                    "isKeyboardEnabled" -> {
                        val enabled = isKeyboardEnabled()
                        result.success(enabled)
                    }
                    else -> result.notImplemented()
                }
            }
    }
    
    private fun checkOverlayPermission(): Boolean {
        return Settings.canDrawOverlays(this)
    }
    
    private fun requestOverlayPermission() {
        val intent = Intent(
            Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
            Uri.parse("package:$packageName")
        )
        startActivityForResult(intent, REQUEST_OVERLAY_PERMISSION)
    }
    
    private fun startOverlayService() {
        val intent = Intent(this, ChatOverlayService::class.java).apply {
            action = ChatOverlayService.ACTION_START_OVERLAY
        }
        ContextCompat.startForegroundService(this, intent)
    }
}
```

### 3. Backend Layer

**Platform:** Cloudflare Workers  
**Runtime:** V8 JavaScript Engine

The backend provides AI-powered analysis and chat functionality with global edge deployment for low latency.

#### Key Endpoints

**/api/analyze-screen**
- Receives screen content from `ScreenReaderService`
- Performs threat detection using ML models
- Returns threat classifications and risk scores

**/api/chat**
- Handles conversational AI requests
- Supports context injection from screen content
- Returns streaming or complete responses

**/api/keyboard-assist**
- Provides text completion suggestions
- Handles translation requests
- Processes grammar correction

---

## Component Deep Dive

### ScreenReaderService

#### Accessibility Event Handling

The service responds to the following accessibility events:

- `TYPE_WINDOW_STATE_CHANGED`: Triggered when a new window appears
- `TYPE_WINDOW_CONTENT_CHANGED`: Triggered when window content updates
- `TYPE_VIEW_SCROLLED`: Triggered during scrolling

#### Node Tree Parsing Algorithm

```kotlin
data class TextElement(
    val text: String,
    val bounds: Rect,
    val className: String,
    val isClickable: Boolean,
    val isEditable: Boolean
)

fun parseNodeTree(root: AccessibilityNodeInfo): List<TextElement> {
    val elements = mutableListOf<TextElement>()
    val queue = LinkedList<AccessibilityNodeInfo>()
    queue.add(root)
    
    while (queue.isNotEmpty()) {
        val node = queue.poll()
        
        // Extract relevant information
        if (node.text != null || node.contentDescription != null) {
            val bounds = Rect()
            node.getBoundsInScreen(bounds)
            
            elements.add(TextElement(
                text = node.text?.toString() 
                    ?: node.contentDescription?.toString() ?: "",
                bounds = bounds,
                className = node.className?.toString() ?: "unknown",
                isClickable = node.isClickable,
                isEditable = node.isEditable
            ))
        }
        
        // Add children to queue
        for (i in 0 until node.childCount) {
            node.getChild(i)?.let { queue.add(it) }
        }
    }
    
    return elements
}
```

#### Threat Detection Logic

The service categorizes threats into severity levels:

```kotlin
enum class ThreatLevel {
    SAFE,       // Green indicator
    SUSPICIOUS, // Yellow indicator
    DANGEROUS   // Red indicator
}

data class Threat(
    val element: TextElement,
    val level: ThreatLevel,
    val type: ThreatType,
    val confidence: Float,
    val description: String
)

enum class ThreatType {
    PHISHING_URL,
    SUSPICIOUS_PERMISSION_REQUEST,
    FAKE_LOGIN_PROMPT,
    PAYMENT_SCAM,
    SOCIAL_ENGINEERING
}
```

### ChatOverlayService

#### Gesture Recognition

The floating bubble supports multiple gesture interactions:

- **Single Tap:** Opens radial menu
- **Long Press:** Locks bubble in place
- **Drag:** Moves bubble position
- **Double Tap:** Opens chat window directly

#### Radial Menu Layout

```xml
<!-- radial_menu.xml -->
<RelativeLayout
    android:id="@+id/radial_menu_container"
    android:layout_width="match_parent"
    android:layout_height="match_parent">
    
    <ImageView
        android:id="@+id/action_scan"
        android:layout_width="48dp"
        android:layout_height="48dp"
        android:src="@drawable/ic_scan"
        android:contentDescription="Trigger Scan"/>
    
    <ImageView
        android:id="@+id/action_chat"
        android:layout_width="48dp"
        android:layout_height="48dp"
        android:src="@drawable/ic_chat"
        android:contentDescription="Open Chat"/>
    
    <ImageView
        android:id="@+id/action_settings"
        android:layout_width="48dp"
        android:layout_height="48dp"
        android:src="@drawable/ic_settings"
        android:contentDescription="Settings"/>
</RelativeLayout>
```

### MainActivity

#### Permission Request Flow

```kotlin
private fun requestAllPermissions() {
    // 1. Check overlay permission
    if (!Settings.canDrawOverlays(this)) {
        requestOverlayPermission()
        return
    }
    
    // 2. Check accessibility permission
    if (!isAccessibilityServiceEnabled()) {
        requestAccessibilityPermission()
        return
    }
    
    // 3. All permissions granted
    notifyFlutterPermissionsGranted()
}

private fun isAccessibilityServiceEnabled(): Boolean {
    val service = "${packageName}/${ScreenReaderService::class.java.canonicalName}"
    val enabledServices = Settings.Secure.getString(
        contentResolver,
        Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
    )
    return enabledServices?.contains(service) == true
}
```

### API Service

#### Request/Response Models

```dart
// lib/models/screen_analysis_request.dart
class ScreenAnalysisRequest {
  final List<ScreenElement> elements;
  final String packageName;
  final DateTime timestamp;
  
  Map<String, dynamic> toJson() => {
    'elements': elements.map((e) => e.toJson()).toList(),
    'package_name': packageName,
    'timestamp': timestamp.toIso8601String(),
  };
}

// lib/models/screen_analysis_response.dart
class ScreenAnalysisResponse {
  final List<ThreatInfo> threats;
  final double overallRiskScore;
  final String summary;
  
  factory ScreenAnalysisResponse.fromJson(Map<String, dynamic> json) {
    return ScreenAnalysisResponse(
      threats: (json['threats'] as List)
          .map((t) => ThreatInfo.fromJson(t))
          .toList(),
      overallRiskScore: json['overall_risk_score'],
      summary: json['summary'],
    );
  }
}
```

#### API Client Implementation

```dart
// lib/services/api_service.dart
class ApiService {
  static const String baseUrl = 'https://api.stremini.workers.dev';
  final http.Client _client;
  
  ApiService({http.Client? client}) 
    : _client = client ?? http.Client();
  
  Future<ScreenAnalysisResponse> analyzeScreen(
    ScreenAnalysisRequest request
  ) async {
    try {
      final response = await _client.post(
        Uri.parse('$baseUrl/api/analyze-screen'),
        headers: {'Content-Type': 'application/json'},
        body: jsonEncode(request.toJson()),
      ).timeout(Duration(seconds: 10));
      
      if (response.statusCode == 200) {
        return ScreenAnalysisResponse.fromJson(
          jsonDecode(response.body)
        );
      } else {
        throw ApiException(
          'Analysis failed: ${response.statusCode}',
          statusCode: response.statusCode,
        );
      }
    } on TimeoutException {
      throw ApiException('Request timeout');
    } on SocketException {
      throw ApiException('No internet connection');
    }
  }
  
  Stream<String> chatStream(String message, {String? context}) async* {
    final request = http.Request(
      'POST',
      Uri.parse('$baseUrl/api/chat'),
    );
    
    request.headers['Content-Type'] = 'application/json';
    request.body = jsonEncode({
      'message': message,
      'context': context,
      'stream': true,
    });
    
    final response = await _client.send(request);
    
    await for (final chunk in response.stream.transform(utf8.decoder)) {
      yield chunk;
    }
  }
}
```

---

## Installation and Setup

### Prerequisites

Before building Stremini AI, ensure you have the following installed:

#### Required Software

1. **Flutter SDK** (Latest stable version)
   ```bash
   flutter --version
   # Flutter 3.16.0 or higher required
   ```

2. **Android SDK**
   - Minimum API Level: 26 (Android 8.0 Oreo)
   - Target API Level: 34 (Android 14)
   - Build Tools: 34.0.0

3. **Java Development Kit (JDK)**
   ```bash
   java -version
   # OpenJDK 11 or higher required
   ```

4. **Android Studio** (Recommended)
   - Version: 2023.1.1 or higher
   - Includes Android SDK and emulator

#### Optional Tools

- **VS Code** with Flutter and Dart extensions
- **Android Device** (Physical device recommended for testing Accessibility features)
- **Git** for version control

### Development Environment Setup

#### 1. Clone the Repository

```bash
git clone https://github.com/yourusername/stremini-ai.git
cd stremini-ai
```

#### 2. Install Flutter Dependencies

```bash
flutter pub get
```

This will install all required packages defined in `pubspec.yaml`:
- `flutter_riverpod`: State management
- `http`: HTTP client
- `shared_preferences`: Local storage
- Additional dependencies...

#### 3. Configure Android SDK

Ensure your `local.properties` file points to the correct SDK location:

```properties
# android/local.properties
sdk.dir=/Users/yourusername/Library/Android/sdk
flutter.sdk=/Users/yourusername/flutter
```

#### 4. Verify Installation

```bash
flutter doctor -v
```

Ensure all checks pass (✓). Address any issues before proceeding.

### Building from Source

#### Debug Build

For development and testing:

```bash
flutter build apk --debug
```

Output: `build/app/outputs/flutter-apk/app-debug.apk`

#### Release Build

For production deployment:

```bash
flutter build apk --release
```

Output: `build/app/outputs/flutter-apk/app-release.apk`

#### App Bundle (For Google Play)

```bash
flutter build appbundle --release
```

Output: `build/app/outputs/bundle/release/app-release.aab`

#### Build Modes

- **Debug:** Includes debugging symbols, hot reload support
- **Profile:** Optimized for performance profiling
- **Release:** Fully optimized, minified, production-ready

### Running on Device

#### Using Android Studio

1. Open the project in Android Studio
2. Connect your Android device via USB
3. Enable **Developer Options** and **USB Debugging** on device
4. Click **Run** (▶️) button in Android Studio

#### Using Command Line

```bash
# List connected devices
flutter devices

# Run on specific device
flutter run -d <device-id>

# Run with hot reload
flutter run --hot
```

#### Emulator Setup

**Note:** Emulators may have limitations with Accessibility Services and System Alert Windows.

```bash
# List available emulators
flutter emulators

# Launch emulator
flutter emulators --launch <emulator-id>

# Run app on emulator
flutter run
```

---

## Configuration

### Environment Variables

Create a `.env` file in the project root:

```env
# Backend Configuration
API_BASE_URL=https://api.stremini.workers.dev
API_TIMEOUT_SECONDS=10

# Feature Flags
ENABLE_ANALYTICS=false
ENABLE_CRASH_REPORTING=false

# Debug Settings
DEBUG_OVERLAY_BOUNDS=false
DEBUG_LOG_API_REQUESTS=true
```

Load environment variables in Dart:

```dart
// lib/config/env.dart
import 'package:flutter_dotenv/flutter_dotenv.dart';

class Env {
  static String get apiBaseUrl => dotenv.env['API_BASE_URL'] ?? '';
  static int get apiTimeout => 
    int.parse(dotenv.env['API_TIMEOUT_SECONDS'] ?? '10');
}
```

### App Configuration

Modify `lib/config/app_config.dart`:

```dart
class AppConfig {
  // Overlay Settings
  static const double bubbleSize = 56.0;
  static const int bubbleOpacity = 200; // 0-255
  
  // Scan Settings
  static const Duration scanDebounce = Duration(milliseconds: 500);
  static const int maxConcurrentScans = 1;
  
  // Chat Settings
  static const int maxMessageHistory = 100;
  static const Duration messageTimeout = Duration(seconds: 30);
  
  // Security
  static const bool requireScreenLock = false;
  static const Duration sessionTimeout = Duration(minutes: 30);
}
```

---

## Permissions

### Required Permissions

Stremini AI requires the following Android permissions:

#### 1. SYSTEM_ALERT_WINDOW (Display Over Other Apps)

**Purpose:** Allows the app to draw floating windows and overlays on top of other applications.

**Used By:**
- `ChatOverlayService` (floating bubble and chat window)
- `ScreenReaderService` (threat indicator overlays)

**AndroidManifest.xml:**
```xml
<uses-permission android:name="android.permission.SYSTEM_ALERT_WINDOW"/>
```

**Request Code:**
```kotlin
if (!Settings.canDrawOverlays(context)) {
    val intent = Intent(
        Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
        Uri.parse("package:${context.packageName}")
    )
    startActivityForResult(intent, REQUEST_OVERLAY_PERMISSION)
}
```

#### 2. BIND_ACCESSIBILITY_SERVICE

**Purpose:** Grants access to screen content and UI elements across all applications.

**Used By:**
- `ScreenReaderService` (screen content extraction and analysis)

**AndroidManifest.xml:**
```xml
<service
    android:name=".ScreenReaderService"
    android:permission="android.permission.BIND_ACCESSIBILITY_SERVICE"
    android:exported="false">
    <intent-filter>
        <action android:name="android.accessibilityservice.AccessibilityService" />
    </intent-filter>
    <meta-data
        android:name="android.accessibilityservice"
        android:resource="@xml/accessibility_service_config" />
</service>
```

**Configuration (res/xml/accessibility_service_config.xml):**
```xml
<?xml version="1.0" encoding="utf-8"?>
<accessibility-service
    xmlns:android="http://schemas.android.com/apk/res/android"
    android:accessibilityEventTypes="typeWindowStateChanged|typeWindowContentChanged"
    android:accessibilityFeedbackType="feedbackGeneric"
    android:accessibilityFlags="flagReportViewIds|flagRetrieveInteractiveWindows"
    android:canRetrieveWindowContent="true"
    android:description="@string/accessibility_service_description"
    android:notificationTimeout="100"
    android:packageNames="@null"
    android:settingsActivity="com.android.stremini_ai.MainActivity" />
```

#### 3. FOREGROUND_SERVICE

**Purpose:** Ensures the overlay service remains active without being killed by the system.

**AndroidManifest.xml:**
```xml
<uses-permission android:name="android.permission.FOREGROUND_SERVICE"/>
<uses-permission android:name="android.permission.FOREGROUND_SERVICE_SPECIAL_USE"/>
```

#### 4. INTERNET

**Purpose:** Enables communication with the AI backend API.

**AndroidManifest.xml:**
```xml
<uses-permission android:name="android.permission.INTERNET"/>
```

#### 5. POST_NOTIFICATIONS (Android 13+)

**Purpose:** Displays foreground service notifications.

**AndroidManifest.xml:**
```xml
<uses-permission android:name="android.permission.POST_NOTIFICATIONS"/>
```

### Runtime Permission Flow

```kotlin
// Permission request sequence
fun requestPermissionsSequentially(activity: Activity) {
    when {
        // Step 1: Overlay permission
        !Settings.canDrawOverlays(activity) -> {
            requestOverlayPermission(activity)
        }
        // Step 2: Accessibility permission
        !isAccessibilityEnabled(activity) -> {
            showAccessibilityDialog(activity)
        }
        // Step 3: Notification permission (Android 13+)
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
        !hasNotificationPermission(activity) -> {
            ActivityCompat.requestPermissions(
                activity,
                arrayOf(Manifest.permission.POST_NOTIFICATIONS),
                REQUEST_NOTIFICATION_PERMISSION
            )
        }
        // All permissions granted
        else -> {
            onAllPermissionsGranted()
        }
    }
}
```

### Permission Best Practices

- **Request Just-In-Time:** Only request permissions when needed
- **Explain Clearly:** Show rationale dialogs before requesting sensitive permissions
- **Handle Denials Gracefully:** Provide alternative functionality or clear instructions
- **Periodic Checks:** Monitor permission status as users can revoke them at any time

---

## Usage Guide

### Initial Setup

1. **Install the App**
   - Download and install the APK on your Android device
   - Open Stremini AI from your app drawer

2. **Grant Overlay Permission**
   - Tap "Enable Floating Assistant"
   - You'll be redirected to Settings → Apps → Special Access → Display over other apps
   - Find Stremini AI and toggle the switch to ON
   - Return to the app

3. **Enable Accessibility Service**
   - Tap "Enable Screen Scanner"
   - You'll be redirected to Settings → Accessibility
   - Find "Stremini AI Screen Reader" under Downloaded Services
   - Toggle the service ON
   - Review and accept the permission warning

4. **Configure Preferences**
   - Set scan frequency (Manual, On-demand, Continuous)
   - Choose threat notification style
   - Customize bubble appearance

### Enabling Screen Scanning

#### Manual Scan Mode

1. Open the floating bubble
2. Tap to open the radial menu
3. Select the "Scan" icon
4. Wait for analysis to complete
5. Review threat indicators overlaid on screen

#### Automatic Scan Mode

1. Navigate to Settings → Scanner Settings
2. Enable "Auto-scan on app launch"
3. Configure scan triggers:
   - New window opened
   - URL detected
   - Payment form detected
4. Set scan frequency limits to preserve battery

### Using the Floating Assistant

#### Opening the Chat Interface

**Method 1: Via Floating Bubble**
1. Tap the floating bubble once
2. Select "Chat" from the radial menu
3. Chat window slides up from bottom

**Method 2: Quick Access**
1. Double-tap the floating bubble
2. Chat window opens immediately

#### Context-Aware Queries

The assistant can reference on-screen content:

1. Open a webpage or app
2. Trigger a screen scan
3. Open the chat interface
4. Ask questions about the content:
   - "Is this website legitimate?"
   - "Summarize this article"
   - "Translate this text to Spanish"

#### Managing the Bubble

- **Move:** Drag the bubble to any screen edge
- **Hide:** Long-press and select "Hide temporarily"
- **Lock Position:** Long-press and select "Lock position"
- **Close:** Swipe down on the bubble

### AI Keyboard Integration

#### Enabling Stremini Keyboard

1. Navigate to Settings → System → Languages & Input → On-screen keyboard
2. Select "Manage on-screen keyboards"
3. Enable "Stremini AI Keyboard"
4. Return and select "Stremini AI Keyboard" as default

#### Using Keyboard Features

**Text Completion:**
- Start typing in any text field
- AI suggestions appear above keyboard
- Tap suggestion to insert

**Translation:**
- Type message in your language
- Tap translation icon
- Select target language
- Translated text appears in suggestion bar

**Tone Adjustment:**
- Type or paste your message
- Tap tone icon
- Select desired tone (Professional, Casual, Friendly, Formal)
- Rewritten message appears as suggestion

**Grammar Correction:**
- Automatic underlining of potential errors
- Tap underlined text for suggestions
- Select correction to apply

---

## API Documentation

### Method Channels

#### stremini.chat.overlay

**Methods:**

| Method | Parameters | Returns | Description |
|--------|-----------|---------|-------------|
| `startOverlay` | None | `bool` | Starts the floating overlay service |
| `stopOverlay` | None | `bool` | Stops the floating overlay service |
| `isOverlayActive` | None | `bool` | Checks if overlay service is running |
| `updateBubblePosition` | `x: int, y: int` | `void` | Updates bubble coordinates |
| `setBubbleVisibility` | `visible: bool` | `void` | Shows or hides the bubble |

**Example Usage:**

```dart
final platform = MethodChannel('stremini.chat.overlay');

// Start overlay
try {
  final result = await platform.invokeMethod('startOverlay');
  print('Overlay started: $result');
} on PlatformException catch (e) {
  print('Error: ${e.message}');
}

// Check status
final isActive = await platform.invokeMethod<bool>('isOverlayActive');
```

#### stremini.keyboard

**Methods:**

| Method | Parameters | Returns | Description |
|--------|-----------|---------|-------------|
| `enableKeyboard` | None | `void` | Opens keyboard settings |
| `isKeyboardEnabled` | None | `bool` | Checks if keyboard is active IME |
| `getSuggestions` | `text: String` | `List<String>` | Requests text suggestions |
| `translateText` | `text: String, targetLang: String` | `String` | Translates text |

### Event Channels

#### stremini.keyboard.suggestions

Streams real-time text suggestions from the keyboard.

```dart
final eventChannel = EventChannel('stremini.keyboard.suggestions');

eventChannel.receiveBroadcastStream().listen((suggestion) {
  print('New suggestion: $suggestion');
  // Update UI with suggestion
});
```

#### stremini.scanner.results

Streams scan results as they're detected.

```dart
final scanChannel = EventChannel('stremini.scanner.results');

scanChannel.receiveBroadcastStream().listen((result) {
  final scanResult = ScanResult.fromJson(jsonDecode(result));
  // Handle new threat detection
});
```

### Backend Endpoints

#### POST /api/analyze-screen

Analyzes screen content for threats.

**Request Body:**
```json
{
  "elements": [
    {
      "text": "Click here to claim your prize!",
      "bounds": {"left": 100, "top": 200, "right": 400, "bottom": 250},
      "className": "android.widget.TextView",
      "is_clickable": true
    }
  ],
  "package_name": "com.example.app",
  "timestamp": "2024-02-14T10:30:00Z"
}
```

**Response:**
```json
{
  "threats": [
    {
      "element_index": 0,
      "level": "DANGEROUS",
      "type": "PHISHING_URL",
      "confidence": 0.95,
      "description": "Suspicious domain attempting to impersonate official service"
    }
  ],
  "overall_risk_score": 0.85,
  "summary": "1 dangerous threat detected",
  "scan_id": "scan_abc123"
}
```

#### POST /api/chat

Handles conversational AI requests.

**Request Body:**
```json
{
  "message": "Is this website safe?",
  "context": {
    "screen_content": "Welcome to PayPaI.com...",
    "app_package": "com.android.chrome"
  },
  "conversation_id": "conv_xyz789",
  "stream": false
}
```

**Response:**
```json
{
  "response": "This appears to be a phishing attempt. The domain 'PayPaI.com' uses a capital 'I' instead of lowercase 'l' to mimic PayPal. Do not enter any credentials.",
  "confidence": 0.92,
  "sources": ["threat_database", "url_analysis"]
}
```

#### POST /api/keyboard-assist

Provides keyboard assistance features.

**Request Body:**
```json
{
  "action": "translate",
  "text": "Hello, how are you?",
  "source_lang": "en",
  "target_lang": "es"
}
```

**Response:**
```json
{
  "result": "Hola, ¿cómo estás?",
  "confidence": 0.98
}
```

---

## Security Considerations

### Data Privacy

- **No Data Retention:** Screen content is analyzed in real-time and not stored on servers
- **Encrypted Transit:** All API communications use TLS 1.3
- **Local Processing:** Basic threat detection occurs on-device when possible
- **No Tracking:** The app does not collect analytics or user behavior data

### Permission Security

- **Minimal Scope:** Accessibility service only requests necessary event types
- **User Control:** All features can be toggled on/off independently
- **Transparent Usage:** Clear explanations provided for each permission

### Threat Model

**Protected Against:**
- Phishing websites mimicking legitimate services
- Social engineering attempts in messaging apps
- Malicious apps requesting excessive permissions
- Fake payment forms

**Not Protected Against:**
- Zero-day exploits in Android OS
- Physical device access attacks
- Sophisticated APT (Advanced Persistent Threat) attacks
- Malware with root-level access

### Best Practices

1. **Keep Updated:** Regularly update the app to receive latest threat signatures
2. **Review Permissions:** Periodically review granted permissions
3. **Report Threats:** Use in-app reporting for false positives/negatives
4. **Secure Backend:** Ensure backend API keys are rotated regularly

---

## Performance Optimization

### Battery Optimization

**Scan Frequency Management:**
```kotlin
// Implement exponential backoff for scans
class ScanScheduler {
    private var scanInterval = 500L // milliseconds
    private val maxInterval = 5000L
    
    fun scheduleScan() {
        Handler(Looper.getMainLooper()).postDelayed({
            performScan()
            scanInterval = min(scanInterval * 2, maxInterval)
        }, scanInterval)
    }
    
    fun resetInterval() {
        scanInterval = 500L
    }
}
```

**Overlay Rendering Optimization:**
```kotlin
// Use hardware acceleration
val params = WindowManager.LayoutParams().apply {
    flags = flags or WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED
}

// Minimize overdraw
overlayView.setLayerType(View.LAYER_TYPE_HARDWARE, null)
```

### Memory Management

**Node Tree Recycling:**
```kotlin
fun extractScreenContent(root: AccessibilityNodeInfo): ScreenContent {
    val elements = mutableListOf<TextElement>()
    
    try {
        traverseAndExtract(root, elements)
    } finally {
        // Always recycle nodes
        recycleNodeTree(root)
    }
    
    return ScreenContent(elements)
}

fun recycleNodeTree(node: AccessibilityNodeInfo?) {
    node?.let {
        for (i in 0 until it.childCount) {
            recycleNodeTree(it.getChild(i))
        }
        it.recycle()
    }
}
```

### Network Optimization

**Request Batching:**
```dart
class BatchedApiService {
  final _requestQueue = <ScreenAnalysisRequest>[];
  Timer? _batchTimer;
  
  void queueAnalysis(ScreenAnalysisRequest request) {
    _requestQueue.add(request);
    
    _batchTimer?.cancel();
    _batchTimer = Timer(Duration(milliseconds: 200), () {
      _processBatch();
    });
  }
  
  Future<void> _processBatch() async {
    if (_requestQueue.isEmpty) return;
    
    final batch = List.of(_requestQueue);
    _requestQueue.clear();
    
    await _apiService.analyzeBatch(batch);
  }
}
```

---

## Troubleshooting

### Common Issues

#### Floating Bubble Not Appearing

**Symptoms:** Overlay service starts but bubble is not visible

**Solutions:**
1. Check overlay permission:
   ```kotlin
   if (!Settings.canDrawOverlays(context)) {
       // Request permission again
   }
   ```
2. Verify service is running:
   ```bash
   adb shell dumpsys activity services | grep ChatOverlayService
   ```
3. Check for conflicting overlay apps (e.g., Facebook Messenger bubbles)
4. Restart the device

#### Screen Scanner Not Working

**Symptoms:** Scanner doesn't detect any content or threats

**Solutions:**
1. Verify accessibility service is enabled:
   ```bash
   adb shell settings get secure enabled_accessibility_services
   ```
2. Check service configuration in `accessibility_service_config.xml`
3. Ensure `canRetrieveWindowContent` is set to `true`
4. Grant accessibility permission again from Settings
5. Check for Android version-specific restrictions (Samsung, Xiaomi custom ROMs)

#### Chat Responses Timing Out

**Symptoms:** Chat requests fail or take too long

**Solutions:**
1. Check internet connectivity
2. Verify backend API status
3. Increase timeout duration:
   ```dart
   final response = await http.post(url)
     .timeout(Duration(seconds: 30));
   ```
4. Check for network restrictions (VPN, firewall)

#### High Battery Drain

**Symptoms:** App consumes excessive battery

**Solutions:**
1. Reduce scan frequency in Settings
2. Disable continuous scanning mode
3. Enable battery optimization for the app:
   ```bash
   adb shell dumpsys deviceidle whitelist
   ```
4. Check for wake locks:
   ```bash
   adb shell dumpsys power | grep stremini
   ```

### Debug Mode

Enable detailed logging:

```kotlin
// In MainActivity.kt
companion object {
    const val DEBUG = true
}

fun log(message: String) {
    if (DEBUG) {
        Log.d("StreminiAI", message)
    }
}
```

View logs:
```bash
adb logcat | grep StreminiAI
```

### Crash Reporting

Enable crash dumps:

```bash
adb shell setprop debug.stremini.crash_dump 1
adb logcat -b crash
```

---

## Development

### Project Structure

```
stremini-ai/
├── android/
│   ├── app/
│   │   ├── src/
│   │   │   ├── main/
│   │   │   │   ├── kotlin/com/android/stremini_ai/
│   │   │   │   │   ├── MainActivity.kt
│   │   │   │   │   ├── ScreenReaderService.kt
│   │   │   │   │   ├── ChatOverlayService.kt
│   │   │   │   │   ├── StreminiIME.kt
│   │   │   │   │   └── utils/
│   │   │   │   ├── res/
│   │   │   │   │   ├── layout/
│   │   │   │   │   ├── values/
│   │   │   │   │   └── xml/
│   │   │   │   └── AndroidManifest.xml
│   │   └── build.gradle
│   └── build.gradle
├── lib/
│   ├── main.dart
│   ├── config/
│   │   ├── app_config.dart
│   │   └── env.dart
│   ├── models/
│   │   ├── screen_element.dart
│   │   ├── threat_info.dart
│   │   └── chat_message.dart
│   ├── providers/
│   │   ├── overlay_provider.dart
│   │   ├── chat_provider.dart
│   │   └── permissions_provider.dart
│   ├── screens/
│   │   ├── home_screen.dart
│   │   ├── chat_screen.dart
│   │   └── permissions_screen.dart
│   ├── services/
│   │   ├── api_service.dart
│   │   ├── overlay_service.dart
│   │   └── keyboard_service.dart
│   └── widgets/
│       ├── threat_indicator.dart
│       └── chat_bubble.dart
├── backend/
│   ├── src/
│   │   ├── index.ts
│   │   ├── handlers/
│   │   │   ├── analyze.ts
│   │   │   ├── chat.ts
│   │   │   └── keyboard.ts
│   │   └── utils/
│   └── wrangler.toml
├── test/
│   ├── widget_test.dart
│   └── integration_test/
├── pubspec.yaml
└── README.md
```

### Adding New Features

#### Example: Adding a New Threat Type

1. **Define the threat type** in Kotlin:
```kotlin
// ScreenReaderService.kt
enum class ThreatType {
    PHISHING_URL,
    SUSPICIOUS_PERMISSION_REQUEST,
    FAKE_LOGIN_PROMPT,
    PAYMENT_SCAM,
    SOCIAL_ENGINEERING,
    CRYPTOCURRENCY_SCAM  // New type
}
```

2. **Update detection logic**:
```kotlin
fun detectCryptoScam(element: TextElement): Boolean {
    val cryptoKeywords = listOf(
        "send bitcoin",
        "wallet address",
        "transfer crypto"
    )
    return cryptoKeywords.any { 
        element.text.contains(it, ignoreCase = true) 
    }
}
```

3. **Update backend analysis**:
```typescript
// backend/src/handlers/analyze.ts
export function analyzeThreat(text: string): ThreatInfo {
    if (containsCryptoScam(text)) {
        return {
            type: 'CRYPTOCURRENCY_SCAM',
            level: 'DANGEROUS',
            confidence: 0.9
        };
    }
    // ... other checks
}
```

4. **Update UI**:
```dart
// lib/widgets/threat_indicator.dart
Color _getThreatColor(ThreatType type) {
    switch (type) {
        case ThreatType.cryptocurrencyScam:
            return Colors.orange;
        // ... other cases
    }
}
```

### Testing

#### Unit Tests

```dart
// test/services/api_service_test.dart
import 'package:flutter_test/flutter_test.dart';
import 'package:mockito/mockito.dart';

void main() {
  group('ApiService', () {
    test('analyzeScreen returns valid response', () async {
      final mockClient = MockClient();
      final apiService = ApiService(client: mockClient);
      
      when(mockClient.post(any, body: anyNamed('body')))
        .thenAnswer((_) async => http.Response(
          '{"threats": [], "overall_risk_score": 0.0}',
          200
        ));
      
      final result = await apiService.analyzeScreen(testRequest);
      
      expect(result.threats, isEmpty);
      expect(result.overallRiskScore, 0.0);
    });
  });
}
```

#### Widget Tests

```dart
// test/screens/home_screen_test.dart
void main() {
  testWidgets('HomeScreen displays permission status', (tester) async {
    await tester.pumpWidget(
      ProviderScope(
        child: MaterialApp(home: HomeScreen()),
      ),
    );
    
    expect(find.text('Overlay Permission'), findsOneWidget);
    expect(find.text('Accessibility Permission'), findsOneWidget);
  });
}
```

#### Integration Tests

```bash
flutter test integration_test/app_test.dart
```

---

## Known Limitations

1. **Android Version Compatibility**
   - Minimum API Level 26 (Android 8.0)
   - Some features may be restricted on custom ROMs (MIUI, One UI)

2. **Accessibility Service Restrictions**
   - Cannot read content from password fields
   - Some apps block accessibility access (banking apps)
   - Performance impact on low-end devices

3. **Overlay Limitations**
   - Cannot draw over system UI (status bar, navigation bar)
   - Some launchers restrict overlay positioning
   - May conflict with other overlay apps

4. **Backend Limitations**
   - Cloudflare Workers have 50ms CPU time limit
   - Request/response size limits (10MB)
   - Rate limiting on free tier

5. **Language Support**
   - Primary threat detection optimized for English
   - Translation quality varies by language pair

---

## Roadmap

### Version 2.0 (Q2 2024)
- [ ] On-device ML models for offline threat detection
- [ ] Multi-language support for threat detection
- [ ] Widget support for quick access
- [ ] Improved battery optimization

### Version 2.1 (Q3 2024)
- [ ] OCR for image-based threats
- [ ] Voice assistant integration
- [ ] Custom threat rules
- [ ] Export scan reports

### Version 3.0 (Q4 2024)
- [ ] iOS version (SwiftUI + UIKit)
- [ ] Web dashboard for threat analytics
- [ ] Team/Family protection plans
- [ ] Advanced AI models (GPT-4 integration)

---

## Contributing

We welcome contributions! Please follow these guidelines:

### How to Contribute

1. **Fork the repository**
2. **Create a feature branch**
   ```bash
   git checkout -b feature/amazing-feature
   ```
3. **Make your changes**
4. **Write tests**
5. **Commit with descriptive messages**
   ```bash
   git commit -m "Add: Cryptocurrency scam detection"
   ```
6. **Push to your fork**
   ```bash
   git push origin feature/amazing-feature
   ```
7. **Open a Pull Request**

### Code Style

**Dart:**
- Follow official [Dart Style Guide](https://dart.dev/guides/language/effective-dart/style)
- Run `flutter format .` before committing
- Use `flutter analyze` to check for issues

**Kotlin:**
- Follow [Kotlin Coding Conventions](https://kotlinlang.org/docs/coding-conventions.html)
- Use 4 spaces for indentation
- Maximum line length: 120 characters

### Commit Messages

Use conventional commits:
- `feat:` New feature
- `fix:` Bug fix
- `docs:` Documentation changes
- `style:` Code style changes (formatting)
- `refactor:` Code refactoring
- `test:` Adding tests
- `chore:` Maintenance tasks

---

## License

This project is licensed under the MIT License - see the [LICENSE](LICENSE) file for details.

```
MIT License

Copyright (c) 2024 Stremini AI

Permission is hereby granted, free of charge, to any person obtaining a copy
of this software and associated documentation files (the "Software"), to deal
in the Software without restriction, including without limitation the rights
to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
copies of the Software, and to permit persons to whom the Software is
furnished to do so, subject to the following conditions:

The above copyright notice and this permission notice shall be included in all
copies or substantial portions of the Software.

THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
SOFTWARE.
```

---

## Support

### Getting Help

- **Documentation:** [docs.stremini.ai](https://docs.stremini.ai)
- **GitHub Issues:** [github.com/yourusername/stremini-ai/issues](https://github.com/yourusername/stremini-ai/issues)
- **Email:** support@stremini.ai
- **Discord:** [discord.gg/stremini](https://discord.gg/stremini)

### FAQ

**Q: Does Stremini AI work on Android tablets?**  
A: Yes, but the overlay positioning may need manual adjustment on larger screens.

**Q: Can I use this on rooted devices?**  
A: Yes, but some security features may behave differently.

**Q: How much data does the app use?**  
A: Approximately 5-10MB per day with moderate scanning usage.

**Q: Is my screen content sent to servers?**  
A: Only when threats are detected and need cloud-based analysis. Most processing is local.

---

## Acknowledgments

- **Flutter Team** for the excellent framework
- **Android Accessibility Team** for comprehensive APIs
- **Cloudflare** for Workers platform
- **Community Contributors** for feature suggestions and bug reports

---

**Built with ❤️ by the Stremini AI Team**

*Last Updated: February 14, 2024*
