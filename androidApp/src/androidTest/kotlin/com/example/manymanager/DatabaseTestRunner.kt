package com.example.manymanager

import android.app.Application
import android.content.Context
import androidx.test.runner.AndroidJUnitRunner

/** The test Application supplies in-memory Room; production storage/preferences are never opened. */
class DatabaseTestRunner : AndroidJUnitRunner() {
    override fun newApplication(cl: ClassLoader, className: String, context: Context): Application =
        super.newApplication(cl, BudgetTestApplication::class.java.name, context)
}
