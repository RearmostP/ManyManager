package com.example.manymanager

import android.app.Application
import android.content.Context
import androidx.test.runner.AndroidJUnitRunner

/** Database fixtures never start the production Application or initialize its database. */
class DatabaseTestRunner : AndroidJUnitRunner() {
    override fun newApplication(cl: ClassLoader, className: String, context: Context): Application =
        super.newApplication(cl, Application::class.java.name, context)
}
