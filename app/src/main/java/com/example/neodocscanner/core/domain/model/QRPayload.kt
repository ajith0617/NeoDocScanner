package com.example.neodocscanner.core.domain.model

import com.google.gson.Gson
import com.google.gson.annotations.SerializedName

/**
 * Decodable model for the JSON embedded in the server-generated QR code.
 *
 * Expected format:
 * {
 *   "ref":           "APP-2026-001234",
 *   "type":          "home_loan",
 *   "applicantName": "Rahul Sharma",
 *   "branch":        "Mumbai Main Branch",
 *   "createdAt":     "2026-03-20T10:30:00Z",
 *   "requiredDocs":  ["aadhaar", "pan", "bankStatement"]
 * }
 *
 * iOS equivalent: QRPayload.swift (Decodable struct).
 */
data class QRPayload(
    @SerializedName("ref")           val ref: String,
    @SerializedName("type")          val type: String,
    @SerializedName("applicantName") val applicantName: String? = null,
    @SerializedName("branch")        val branch: String? = null,
    @SerializedName("createdAt")     val createdAt: String? = null,
    @SerializedName("requiredDocs")  val requiredDocs: List<String>? = null
) {
    val resolvedTemplate: ApplicationTemplate
        get() = ApplicationTemplate.forId(type) ?: ApplicationTemplate.all.first()

    val suggestedName: String
        get() = if (!applicantName.isNullOrBlank()) {
            "${resolvedTemplate.name} — $applicantName"
        } else {
            resolvedTemplate.name
        }

    /** Re-encodes the payload to a compact JSON string for storage in serverMetadata. */
    val rawJson: String
        get() = Gson().toJson(this)

    companion object {
        sealed class ParseResult {
            data class Success(val payload: QRPayload) : ParseResult()
            data class Failure(val message: String)   : ParseResult()
        }

        fun parse(raw: String): ParseResult {
            if (raw.isBlank()) return ParseResult.Failure("QR code is empty.")
            return try {
                val payload = Gson().fromJson(raw, QRPayload::class.java)
                if (payload?.ref.isNullOrBlank()) {
                    ParseResult.Failure("QR code is missing the application reference ID.")
                } else {
                    ParseResult.Success(payload)
                }
            } catch (e: Exception) {
                ParseResult.Failure("QR code format not recognised. ${e.message}")
            }
        }
    }
}
