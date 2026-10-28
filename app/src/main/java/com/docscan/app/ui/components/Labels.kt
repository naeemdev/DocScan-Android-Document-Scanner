package com.docscan.app.ui.components

import androidx.annotation.StringRes
import com.docscan.app.R
import com.docscan.app.domain.model.PageFilter
import com.docscan.app.domain.model.PageSize
import com.docscan.app.domain.model.PdfQuality
import com.docscan.app.domain.model.TitleDateStyle

@get:StringRes
val PageFilter.label: Int
    get() = when (this) {
        PageFilter.ORIGINAL -> R.string.filter_original
        PageFilter.ENHANCE -> R.string.filter_enhance
        PageFilter.GRAYSCALE -> R.string.filter_grayscale
        PageFilter.BLACK_WHITE -> R.string.filter_black_white
        PageFilter.LIGHTEN -> R.string.filter_lighten
    }

@get:StringRes
val PageSize.label: Int
    get() = when (this) {
        PageSize.A4 -> R.string.page_size_a4
        PageSize.LETTER -> R.string.page_size_letter
        PageSize.FIT_IMAGE -> R.string.page_size_fit
    }

@get:StringRes
val PdfQuality.label: Int
    get() = when (this) {
        PdfQuality.SMALL -> R.string.quality_small
        PdfQuality.BALANCED -> R.string.quality_balanced
        PdfQuality.HIGH -> R.string.quality_high
    }

@get:StringRes
val TitleDateStyle.label: Int
    get() = when (this) {
        TitleDateStyle.DATE_TIME -> R.string.title_style_date_time
        TitleDateStyle.DATE -> R.string.title_style_date
    }
