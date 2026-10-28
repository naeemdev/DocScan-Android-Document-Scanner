package com.docscan.app.data.local

import androidx.room.ColumnInfo
import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import androidx.room.Relation

@Entity(tableName = "folders")
data class FolderEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val createdAt: Long,
)

@Entity(
    tableName = "documents",
    foreignKeys = [
        ForeignKey(
            entity = FolderEntity::class,
            parentColumns = ["id"],
            childColumns = ["folderId"],
            onDelete = ForeignKey.SET_NULL,
        ),
    ],
    indices = [Index("updatedAt"), Index("folderId")],
)
data class DocumentEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    val createdAt: Long,
    val updatedAt: Long,
    val folderId: Long? = null,
)

@Entity(
    tableName = "pages",
    foreignKeys = [
        ForeignKey(
            entity = DocumentEntity::class,
            parentColumns = ["id"],
            childColumns = ["documentId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("documentId")],
)
data class PageEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val documentId: Long,
    val position: Int,
    val originalPath: String,
    val processedPath: String,
    val filter: String,
    val rotation: Int,
    val ocrText: String?,
    /** "x1,y1,x2,y2,x3,y3,x4,y4", clockwise from top-left; null = whole image. */
    val crop: String? = null,
    @ColumnInfo(defaultValue = "0") val brightness: Float = 0f,
    @ColumnInfo(defaultValue = "1") val contrast: Float = 1f,
    val signaturePath: String? = null,
    val signatureX: Float? = null,
    val signatureY: Float? = null,
    val signatureWidth: Float? = null,
)

data class DocumentWithPages(
    @Embedded val document: DocumentEntity,
    @Relation(parentColumn = "id", entityColumn = "documentId") val pages: List<PageEntity>,
)

data class DocumentSummaryRow(
    val id: Long,
    val title: String,
    val updatedAt: Long,
    val pageCount: Int,
    val coverPath: String?,
)

data class FolderRow(
    val id: Long,
    val name: String,
    val documentCount: Int,
)
