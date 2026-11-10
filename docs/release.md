# ManyManager release guide

This guide targets the personal edition on `main`. For the first Google Play preparation, use the signed AAB and [short upload guide](play-upload.md) below. For separately authorized GitHub APK releases, use `v<VERSION>` and `ManyManager-<VERSION>.apk` for this branch. The tag naming follows the confirmed 0.3.0 release convention; preserve existing historical tags.

Run commands from the repository root, using a clean, reviewed commit and a compatible JDK. The current checked-in ManyManager configuration is `com.example.manymanager`, versionName `0.3.0`, versionCode `4` (the last distributed GitHub APK used code `3`). Recheck [androidApp/build.gradle.kts](../androidApp/build.gradle.kts) before each release; this is not a claim that an APK was published.

## First Google Play bundle preparation — 10 November 2026

This is a local signed-bundle handoff, not a Play submission or production-eligibility approval. Nothing was uploaded or published and no tag/release was created for this preparation. [Hebrew Console handoff](play-upload.md).

### Baseline and version choice

GitHub releases and remote tags were inspected read-only. Personal-edition APKs `0.1.0`, `0.2.0`, `v0.3.0` use `com.example.manymanager` with versionCodes 1, 2 and 3 respectively. Simple-edition tags/assets use a separate identity and are excluded. The latest personal release points to build commit `021f00650c573af6e46f6ed00554804fb1cd04e2`.

The local `private-release/ManyManager-0.3.0.apk` SHA-256 is `329af9339d2c2b95e860fcfcf139ded33c0447106c5951bbb7d4313e64e62ede`, matching the GitHub asset digest. Its verified release certificate matches the existing secure keystore:

```text
CN=ManyManager Private Release
RSA 4096; certificate valid 2 November 2026–20 March 2054
SHA-256 E4:AE:E9:D1:75:AC:C7:1C:7A:A6:FA:5D:96:02:3E:CB:2D:93:FD:03:B3:23:42:0A:37:9E:A7:7C:A8:DA:55:FC
```

The preparation increments only versionCode 3→4; versionName remains `0.3.0`. Code 4 exceeds all known distributed personal-edition builds, **not necessarily all past Play uploads**. No Play account/version history is available locally. Before upload, inspect all Play tracks, drafts and bundles; if code 4 has already been used or is not high enough, increment it and rebuild. Exact package acceptance by Play is also pending. Preserve `com.example.manymanager`; capture the exact Console rejection if any, rather than choosing a replacement package.

The bundle includes the independent budget model and Room schema 2 migration. The [device verification record](budget-model.md#follow-up-verification-on-a-physical-device) documents 76 passing tests on SM-S928B / API 37, including populated SQLite migration, historical amounts and period handling. Build/version/documentation changes do not affect that implementation; the documented device run is reused. No existing device installation is changed.

### Build command and artifact

```powershell
powershell.exe -NoProfile -ExecutionPolicy Bypass -File scripts/release.ps1 -Artifact Bundle -Verify
```

The helper reuses the existing external PKCS12/DPAPI credentials. `-Verify` first runs `:androidApp:testDebugUnitTest --rerun :androidApp:lintRelease`; then Bundle selects `:androidApp:bundleRelease`. The helper signs the resulting AAB again with the same key using JDK `jarsigner` to put manifest/signature entries first for streaming JAR verification; application payloads are preserved. Each invocation uses `--no-configuration-cache --console=plain`. The default `-Artifact Apk` preserves the existing APK workflow. This project does not expose a `testReleaseUnitTest` task; no new test variant or dependency was added.

- Gradle output: `androidApp/build/outputs/bundle/release/androidApp-release.aab`.
- Verified delivery copy: `private-release/ManyManager-0.3.0-vc4-play.aab`.
- Expected metadata: package/namespace `com.example.manymanager`, versionCode `4`, versionName `0.3.0`, min SDK `30`, target/compile SDK `37`. App label/icon, behavior, database and signing credentials are unchanged.
- Fresh verification: **37 JVM tests passed, zero failures/errors/skips**; `lintRelease` passed with **0 errors and 88 warnings** (resource/UI, existing launcher-icon, API/dependency suggestions; no unrelated fixes or icon changes). The signed release bundle build passed. See local `androidApp/build/test-results/testDebugUnitTest/` and `androidApp/build/reports/lint-results-release.html`.
- Official Google `bundletool-all-1.18.3.jar` was downloaded read-only; SHA-256 `a099cfa1543f55593bc2ed16a70a7c67fe54b1747bb7301f37fdfd6d91028e29` matches its GitHub release asset digest. `bundletool validate`, manifest dump and config dump passed.
- Manifest inspection confirmed the metadata above, no debuggable/testOnly flag, backup retained and existing application/icon/theme references.
- `jarsigner -verify -strict` with the existing keystore succeeded; `keytool -printcert -jarfile` confirmed the historical certificate. An entry-by-entry Java verification checked **all 515 payload entries**, their expected signer fingerprint, and successful verification through both `JarFile` and `JarInputStream`. SHA-256 comparison before/after signature-entry normalization confirmed all 515 payloads unchanged. The original streaming-manifest warning was resolved. JDK still notes that POSIX ZIP attributes are not signed; file payloads are verified.
- Bundle config requests `PAGE_ALIGNMENT_16K`. Static ELF checks passed for both 64-bit `libandroidx.graphics.path.so` libraries (arm64-v8a and x86_64): all LOAD segments aligned to at least 16384 bytes, offset/address congruence and RELRO boundary checks passed. Four native libraries are packaged overall. No runtime test on a 16 KB device or Play-generated APK has been performed for this preparation.
- Final build commit, delivery size and checksum will be recorded after the clean-commit rebuild. Rebuilding/signing can change archive bytes; recheck the actual delivered file.

If signing files are missing, restore the **existing** `manymanager-release.p12` and matching `release-credential.clixml` in `%USERPROFILE%\\.manymanager\\signing`, or supply their secure directory with `-SigningDirectory`. DPAPI credentials require the matching Windows user/machine; otherwise securely reprovision credentials for the same key. Do not run `-InitializeKey` to repair a missing update key. Never put passwords in command history, logs, documentation or Git. The command above finishes the bundle once the existing credentials are restored.

### Upload signature versus Play app signing

The AAB is signed locally with the existing release key, serving the **initial upload-key role**; no replacement or separate new key was generated. The intended **app-signing identity** for continuity with distributed GitHub APKs is the certificate above. Those are distinct roles even when bootstrapped from the same local key.

Play signs delivered APKs with its configured app-signing key, not merely whichever certificate signs this AAB. Before rollout, choose the existing-key export/import flow (Console-provided PEPK instructions) and verify App signing key certificates against the historical certificate, or a validated signing lineage for each supported Android generation. Do not accept a new Google-generated identity on the assumption that a matching upload certificate preserves GitHub upgrades. Current Console defaults can include hybrid/key-upgrade configurations; inspect all relevant certificates. A separate upload key can later be deliberately created and registered; none is silently provisioned here. This preparation does not export or upload the private app-signing key.

### Official requirements and remaining Console checks

Checked on 10 November 2026:

- [Target API policy](https://support.google.com/googleplay/android-developer/answer/11926878?hl=en): new phone/tablet submissions require API 36 or higher from 31 August 2026. Target 37 meets this numeric minimum; no SDK/build-tool/dependency upgrade is required for this preparation.
- [Bundle signing](https://developer.android.com/studio/publish/app-signing) and [Play App Signing](https://support.google.com/googleplay/android-developer/answer/9842756?hl=en): locally verify upload signature separately from Play's delivered APK identity. Final enrollment, certificates/lineage and update compatibility require Console processing.
- [16 KB compatibility](https://developer.android.com/guide/practices/page-sizes): inspect bundle ZIP alignment and bundled 64-bit ELF libraries. Static checks do not replace a 16 KB runtime test or Play's own processing/compatibility verdict.
- [Personal developer accounts](https://support.google.com/googleplay/android-developer/answer/14151465?hl=en) created after 13 November 2023 require a closed test with at least 12 continuously opted-in testers for 14 days, followed by a production-access application. Internal testing and local device tests are not substitutes. The app's “personal edition” name does not establish the developer account type or creation date.
- Before production, complete account verification and Console dashboard declarations, store listing/assets, privacy policy/Data safety, content rating, audience, ads, app access and applicable financial-feature declarations. Play policy review, package/code acceptance, device eligibility, processed bundle reports and account-specific production access remain pending. A locally valid signed AAB does not prove eligibility.

Create only an Internal testing **draft** for the manual acceptance check, following [the short guide](play-upload.md). Do not start a rollout. If any Console error occurs, record its exact text and resolve that specific issue without changing the package identity preemptively.

## Independent releases

| Branch | Application | Tag | APK | Release title |
| --- | --- | --- | --- | --- |
| `main` | ManyManager | `v<VERSION>` | `ManyManager-<VERSION>.apk` | `v<VERSION>` |
| `original` | ManyManager Simple | `simple-v<VERSION>` | `ManyManager-Simple-<VERSION>.apk` | `ManyManager Simple <VERSION>` |

Each application can advance and publish independently. Each release contains only its own APK and its tag points to the exact build commit. Main uses `v<VERSION>` tags and Simple uses `simple-v<VERSION>`; identify the application by this convention rather than GitHub's global “Latest”. Preserve historical tags/releases, including `0.1.0`.

The personal edition on main uses `com.example.manymanager`; Simple on original uses `com.example.simple.manymanager`. The packages can be installed side by side and keep separate app-private databases/preferences. Changing applicationId does not automatically transfer data. VersionCode can advance independently for each package. Normal updates require the same applicationId and signing identity and a versionCode higher than every previously distributed build for that package; update versionName accordingly.

## Build and verify

Use the existing release key:

```powershell
powershell.exe -NoProfile -ExecutionPolicy Bypass -File scripts/release.ps1
```

[release.ps1](../scripts/release.ps1) defaults to Android Studio JBR at `C:\Program Files\Android\Android Studio\jbr`; use `-JavaHome` for another supported JDK. It runs `:androidApp:assembleRelease --no-configuration-cache --console=plain`. Avoid debug logging/build scans containing credentials. Missing signing configuration fails explicitly rather than producing an unsigned/debug-signed substitute.

With JAVA_HOME set to a supported JDK, verify the output (adjust the installed build-tools path if necessary):

```powershell
& "$env:LOCALAPPDATA\Android\Sdk\build-tools\36.0.0\apksigner.bat" verify --verbose --print-certs androidApp/build/outputs/apk/release/androidApp-release.apk
& "$env:LOCALAPPDATA\Android\Sdk\build-tools\36.0.0\aapt.exe" dump badging androidApp/build/outputs/apk/release/androidApp-release.apk
& "$env:LOCALAPPDATA\Android\Sdk\build-tools\36.0.0\aapt.exe" dump xmltree androidApp/build/outputs/apk/release/androidApp-release.apk AndroidManifest.xml
Get-FileHash -Algorithm SHA256 androidApp/build/outputs/apk/release/androidApp-release.apk
git status --short
git rev-parse HEAD
```

Confirm package/version, non-debuggable/non-testOnly manifest, backup settings, valid signature and the same release certificate as a trusted previous APK/key. A debug-signed installation cannot be updated by a differently signed release; stop and inspect rather than uninstalling or clearing data.

Copy only the verified APK to the ignored `private-release/` directory using the table's filename (for example `ManyManager-0.2.0.apk`). Do not overwrite an existing delivery file without investigating. Hash the delivered copy too and record its checksum, certificate and build commit. Never include signing material with the APK. Tag/publish/upload only when authorized, using the exact build commit and naming above.

## Signing configuration and continuity

- Default signing directory: `%USERPROFILE%\.manymanager\signing`, outside the repository.
- Key file: `manymanager-release.p12`; alias: `manymanager-release`.
- Credential file: `release-credential.clixml`, a PSCredential protected by Windows DPAPI for the current account/computer.
- `-SigningDirectory` may select another secure directory outside the repository.
- Gradle process variables: `MANYMANAGER_RELEASE_STORE_FILE`, `MANYMANAGER_RELEASE_STORE_PASSWORD`, `MANYMANAGER_RELEASE_KEY_ALIAS`, `MANYMANAGER_RELEASE_KEY_PASSWORD`. Never commit their values. Use `--no-configuration-cache`; the script clears its process credentials on completion.
- `-InitializeKey` is initial provisioning only, requiring both signing files to be absent. Never generate a replacement key for an update.

Back up the p12 and password separately (encrypted backup and password manager), retaining restricted directory permissions. CLIXML alone is not a portable backup: DPAPI may not decrypt on another account/computer. Loss of key/password can prevent update continuity; backup restoration has not yet been tested.

For deliberate local password-manager backup, the script's `-CopyPassword` option copies the password to the clipboard without printing it. Paste only into a secure password manager, then run `Set-Clipboard -Value $null`. Never paste it into chat, logs or source.

## Related records

- [Current application and maintenance guidance](README.md), including schema preservation, backup eligibility and isolated instrumentation precautions.
- [Historical release preparation](archive/release-preparation.md): original 0.1.0 artifact/certificate records and 0.2.0 preparation; historical “not published” statements apply to their original dates.
- [Persistence acceptance](archive/persistent-data-acceptance.md) and [expense/details delivery](archive/expense-details.md): historical verification and remaining gaps.
