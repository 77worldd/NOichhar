# NOichhar

NOichhar is a privacy-focused Android DNS and app-request filter. It uses Android's local `VpnService`; no root, account, remote proxy, HTTPS decryption or user CA is needed. The application ID remains `com.noichhar.app`.

## Obsidian Minimalism (v2.1.0)

- A dark, raven-branded interface with a breathing shield, live device throughput, a 24-hour activity heatmap, a floating five-tab navigation bar and an adaptive launcher icon.
- **Home:** seven-day allowed/blocked filtered-request totals and a heatmap built from recorded events—not simulated data.
- **Stats:** 1-hour, 24-hour and 7-day windows, per-UID request rankings, a radial chart and optional Android network-usage totals. The cache button clears the VPN's DNS cache; it does not claim to close unrelated connections.
- **RPN:** update the six configured lists, add domain rules, manage block/allow and app rules, or choose DNS filtering, app rules, or both. App rules apply to requests the app can actually observe.
- **Configure:** persistent preferences, provider choices, advanced DNS settings and Usage Access. **About** reads the installed version.
- A version-2 Room database with a non-destructive migration. Existing app rules, lists and logs are preserved, and existing DNS events are copied into anonymous aggregate statistics.
- A bounded 500,000-domain update pipeline, Bloom-filter-assisted immutable lookup snapshots, DNS/Adblock/hosts parsing, user exceptions and an offline starter list. If every download fails, the existing list is retained.

## Requirements and build

Android 7.0 (API 24) or newer. Hostname-aware proxy filtering requires Android 10+. Build with JDK 17, Android SDK Platform 34 and Build Tools 34.0.0:

```bash
./gradlew clean
./gradlew testDebugUnitTest assembleDebug assembleRelease
```

The local release output is `app/build/outputs/apk/release/app-release-unsigned.apk`. Official releases are produced by `.github/workflows/release.yml`, which builds, tests, aligns, signs and verifies the APK, checks its certificate against v2.0.0, and publishes a SHA-256 checksum. The workflow refuses to create a replacement signing key if the official key is unavailable.

## First run

1. Install the signed APK from [Releases](https://github.com/77worldd/NOichhar/releases), open it, and tap **Activate**. Accept the Android VPN prompt.
2. Choose a DNS provider in **Configure**, or open **Advanced DNS settings** for DoH, DoT, plain, system and custom endpoints. Disable Android Private DNS if it bypasses the local resolver.
3. Use **RPN** for protection mode, blocklists and custom rules. An initial bundled list works offline; full lists are downloaded from their publishers when updates succeed.
4. To see per-app bandwidth, open **Stats → Grant Usage Access** and allow NOichhar in Android's special-access settings. Protection must be active while samples are collected.

## What the statistics mean

- The live upload/download rate is **device-wide** `TrafficStats`, not VPN-only bandwidth.
- App bandwidth comes from Android `NetworkStatsManager` when Usage Access is granted. It is sampled while protection is active and retained on the device for seven days; background cleanup is subject to Android scheduling and may be delayed if the app is force-stopped. Android can report coarse or delayed buckets; mobile data may be unavailable due to platform/device permissions. On Android 7, Wi-Fi/mobile transport is used as an estimate of metered status. Shared UIDs are reported together.
- Request counts come from actual DNS, supported HTTP proxy and shield events. They are **filtered requests**, not live socket or active-connection counts. The heatmap uses 120 twelve-minute buckets from those events.
- Detailed request logging can be disabled in Configure; aggregate counts without domains or IP addresses are still kept for the statistics and heatmap. Detailed logs and aggregates stay on-device.

## How protection works and its limits

The VPN routes its virtual DNS address and a bounded set of protected IPv4 addresses into the local TUN interface. System DNS traffic sent to the VPN's virtual resolver is handled over UDP and TCP port 53; it is checked against the active rule snapshot, resolved through the selected upstream, cached, and answered locally. Most other traffic remains on the device's normal network path, so the app does not relay every packet through userspace.

The optional advanced layer registers a local HTTP proxy for apps that honour Android's proxy recommendation, and can block selected safe-to-route IPv4 addresses. Apps using hard-coded DNS servers, encrypted DNS, QUIC, custom sockets, direct IPs, or IPv6 can bypass some or all filtering. Android Private DNS may bypass the local DNS server. The app does not filter HTTPS content or remove cosmetic ad spaces; domains that serve both content and ads cannot be separated by DNS alone. Android permits only one active VPN at a time. These limitations also mean NOichhar cannot truthfully close or count all device connections.

## Privacy and sources

Rules are evaluated locally; the chosen DNS resolver receives upstream queries and public blocklist publishers receive update requests. The optional loopback proxy listens only on the device. There is no analytics SDK or remote proxy. The bundled Inter font is distributed under the SIL Open Font License in `app/src/main/assets/brand/INTER_OFL.txt`.

Automatic updates are configured for [HaGeZi](https://github.com/hagezi/dns-blocklists), [StevenBlack hosts](https://github.com/StevenBlack/hosts), [AdGuard DNS filter](https://github.com/AdguardTeam/AdGuardSDNSFilter), [OISD](https://oisd.nl/), [1Hosts](https://github.com/badmojr/1Hosts), and [Peter Lowe's list](https://pgl.yoyo.org/adservers/). The built-in list's attributions are included in `app/src/main/res/raw/default_blocklist.txt`. Updates are bounded, run in the background, and preserve user-added rules.

## Verification

CI validates XML, runs JVM tests including 500,000-domain and atomic-reload coverage, and performs clean debug and release builds. Android instrumentation tests on API 24 and 29 cover database migration, aggregate queries, mode/settings persistence, five-tab navigation and real local VPN start/stop. No test can establish universal filtering across all Android devices or traffic that bypasses the operating system's VPN DNS/proxy configuration.
