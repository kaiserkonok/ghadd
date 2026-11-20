package org.ghadd

import android.app.Application

class App : Application() {
    override fun onCreate() {
        super.onCreate()
        Prefs.init(this)
    }
}
