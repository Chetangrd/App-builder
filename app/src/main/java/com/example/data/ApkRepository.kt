package com.example.data

import kotlinx.coroutines.flow.Flow

class ApkRepository(private val dao: GeneratedApkDao) {
    val allApks: Flow<List<GeneratedApk>> = dao.getAllApks()

    suspend fun insert(apk: GeneratedApk): Long = dao.insertApk(apk)

    suspend fun delete(apk: GeneratedApk) = dao.deleteApk(apk)

    suspend fun getById(id: Long): GeneratedApk? = dao.getApkById(id)
}
