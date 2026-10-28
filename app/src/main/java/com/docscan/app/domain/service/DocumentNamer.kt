package com.docscan.app.domain.service

/** Localised titles for documents the app creates. */
interface DocumentNamer {
    fun newScanTitle(epochMillis: Long): String
    fun mergedTitle(firstTitle: String, otherCount: Int): String
}
