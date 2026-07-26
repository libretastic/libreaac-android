package org.libreaac.app

import org.junit.Assert.assertEquals
import org.junit.Test

class FileNamesTest {
    @Test
    fun replacesCharactersUnsafeAcrossDesktopFilesystems() {
        assertEquals("my-board-.obz", FileNames.safe(" my/board?.obz "))
    }

    @Test
    fun suppliesFallbackForBlankNames() {
        assertEquals("vocabulary.obf", FileNames.safe("..."))
    }
}

