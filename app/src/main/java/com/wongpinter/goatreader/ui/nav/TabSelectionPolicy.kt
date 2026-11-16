package com.wongpinter.goatreader.ui.nav

/** Avoids graph-dependent navigation while NavHost is still installing its start destination. */
internal fun shouldNavigateToLibraryForTabSelection(
    hasCurrentDestination: Boolean,
    currentDestinationIsTopLevel: Boolean,
): Boolean = hasCurrentDestination && !currentDestinationIsTopLevel
