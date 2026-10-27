# NOichhar

NOichhar is a clean, local DNS and app-traffic filter for Android. It uses Android's `VpnService` to filter DNS requests locally and, in Maximum mode, adds hostname-aware filtering for compatible apps plus route-level protection against known ad-server IPs. It does **not** use a remote proxy, decrypt HTTPS, require root, require an account, or install a user CA certificate.

Version **2.0.1** is a stability patch on top of the Maximum Protection upgrade: stronger in-app ad blocking, automatic fresh filter lists, a calmer Material 3 interface, and accessibility.

## Highlights

- **Layered filtering**
  - Local DNS filtering with exact, parent-domain, wildcard and user rules.
  - DNS over UDP **and DNS over TCP on port 53** answered by the local resolver.
  - Hostname-aware HTTP/HTTPS filtering for apps that honour Android's VPN HTTP-proxy recommendation on Android 10+.
  - Route-level IP shield for high-value ad, tracker and encrypted-DNS-bypass servers.
  - CNAME/alias inspection so a blocked upstream alias cannot leak through an allowed name.
- **Fresh, real filter lists**
  - Curated offline list with thousands of common mobile ad and tracker domains.
  - Automatic 12-hour updates from HaGeZi Multi PRO mini, StevenBlack Unified hosts and AdAway hosts.
  - Robust hosts, plain-domain, compressed-hosts and Adblock Plus rule parsing.
  - Exception rules, essential-service allowlist and user-defined allow rules.
  - User rules always override downloaded rules.
- **Secure upstreams**
  - DNS over HTTPS, DNS over TLS, plain DNS, System DNS and validated custom endpoints.
  - Transaction-ID-safe DNS caching that respects upstream TTLs.
  - Optional blocking of well-known hard-coded encrypted-DNS resolvers.
- **App controls**
  - Per-app internet, Wi-Fi, mobile-data and locked-screen DNS decisions.
  - Boot start, package-change refresh, lockdown mode, network monitoring and notification stop action.
- **Logs and privacy**
  - Searchable DNS and blocked-connection decisions, clear logs and CSV export.
  - Logs stay on the device. No analytics, advertising SDKs, accounts or remote proxy.
- **Clean Android UI**
  - Cohesive deep-teal and slate Material 3 palette with light/dark themes.
  - One-tap Balanced/Maximum protection selector.
  - Large touch targets, labelled controls, readable contrast and Android font-scaling support.
  - Smooth layout transitions, tinted status cards and adaptive launcher icons.
- **Verified build**
  - JVM unit tests, XML validation and Android CI builds on every push.
  - Signed official releases with SHA-256 checksums and signing-key continuity checks.

## Requirements

- Android 7.0 (API 24) or newer.
- Android Studio with JDK 17 for local development.
- Android SDK Platform 34 and Build Tools 34.0.0.
- No root access.
- Hostname-aware app filtering requires Android 10 (API 29) or newer.

## Build

```bash
./gradlew testDebugUnitTest assembleDebug assembleRelease
```

Outputs:

```text
app/build/outputs/apk/debug/app-debug.apk
app/build/outputs/apk/release/app-release-unsigned.apk
```

Official release APKs are aligned, signed, checked against the v1 signing certificate, and published by `.github/workflows/ci.yml`. `.github/workflows/release.yml` provides the tag/manual release path. The signing key is generated once inside GitHub Actions cache and is never committed to the repository.

## First run

1. Install the signed APK from the [releases page](https://github.com/77worldd/NOichhar/releases).
2. Open the app and accept Android's VPN connection request.
3. On the **Home** screen, choose **Balanced** or **Maximum** protection. Maximum is selected by default.
4. In the **DNS** tab, choose a provider and protocol.
5. If Android **Private DNS** is enabled, use the button on the DNS screen to open network settings and turn it off.
6. Return to **Home** and tap **START**.

## How it works

NOichhar creates a VPN interface with address `10.111.222.1/32` and DNS server `10.111.222.3/32`. Only that virtual DNS address, plus a bounded set of shielded ad-server IPs, is routed into the TUN interface. DNS packets are parsed locally, checked against firewall, blocklist and allowlist rules, resolved through the configured upstream, cached and written back to the requesting app.

Maximum mode also starts a loopback HTTP proxy and registers it with Android's `VpnService.Builder.setHttpProxy()` on Android 10+. For apps that honour the system proxy, HTTPS `CONNECT` hostnames and plain HTTP `Host` headers are checked against the same rules before a protected upstream socket is opened. This catches many in-app ad requests without decrypting TLS.

The app identifies the UID of DNS sockets on Android 10+ with `ConnectivityManager.getConnectionOwnerUid`, with a `/proc/net` fallback for older Android versions. App and locked-screen rules therefore operate on real requests rather than visual placeholders.

The network shield resolves a bounded list of high-value ad/tracker domains, subtracts IPs used by essential first-party services and public DNS resolvers, and installs only safe IPv4 `/32` routes. TCP packets to those addresses are rejected with a RST; UDP packets are dropped. Normal traffic remains on the device's normal network path, so NOichhar does not relay every packet through userspace.

## Honest limitations

- `setHttpProxy()` is a recommendation, not a guarantee. Apps with custom networking stacks, hard-coded DNS-over-HTTPS, QUIC/HTTP-3 or raw sockets can ignore it.
- DNS-only and route-based filtering cannot remove ad placeholders or block ads served from the same hostname as content. NOichhar does not perform cosmetic filtering or HTTPS interception.
- Direct connections to unknown IP addresses are outside the DNS and IP-shield model.
- Android Private DNS can bypass the local DNS server; the DNS screen warns when it is enabled.
- IPv6 DNS and direct IPv6 connections are not intercepted. On IPv6-only networks, filtering may be incomplete.
- A local VPN still occupies Android's single active VPN slot, so it cannot normally run alongside another VPN app.
- Blocking hard-coded encrypted DNS is optional because some legitimate apps rely on their own resolver. If an app stops working, add its domain to **Firewall → Allowed domains** or disable **Block encrypted-DNS bypass**.

## Project layout

```text
app/src/main/java/com/noichhar/app/
├── blocker/     filter-list download, parsing, matching and updates
├── database/    Room entities, DAOs and database
├── dns/         DNS wire parser, TTL-aware cache and upstream resolvers
├── net/         IPv4/TCP/UDP packet handling, local proxy and network shield
├── receiver/    boot and package-change receivers
├── service/     VPN, firewall policy and connection logging
├── ui/          activities, fragments, adapters, dialogs and view models
└── util/        preferences, constants, notifications and validators

app/src/main/res/raw/ curated offline filters, allowlist, DNS-bypass and shield domains
app/src/main/res/     layouts, Material 3 themes, icons and accessibility resources
app/src/test/         JVM unit tests
.github/workflows     CI and official signed release
docs/                 original complete implementation specification
```

## Filter-list attribution

The offline list and automatic updates use publicly maintained community sources, including:

- [HaGeZi DNS Blocklists](https://github.com/hagezi/dns-blocklists) — GPL-3.0.
- [StevenBlack/hosts](https://github.com/StevenBlack/hosts) — MIT.
- [AdAway default hosts](https://github.com/AdAway/adaway.github.io) — CC BY 3.0.

The shipped subset is stored in `app/src/main/res/raw/default_blocklist.txt` with attribution. Full lists are downloaded by the app on a periodic, unmetered-network schedule.

## Privacy

DNS filtering, hostname checks and logs happen on the device. NOichhar does not include analytics, advertising SDKs, accounts, or a remote proxy. Blocklist updates download public filter files directly from the configured sources. The local HTTP proxy listens only on `127.0.0.1`.

## Verification

```bash
./gradlew testDebugUnitTest
```

The CI workflow also validates all XML resources before building. Both official release paths run the same tests, build a minified release APK, align and sign it with `apksigner`, verify the signature and signing-key continuity, and publish the APK with a SHA-256 checksum.
