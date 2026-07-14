package com.apptolast.checkoutkmp.di

import com.apptolast.checkoutkmp.data.di.dataModule
import com.apptolast.checkoutkmp.domain.di.domainModule
import org.koin.core.context.startKoin
import org.koin.dsl.KoinAppDeclaration

/**
 * Starts Koin with every shared module. Platform hosts pass their own configuration (e.g. Android's
 * `androidContext`) through [config]; iOS just calls `initKoin()`.
 */
fun initKoin(config: KoinAppDeclaration? = null) = startKoin {
    config?.invoke(this)
    modules(domainModule, dataModule, presentationModule)
}
