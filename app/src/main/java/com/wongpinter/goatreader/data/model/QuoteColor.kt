package com.wongpinter.goatreader.data.model

import kotlinx.serialization.Serializable

/** Stable saved identifiers; DEFAULT keeps the appearance of existing quotes. */
@Serializable
enum class QuoteColor { DEFAULT, BLUE, ROSE, AMBER, VIOLET }
