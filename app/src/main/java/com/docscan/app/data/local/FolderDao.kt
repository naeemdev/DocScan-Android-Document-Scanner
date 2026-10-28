package com.docscan.app.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface FolderDao {
    @Query(
        """
        SELECT f.id, f.name, (SELECT COUNT(*) FROM documents d WHERE d.folderId = f.id) AS documentCount
        FROM folders f
        ORDER BY f.name COLLATE NOCASE
        """,
    )
    fun observeFolders(): Flow<List<FolderRow>>

    @Insert
    suspend fun insert(folder: FolderEntity): Long

    @Query("UPDATE folders SET name = :name WHERE id = :id")
    suspend fun rename(id: Long, name: String)

    @Query("DELETE FROM folders WHERE id = :id")
    suspend fun delete(id: Long)

    @Query("DELETE FROM folders")
    suspend fun deleteAll()
}
