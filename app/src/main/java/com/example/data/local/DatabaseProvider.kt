package com.example.data.local

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

object DatabaseProvider {
    @Volatile
    private var instance: AppDatabase? = null

    private val _dbEpoch = MutableStateFlow(0)
    val dbEpoch: StateFlow<Int> = _dbEpoch.asStateFlow()

    @Synchronized
    fun getDatabase(context: Context): AppDatabase {
        return instance ?: AppDatabase.build(context.applicationContext).also {
            instance = it
        }
    }

    @Synchronized
    fun closeDatabase() {
        instance?.close()
        instance = null
    }

    @Synchronized
    fun reopenDatabase(context: Context): AppDatabase {
        instance?.close()
        instance = null
        val fresh = AppDatabase.build(context.applicationContext)
        instance = fresh
        _dbEpoch.value += 1
        return fresh
    }
}
