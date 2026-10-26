# NOichhar official release

This release contains a signed production APK built by GitHub Actions from the source in this repository.

## What is included

- Local DNS firewall using Android `VpnService`; no root and no remote proxy.
- DNS over HTTPS, DNS over TLS, plain DNS, System DNS and custom endpoints.
- Built-in ad/tracker blocklist, remote blocklist updates, wildcard and parent-domain rules.
- User-defined block rules with delete support.
- DNS query logs, live search, clear and CSV export.
- App-level DNS rules for internet, Wi-Fi, mobile data and locked-screen behavior.
- Start on boot, dark mode, notification action, network-change cache clearing.
- Material 3 Android UI with bottom navigation, empty states and adaptive launcher icons.
- Unit tests for DNS packet parsing, caching, endpoint validation and block rules.

## Installation

1. Download the signed `NOichhar-v1.0.0.apk` asset.
2. Install it on Android 7.0 (API 24) or newer.
3. Open NOichhar and accept the Android VPN connection prompt.
4. Turn off Android **Private DNS** in system network settings if it is set to automatic/strict mode.
5. Tap **START**.

## Important platform notes

- Apps that hard-code their own DNS-over-HTTPS endpoint may bypass the device DNS server.
- Android Private DNS can bypass the local DNS server; the DNS screen warns when it is enabled.
- App rules block DNS resolution, which stops normal domain-based traffic. Connections made directly to a known IP address are outside the scope of this DNS-only VPN.
- The VPN routes only NOichhar's virtual DNS address; normal app traffic is not proxied through a remote server.

The release APK is signed with the official repository signing key generated and cached by GitHub Actions. The private key is never committed to Git.
