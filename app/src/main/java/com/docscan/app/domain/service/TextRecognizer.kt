package com.docscan.app.domain.service

interface TextRecognizer {
    suspend fun recognize(imagePath: String): String
}
