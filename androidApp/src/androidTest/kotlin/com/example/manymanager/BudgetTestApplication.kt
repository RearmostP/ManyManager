package com.example.manymanager

import android.content.SharedPreferences
import androidx.room.Room
import com.example.manymanager.data.ManyManagerRepository
import com.example.manymanager.data.local.ManyManagerDatabase
import java.time.Clock
import java.time.Instant
import java.time.ZoneId

/** Activity tests use the real application boundary with isolated Room and appearance preferences. */
class BudgetTestApplication : ManyManagerApplication() {
    val clock = MovingClock()
    override val database by lazy {
        Room.inMemoryDatabaseBuilder(this, ManyManagerDatabase::class.java).build()
    }
    override val repository by lazy { ManyManagerRepository(database, databaseInitialization, clock) }

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
