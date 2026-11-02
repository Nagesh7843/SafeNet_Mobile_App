package com.safenet.android.domain.model

data class BlockEvent(
    val id: Long = 0,
    val timestamp: Long = System.currentTimeMillis(),
    val domain: String,
    val category: DomainCategory,
    val actionTaken: String
)

data class SecurityEvent(
    val id: Long = 0,
    val timestamp: Long = System.currentTimeMillis(),
    val severity: String, // INFO, WARNING, CRITICAL
    val eventType: String,
    val description: String
)
