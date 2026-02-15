# Google Play Store Configuration

## App Details for Publishing

### App Name

**Stremini AI** - Scam Protection & Smart Chatbot

### Short Description

Protect yourself from online scams with AI-powered security detection. Real-time screen analysis and intelligent chatbot assistance in one app.

### Full Description

**Stremini AI** is your personal AI-powered security guardian. Combine intelligent customer support with real-time scam detection.

**Key Features:**
🛡️ **Real-Time Scam Detection** - Accessibility service analyzes screen content to identify suspicious links, messages, and potential threats
💬 **Smart Chatbot** - Ask questions, get instant AI-powered assistance
🔒 **Privacy First** - Your data stays on your device; encrypted communication
⌨️ **AI Keyboard** - Secure text input with predictive assistance
🚀 **Always On** - Floating bubble for instant access from any app

**Permissions Explained:**

- **Accessibility Service**: Required to scan screen for scam detection - we never capture personal data, only analyze visible content for security threats
- **Overlay Permission**: Allows floating bubble to appear over other apps for quick access
- **Camera & Files**: Only used when YOU explicitly choose to attach media to chat
- **Notifications**: For security alerts about detected threats

**Privacy & Security:**
✓ No unnecessary data collection
✓ End-to-end encrypted communication
✓ Full permission control - disable features at any time
✓ Transparent about data usage
✓ Regular security updates

**How It Works:**

1. Enable accessibility service in settings (optional, for scam detection)
2. Ask our chatbot any questions
3. Get real-time security alerts when threats are detected
4. Enjoy protected browsing with confidence

**Perfect For:**

- Protection from phishing attempts
- Identifying suspicious websites and messages
- Quick access to customer support
- Learning about online security
- Anyone who feels unsafe online

---

## Technical Requirements

### Minimum Requirements

- Android 8.0 (API 26) and above
- 50 MB free storage space
- Active internet connection for chat features

### Target Devices

- Phones and tablets
- Primarily optimized for portrait orientation

### Content Rating Questionnaire

**Answers:**

- Violence: None
- Sexual Content: None
- Profanity: None
- Alcohol/Tobacco: None
- Gambling: None
- Frightening Content: None
- Ads: Optional (can be disabled)
- In-App Purchases: None

**Primary Age Group:** 13+

## Compliance Information

### Data Collection & Privacy

- Compliant with GDPR
- Compliant with CCPA
- Privacy Policy: See PRIVACY_POLICY.md
- No third-party data selling
- User can delete data anytime

### Permissions Justification

#### android.permission.INTERNET

**Purpose:** Enable AI chatbot communication, security updates, and cloud analysis

#### android.permission.SYSTEM_ALERT_WINDOW

**Purpose:** Display floating assistance bubble over other applications

#### android.permission.FOREGROUND_SERVICE

**Purpose:** Keep scam detection service running while user uses other apps

#### android.permission.ACCESSIBILITY_SERVICE

**Purpose:** Screen analysis for real-time scam detection ONLY
**Commitment:**

- No keystroke logging
- No personal data capture
- No screenshot storage
- Security-focused analysis only

#### android.permission.CAMERA

**Purpose:** User-initiated image attachment in chat (optional)

#### android.permission.READ_MEDIA_IMAGES

**Purpose:** Allow users to attach images from gallery (Android 13+)

#### android.permission.POST_NOTIFICATIONS

**Purpose:** Security alerts about detected threats

## Security & Testing

### Pre-Launch Security

- ✓ Tested on Android 12, 13, 14, 15
- ✓ ProGuard obfuscation enabled
- ✓ Native code analysis completed
- ✓ Malware scan: PASS
- ✓ SSL/TLS encryption verified
- ✓ Permissions audit: Compliant

### Signing & Build

- Release APK signed with release keystore
- minifyEnabled = true for production builds
- shrinkResources = true for size optimization
- Target API 35 (Android 15) compliance

## Release Checklist

Before submitting to Play Store:

- [ ] Update version code in build.gradle.kts
- [ ] Verify no debug logging in release builds
- [ ] Test on physical devices
- [ ] Verify all permissions work correctly
- [ ] Check app size (should be < 100MB)
- [ ] Test scam detection functionality
- [ ] Verify privacy policy is accessible
- [ ] Check Terms of Service
- [ ] Update screenshots and promotional images
- [ ] Verify support email is active
- [ ] Test on minimum supported API (26)
- [ ] Run Play Store Console pre-launch testing

## Sensitive App Policies

### Accessibility Service Policy

✓ Only used for stated purpose (scam detection)
✓ No logging of sensitive information
✓ No data transfer outside app
✓ User can disable anytime
✓ Clear UI about what's being analyzed

### Ads Policy

- Currently NO ads
- If ads are added in future:
  - User-friendly ad placement
  - Clear "Skip ad" options
  - Compliant ad networks only
  - No malicious ad content

## Sample Screenshots Title & Description

### Screenshot 1 (Scam Detection)

**Title:** Real-Time Threat Detection
**Description:** Stremini AI analyzes screen content in real-time to identify and alert you about potential scams and suspicious links.

### Screenshot 2 (Chatbot)

**Title:** Instant AI Assistant
**Description:** Ask questions, get answers. Our intelligent chatbot is available 24/7 to help with your queries.

### Screenshot 3 (Floating Bubble)

**Title:** Always Accessible
**Description:** Access the chatbot from any app with our convenient floating bubble interface.

### Screenshot 4 (Security)

**Title:** Privacy Protected
**Description:** Your data stays secure with encrypted communication and optional features you can control.

---

## Support Information

**Support Email:** support@stremini.ai
**Website:** https://stremini.ai
**Privacy Policy:** See included PRIVACY_POLICY.md
**Terms of Service:** [To be created]

## Contact Information

**Organization:** Stremini AI Inc.
**Contact:** support@stremini.ai
**Website:** https://stremini.ai
