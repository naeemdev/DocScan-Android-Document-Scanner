package com.docscan.app.data.service

import android.content.Context
import android.net.Uri
import com.docscan.app.domain.service.TextRecognizer
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import kotlinx.coroutines.tasks.await
import java.io.File

class MlKitTextRecognizer(private val context: Context) : TextRecognizer {
    private val client by lazy { TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS) }

    override suspend fun recognize(imagePath: String): String =
        client.process(InputImage.fromFilePath(context, Uri.fromFile(File(imagePath)))).await().text
}
