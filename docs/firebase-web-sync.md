# Firebase Auth and Web Sync

This app uses Firebase Authentication plus Firebase Realtime Database to sync Android chat history and to let a future GitHub Pages web UI queue work for the phone to execute.

## Firebase Console Setup

1. Authentication > Sign-in method:
   - Enable Email/Password.
   - Enable Google.
2. Project settings > Your apps:
   - Add SHA-1 fingerprints for all Android package names:
     - `com.charles.ollama.client`
     - `com.charles.ollama.client.github`
     - `com.charles.ollama.client.play`
   - Include debug, release, and Play App Signing SHA-1 fingerprints where applicable.
   - After enabling Google sign-in, download the updated `google-services.json` and replace `app/google-services.json`.
   - Copy the generated Web client ID into either local `local.properties` as `google.webClientId=...` or GitHub Actions secret `GOOGLE_WEB_CLIENT_ID`.
3. GitHub Actions:
   - Update the `GOOGLE_SERVICES_JSON` repository secret with the new file content.
   - Add or update `GOOGLE_WEB_CLIENT_ID` with the Web OAuth client ID used for Google sign-in.
4. Realtime Database:
   - Create the database in locked mode.
   - Deploy rules from this repo with `firebase deploy --only database`.
   - If the CLI reports that no Realtime Database instance exists, create it in the Firebase Console first, then rerun the deploy command.
5. Future GitHub Pages web app:
   - Add the GitHub Pages domain, and any custom domain, to Firebase Authentication authorized domains.
   - Do not enable Firebase Hosting for this project unless the hosting strategy changes.

## Android Behavior

The Settings screen contains Account & web sync controls. Users can create an account with email/password, sign in with email/password, reset password, sign in with Google, sign out, and enable Web sync.

Sync only runs when a Firebase user is signed in and Web sync is enabled. Local chats remain local when the user is signed out or the toggle is off.

The app adds stable `syncId` values to local Room rows. Local auto-increment IDs are never used as cloud IDs. Deletions for synced rows are tombstones so other clients can receive the delete.

## Realtime Database Paths

All app data is under the authenticated user:

```text
/users/{uid}/profile
/users/{uid}/settings
/users/{uid}/threads/{threadSyncId}
/users/{uid}/messages/{threadSyncId}/{messageSyncId}
/users/{uid}/webRequests/{requestId}
/users/{uid}/devices/{deviceId}
```

Rules in `database.rules.json` require `auth.uid == uid`.

## Thread Shape

```json
{
  "syncId": "uuid",
  "title": "Chat title",
  "model": "llama3.2",
  "serverId": 1,
  "streamEnabled": true,
  "systemPrompt": null,
  "vibrationEnabled": true,
  "showThinking": false,
  "isPinned": false,
  "isArchived": false,
  "label": null,
  "temperature": null,
  "topP": null,
  "topK": null,
  "numCtx": null,
  "seed": null,
  "createdAt": 1710000000000,
  "updatedAt": 1710000000000,
  "syncVersion": 1,
  "syncUpdatedAt": 1710000000000,
  "deleted": false
}
```

## Message Shape

```json
{
  "syncId": "uuid",
  "threadSyncId": "uuid",
  "role": "user",
  "content": "Hello",
  "thinking": null,
  "images": null,
  "evalCount": null,
  "evalDurationNs": null,
  "promptEvalCount": null,
  "totalDurationNs": null,
  "timestamp": 1710000000000,
  "syncVersion": 1,
  "syncUpdatedAt": 1710000000000,
  "deleted": false
}
```

## Web Request Shape

The GitHub Pages web UI should write pending requests that the phone will execute:

```json
{
  "threadSyncId": "uuid",
  "threadTitle": "Optional title",
  "model": "required unless the synced thread already has a model",
  "content": "User prompt from web",
  "images": null,
  "status": "pending",
  "createdAt": 1710000000000
}
```

Android claims the request and updates it:

```json
{
  "status": "running",
  "claimedByDeviceId": "android-device-id",
  "claimedAt": 1710000000000
}
```

After phone-side generation, Android writes the resulting chat messages and sets `status` to `complete`. On failure it sets `status` to `error` and writes an `error` string.

## Conflict Policy

The current Android implementation uses last-write-wins based on `syncUpdatedAt`. Tombstones win over older non-deleted records. Message `syncId` values are immutable; edits overwrite the same message key.

## Notes for the Web App

Use the Firebase web SDK directly from GitHub Pages. The Firebase `apiKey` is not secret, but the Realtime Database rules are mandatory because all authorization is enforced by Firebase Auth and rules.

For large image sync, consider moving images to Firebase Storage later and storing only references in Realtime Database. The Android app currently stores message images as base64 strings, which is compatible but can become expensive or hit payload limits.
