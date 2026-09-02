package com.yungmoolah.converter

import android.app.Application
import android.util.Log
import androidx.work.Configuration
import com.yungmoolah.converter.data.RatesApi
import com.yungmoolah.converter.data.RatesRepository
import com.yungmoolah.converter.data.RatesStore
import com.yungmoolah.converter.work.RefreshWorker

/**
 * Owns the single repository instance and arms background refresh.
 *
 * The graph is small enough that hand-wiring here is clearer than a DI framework.
 *
 * Implements [Configuration.Provider] because the manifest opts out of
 * WorkManager's automatic startup initializer: that keeps WorkManager off the
 * cold-start path until something actually asks for it.
 */
class MoolahApplication : Application(), Configuration.Provider {

    val repository: RatesRepository by lazy {
        RatesRepository(store = RatesStore.create(this), api = RatesApi())
    }

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setMinimumLoggingLevel(if (BuildConfig.DEBUG) Log.INFO else Log.ERROR)
            .build()

    override fun onCreate() {
        super.onCreate()
        RefreshWorker.schedule(this)
    }
}
