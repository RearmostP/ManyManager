package com.example.manymanager

import android.app.Application
import android.util.Log
import com.example.manymanager.data.ManyManagerRepository
import java.time.Clock
import com.example.manymanager.data.local.CalculatorEntity
import com.example.manymanager.data.local.ManyManagerDatabase
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async

/** Owns storage for the process. Existing business screens still use their temporary ViewModel. */
class ManyManagerApplication : Application() {
    private val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    val database: ManyManagerDatabase by lazy { ManyManagerDatabase.create(this) }

    val repository: ManyManagerRepository by lazy {
        ManyManagerRepository(database, databaseInitialization, Clock.systemDefaultZone())
    }

    lateinit var databaseInitialization: Deferred<Result<CalculatorEntity>>
        private set

    override fun onCreate() {
        super.onCreate()
        databaseInitialization = applicationScope.async {
            try {
                Result.success(database.dao().initializeLocalCalculator())
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (failure: Exception) {
                // Preserve the failure for future consumers, without interrupting unrelated screens.
                Log.e("ManyManagerDatabase", "Local calculator initialization failed", failure)
                Result.failure(failure)
            }
        }
    }
}
