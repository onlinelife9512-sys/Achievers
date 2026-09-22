package com.oble.ideacapture

import android.app.Application
import com.oble.ideacapture.service.Notifications
import com.oble.ideacapture.work.WorkScheduler
import kotlinx.coroutines.launch

class ObleApp : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
        Notifications.createChannels(this)
        // App restart / crash recovery: close sessions that never ended and process any
        // speech that was captured but not analysed yet (runs once network is available).
        container.appScope.launch { container.repository.closeDanglingSessions() }
        WorkScheduler.enqueuePendingProcessing(this)
    }
}
