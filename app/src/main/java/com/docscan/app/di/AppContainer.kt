package com.docscan.app.di

import android.content.Context
import androidx.room.Room
import com.docscan.app.BuildConfig
import com.docscan.app.data.billing.CurrentActivity
import com.docscan.app.data.billing.PlayBillingRepository
import com.docscan.app.data.local.AppDatabase
import com.docscan.app.data.local.MIGRATION_1_2
import com.docscan.app.data.repository.RoomDocumentRepository
import com.docscan.app.data.repository.RoomFolderRepository
import com.docscan.app.data.repository.SharedPrefsSettingsRepository
import com.docscan.app.data.service.AndroidDocumentNamer
import com.docscan.app.data.service.AndroidFileStore
import com.docscan.app.data.service.AndroidIdCardComposer
import com.docscan.app.data.service.AndroidImageProcessor
import com.docscan.app.data.service.AndroidPdfExporter
import com.docscan.app.data.service.AndroidPdfRasterizer
import com.docscan.app.data.service.AndroidSignatureStore
import com.docscan.app.data.service.AndroidWatermarker
import com.docscan.app.data.service.MlKitTextRecognizer
import com.docscan.app.domain.repository.DocumentRepository
import com.docscan.app.domain.repository.EntitlementRepository
import com.docscan.app.domain.repository.FolderRepository
import com.docscan.app.domain.repository.SettingsRepository
import com.docscan.app.domain.service.FileStore
import com.docscan.app.domain.service.ImageProcessor
import com.docscan.app.domain.service.SignatureStore
import com.docscan.app.domain.usecase.CreateFolderUseCase
import com.docscan.app.domain.usecase.CreateIdCardUseCase
import com.docscan.app.domain.usecase.CropPageUseCase
import com.docscan.app.domain.usecase.DeleteAllDataUseCase
import com.docscan.app.domain.usecase.DeleteDocumentUseCase
import com.docscan.app.domain.usecase.DeleteFolderUseCase
import com.docscan.app.domain.usecase.DeletePageUseCase
import com.docscan.app.domain.usecase.DeleteSignatureUseCase
import com.docscan.app.domain.usecase.EditPageUseCase
import com.docscan.app.domain.usecase.ExportDocumentUseCase
import com.docscan.app.domain.usecase.ExportTextUseCase
import com.docscan.app.domain.usecase.ImportPdfUseCase
import com.docscan.app.domain.usecase.ImportScanUseCase
import com.docscan.app.domain.usecase.MergeDocumentsUseCase
import com.docscan.app.domain.usecase.MoveDocumentsUseCase
import com.docscan.app.domain.usecase.MovePageUseCase
import com.docscan.app.domain.usecase.ObserveDocumentUseCase
import com.docscan.app.domain.usecase.ObserveDocumentsUseCase
import com.docscan.app.domain.usecase.ObserveFoldersUseCase
import com.docscan.app.domain.usecase.ObserveSignaturesUseCase
import com.docscan.app.domain.usecase.PlaceSignatureUseCase
import com.docscan.app.domain.usecase.RecognizeDocumentTextUseCase
import com.docscan.app.domain.usecase.RemoveSignatureUseCase
import com.docscan.app.domain.usecase.RenameDocumentUseCase
import com.docscan.app.domain.usecase.RenameFolderUseCase
import com.docscan.app.domain.usecase.RequireProUseCase
import com.docscan.app.domain.usecase.RotatePageUseCase
import com.docscan.app.domain.usecase.SavePagesUseCase
import com.docscan.app.domain.usecase.SaveSignatureUseCase

/** The only place that names concrete implementations. Everything else depends on domain interfaces. */
class AppContainer(context: Context, currentActivity: CurrentActivity) {
    private val database = Room.databaseBuilder(context, AppDatabase::class.java, "docscan.db")
        .addMigrations(MIGRATION_1_2)
        .build()
    private val documents: DocumentRepository = RoomDocumentRepository(database)
    private val folders: FolderRepository = RoomFolderRepository(database)
    private val fileStore: FileStore = AndroidFileStore(context)
    private val imageProcessor: ImageProcessor = AndroidImageProcessor()
    private val signatureStore: SignatureStore = AndroidSignatureStore(context)

    val settings: SettingsRepository =
        SharedPrefsSettingsRepository(context.getSharedPreferences("settings", Context.MODE_PRIVATE))
    val entitlements: EntitlementRepository = PlayBillingRepository(context, currentActivity, BuildConfig.DEBUG)
    private val namer = AndroidDocumentNamer(context, settings)
    private val requirePro = RequireProUseCase(entitlements)

    // Documents
    val observeDocuments = ObserveDocumentsUseCase(documents)
    val observeDocument = ObserveDocumentUseCase(documents)
    private val savePages = SavePagesUseCase(documents, fileStore, imageProcessor, settings, namer)
    val importScan = ImportScanUseCase(fileStore, savePages)
    val importPdf = ImportPdfUseCase(AndroidPdfRasterizer(context), fileStore, savePages)
    val createIdCard = CreateIdCardUseCase(fileStore, AndroidIdCardComposer(), savePages)
    val mergeDocuments = MergeDocumentsUseCase(documents, fileStore, namer)
    val renameDocument = RenameDocumentUseCase(documents)
    val deleteDocument = DeleteDocumentUseCase(documents, fileStore)
    val deleteAllData = DeleteAllDataUseCase(documents, folders, fileStore, signatureStore)
    val exportDocument = ExportDocumentUseCase(AndroidPdfExporter(), AndroidWatermarker(), fileStore, settings, entitlements)
    val exportText = ExportTextUseCase(documents, fileStore)
    val recognizeText = RecognizeDocumentTextUseCase(documents, MlKitTextRecognizer(context))

    // Pages
    val editPage = EditPageUseCase(documents, fileStore, imageProcessor)
    val rotatePage = RotatePageUseCase(editPage)
    val cropPage = CropPageUseCase(editPage)
    val movePage = MovePageUseCase(documents)
    val deletePage = DeletePageUseCase(documents, fileStore, deleteDocument)
    val placeSignature = PlaceSignatureUseCase(fileStore, editPage, requirePro)
    val removeSignature = RemoveSignatureUseCase(editPage)

    // Folders
    val observeFolders = ObserveFoldersUseCase(folders)
    val createFolder = CreateFolderUseCase(folders)
    val renameFolder = RenameFolderUseCase(folders)
    val deleteFolder = DeleteFolderUseCase(folders)
    val moveDocuments = MoveDocumentsUseCase(documents)

    // Signatures
    val observeSignatures = ObserveSignaturesUseCase(signatureStore)
    val saveSignature = SaveSignatureUseCase(signatureStore, requirePro)
    val deleteSignature = DeleteSignatureUseCase(signatureStore)
}
