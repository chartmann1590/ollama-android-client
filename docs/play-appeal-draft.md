## Google Play Appeal Draft (Device and Network Abuse)

Hello Google Play Policy Team,

Thank you for the review. We identified and removed the behavior that caused this issue.

### Root cause
- The previous Play-submitted artifact included an in-app update prompt that could open a GitHub release APK download link.
- We understand this can be interpreted as facilitating app updates outside Google Play.

### Remediation completed
- We split distribution into two product flavors:
  - `play` (Google Play distribution)
  - `github` (separate non-Play distribution channel)
- The Play flavor now has compile-time enforcement that disables the GitHub update prompt:
  - `ENABLE_GITHUB_UPDATE_PROMPT=false` for `play`
  - `ENABLE_GITHUB_UPDATE_PROMPT=true` for `github`
- The GitHub flavor uses a distinct package ID (`com.charles.ollama.client.github`) to separate channels.
- In the Play flavor, users can only receive app updates through Google Play's update mechanism.

### Code evidence
- Flavor split and compile-time flag:
  - `app/build.gradle.kts`
- Update prompt gating:
  - `app/src/main/java/com/charles/ollama/client/MainActivity.kt`

### Additional note
- The app does not request package install permissions and does not use installer intents for APK installation.

We respectfully request re-review of the updated Play-distributed artifact. We are committed to ongoing compliance with Google Play policies.
