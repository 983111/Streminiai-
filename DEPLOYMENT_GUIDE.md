# Google Play Store Deployment Guide

## Pre-Deployment Requirements

### 1. Google Play Developer Account

- Create account at https://play.google.com/apps/publish
- Complete business registration
- Add payment method
- Agree to Developer Distribution Agreement

### 2. Signing Key Setup

#### Generate Release Keystore (One-time)

```bash
keytool -genkey -v -keystore ~/stremini-release.keystore \
  -keyalg RSA -keysize 2048 -validity 10000 \
  -alias stremini_release_key
```

#### Store Keystore Securely

- **DO NOT commit to git**
- **DO NOT share publicly**
- Back up to secure location (encrypted drive/cloud)
- Store password in secure vault (1Password, LastPass, Keeper)
- Document keystore location and alias for team

### 3. Build Preparation

#### Update Version

Edit `android/app/build.gradle.kts`:

```gradle
defaultConfig {
    versionCode = 2        // Increment by 1
    versionName = "1.0.1"  // MAJOR.MINOR.PATCH
}
```

#### Verify Signing Config

```gradle
buildTypes {
    release {
        signingConfig signingConfigs.release
    }
}
```

**Add to `~/.gradle/gradle.properties`** (local, not in git):

```properties
STREMINI_RELEASE_STORE_FILE=/path/to/stremini-release.keystore
STREMINI_RELEASE_STORE_PASSWORD=your_keystore_password
STREMINI_RELEASE_KEY_ALIAS=stremini_release_key
STREMINI_RELEASE_KEY_PASSWORD=your_key_password
```

## Build Process

### Step 1: Clean Build

```bash
flutter clean
cd android
./gradlew clean
cd ..
flutter pub get
```

### Step 2: Build Release APK

```bash
flutter build apk --release
```

**Output:** `build/app/outputs/flutter-apk/app-release.apk`

### Step 3: Build App Bundle (Recommended)

```bash
flutter build appbundle --release
```

**Output:** `build/app/outputs/bundle/release/app-release.aab`

_App Bundle is smaller and recommended for Play Store Distribution_

### Step 4: Verify Build

```bash
# Check APK size
ls -lh build/app/outputs/flutter-apk/app-release.apk

# Verify signing
jarsigner -verify -verbose -certs build/app/outputs/flutter-apk/app-release.apk

# List exported classes (security check)
bundletool dump manifest --bundle=build/app/outputs/bundle/release/app-release.aab
```

## Upload to Google Play Console

### Step 1: Access Play Console

1. Go to https://play.google.com/console
2. Select "Stremini AI" app
3. Navigate to **Release** → **Production**

### Step 2: Create Release

1. Click **Create new release**
2. Upload App Bundle (`.aab` file)
3. Review app bundle:
   - Check all features are listed
   - Verify supported devices
   - Check APK breakdown

### Step 3: Configure Release Details

#### Release Notes

```
Version 1.0.1 - Bug Fixes & Improvements

NEW:
• [Feature name if applicable]

FIXES:
• Fixed crash when sending large attachments
• Improved accessibility service stability
• Enhanced screen detection accuracy

IMPROVEMENTS:
• Faster chat response times
• Better permission handling
• Updated security libraries
```

#### Content Rating

- Already completed in initial setup
- Update if app focus changes
- Go to **Setup** → **App content**

#### Testing Instructions

Provide QA instructions (if applying for expedited review):

```
1. Enable accessibility service in Settings
2. Grant overlay permission
3. Open any app and look for floating bubble
4. Test chat with sample messages: "Hello", "What are scams?"
5. Verify no crashes occur
```

### Step 4: Review Release

Before submitting:

- [x] Version code incremented
- [x] App title and description correct
- [x] Screenshots and videos up-to-date
- [x] Privacy policy URL accessible
- [x] Contact email verified
- [x] All app details complete
- [x] No restricted content warnings
- [x] Permissions justified

### Step 5: Submit Release

1. **Set release name**: "Release 1.0.1" or "Christmas Update"
2. **Choose roll-out strategy**:
   - **Staged rollout** (recommended for first releases)
     - Start at 5% of users
     - Monitor for crashes
     - Increase to 25% → 50% → 100%
   - **Immediate full release**
     - For minor bug fixes
     - Only if urgently needed

3. Click **Review release**
4. Verify all information is correct
5. Click **Start rollout to Production**

## Post-Deployment

### Immediate Monitoring (First Week)

```
Day 1:
- Monitor Android Vitals every 2 hours
- Watch crash rate closely
- Read user reviews/comments
- Check installation success rate

Day 3-7:
- If crash rate < 0.5% → Increase rollout gradually
- If crash rate > 1% → Consider rollback
- Address critical issues quickly
```

### Android Vitals Dashboard

- Go to **Quality** → **Android Vitals**
- Monitor:
  - Crash rate
  - ANR (Application Not Responding) rate
  - Frozen frame rate
  - Slow render rate
  - Excessive wake-up rate

### User Feedback

- Go to **Reviews**
- Filter by rating and date
- Respond to critical issues within 24 hours
- Create follow-up release if needed

### Crash Reporting

- Go to **Crashes and ANRs**
- Click on crash type for details
- Stack trace helps identify root cause
- Upload ProGuard mapping if not auto-mapped

## Rollback Procedure

If critical issues discovered:

1. **Immediate**: Pause rollout
   - Go to **Release** → **Production**
   - Click **Pause rollout**
   - Users keep current version (no forced update)

2. **Short-term**: Reduce rollout
   - Go to **Manage release**
   - Adjust percentage back to 0% or 5%
   - This doesn't uninstall app for existing users

3. **Long-term**: Release hotfix
   - Increment version code
   - Build and test locally
   - Create new release with fixes
   - Submit as bug fix release

## Update Cadence

### Regular Updates

- Bug fix releases: 1-2 weeks if critical
- Feature releases: Monthly
- Major updates: Quarterly

### Maintenance Releases

- Keep dependencies up-to-date
- Android API level updates required by Play Store
- Security patches for vulnerabilities

## Monitoring After Launch

### Key Metrics Dashboard

```
Daily Checklist:
1. Crash rate < 1%?
2. ANR rate < 0.5%?
3. Good reviews > Bad reviews?
4. Installation success > 95%?
5. No critical user reports?
```

### Weekly Review

- Analyze Install/Uninstall trends
- Review top crashes
- Check user acquisition metrics
- Plan next release features

## Important Links

- **Play Console**: https://play.google.com/console
- **Android Dashboard**: https://play.google.com/console/overview
- **Google Play Policy**: https://play.google.com/about/developer-content-policy/
- **Android Version History**: https://en.wikipedia.org/wiki/Android_version_history

## Troubleshooting

### APK Upload Fails

- Verify signing certificate
- Check version code is incremented
- Ensure file size < 100MB
- Verify package name matches registered package

### Build Fails

```bash
# Clear everything
flutter clean
cd android && ./gradlew clean && cd ..

# Rebuild
flutter pub get
flutter build appbundle --release
```

### Low Installation Rate

- Check device compatibility
- Review minimum SDK requirement
- Check permission requirements
- Review recent crash reports

### High Crash Rate

- Check Android Vitals crash report
- Test on problematic devices
- Check logcat output: `flutter logs`
- Roll back if critical

---

**Happy deploying! Remember to test thoroughly before each release.**
