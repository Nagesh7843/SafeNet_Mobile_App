package com.safenet.android

import android.app.Application
import com.safenet.android.core.security.SecurityKeyStoreHelper
import com.safenet.android.data.db.SafeNetDatabaseHelper
import com.safenet.android.dpc.DpcManager
import com.safenet.android.dpc.browser.BrowserPolicyManager
import com.safenet.android.engine.vpn.SafeNetVpnService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class SafeNetApplication : Application() {

    lateinit var databaseHelper: SafeNetDatabaseHelper
        private set

    lateinit var dpcManager: DpcManager
        private set

    lateinit var browserPolicyManager: BrowserPolicyManager
        private set

    lateinit var keyStoreHelper: SecurityKeyStoreHelper
        private set

    private val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    companion object {
        lateinit var instance: SafeNetApplication
            private set
    }

    override fun onCreate() {
        super.onCreate()
        instance = this

        databaseHelper = SafeNetDatabaseHelper(this)
        dpcManager = DpcManager(this)
        browserPolicyManager = BrowserPolicyManager(this, dpcManager)
        keyStoreHelper = SecurityKeyStoreHelper()

        // Wire DNS filter engine block events to local database
        SafeNetVpnService.filterEngine.onBlockEventListener = { blockEvent ->
            applicationScope.launch {
                try {
                    databaseHelper.recordBlockEvent(blockEvent)
                } catch (e: Exception) {}
            }
        }
    }
}
