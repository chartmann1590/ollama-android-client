# Ollama Chat — Play Console inputs (Play flavor)

Package: **com.charles.ollama.client.play** · App name on Play: **Ollama AI Chat**
Privacy policy: https://chartmann1590.github.io/ollama-android-client/privacy.html
Account deletion URL: https://chartmann1590.github.io/ollama-android-client/delete-account.html
Ads declaration: **Yes, contains ads** (AdMob; UMP consent flow runs before ads initialize)

## Data Safety — answer set (built from the Play flavor's actual SDKs/behavior)

- **Collects data:** Yes · **Shares data:** Yes (Advertising ID → Google AdMob only)
- **All data encrypted in transit:** Yes — every developer-operated endpoint
  (Firebase, AdMob, Play Billing, the feedback relay) is HTTPS. Exception to
  note honestly in review notes if asked: chat traffic to the *user's own*
  Ollama server can be HTTP on their LAN (see Cleartext note below); that
  traffic goes to a server the user configures and controls, not to us.
- **Account creation:** Username and password (email) + OAuth (Google).
  An account is optional — the app is fully usable signed-out (local chats,
  LAN Ollama servers, on-device models). An account is only needed for the
  optional Web Sync feature and purchase restoration.
- **Account deletion:** In-app (Settings → Account & web sync → Delete
  account) and web URL above. Deletion wipes the Firebase user plus all
  synced Realtime Database data (threads, messages, devices, profile,
  web requests, subscription flags) and revokes premium entitlements.

| Data type | Collected | Shared | Purpose | Notes |
|---|---|---|---|---|
| Email address | Yes | No | Account management, App functionality | Firebase Auth; only if the user creates an account |
| User IDs | Yes | No | Account management, App functionality | Firebase UID; FCM registration token for sync triggers/notifications |
| Other user-generated content | Yes | No | App functionality | Chat threads/messages sync to Firebase Realtime Database **only** when the user enables Web Sync; stored under the user's own UID |
| Other user-generated content (reports) | Yes | No | App functionality, safety | AI-content reports: flagged message snippet (≤1,000 chars), optional reason, timestamp, package name → Firebase RTDB `/contentReports`, admin-only access |
| Other user-generated content (feedback) | Yes | No | App functionality | Voluntary bug reports (text, optional name/email, optional screenshot, diagnostics) sent via our Cloudflare relay to the project's GitHub issue tracker; the in-app form warns that submissions are public on GitHub |
| Photos | Yes | No | App functionality | User-selected images attached to chats (sent to the user's own Ollama server; synced only if Web Sync is on) and optional bug-report screenshots |
| App interactions | Yes | No | Analytics | Firebase Analytics |
| Crash logs | Yes | No | Analytics, App functionality | Firebase Crashlytics |
| Diagnostics | Yes | No | Analytics, App functionality | Firebase Performance Monitoring |
| Device or other IDs | Yes | **Yes** | Advertising (shared copy) | Advertising ID → AdMob; Firebase installation ID collected, not shared |
| Purchase history | Yes | No | App functionality | Play Billing purchase state for Premium (remove-ads / Web Sync plans); payment details are handled by Google Play, never received by the app |

Not collected: location, contacts, calendar, SMS/call logs, health data,
financial credentials. Voice input uses Android's system speech-to-text; the
app does not collect audio. ML Kit Translate and LiteRT-LM inference run
on-device.

## Self-update removal — evidence (Device and Network Abuse remediation)

The original package `com.charles.ollama.client` was suspended in Apr 2026
because its update prompt could open a GitHub release APK download. The Play
flavor is structurally incapable of this:

- All updater code (`UpdateChecker`, `UpdateInfo`, `UpdatePreferences`,
  `UpdateViewModel`, the real `UpdateAvailablePrompt`) lives **only** in
  `app/src/github/` (the separate `com.charles.ollama.client.github` build).
- The Play flavor's `UpdateAvailablePrompt` (`app/src/play/.../ui/update/`)
  is a no-op stub: `fun UpdateAvailablePrompt() = Unit`. `MainActivity`
  calls it unconditionally; on Play it renders nothing.
- CI enforces the separation: `android-ci.yml` step "Verify flavor
  separation and version metadata" fails the build if any updater file
  appears under `app/src/main/` or the Play stub goes missing.
- The updater-only BuildConfig fields (`GITHUB_OWNER`, `GITHUB_REPO`,
  `BUILD_COMMIT_EPOCH_SECONDS`) were moved out of `defaultConfig` into the
  github flavor block (Nov 23, 2026), and the dead
  `ENABLE_GITHUB_UPDATE_PROMPT` flag was removed from both flavors — the
  Play build's BuildConfig contains no reference to the updater.
- No `REQUEST_INSTALL_PACKAGES` permission, no `PackageInstaller` usage, no
  DownloadManager-to-APK, no APK install intent anywhere in the Play sources
  or merged manifest (grep-verified). The only FileProvider exposes
  `cache/exports/` for sharing exported chat Markdown — no APK path.
- The remaining GitHub-named code in the shared sources is the *feedback*
  client (`GitHubApiService` / `GitHubFeedbackRepository`): it talks to our
  Cloudflare Worker relay (`...workers.dev`), never to api.github.com, and
  only posts user-submitted bug reports. No token is embedded in the app
  (the worker holds it server-side).

## AI-generated content reporting

Play's AI-Generated Content policy requirement is met in-app: every assistant
message's overflow (⋮) menu offers **Report** (ChatScreen →
`ChatViewModel.reportMessage` → `ContentReportStorage`), which writes the
report to Firebase RTDB `/contentReports`. `database.rules.json` allows
create-only report writes and restricts reads to the admin. The privacy
policy's "AI-Generated Content" section documents this, and the listing's
feature list mentions per-message report.

## Account deletion — verification notes

- In-app: Settings → Delete account → `SettingsViewModel.deleteAccount`
  revokes premium entitlements, wipes synced data
  (`RealtimeChatSyncRepository.deleteAllUserData`), then deletes the
  Firebase user (`AuthRepository.deleteAccount`, with recent-login
  re-authentication for Google accounts).
- **Bug found and fixed Nov 23, 2026:** `database.rules.json` grants write
  access per node under `users/{uid}` but **not** on the `users/{uid}` node
  itself, so the previous single `removeValue()` on the user node was denied
  by the rules — the app swallowed the failure and the web flow errored out.
  Both the app and `website/chat.js` now delete node-by-node (messages →
  threads → devices → webRequests → profile/subscription/usage/models),
  which the existing rules permit. No rules change was needed.
- Web: https://chartmann1590.github.io/ollama-android-client/delete-account.html
  explains both paths and links to the Web Chat sign-in/delete flow.

## Cleartext (LAN Ollama servers)

`usesCleartextTraffic=true` + `network_security_config.xml` permit cleartext
globally. This is core functionality, not an oversight: the app's primary
purpose is connecting to Ollama servers that users run on their own LAN,
which serve plain HTTP by default, at arbitrary user-entered hostnames/IPs.
Android's network-security-config cannot express "private IP ranges only"
(domain entries are exact hosts), so the permission cannot be scoped more
narrowly without breaking user servers. All developer-operated endpoints
(Firebase, AdMob, Billing, feedback relay, Hugging Face model downloads)
are HTTPS regardless.

## AdMob

- Nov 10, 2026 AdMob restriction email for this app states the reason
  verbatim: **"Play App Disabled"** (warning email Nov 7, restriction
  Nov 10). It is a consequence of the app's Play status, **not** an SDK or
  policy demand — no AdMob action is required beyond the app returning to
  Play (plus a Policy Center review request if prompted).
- SDK: play-services-ads upgraded 22.6.0 → **23.6.0** (Nov 23, 2026), paired
  with UMP 2.2.0 (the portfolio-standard pairing; UMP consent already runs
  before ad initialization). Billing remains 9.0.0; targetSdk 36.
- Real ad unit IDs are injected at build time from GitHub secrets
  (`ADMOB_APP_ID`, `ADMOB_*_AD_UNIT_ID` env → `app/build.gradle.kts`
  `adProp()`, env wins over local.properties; the in-repo defaults are
  Google's public **test** IDs and are only used when no secret is set,
  e.g. forks/local dev). The publish workflow sets all of them from secrets.

## App name / brand (parent decision — not changed here)

- Play listing title: **"Ollama AI Chat"** (`listing/app_name.txt`, pushed by
  the publish workflow). The Play flavor's in-app name is "Ollama Chat".
- Google's Apr 2026 remedy for the suspended original was "new package name
  AND new app name." The package is new (`...client.play`); the name is a
  variant of the original ("Ollama Chat" → "Ollama AI Chat") and still
  leads with the third-party "Ollama" brand. This build does **not** rename
  anything. Recommendation for the parent's decision: a rename to a
  non-branded title (e.g. one that describes the app as an unofficial client
  "for Ollama") would more fully satisfy the remedy language and reduce
  impersonation/trademark risk; it is a listing-only change in Console.

## Known history for the reviewer packet

- Jun 29, 2026: the .play app's last submission was rejected under the
  **Broken Functionality** policy ("app doesn't open or load", vc 1094).
  Mitigation: `android-ci.yml` runs an emulator launch smoke test of the
  Play **release** APK (installs the AAB-derived universal APK, launches,
  fails on any FATAL EXCEPTION) on every PR/push.
- The .play listing in Console was Removed Nov 17, 2026 with the appeal note
  "Following an appeal, your app is no longer suspended. You still need to
  fix the issue, and resubmit your app." Console's target-API (API 35
  artifact) and Billing Library complaints refer to the old published
  artifact; this build targets API 36 with Billing 9.0.0 and supersedes it.
