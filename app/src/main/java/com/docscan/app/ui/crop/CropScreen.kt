package com.docscan.app.ui.crop

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
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
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.docscan.app.R
import com.docscan.app.domain.model.CropQuad
import com.docscan.app.domain.model.Point
import com.docscan.app.domain.model.isUsable
import com.docscan.app.ui.components.FileImage
import com.docscan.app.ui.components.appViewModel
import com.docscan.app.ui.components.rememberImageAspect
import com.docscan.app.ui.preview.PreviewData
import com.docscan.app.ui.theme.DocScanTheme
import com.docscan.app.ui.theme.Sizes
import com.docscan.app.ui.theme.Spacing

@Composable
fun CropRoute(documentId: Long, pageId: Long, onBack: () -> Unit) {
    val viewModel = appViewModel { CropViewModel(documentId, pageId, observeDocument, cropPage) }
    val state by viewModel.state.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    val failed = stringResource(R.string.error_generic)
    var corners by remember(state.page?.id) {
        mutableStateOf(
            (state.page?.adjustments?.crop ?: CropQuad.FULL).corners
        )
    }

    LaunchedEffect(viewModel) {
        viewModel.events.collect { event ->
            when (event) {
                CropEvent.Done -> onBack()
                CropEvent.Failed -> snackbar.showSnackbar(failed)
            }
        }
    }

    CropScreen(
        state = state,
        corners = corners,
        onCornersChange = { corners = it },
        onApply = { viewModel.onApply(CropQuad.of(corners)) },
        onReset = { corners = CropQuad.FULL.corners },
        onBack = onBack,
        snackbar = snackbar,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CropScreen(
    state: CropUiState,
    corners: List<Point>,
    onCornersChange: (List<Point>) -> Unit,
    onApply: () -> Unit,
    onReset: () -> Unit,
    onBack: () -> Unit,
    snackbar: SnackbarHostState = remember { SnackbarHostState() },
) {
    val valid = CropQuad.of(corners).isUsable()
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.crop_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            Icons.AutoMirrored.Outlined.ArrowBack,
                            stringResource(R.string.action_back)
                        )
                    }
                },
            )
        },
        bottomBar = {
            Surface(tonalElevation = Spacing.xs) {
                Column(Modifier
                    .fillMaxWidth()
                    .navigationBarsPadding()
                    .padding(Spacing.m)) {
                    Text(
                        stringResource(if (valid) R.string.crop_hint else R.string.crop_invalid),
                        style = MaterialTheme.typography.bodyMedium,
                        color = if (valid) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.error,
                    )
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .padding(top = Spacing.s),
                        horizontalArrangement = Arrangement.End
                    ) {
                        TextButton(onClick = onReset, enabled = !state.isSaving) {
                            Text(
                                stringResource(R.string.crop_whole_page)
                            )
                        }
                        Button(
                            onClick = onApply,
                            enabled = valid && state.page != null && !state.isSaving
                        ) {
                            Text(stringResource(R.string.action_apply))
                        }
                    }
                }
            }
        },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        Box(
            Modifier
                .padding(padding)
                .fillMaxSize()
                .padding(Spacing.l),
            contentAlignment = Alignment.Center
        ) {
            when {
                state.isLoading -> CircularProgressIndicator()
                else -> CropCanvas(state.page?.originalPath, corners, onCornersChange)
            }
            if (state.isSaving) CircularProgressIndicator()
        }
    }
}

/** The original scan (before rotation) with the crop outline and four draggable corners. */
@Composable
private fun CropCanvas(
    path: String?,
    corners: List<Point>,
    onCornersChange: (List<Point>) -> Unit
) {
    val aspect = rememberImageAspect(path)
    if (aspect == null) {
        FileImage(path, null, Modifier.fillMaxSize())
        return
    }
    val outline = MaterialTheme.colorScheme.primary
    BoxWithConstraints(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        val fitsWidth = aspect > maxWidth / maxHeight
        val width = if (fitsWidth) maxWidth else maxHeight * aspect
        val height = if (fitsWidth) maxWidth / aspect else maxHeight
        Box(Modifier.size(width, height)) {
            FileImage(
                path,
                stringResource(R.string.crop_image_description),
                Modifier.fillMaxSize(),
                maxSide = 2000
            )
            Canvas(Modifier.fillMaxSize()) {
                val path2 = Path().apply {
                    corners.forEachIndexed { i, p ->
                        val o = Offset(p.x * size.width, p.y * size.height)
                        if (i == 0) moveTo(o.x, o.y) else lineTo(o.x, o.y)
                    }
                    close()
                }
                drawPath(path2, outline, style = Stroke(width = Sizes.hairline.toPx() * 2))
            }
            val labels = listOf(
                R.string.crop_corner_top_left,
                R.string.crop_corner_top_right,
                R.string.crop_corner_bottom_right,
                R.string.crop_corner_bottom_left
            )
            corners.forEachIndexed { index, point ->
                CornerHandle(point, width, height, stringResource(labels[index])) { moved ->
                    onCornersChange(corners.toMutableList().also { it[index] = moved })
                }
            }
        }
    }
}

@Composable
private fun CornerHandle(
    point: Point,
    width: Dp,
    height: Dp,
    description: String,
    onMove: (Point) -> Unit
) {
    val density = LocalDensity.current
    val current by rememberUpdatedState(point)
    val move by rememberUpdatedState(onMove)
    val half = Sizes.cropHandle / 2
    Box(
        Modifier
            .offset(width * point.x - half, height * point.y - half)
            .size(Sizes.cropHandle)
            .semantics { contentDescription = description }
            .pointerInput(width, height) {
                // The gesture tracks its own position; several drag events can arrive between recompositions.
                var position = current
                detectDragGestures(onDragStart = { position = current }) { change, drag ->
                    change.consume()
                    val dx = with(density) { drag.x.toDp() } / width
                    val dy = with(density) { drag.y.toDp() } / height
                    position = Point((position.x + dx).coerceIn(0f, 1f), (position.y + dy).coerceIn(0f, 1f))
                    move(position)
                }
            },
        contentAlignment = Alignment.Center,
    ) {
        Box(
            Modifier
                .size(Sizes.cropHandle / 2)
                .background(MaterialTheme.colorScheme.primary, CircleShape)
                .border(Sizes.hairline * 2, MaterialTheme.colorScheme.onPrimary, CircleShape),
        )
    }
}

@Preview(name = "Crop – whole page", showBackground = true)
@Composable
private fun CropPreview() = DocScanTheme {
    CropScreen(
        CropUiState(PreviewData.pages[0], isLoading = false),
        CropQuad.FULL.corners,
        {},
        {},
        {},
        {})
}

@Preview(name = "Crop – invalid outline", showBackground = true)
@Composable
private fun CropInvalidPreview() = DocScanTheme {
    val twisted = listOf(Point(0.9f, 0.1f), Point(0.1f, 0.1f), Point(0.9f, 0.9f), Point(0.1f, 0.9f))
    CropScreen(CropUiState(PreviewData.pages[0], isLoading = false), twisted, {}, {}, {}, {})
}

@Preview(name = "Crop – saving", showBackground = true)
@Composable
private fun CropSavingPreview() = DocScanTheme {
    CropScreen(
        CropUiState(PreviewData.pages[0], isLoading = false, isSaving = true),
        CropQuad.FULL.corners,
        {},
        {},
        {},
        {})
}
