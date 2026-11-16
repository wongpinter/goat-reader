package com.wongpinter.goatreader.ui

import com.wongpinter.goatreader.ui.nav.shouldNavigateToLibraryForTabSelection
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TabSelectionPolicyTest {
    @Test
    fun missingDestinationSwitchesPagerWithoutNavigating() {
        assertFalse(shouldNavigateToLibraryForTabSelection(false, false))
    }

    @Test
    fun topLevelDestinationSwitchesPagerWithoutNavigating() {
        assertFalse(shouldNavigateToLibraryForTabSelection(true, true))
    }

    @Test
    fun detailDestinationNavigatesBackToLibrary() {
        assertTrue(shouldNavigateToLibraryForTabSelection(true, false))
    }
}
