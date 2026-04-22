package com.example.neodocscanner.feature.vault.data.service.text

import com.example.neodocscanner.core.domain.model.DocumentClass
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class DocumentFieldExtractorServicePassportTest {

    private val extractor = DocumentFieldExtractorService()

    @Test
    fun `extracts given name and surname from noisy passport labels`() {
        val raw = """
            nfs/ Type
            REPUBLIC OF. INDIA
            U/Surname
            VO0TOORI
            f w m/Given Namels
            HARSHENDAR REDDY
            en / Nationality
            INDIAN
            g ue / Country Code
            IND
            or/ Place f Birth
            MADANAPALLE ANDHRA PRADESH
            or er/ Place of issue
            HYDERABAD
            r we fef/ Date of lssue
            11/04/2014
            wgte I Passport No.
            L8415370
            NDate of Birth
            21/06/1993
            PINDVO0TOORIHARSHENDARREDDY
            L84153709 IND9306211M2404107
            wrfty h ferf /Date of Expiny
            10/04/2024
        """.trimIndent()

        val fields = extractor.extract(DocumentClass.PASSPORT, raw)
        fun value(label: String): String? = fields.firstOrNull { it.label == label }?.value

        assertEquals("VOOTOORI", value("Surname"))
        assertEquals("HARSHENDAR REDDY", value("Given Name"))
        assertEquals("HARSHENDAR REDDY VOOTOORI", value("Name"))
    }

    @Test
    fun `does not duplicate surname into name when given name is missing`() {
        val raw = """
            REPUBLIC OF INDIA
            Surname
            VO0TOORI
            Passport No.
            L8415370
            Date of Birth
            21/06/1993
        """.trimIndent()

        val fields = extractor.extract(DocumentClass.PASSPORT, raw)
        fun value(label: String): String? = fields.firstOrNull { it.label == label }?.value

        assertEquals("VOOTOORI", value("Surname"))
        assertNull(value("Name"))
    }

    @Test
    fun `keeps passport dates as dates and rejects date-like place of birth values`() {
        val frontText = """
            REPUBLIC OF INDIA
            Place of Birth
            11/04/2014
            Date of Birth
            21/06/1993
            Date of Expiry
            10/04/2024
        """.trimIndent()
        val backText = """
            Legal Guardian
            Legal Guardian
        """.trimIndent()

        val fields = extractor.extract(DocumentClass.PASSPORT, frontText, backText)
        fun value(label: String): String? = fields.firstOrNull { it.label == label }?.value

        assertNull(value("Place of Birth"))
        assertEquals("21 Jun 1993", value("Date of Birth"))
        assertEquals("10 Apr 2024", value("Date of Expiry"))
        assertNull(value("Name of Father/Legal Guardian"))
    }

    @Test
    fun `rejects numeric and special character passport place values`() {
        val raw = """
            REPUBLIC OF INDIA
            Place of Birth
            12345
            Place of Issue
            HYDERABAD-1
            Date of Birth
            21/06/1993
        """.trimIndent()

        val fields = extractor.extract(DocumentClass.PASSPORT, raw)
        fun value(label: String): String? = fields.firstOrNull { it.label == label }?.value

        assertNull(value("Place of Birth"))
        assertNull(value("Place of Issue"))
    }

    @Test
    fun `keeps indian state only in place fields and not in passport name fields`() {
        val raw = """
            REPUBLIC OF INDIA
            Surname
            ANDHRA PRADESH
            Given Name
            HARSHENDAR REDDY
            Place of Birth
            ANDHRA PRADESH
            Date of Birth
            21/06/1993
        """.trimIndent()

        val fields = extractor.extract(DocumentClass.PASSPORT, raw)
        fun value(label: String): String? = fields.firstOrNull { it.label == label }?.value

        assertNull(value("Surname"))
        assertEquals("HARSHENDAR REDDY", value("Given Name"))
        assertEquals("HARSHENDAR REDDY", value("Name"))
        assertEquals("ANDHRA PRADESH", value("Place of Birth"))
    }

    @Test
    fun `does not treat legal guardian label as father guardian value`() {
        val frontText = """
            REPUBLIC OF INDIA
            Date of Birth
            21/06/1993
        """.trimIndent()
        val backText = """
            Name of Father/Legal Guardian
            Legal Guardian
        """.trimIndent()

        val fields = extractor.extract(DocumentClass.PASSPORT, frontText, backText)
        fun value(label: String): String? = fields.firstOrNull { it.label == label }?.value

        assertNull(value("Name of Father/Legal Guardian"))
    }

    @Test
    fun `extracts passport name and father when given and guardian labels are noisy`() {
        val frontText = """
            REPUBLIC OF INDIA
            Surname
            SUBRAMANIAN
            Glven Nameo
            RAJIV
            Place of Birth
            THIRUTHURAIPOONDI TAMIL NADU
            Date of Birth
            17/07/1991
        """.trimIndent()
        val backText = """
            Name of Father/ Legal Guardlan
            SUBRAMANIAN
        """.trimIndent()

        val fields = extractor.extract(DocumentClass.PASSPORT, frontText, backText)
        fun value(label: String): String? = fields.firstOrNull { it.label == label }?.value

        assertEquals("SUBRAMANIAN", value("Surname"))
        assertEquals("RAJIV", value("Given Name"))
        assertEquals("RAJIV SUBRAMANIAN", value("Name"))
        assertEquals("SUBRAMANIAN", value("Name of Father/Legal Guardian"))
    }
}
