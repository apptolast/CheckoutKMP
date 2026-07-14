package com.apptolast.checkoutkmp

import android.app.Application
import com.apptolast.checkoutkmp.di.initKoin
import org.koin.android.ext.koin.androidContext

class CheckoutApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        initKoin {
            androidContext(this@CheckoutApplication)
        }
    }
}
