# NOichhar

NOichhar is a clean, local DNS firewall and ad blocker for Android. It uses Android's `VpnService` to route only DNS queries through a local virtual DNS server, applies block and app rules, and leaves ordinary internet traffic on the device's normal network path. No root, account, remote proxy or tracking server is required.

## Highlights

- **DNS filtering:** exact domains, parent domains, wildcards, user rules and updatable hosts lists.
- **Secure upstreams:** DNS over HTTPS, DNS over TLS, plain DNS, built-in provider presets, System DNS and validated custom endpoints.
- **Fast by design:** local response cache and DNS-only TUN routing; non-DNS traffic is not sent through an external proxy.
- **App DNS rules:** per-app internet, Wi-Fi, mobile-data and locked-screen/background DNS decisions.
- **Logs you control:** searchable DNS decisions, clear logs and CSV export through Android's document picker.
- **Everyday settings:** start on boot, dark/system theme, configurable logging, notification stop action.
- **Modern Android UI:** Material 3, bottom navigation, adaptive icon, day/night colors, empty states and content descriptions.
- **Verified build:** JVM unit tests and Android CI build on every push, plus a signed official release workflow.

## Requirements

- Android 7.0 (API 24) or newer.
- Android Studio with JDK 17 for local development.
- Android SDK Platform 34 and Build Tools 34.0.0.
- No root access.

## Build

```bash
./gradlew testDebugUnitTest assembleDebug assembleRelease
```

Outputs:

```text
app/build/outputs/apk/debug/app-debug.apk
app/build/outputs/apk/release/app-release-unsigned.apk
```

The official GitHub Release APK is aligned, signed and verified by `.github/workflows/release.yml`. The signing key is generated once inside GitHub Actions cache and is never committed to the repository.

## First run

1. Install the signed APK from the [releases page](https://github.com/77worldd/NOichhar/releases).
2. Open the app and accept Android's VPN connection request.
3. In the **DNS** tab, choose a provider and protocol.
4. If Android **Private DNS** is enabled, use the button on the DNS screen to open network settings and turn it off.
5. Return to **Home** and tap **START**.

## How it works

NOichhar creates a VPN interface with address `10.111.222.1/32` and DNS server `10.111.222.3/32`. Only that virtual DNS address is routed into the TUN interface. DNS packets are parsed locally, checked against firewall and blocklist rules, resolved through the configured upstream, cached and written back to the app. All other network traffic continues over the normal Wi-Fi or mobile network.

The app identifies the UID of each DNS socket on Android 10+ with `ConnectivityManager.getConnectionOwnerUid`, with a `/proc/net/udp` fallback for older Android versions. App and locked-screen rules therefore operate on real DNS requests rather than visual placeholders.

## Honest limitations

- Android `VpnService` cannot route by port. NOichhar avoids capturing all traffic by routing only a virtual DNS IP; this is why it does not slow down normal TCP/UDP connections.
- Apps using their own hard-coded DNS-over-HTTPS resolver, or Android Private DNS, may bypass the device DNS server.
- App rules block DNS resolution, which prevents normal domain-based traffic. Direct IP connections are not intercepted by this DNS-only architecture.
- DNS over TCP on port 53 is not forwarded; normal UDP DNS and encrypted DNS upstreams are supported.

## Project layout

```text
app/src/main/java/com/noichhar/app/
├── blocker/     blocklist loading, parsing and matching
├── database/    Room entities, DAOs and database
├── dns/         DNS wire parser, cache and upstream resolvers
├── net/         IP/UDP packet handling and UID resolution
├── receiver/    boot and package-change receivers
├── service/     VPN, firewall policy and connection logging
├── ui/          activities, fragments, adapters, dialogs and view models
└── util/        preferences, constants, notifications and validators

app/src/main/res/ layouts, Material 3 themes, icons and raw blocklist
app/src/test/     JVM unit tests
.github/workflows CI and official signed release
docs/             original complete implementation specification
```

## Privacy

DNS filtering and logs stay on the device. NOichhar does not include analytics, advertising SDKs, accounts, or a remote proxy. Blocklist updates download public hosts files directly from the configured source.

## Verification

```bash
./gradlew testDebugUnitTest
```

The CI workflow also validates all XML resources before building. The release workflow runs the same tests, builds a minified release APK, aligns and signs it with `apksigner`, verifies the signature and publishes the APK with a SHA-256 checksum.
