package com.docscan.app.domain.service

interface IdCardComposer {
    /** Places the front image above the back image on one portrait page, written as a JPEG to [outputPath]. */
    suspend fun compose(frontPath: String, backPath: String, outputPath: String)
}
