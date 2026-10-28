package com.docscan.app.ui.signature

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.docscan.app.R
import com.docscan.app.domain.model.Point
import com.docscan.app.ui.components.appViewModel
import com.docscan.app.ui.theme.DocScanTheme
import com.docscan.app.ui.theme.FixedColors
import com.docscan.app.ui.theme.Sizes
import com.docscan.app.ui.theme.Spacing

@Composable
fun SignaturePadRoute(onBack: () -> Unit) {
    val viewModel = appViewModel { SignaturePadViewModel(saveSignature) }
    val saving by viewModel.saving.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    val failed = stringResource(R.string.error_generic)
    var strokes by remember { mutableStateOf(emptyList<List<Point>>()) }

    LaunchedEffect(viewModel) {
        viewModel.events.collect { event ->
            when (event) {
                SignatureEvent.Saved -> onBack()
                SignatureEvent.Failed -> snackbar.showSnackbar(failed)
            }
        }
    }

    SignaturePadScreen(
        strokes = strokes,
        saving = saving,
        onStrokesChange = { strokes = it },
        onSave = { viewModel.onSave(strokes, Sizes.signaturePadAspect) },
        onBack = onBack,
        snackbar = snackbar,
    )
}

/** [strokes] are normalised to the pad (0..1 on each axis). */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SignaturePadScreen(
    strokes: List<List<Point>>,
    saving: Boolean,
    onStrokesChange: (List<List<Point>>) -> Unit,
    onSave: () -> Unit,
    onBack: () -> Unit,
    snackbar: SnackbarHostState = remember { SnackbarHostState() },
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.sign_new_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            Icons.AutoMirrored.Outlined.ArrowBack,
                            stringResource(R.string.action_back)
                        )
                    }
                },
                actions = {
                    TextButton(
                        onClick = { onStrokesChange(emptyList()) },
                        enabled = strokes.isNotEmpty() && !saving
                    ) {
                        Text(stringResource(R.string.action_clear))
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        Column(
            Modifier
                .padding(padding)
                .fillMaxSize()
                .padding(Spacing.m),
            verticalArrangement = Arrangement.spacedBy(Spacing.m),
        ) {
            Text(
                stringResource(R.string.sign_new_hint),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            SignaturePad(strokes, onStrokesChange)
            Button(
                onClick = onSave,
                enabled = strokes.any { it.size >= 2 } && !saving,
                modifier = Modifier.fillMaxWidth(),
            ) { Text(stringResource(R.string.action_save_signature)) }
        }
    }
}

@Composable
private fun SignaturePad(strokes: List<List<Point>>, onStrokesChange: (List<List<Point>>) -> Unit) {
    val current by rememberUpdatedState(strokes)
    val change by rememberUpdatedState(onStrokesChange)
    val ink = PAD_INK
    val guide = MaterialTheme.colorScheme.outlineVariant
    val description = stringResource(R.string.sign_pad_description)
    Box(
        Modifier
            .fillMaxWidth()
            .aspectRatio(Sizes.signaturePadAspect)
            .background(FixedColors.paper, RoundedCornerShape(Spacing.s))
            .border(
                Sizes.hairline,
                MaterialTheme.colorScheme.outline,
                RoundedCornerShape(Spacing.s)
            )
            .semantics { contentDescription = description },
        contentAlignment = Alignment.Center,
    ) {
        Canvas(
            Modifier
                .fillMaxSize()
                .pointerInput(Unit) {
                    fun Offset.normalised() = Point(
                        (x / size.width).coerceIn(0f, 1f),
                        (y / size.height).coerceIn(0f, 1f),
                    )
                    // Events arrive faster than recomposition, so the gesture keeps its own copy of the strokes.
                    var working = current
                    detectDragGestures(
                        onDragStart = { start ->
                            working = current + listOf(listOf(start.normalised()))
                            change(working)
                        },
                        onDrag = { pointer, _ ->
                            pointer.consume()
                            working = working.dropLast(1) + listOf(working.last() + pointer.position.normalised())
                            change(working)
                        },
                    )
                },
        ) {
            // Signing line at three quarters of the height.
            val lineY = size.height * 0.75f
            drawLine(
                guide,
                Offset(size.width * 0.05f, lineY),
                Offset(size.width * 0.95f, lineY),
                strokeWidth = 1.dp.toPx()
            )
            strokes.forEach { stroke ->
                val path = Path()
                stroke.forEachIndexed { i, p ->
                    if (i == 0) path.moveTo(
                        p.x * size.width,
                        p.y * size.height
                    ) else path.lineTo(p.x * size.width, p.y * size.height)
                }
                drawPath(
                    path,
                    ink,
                    style = Stroke(
                        width = 3.dp.toPx(),
                        cap = StrokeCap.Round,
                        join = StrokeJoin.Round
                    )
                )
            }
        }
    }
}

// Same ink as the saved image (AndroidSignatureStore) so what you draw is what you get.
private val PAD_INK = Color(0xFF102060)

@Preview(name = "Signature – empty pad", showBackground = true)
@Composable
private fun SignatureEmptyPreview() =
    DocScanTheme { SignaturePadScreen(emptyList(), false, {}, {}, {}) }

@Preview(name = "Signature – drawn", showBackground = true)
@Composable
private fun SignatureDrawnPreview() = DocScanTheme {
    val stroke =
        (0..20).map { i -> Point(0.1f + i * 0.04f, 0.6f - 0.15f * kotlin.math.sin(i / 2f)) }
    SignaturePadScreen(listOf(stroke), false, {}, {}, {})
}
