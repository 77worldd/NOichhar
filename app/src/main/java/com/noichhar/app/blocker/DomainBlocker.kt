package com.noichhar.app.blocker

/** A normalized rule; explicit allows override downloaded block rules. */
data class BlockRule(
    val domain: String,
    val wildcard: Boolean = false,
    val allow: Boolean = false,
    val user: Boolean = false
)

/** Compatibility name retained for existing callers and integrations. */
class DomainBlocker : EnhancedBlocklistEngine()
