# NOichhar v2.0.0 — Maximum Protection

This release is a major upgrade focused on blocking more ads inside Android apps while keeping the app light, clean and stable.

## What is new

### Stronger in-app ad blocking

- Four protection layers in **Maximum** mode: DNS filtering, DNS-over-TCP filtering, hostname-aware app filtering and route-level IP shielding.
- Local loopback HTTP proxy registered through Android's `VpnService.Builder.setHttpProxy()` on Android 10+.
- HTTPS `CONNECT` hostname and plain HTTP `Host` checks against the same blocklist, allowlist and per-app rules.
- DNS over TCP on port 53 is now answered locally with correctly framed TCP DNS responses.
- CNAME/alias inspection prevents blocked upstream aliases from bypassing a filter through an allowed name.
- Curated network shield resolves high-value ad/tracker hosts and blocks safe, non-shared IPv4 routes.
- Optional blocking of well-known hard-coded encrypted-DNS resolver hostnames.

### Better filter lists

- Thousands of curated offline mobile ad and tracker domains.
- Automatic 12-hour updates from HaGeZi Multi PRO mini, StevenBlack Unified hosts and AdAway hosts.
- Robust hosts, compressed-hosts, plain-domain and Adblock Plus parsing, including exception rules.
- Essential first-party allowlist and user-defined allow rules for fixing false positives.
- User block and allow rules always override downloaded rules.
- Chunked, bounded database writes and a suffix-set matcher to keep lookups fast.

### Performance and reliability

- No full-traffic userspace relay and no HTTPS decryption.
- Normal app traffic stays on the normal network path; only DNS and shielded IPs enter the TUN interface.
- TTL-aware DNS cache respects upstream record lifetimes.
- Background filter updates run on unmetered networks through WorkManager.
- A bounded proxy connection pool and per-flow streaming relay avoid buffering whole downloads.

### Cleaner, more accessible UI

- New deep-teal and slate Material 3 palette with coordinated light and dark themes.
- One-tap **Balanced / Maximum** protection selector on Home.
- Refreshed hero card, soft stat cards, status pill, protection explanation and update button.
- New Protection settings for maximum mode, DNS-bypass blocking, automatic updates and manual refresh.
- Allowlist screen with subdomain support.
- Larger touch targets, labelled controls, readable contrast, scalable text and smooth layout transitions.
- Version 2.0.0 with adaptive launcher icons and signed release verification.

## Installation

1. Download the signed `NOichhar-v2.0.0.apk` asset.
2. Install it on Android 7.0 (API 24) or newer.
3. Open NOichhar and accept the Android VPN connection prompt.
4. Turn off Android **Private DNS** in system network settings if it is set to automatic/strict mode.
5. Choose **Maximum** protection and tap **START**.

The release workflow checks that the signing certificate matches the official v1.0.0 release. If Android still reports a signature conflict on a modified system ROM, uninstall the old build and install this APK again.

## Important platform notes

- Hostname-aware app filtering requires Android 10+ and only applies to apps that honour Android's system HTTP-proxy recommendation.
- Apps with custom networking stacks, hard-coded DNS-over-HTTPS, QUIC/HTTP-3 or raw sockets can bypass parts of the filter.
- DNS and hostname filtering cannot remove ad placeholders or block ads served from the same hostname as content.
- NOichhar does not decrypt HTTPS or install a CA certificate.
- Direct connections to unknown IP addresses and IPv6-only traffic remain outside this DNS and IPv4 route-shield architecture.
- The local VPN occupies Android's single active VPN slot.

The release APK is signed with the official repository signing key generated and cached by GitHub Actions. The private key is never committed to Git.
