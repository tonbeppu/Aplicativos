package com.movedados.witon

import android.app.Application
import com.movedados.witon.core.ServiceLocator

class WiTonApp : Application() {
    override fun onCreate() {
        super.onCreate()
        ServiceLocator.init(this)
    }
}
