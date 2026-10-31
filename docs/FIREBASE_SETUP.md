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
5. In your local terminal, retrieve your debug signing key certificate fingerprints:
   ```bash
   keytool -list -v -keystore debug.keystore -alias androiddebugkey -storepass android -keypass android
   ```
6. Add the resulting fingerprints to **SHA certificate fingerprints** in the Firebase Console:
   - **Debug SHA-1**: `2F:B6:CA:1E:E2:A5:A7:A3:68:52:C3:B0:83:32:CF:78:AB:4B:F0:93`
   - **Debug SHA-256**: `42:8D:8B:CD:99:8A:4A:EA:76:FE:87:DC:59:3D:9B:9A:81:43:EE:4F:DD:C9:21:D6:D9:56:5F:4B:04:C9:B8:83`
7. If building a release APK, also add the release keystore's SHA-1 and SHA-256.

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
