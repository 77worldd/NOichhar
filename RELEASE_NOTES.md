# NOichhar v2.2.1 — Monochrome + Instant Activation

A black & white redesign with a faster, fully responsive protection toggle. The package remains `com.noichhar.app`; no root is required.

## What's new in v2.2.1

- **Instant activation.** Tapping ACTIVATE now flips the shield, badge and button immediately, shows a CONNECTING state while the tunnel is negotiated, and settles on PROTECTED the moment the service reports it — no more second tap to see the change.
- **One-tap start.** When VPN consent already exists the confirmation dialog is skipped entirely, so protection starts on a single tap.
- **Faster tunnel bring-up.** Local startup work (rules, counters, app inventory) now runs concurrently, and the tunnel no longer waits for shield DNS resolution before it can be established — the cached route set is used immediately and the cache is refreshed in the background.
- **Cancel while connecting.** The toggle reads CANCEL during the handshake, so a slow network never leaves you tapping a button that looks unresponsive.
- **Reliable UI state.** The home screen observes a process-wide connection state instead of relying only on a broadcast, so the status can never stay stale after a missed transition.

## Monochrome design (v2.2.0)

- Pure-black surfaces, `#141414` cards with subtle `#252525` borders, white primary text and grey secondary labels.
- White primary buttons with black text, outlined secondary actions, white switches and radio indicators.
- Greyscale charts: white/grey throughput waveform, six-step grey radial chart and a white-intensity activity heatmap.
- No purple, teal, green or red accents remain anywhere in layouts, drawables or custom views.

## Performance

- DNS connection pooling with keep-alive for DoH lookups.
- Concurrent 2,000-entry DNS cache with TTL respect.
- Reused packet buffers and O(1) rule lookups in the interception path.
- Parallel service start-up removes the serial wait before the tunnel handshake.

## Installation and usage

Install the signed `NOichhar-v2.2.1.apk` over a compatible v2 release, open the app and accept the VPN prompt once. Grant **Usage Access** from the Stats or Configure screen for optional per-app bandwidth. Android may delay or deny some mobile usage data; the app labels unavailable data rather than inventing values.

## Important limitations

Only traffic sent to the VPN DNS address and selected shield routes enters the TUN interface. App rules cover DNS and compatible system-proxy requests, not every direct connection. Hard-coded or encrypted DNS, some custom networking stacks, QUIC and IPv6 can bypass filtering. Throughput and bandwidth figures are device-wide Android estimates, not a count of active VPN connections. Detailed logs can be disabled; anonymous, per-UID aggregate statistics have a seven-day on-device retention policy; Android scheduling or a force-stop can delay cleanup.

The release workflow verifies signing-certificate continuity with v2.0.0 and publishes a SHA-256 checksum. Users upgrading from the differently signed v1.0.0 must uninstall that version first.
