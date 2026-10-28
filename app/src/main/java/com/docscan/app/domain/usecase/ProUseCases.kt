package com.docscan.app.domain.usecase

import com.docscan.app.domain.repository.EntitlementRepository

class ProRequiredException : IllegalStateException("This needs Pro")

/** Domain-side guard for Pro features; the UI checks first, this makes sure nothing slips past it. */
class RequireProUseCase(private val entitlements: EntitlementRepository) {
    operator fun invoke() {
        if (!entitlements.isPro.value) throw ProRequiredException()
    }
}
