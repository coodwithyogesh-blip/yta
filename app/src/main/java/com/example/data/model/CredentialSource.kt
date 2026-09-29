package com.example.data.model

enum class CredentialSource(val displayName: String) {
    MANUALLY_ENTERED("Manually Entered"),
    IMPORTED_TXT("Imported TXT"),
    VAULT("Authorized Credentials Vault")
}
