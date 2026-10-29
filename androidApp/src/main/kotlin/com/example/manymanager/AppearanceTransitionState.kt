package com.example.manymanager

import android.graphics.Bitmap
import androidx.lifecycle.ViewModel

/** A single transient frame retained across recreation, never written to storage. */
class AppearanceTransitionState : ViewModel() {
    var snapshot: Bitmap? = null

    override fun onCleared() {
        snapshot = null
    }
}
