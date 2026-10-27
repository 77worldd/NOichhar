# NOichhar v2.1.0 — Obsidian Minimalism

A raven-branded, dark-first redesign with real request statistics and a larger, safer blocklist engine. The package remains `com.noichhar.app`; no root is required.

## What's new

- Breathing raven shield, glass-style cards, device-wide throughput waveform, real 24-hour activity heatmap and a floating five-tab navigation bar.
- A Stats screen with 1-hour, 24-hour and seven-day filtered-request rankings and a radial chart, plus optional per-UID network-usage totals from Android.
- RPN controls for DNS filtering and app rules, custom rules and allowlists, list updates, app management and logs. Settings and DNS options persist and apply to the running VPN.
- Version-2 Room database migration preserves existing data. DNS events are aggregated without domain or IP details for the heatmap and counters; detailed logging can be disabled separately.
- A Bloom-filter-assisted, atomically updated rule index and bounded downloads supporting up to 500,000 block rules. If all downloads fail, the previous list is retained.
- Adaptive and legacy launcher icons made from the supplied raven emblem.

## Installation and usage

Install the signed `NOichhar-v2.1.0.apk` over a compatible v2 release, open the app and accept the VPN prompt. Grant **Usage Access** from the Stats or Configure screen for optional per-app bandwidth. Android may delay or deny some mobile usage data; the app labels unavailable data rather than inventing values.

## Important limitations

Only traffic sent to the VPN DNS address and selected shield routes enters the TUN interface. App rules cover DNS and compatible system-proxy requests, not every direct connection. Hard-coded or encrypted DNS, some custom networking stacks, QUIC and IPv6 can bypass filtering. Throughput and bandwidth figures are device-wide Android estimates, not a count of active VPN connections. Detailed logs can be disabled; anonymous, per-UID aggregate statistics remain on-device for up to seven days.

The release workflow verifies signing-certificate continuity with v2.0.0 and publishes a SHA-256 checksum. Users upgrading from the differently signed v1.0.0 must uninstall that version first.
