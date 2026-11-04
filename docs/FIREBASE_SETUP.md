# Firebase & Google Sign-In Setup Guide for Nexus Agent v5

This guide provides step-by-step instructions to configure your own Firebase project for Nexus Agent v5 with Google Sign-In, Email/Password authentication, and Firestore cloud synchronization.

---

## 1. Firebase Project & Authentication Configuration

1. Open the [Firebase Console](https://console.firebase.google.com/) and create a new Firebase project (or select an existing one).
2. Navigate to **Build > Authentication > Sign-in method**:
   - **Google**: Click **Google**, toggle **Enable**, select your project support email, and click **Save**.
   - **Email/Password**: Click **Email/Password**, toggle **Enable**, and click **Save**.

---

## 2. Register Android Application & SHA Fingerprints

1. In Firebase Console, go to **Project Settings (gear icon) > General > Your apps**.
2. Click **Add app** > select the **Android** platform icon.
3. Enter the exact Android package name:
   ```
   com.aistudio.nexusagent.v5sync
   ```
4. Enter an App nickname (e.g., `Nexus Agent v5`).

### Required Configuration Match Table

| Field | Required Value | Where Configured / Verified |
| :--- | :--- | :--- |
| **Package Name (`applicationId`)** | `com.aistudio.nexusagent.v5sync` | `app/build.gradle.kts`, `app/google-services.json` |
| **Debug Keystore SHA-1** | `82:7D:D6:EC:05:17:6F:2D:EA:87:7A:DA:5A:E0:41:71:D4:AB:D6:F4` | Firebase Console > Project Settings > Android App SHA fingerprints |
| **Debug Keystore SHA-256** | `49:47:85:37:B1:89:F0:2F:AC:C1:22:16:94:8E:6E:28:9D:31:11:2D:02:A9:2B:A2:88:9C:62:D4:C2:FF:28:92` | Firebase Console > Project Settings > Android App SHA fingerprints |
| **Release Keystore SHA-1** | `14:EC:D8:29:0E:16:C3:D8:29:EC:31:10:2D:0D:D1:99:A6:B1:12:E3` | Firebase Console > Project Settings > Android App SHA fingerprints |
| **Release Keystore SHA-256** | `EA:25:BE:3C:7C:2B:54:FB:71:33:12:35:7F:5C:50:FD:AC:37:1C:35:0B:B5:E4:F4:E0:3D:CF:24:2F:5B:B7:17` | Firebase Console > Project Settings > Android App SHA fingerprints |
| **Web Client ID (`client_type: 3`)** | `68112922414-ef6fb5a80f9b983b1e0be58c1b6f6a82.apps.googleusercontent.com` | `app/google-services.json` (`oauth_client[client_type: 3]`), `default_web_client_id` |
| **Auth Provider** | Google Sign-in enabled | Firebase Console > Authentication > Sign-in method |
| **OAuth Consent Screen** | In **Testing** mode: add your Google account to **Test Users** | Google Cloud Console > APIs & Services > OAuth consent screen |

### Verification Commands

```bash
# Print Debug Keystore Fingerprints
keytool -list -v -keystore debug.keystore -alias androiddebugkey -storepass android -keypass android | grep -E "SHA1:|SHA256:"

# Print Release Keystore Fingerprints
keytool -list -v -keystore my-upload-key.jks -alias upload -storepass android -keypass android | grep -E "SHA1:|SHA256:"

# Print Gradle Signing Report
./gradlew signingReport
```

---

## 3. Download & Replace `google-services.json`

1. In Firebase Console, download the updated `google-services.json` file.
2. Place it at `/app/google-services.json` in this repository.
3. Verify that the file contains:
   - Your package name `com.aistudio.nexusagent.v5sync`
   - An `oauth_client` with `client_type: 3` (this is the Web/Server Client ID used by Credential Manager).

---

## 4. Deploy Firestore Security Rules

1. Ensure the Firestore Database is created in the Firebase Console in **Production mode**.
2. Deploy the hardened `firestore.rules` file:
   ```bash
   firebase deploy --only firestore:rules
   ```
3. The rules enforce strict per-user segregation (`/users/{userId}/**`), validated schema constraints, and a zero-trust default-deny policy.

---

## 5. Enable Firebase App Check (Recommended)

1. In Firebase Console, navigate to **Build > App Check**.
2. Register your Android app with the **Play Integrity** provider (or **Debug** provider for local development/emulator testing).
3. Nexus Agent v5 initializes App Check on launch via `configureAppCheck(this, intent)`.
