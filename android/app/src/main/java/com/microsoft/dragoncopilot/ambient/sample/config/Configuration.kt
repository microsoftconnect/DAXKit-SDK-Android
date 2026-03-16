package com.microsoft.dragoncopilot.ambient.sample.config

import com.microsoft.dragoncopilot.ambient.data.EHRData
import com.microsoft.dragoncopilot.ambient.data.Patient
import com.microsoft.dragoncopilot.ambient.data.Provider
import java.util.Date

/**
 * Sample configuration for the Dragon Copilot Ambient SDK.
 *
 * **Integrators:** Replace the placeholder IDs below with values from your own account
 * management system. These identify your partner, product, organization, and user to the
 * Dragon Copilot service.
 *
 * - **[PARTNER_ID]** / **[PRODUCT_ID]** — Issued when you register with Dragon Copilot.
 * - **[ORG_ID]** — Your healthcare organization's identifier.
 * - **[USER_ID]** — The currently authenticated clinician/provider.

 */
class Configuration {
    companion object {
        const val PARTNER_ID = "dd421ff7-fda5-49c1-bd3a-70a7df54642e"
        const val PRODUCT_ID = "03f7077b-4f0e-41b8-a79c-f37926efcb8d"
        const val CUSTOMER_ID = "01bd0d47-1621-4a29-941d-00e9a9420f20"
        const val USER_ID = "6a28c48e-0cef-4b7f-b9a8-594f45a6cb66"
        const val PROVIDER_NAME = "Dragon Copilot Ambient Android Sample Provider"

        const val GEOGRAPHY = "US"

        /**
         * Build a [Provider] that identifies the partner, product, org, and user to the SDK.
         * The [Provider] is passed to [AmbientClient] at creation time.
         * Replace with a method retrieving the data from your systems
         */
        fun getProvider(): Provider {
            return Provider(
                userId = USER_ID,
                name = PROVIDER_NAME,
                productId = PRODUCT_ID,
                partnerId = PARTNER_ID,
                customerId = CUSTOMER_ID,
                geography = GEOGRAPHY,
            )
        }

        // ── EHR context (sample data) ───────────────────────────────────────
        // In a production app, populate these from the EHR system for each encounter.

        private const val APPOINTMENT_ID = "FakeAppointment"
        private const val ENCOUNTER_ID = "FakeEncounter"
        private val patient = Patient(
            "PID",
            "MRN",
            "Zora",
            "",
            "Arkus-Duntov",
            Date(),
            null,
        )
        private const val REASON_FOR_VISIT = "Multiple organ failure. Anxiety. Depression"

        /**
         * Sample [EHRData] sent to the SDK when opening a session.
         * This binds patient and encounter context to the recording for clinical note generation.
         */
        val ehrData = EHRData(
            APPOINTMENT_ID,
            ENCOUNTER_ID,
            patient,
            REASON_FOR_VISIT
        )
    }
}