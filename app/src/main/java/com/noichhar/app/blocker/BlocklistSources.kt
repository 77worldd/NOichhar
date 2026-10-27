package com.noichhar.app.blocker

/** Public DNS-compatible lists. Updates are best-effort and keep the previous
 * rules if every download fails. */
object BlocklistSources {
    val ALL = listOf(
        "https://raw.githubusercontent.com/hagezi/dns-blocklists/main/hosts/multi.txt",
        "https://raw.githubusercontent.com/StevenBlack/hosts/master/hosts",
        "https://adguardteam.github.io/AdGuardSDNSFilter/Filters/filter.txt",
        "https://big.oisd.nl/domainswild",
        "https://o0.pages.dev/Lite/domains.txt",
        "https://pgl.yoyo.org/adservers/serverlist.php?hostformat=hosts&showintro=0&mimetype=plaintext"
    )
}
