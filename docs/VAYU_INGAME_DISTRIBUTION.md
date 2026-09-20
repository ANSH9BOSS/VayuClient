# VayuClient — Official In-Game JAR Distribution & Update System

## 1. Overview & Architecture

The Vayu Client launcher automatically keeps the Vayu in-game client JAR up to date directly from official GitHub Releases.

```
Official GitHub Releases (ANSH9BOSS/VayuClient)
         │
         ├── vayu_hud_manifest.json (Authoritative artifact directory & SHA-256 digests)
         ├── vayuclient-hud-{ver}-mc{mc}-universal.jar (Per-version universal jars)
         ├── VayuClient.jar (Unified production alias)
         └── SHA256SUMS.txt
         │
         ▼
VayuClient Launcher
         │
         ├── Startup: CheckForUpdateAsync() (Background, non-blocking)
         ├── Pre-Launch: EnsureLatestAsync() (Guarantees verified JAR before Minecraft starts)
         │
         ▼
Verification & Installation
         ├── Streaming download to temporary file (*.download)
         ├── Cryptographic SHA-256 digest comparison
         ├── Atomic installation with automatic rollback protection
         └── Persistent metadata record (vayu_jar_installation.json)
```

---

## 2. Centralized Configuration

All repository coordinates, asset naming conventions, endpoints, and channels are isolated in a single source of truth:
* **Class**: [`VayuInGameDistributionConfig`](file:///c:/Users/ANSH/.gemini/antigravity-ide/scratch/VayuClient/VayuClient/Services/Updates/InGame/VayuInGameDistributionConfig.cs)
* **GitHub Owner**: `ANSH9BOSS`
* **GitHub Repository**: `VayuClient`
* **Slug**: `ANSH9BOSS/VayuClient`
* **Manifest Asset Name**: `vayu_hud_manifest.json`
* **JAR Prefix**: `vayuclient-hud`
* **Unified Standalone Name**: `VayuClient.jar`
* **Channel**: `stable` (prereleases ignored unless explicitly enabled)

---

## 3. How to Build the In-Game Client JAR

From the repository root, run the official build script:

```bash
python vayuclient-hud-mod/build_vayuclient_hud.py
```

This will:
1. Compile the in-game client mod using Java 21.
2. Package universal multi-loader JARs for all supported Minecraft version families (1.21.x and 26.x).
3. Generate the authoritative `vayu_hud_manifest.json` containing SHA-256 checksums, version ranges, entrypoints, and supported loaders.
4. Output all artifacts to `vayuclient-hud-mod/dist/`.

To run the automated verification suite:

```bash
python test_universal_hud.py
```

---

## 4. How to Publish an Official Release

### Option A: Via Git Tag (Automated CI/CD)
Push a semantic version tag to the repository:

```bash
git tag v3.3.3
git push origin v3.3.3
```

The GitHub Actions workflow [`.github/workflows/vayu-client-release.yml`](file:///c:/Users/ANSH/.gemini/antigravity-ide/scratch/VayuClient/.github/workflows/vayu-client-release.yml) will automatically:
1. Build the production in-game JARs.
2. Run test assertions.
3. Compute SHA-256 digests into `SHA256SUMS.txt`.
4. Create the unified alias `VayuClient.jar`.
5. Publish a GitHub Release and upload all production assets.

### Option B: Via Local Release Script
Run the automated release script:

```bash
python publish_github_release.py v3.3.3
```

---

## 5. Expected GitHub Release Assets

The launcher expects the following assets in each published release:
| Asset Name | Purpose |
|------------|---------|
| `vayu_hud_manifest.json` | Master manifest with compatibility matrix, SHA-256 digests, and bytecode metadata. |
| `vayuclient-hud-*.jar` | Universal multi-loader in-game JARs (e.g. `vayuclient-hud-2.1.0-mc1.21.11-universal.jar`). |
| `VayuClient.jar` | Unified fallback production JAR alias. |
| `SHA256SUMS.txt` | Standard plaintext SHA-256 checksum file. |
| `VayuClientSetup.exe` | Standalone Authenticode-signed Windows installer. |

---

## 6. Launcher Update Detection & Verification Flow

Every time the launcher starts:
1. **Startup Check**: `MainViewModel` triggers `IVayuClientJarUpdateService.CheckForUpdateAsync()` asynchronously in the background.
2. **Offline Resilience**: If GitHub is unreachable, the check reports offline and preserves the current working client without throwing or freezing the UI.
3. **Identity & Hash Check**:
   - Queries `https://api.github.com/repos/ANSH9BOSS/VayuClient/releases`.
   - Compares remote release tag and expected SHA-256 against local metadata.
   - If the remote hash matches the local hash, **zero network download occurs**.
4. **Streaming Download**:
   - Downloads ONLY the matching asset to a temporary path (`*.download`).
   - Progress is reported to the UI progress indicator.
5. **SHA-256 Cryptographic Verification**:
   - Calculates local SHA-256 using streaming SHA-256.
   - Compares with expected digest. Rejects corrupt or incomplete files immediately.
6. **Atomic Installation & Rollback**:
   - Creates a `.bak` backup of the existing JAR.
   - Atomically moves the verified file into place.
   - Deploys to instance `mods/` and global shared cache (`%APPDATA%\VayuClient\Assets\Mods`).
   - Updates persistent metadata.
   - If any step fails, the backup is restored immediately.

---

## 7. Local Metadata Storage

Installed JAR metadata is stored in atomic JSON files:
* **Global Metadata**: `%APPDATA%\VayuClient\vayu_jar_installation.json`
* **Instance Metadata**: `%APPDATA%\VayuClient\Instances\<InstanceName>\vayu_client_metadata.json`

Schema example:
```json
{
  "ReleaseTag": "v3.3.2",
  "AssetName": "vayuclient-hud-2.1.0-mc1.21.11-universal.jar",
  "Sha256": "a0145fecc36cd9cd9393a6248a5e96811ddea0ff43b596a52f9fe70ff6bebe2b",
  "InstalledAt": "2026-09-19T17:00:00Z",
  "SourceRepository": "ANSH9BOSS/VayuClient",
  "UpdateTimestamp": "2026-09-19T17:00:00Z",
  "MinecraftVersion": "1.21.11",
  "Loader": "Fabric",
  "HudProductVersion": "2.1.0",
  "FileSizeBytes": 7467844,
  "IsVerified": true
}
```

---

## 8. Rollback & Troubleshooting

### How to Roll Back a Release
If a bad in-game JAR was released:
1. Delete or unpublish the release on GitHub (or publish a new patch release e.g. `v3.3.4` with the previous stable JAR).
2. The launcher will automatically detect the new latest release on startup and deploy the verified stable JAR.
3. If an individual user encounters an issue, deleting `%APPDATA%\VayuClient\vayu_jar_installation.json` forces the launcher to re-verify and re-download the latest official release.
