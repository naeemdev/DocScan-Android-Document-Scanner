package com.docscan.app.domain.model

data class Document(
    val id: Long,
    val title: String,
    val createdAt: Long,
    val updatedAt: Long,
    val pages: List<Page>,
    val folderId: Long? = null,
)

data class DocumentSummary(
    val id: Long,
    val title: String,
    val updatedAt: Long,
    val pageCount: Int,
    val coverPath: String?,
)

data class Folder(
    val id: Long,
    val name: String,
    val documentCount: Int,
)

data class Point(val x: Float, val y: Float)

/** Four corners on the original image, normalised to 0..1, clockwise from top-left. */
data class CropQuad(
    val topLeft: Point,
    val topRight: Point,
    val bottomRight: Point,
    val bottomLeft: Point,
) {
    val corners: List<Point> get() = listOf(topLeft, topRight, bottomRight, bottomLeft)

    companion object {
        val FULL = CropQuad(Point(0f, 0f), Point(1f, 0f), Point(1f, 1f), Point(0f, 1f))

        fun of(corners: List<Point>): CropQuad {
            require(corners.size == 4) { "A crop needs four corners" }
            return CropQuad(corners[0], corners[1], corners[2], corners[3])
        }
    }
}

/** A signature image owned by the page, placed on the finished page. [x], [y], [width] are fractions of the page. */
data class SignaturePlacement(
    val imagePath: String,
    val x: Float,
    val y: Float,
    val width: Float,
)

/** Everything applied to a page's original image, in this order: crop, rotate, colour, signature. */
data class PageAdjustments(
    val filter: PageFilter = PageFilter.ORIGINAL,
    val rotation: Int = 0,
    val crop: CropQuad? = null,
    /** -1..1, 0 = unchanged. */
    val brightness: Float = 0f,
    /** 0.5..2, 1 = unchanged. */
    val contrast: Float = 1f,
    val signature: SignaturePlacement? = null,
)

data class Page(
    val id: Long,
    val documentId: Long,
    val position: Int,
    /** The untouched scan, kept so every adjustment can be redone without quality loss. */
    val originalPath: String,
    /** Original + adjustments; what is shown and exported. */
    val processedPath: String,
    val adjustments: PageAdjustments,
    val ocrText: String?,
) {
    /** Files that belong to this page alone and go when it goes. */
    val ownedFiles: List<String> get() = listOfNotNull(originalPath, processedPath, adjustments.signature?.imagePath)
}

data class NewPage(
    val originalPath: String,
    val processedPath: String,
    val adjustments: PageAdjustments,
    val ocrText: String? = null,
)

enum class PageFilter { ORIGINAL, ENHANCE, GRAYSCALE, BLACK_WHITE, LIGHTEN }

enum class PageSize { A4, LETTER, FIT_IMAGE }

enum class PdfQuality { SMALL, BALANCED, HIGH }

enum class ExportFormat { PDF, JPG }

enum class TitleDateStyle { DATE_TIME, DATE }

data class AppSettings(
    val pageSize: PageSize = PageSize.A4,
    val defaultFilter: PageFilter = PageFilter.ENHANCE,
    val pdfQuality: PdfQuality = PdfQuality.BALANCED,
    /** Prefix for new document names; blank means the localised default. */
    val titlePrefix: String = "",
    val titleDateStyle: TitleDateStyle = TitleDateStyle.DATE_TIME,
    /** Text stamped across exported pages (Pro); blank means none. */
    val watermarkText: String = "",
)

enum class PlanKind { MONTHLY, YEARLY, LIFETIME }

data class Plan(val id: String, val kind: PlanKind, val price: String)

enum class PurchaseResult { SUCCESS, PENDING, CANCELLED, FAILED }

/** A crop must be convex (corners clockwise) and cover a sensible area of the image. */
fun CropQuad.isUsable(): Boolean {
    val c = corners
    if (c.any { it.x !in 0f..1f || it.y !in 0f..1f }) return false
    val crosses = c.indices.map { i ->
        val a = c[i]
        val b = c[(i + 1) % 4]
        val d = c[(i + 2) % 4]
        (b.x - a.x) * (d.y - b.y) - (b.y - a.y) * (d.x - b.x)
    }
    val area = c.indices.sumOf { i -> (c[i].x * c[(i + 1) % 4].y - c[(i + 1) % 4].x * c[i].y).toDouble() } / 2
    return crosses.all { it > 0 } && area >= MIN_CROP_AREA
}

private const val MIN_CROP_AREA = 0.02

const val MAX_TITLE_LENGTH = 120
const val MAX_FOLDER_NAME_LENGTH = 60
const val MAX_PREFIX_LENGTH = 30
const val MAX_WATERMARK_LENGTH = 40
