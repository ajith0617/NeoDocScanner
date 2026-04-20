package com.example.neodocscanner.core.domain.model

/**
 * Static catalogue of predefined application types.
 * Future: replace hardcoded list with an API-driven catalogue.
 *
 * iOS equivalent: ApplicationTemplate.swift (struct with static `all` array).
 */
data class ApplicationTemplate(
    val id: String,
    val name: String,
    val iconName: String,    // Material icon name
    val description: String
) {
    companion object {

        val all: List<ApplicationTemplate> = listOf(
            ApplicationTemplate(
                id          = "bank_account",
                name        = "Bank Account Opening",
                iconName    = "account_balance",
                description = "Identity and address documents for opening a new bank account"
            ),
            ApplicationTemplate(
                id          = "home_loan",
                name        = "Home Loan",
                iconName    = "home",
                description = "Identity and financial documents for a home loan application"
            ),
            ApplicationTemplate(
                id          = "personal_loan",
                name        = "Personal Loan",
                iconName    = "currency_rupee",
                description = "Documents required for a personal or consumer loan"
            ),
            ApplicationTemplate(
                id          = "passport",
                name        = "Passport Application",
                iconName    = "language",
                description = "Identity documents for a fresh or renewal passport application"
            ),
            ApplicationTemplate(
                id          = "visa",
                name        = "Visa Application",
                iconName    = "flight",
                description = "Supporting documents for visa processing"
            ),
            ApplicationTemplate(
                id          = "insurance_claim",
                name        = "Insurance Claim",
                iconName    = "medical_services",
                description = "Documents required to file an insurance claim"
            )
        )

        fun forId(id: String): ApplicationTemplate? = all.find { it.id == id }
    }
}
