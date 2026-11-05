package com.example.simple.manymanager

import android.content.SharedPreferences
import androidx.room.Room
import com.example.simple.manymanager.data.ManyManagerRepository
import com.example.simple.manymanager.data.local.ManyManagerDatabase
import java.time.Clock
import java.time.Instant
import java.time.ZoneId

/** Activity tests use the real application boundary with isolated Room and appearance preferences. */
class BudgetTestApplication : ManyManagerApplication() {
    val clock = MovingClock()
    override val database by lazy {
        Room.inMemoryDatabaseBuilder(this, ManyManagerDatabase::class.java)
            .addCallback(ManyManagerDatabase.HIERARCHY_CALLBACK).build()
    }
    private val memoryRepository by lazy { ManyManagerRepository(database, databaseInitialization, clock) }
    // Only the file-reopen acceptance fixture replaces this; production has no such switch.
    var fileTestRepository: ManyManagerRepository? = null
    override val repository get() = fileTestRepository ?: memoryRepository

    override fun getSharedPreferences(name: String, mode: Int): SharedPreferences =
        super.getSharedPreferences("stage7-test-" + name, mode)
}

class MovingClock : Clock() {
    @Volatile var now: Instant = Instant.parse("2026-11-09T21:59:00Z")
    @Volatile var currentZone: ZoneId = ZoneId.of("Asia/Jerusalem")
    override fun instant(): Instant = now
    override fun getZone(): ZoneId = currentZone
    override fun withZone(zone: ZoneId): Clock = fixed(now, zone)
}
