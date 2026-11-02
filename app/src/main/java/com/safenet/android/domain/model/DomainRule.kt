package com.safenet.android.domain.model

data class DomainRule(
    val domain: String,
    val category: DomainCategory,
    val source: String = "BUILTIN"
)

data class DomainEvaluationResult(
    val domain: String,
    val action: FilterAction,
    val matchedCategory: DomainCategory?,
    val isSafeSearchRewrite: Boolean = false,
    val rewrittenIp: String? = null
)
