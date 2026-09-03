package com.example.data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface GeneratedApkDao {
    @Query("SELECT * FROM generated_apks ORDER BY createdAt DESC")
    fun getAllApks(): Flow<List<GeneratedApk>>

    @Query("SELECT * FROM generated_apks WHERE id = :id")
    suspend fun getApkById(id: Long): GeneratedApk?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertApk(apk: GeneratedApk): Long

    @Delete
    suspend fun deleteApk(apk: GeneratedApk)
}
