package xyz.mdhv.riverwip.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AppInfoTest {
    @Test
    fun packageBaseMatchesGradleProperty() {
        // The package base is centralized; this pins the runtime constant to the
        // working placeholder so the RESERVED rename sweep is caught if partial.
        assertEquals("xyz.mdhv.riverwip", AppInfo.PACKAGE_BASE)
    }

    @Test
    fun workingNameIsRegisterPlaceholder() {
        assertTrue(AppInfo.WORKING_NAME.isNotBlank())
    }
}
