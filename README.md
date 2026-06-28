# KMP Biometric Auth

A Kotlin Multiplatform library for hardware-backed biometric authentication.

- **Android**: Keystore-bound AES/GCM encryption via `BiometricPrompt` + `CryptoObject`
- **iOS**: Keychain item protected by `biometryCurrentSet` access control via `LAContext`

Authentication is cryptographically enforced at the OS level — not a boolean gate.

---

## Platform support

| Platform | Mechanism | Biometric |
|---|---|---|
| Android | Android Keystore + `BiometricPrompt` | Fingerprint (Class 3 / BIOMETRIC_STRONG) |
| iOS | Keychain + `SecAccessControlCreateWithFlags` | Face ID or Touch ID (device default) |

### Android note
Only **Class 3 (BIOMETRIC_STRONG)** biometrics are accepted. Devices with Class 2 face unlock (common on mid-range Samsung devices) will need a fingerprint enrolled to use this library. PIN/passcode fallback is intentionally excluded — the library guarantees hardware-backed biometric authentication only. If your app needs a PIN fallback, implement it on top of this library.

### iOS note
The library automatically uses Face ID or Touch ID depending on the device hardware — no configuration needed. Add `NSFaceIDUsageDescription` to your `Info.plist` (required by Apple, the app will crash without it on Face ID devices).

### Biometric enrollment changes
If the user adds or removes a biometric after first use:
- **Android**: the Keystore key is permanently invalidated. The library self-heals on the next call — the dead key is deleted and a fresh one is created. The first call after enrollment change returns `BiometricResult.Error`.
- **iOS**: the Keychain item becomes inaccessible. Same self-healing behavior — item is deleted and recreated on the next successful auth.

---

## Installation

Add to your `shared/build.gradle.kts`:

```kotlin
commonMain.dependencies {
    implementation("io.github.froyder:kmp-biometric-auth:1.0.0")
}
```

### Project structure note
If you're using the current Android Studio KMP wizard (which separates `androidApp` and `shared` into distinct modules), use `api` instead of `implementation` so that `KmpBiometricAuthenticator` and `BiometricResult` are visible across module boundaries:

```kotlin
commonMain.dependencies {
    api("io.github.froyder:kmp-biometric-auth:1.0.0")
}
```

---

## Setup

### Android
Your `MainActivity` must extend `FragmentActivity` (not `ComponentActivity`):

```kotlin
class MainActivity : FragmentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val authenticator = KmpBiometricAuthenticator(this)
        setContent { App(authenticator) }
    }
}
```

Add to `AndroidManifest.xml`:
```xml
<uses-permission android:name="android.permission.USE_BIOMETRIC" />
```

### iOS
Add to `Info.plist`:
```xml
<key>NSFaceIDUsageDescription</key>
<string>This app uses Face ID to verify your identity.</string>
```

Wire up in `MainViewController.kt`:
```kotlin
fun MainViewController() = ComposeUIViewController {
    App(KmpBiometricAuthenticator())
}
```

---

## Usage

```kotlin
val authenticator = KmpBiometricAuthenticator(activity) // Android
// or
val authenticator = KmpBiometricAuthenticator() // iOS

val result = authenticator.authenticate(
    title = "Verify identity",
    subtitle = "Confirm it's you",
    cancelButtonText = "Cancel" // optional, default is "Cancel"
)

when (result) {
    BiometricResult.Success -> // authenticated
    BiometricResult.Cancelled -> // user dismissed
    BiometricResult.NotAvailable -> // no biometrics enrolled
    is BiometricResult.Error -> // result.message contains details
}
```

---

## Testing

Unit testing biometric APIs requires hardware interaction and OS-level security components that cannot be mocked at the unit level. Integration testing is recommended in the consuming app.

The library includes unit tests for `BiometricResult` — sealed interface exhaustiveness and error message contract.

---

## License

Apache License 2.0 — see [LICENSE](LICENSE) for details.