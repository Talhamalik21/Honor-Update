package com.example.data

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

class UpdateRepository(private val dao: UpdateHistoryDao) {
    val historyList: Flow<List<UpdateHistory>> = dao.getAllHistory()

    suspend fun insertHistory(history: UpdateHistory) {
        dao.insert(history)
    }

    suspend fun clearHistory() {
        dao.clearAll()
    }

    suspend fun preseedIfEmpty() {
        val currentList = historyList.first()
        if (currentList.isEmpty()) {
            dao.insert(
                UpdateHistory(
                    versionName = "MagicOS 8.0.0.105 (C432E6R2P2)",
                    androidVersion = "Android 14",
                    changelog = "This major update introduces MagicOS 8.0, featuring the new Magic Portal for seamless app transitions, a polished capsule pill in the status bar, and advanced secure local privacy computing.",
                    downloadSize = 3450000000L, // 3.45 GB
                    status = "COMPLETED",
                    timestamp = System.currentTimeMillis() - 30 * 24 * 60 * 60 * 1000L // 30 days ago
                )
            )
            dao.insert(
                UpdateHistory(
                    versionName = "MagicOS 7.2.0.198 (C432)",
                    androidVersion = "Android 13",
                    changelog = "Integrates Android security patches to improve host system security and stability.",
                    downloadSize = 485000000L, // 485 MB
                    status = "COMPLETED",
                    timestamp = System.currentTimeMillis() - 90 * 24 * 60 * 60 * 1000L // 90 days ago
                )
            )
        }
    }
}
