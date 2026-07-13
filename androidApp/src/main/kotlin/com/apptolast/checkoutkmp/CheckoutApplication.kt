package com.apptolast.checkoutkmp

import android.app.Application
import com.apptolast.checkoutkmp.data.di.dataModule
import com.apptolast.checkoutkmp.di.presentationModule
import com.apptolast.checkoutkmp.domain.di.domainModule
import org.koin.android.ext.koin.androidContext
import org.koin.core.context.startKoin

class CheckoutApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        startKoin {
            androidContext(this@CheckoutApplication)
            modules(domainModule, dataModule, presentationModule)
        }
    }
}
