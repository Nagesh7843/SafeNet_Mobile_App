package com.safenet.android.data.db

import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import com.safenet.android.domain.model.BlockEvent
import com.safenet.android.domain.model.DomainCategory
import com.safenet.android.domain.model.SecurityEvent

class SafeNetDatabaseHelper(context: Context) : SQLiteOpenHelper(
    context, DATABASE_NAME, null, DATABASE_VERSION
) {

    companion object {
        const val DATABASE_NAME = "safenet.db"
        const val DATABASE_VERSION = 1

        // Table Names
        const val TABLE_DEVICE_CONFIG = "device_config"
        const val TABLE_PROTECTION_POLICY = "protection_policy"
        const val TABLE_DOMAIN_RULE = "domain_rule"
        const val TABLE_ALLOWLIST = "allowlist"
        const val TABLE_BLOCKLIST_VERSION = "blocklist_version"
        const val TABLE_BLOCK_EVENT = "block_event"
        const val TABLE_POLICY_AUDIT = "policy_audit"
        const val TABLE_VPN_STATUS = "vpn_status"
        const val TABLE_BROWSER_POLICY = "browser_policy"
        const val TABLE_SECURITY_EVENT = "security_event"
    }

    override fun onCreate(db: SQLiteDatabase) {
        // 1. DeviceConfig
        db.execSQL("""
            CREATE TABLE $TABLE_DEVICE_CONFIG (
                id INTEGER PRIMARY KEY,
                isDeviceOwner INTEGER NOT NULL DEFAULT 0,
                isAlwaysOnVpnConfigured INTEGER NOT NULL DEFAULT 0,
                isLockdownEnabled INTEGER NOT NULL DEFAULT 0,
                recoveryPasscodeHash TEXT,
                enforceSafeSearch INTEGER NOT NULL DEFAULT 1,
                upstreamDnsType TEXT NOT NULL DEFAULT 'CLOUDFLARE_FAMILY',
                customUpstreamIp TEXT,
                updatedAt INTEGER NOT NULL
            )
        """.trimIndent())

        // Insert initial singleton device config
        val initialConfig = ContentValues().apply {
            put("id", 1)
            put("isDeviceOwner", 0)
            put("isAlwaysOnVpnConfigured", 0)
            put("isLockdownEnabled", 0)
            put("enforceSafeSearch", 1)
            put("upstreamDnsType", "CLOUDFLARE_FAMILY")
            put("updatedAt", System.currentTimeMillis())
        }
        db.insert(TABLE_DEVICE_CONFIG, null, initialConfig)

        // 2. ProtectionPolicy
        db.execSQL("""
            CREATE TABLE $TABLE_PROTECTION_POLICY (
                category TEXT PRIMARY KEY,
                isEnabled INTEGER NOT NULL DEFAULT 1,
                action TEXT NOT NULL DEFAULT 'BLOCK_SINKHOLE',
                description TEXT,
                ruleCount INTEGER NOT NULL DEFAULT 0
            )
        """.trimIndent())

        // Seed default protection policies
        for (category in DomainCategory.values()) {
            val cv = ContentValues().apply {
                put("category", category.id)
                put("isEnabled", if (category.defaultEnabled) 1 else 0)
                put("action", "BLOCK_SINKHOLE")
                put("description", category.description)
                put("ruleCount", 0)
            }
            db.insert(TABLE_PROTECTION_POLICY, null, cv)
        }

        // 3. DomainRule
        db.execSQL("""
            CREATE TABLE $TABLE_DOMAIN_RULE (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                domain TEXT UNIQUE NOT NULL,
                category TEXT NOT NULL,
                source TEXT NOT NULL DEFAULT 'BUILTIN',
                createdAt INTEGER NOT NULL
            )
        """.trimIndent())
        db.execSQL("CREATE INDEX idx_domain_rule_domain ON $TABLE_DOMAIN_RULE (domain)")
        db.execSQL("CREATE INDEX idx_domain_rule_category ON $TABLE_DOMAIN_RULE (category)")

        // 4. Allowlist
        db.execSQL("""
            CREATE TABLE $TABLE_ALLOWLIST (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                domain TEXT UNIQUE NOT NULL,
                notes TEXT,
                createdAt INTEGER NOT NULL
            )
        """.trimIndent())
        db.execSQL("CREATE INDEX idx_allowlist_domain ON $TABLE_ALLOWLIST (domain)")

        // 5. BlocklistVersion
        db.execSQL("""
            CREATE TABLE $TABLE_BLOCKLIST_VERSION (
                versionId TEXT PRIMARY KEY,
                sourceUrl TEXT NOT NULL,
                sha256Hash TEXT NOT NULL,
                signatureValid INTEGER NOT NULL DEFAULT 1,
                domainCount INTEGER NOT NULL,
                installedAt INTEGER NOT NULL,
                isActive INTEGER NOT NULL DEFAULT 1
            )
        """.trimIndent())

        // 6. BlockEvent
        db.execSQL("""
            CREATE TABLE $TABLE_BLOCK_EVENT (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                timestamp INTEGER NOT NULL,
                domain TEXT NOT NULL,
                category TEXT NOT NULL,
                actionTaken TEXT NOT NULL,
                requesterUid INTEGER
            )
        """.trimIndent())
        db.execSQL("CREATE INDEX idx_block_event_timestamp ON $TABLE_BLOCK_EVENT (timestamp)")
        db.execSQL("CREATE INDEX idx_block_event_domain ON $TABLE_BLOCK_EVENT (domain)")

        // 7. PolicyAudit
        db.execSQL("""
            CREATE TABLE $TABLE_POLICY_AUDIT (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                timestamp INTEGER NOT NULL,
                action TEXT NOT NULL,
                details TEXT
            )
        """.trimIndent())

        // 8. VPNStatus
        db.execSQL("""
            CREATE TABLE $TABLE_VPN_STATUS (
                id INTEGER PRIMARY KEY,
                isConnected INTEGER NOT NULL DEFAULT 0,
                connectionTime INTEGER NOT NULL DEFAULT 0,
                bytesIn INTEGER NOT NULL DEFAULT 0,
                bytesOut INTEGER NOT NULL DEFAULT 0,
                queriesProcessed INTEGER NOT NULL DEFAULT 0,
                queriesBlocked INTEGER NOT NULL DEFAULT 0,
                lastError TEXT
            )
        """.trimIndent())
        val vpnCv = ContentValues().apply {
            put("id", 1)
            put("isConnected", 0)
        }
        db.insert(TABLE_VPN_STATUS, null, vpnCv)

        // 9. BrowserPolicy
        db.execSQL("""
            CREATE TABLE $TABLE_BROWSER_POLICY (
                packageName TEXT PRIMARY KEY,
                browserName TEXT NOT NULL,
                isInstalled INTEGER NOT NULL DEFAULT 0,
                supportsIncognitoRestriction INTEGER NOT NULL DEFAULT 0,
                isIncognitoRestricted INTEGER NOT NULL DEFAULT 0,
                lastAppliedTimestamp INTEGER NOT NULL DEFAULT 0
            )
        """.trimIndent())

        // 10. SecurityEvent
        db.execSQL("""
            CREATE TABLE $TABLE_SECURITY_EVENT (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                timestamp INTEGER NOT NULL,
                severity TEXT NOT NULL,
                eventType TEXT NOT NULL,
                description TEXT NOT NULL
            )
        """.trimIndent())
        db.execSQL("CREATE INDEX idx_security_event_timestamp ON $TABLE_SECURITY_EVENT (timestamp)")
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        // Versioned migrations
    }

    // Helper functions for common transactions
    fun recordBlockEvent(event: BlockEvent) {
        writableDatabase.use { db ->
            val cv = ContentValues().apply {
                put("timestamp", event.timestamp)
                put("domain", event.domain)
                put("category", event.category.id)
                put("actionTaken", event.actionTaken)
            }
            db.insert(TABLE_BLOCK_EVENT, null, cv)
        }
    }

    fun recordSecurityEvent(event: SecurityEvent) {
        writableDatabase.use { db ->
            val cv = ContentValues().apply {
                put("timestamp", event.timestamp)
                put("severity", event.severity)
                put("eventType", event.eventType)
                put("description", event.description)
            }
            db.insert(TABLE_SECURITY_EVENT, null, cv)
        }
    }

    fun getRecentBlockEvents(limit: Int = 100): List<BlockEvent> {
        val list = mutableListOf<BlockEvent>()
        readableDatabase.rawQuery(
            "SELECT id, timestamp, domain, category, actionTaken FROM $TABLE_BLOCK_EVENT ORDER BY timestamp DESC LIMIT ?",
            arrayOf(limit.toString())
        ).use { cursor ->
            while (cursor.moveToNext()) {
                val id = cursor.getLong(0)
                val ts = cursor.getLong(1)
                val domain = cursor.getString(2)
                val catStr = cursor.getString(3)
                val action = cursor.getString(4)

                val cat = DomainCategory.values().find { it.id == catStr } ?: DomainCategory.ADULT
                list.add(BlockEvent(id, ts, domain, cat, action))
            }
        }
        return list
    }

    fun getRecentSecurityEvents(limit: Int = 50): List<SecurityEvent> {
        val list = mutableListOf<SecurityEvent>()
        readableDatabase.rawQuery(
            "SELECT id, timestamp, severity, eventType, description FROM $TABLE_SECURITY_EVENT ORDER BY timestamp DESC LIMIT ?",
            arrayOf(limit.toString())
        ).use { cursor ->
            while (cursor.moveToNext()) {
                val id = cursor.getLong(0)
                val ts = cursor.getLong(1)
                val severity = cursor.getString(2)
                val type = cursor.getString(3)
                val desc = cursor.getString(4)
                list.add(SecurityEvent(id, ts, severity, type, desc))
            }
        }
        return list
    }

    fun purgeEventHistory() {
        writableDatabase.use { db ->
            db.delete(TABLE_BLOCK_EVENT, null, null)
            db.delete(TABLE_SECURITY_EVENT, "severity != 'CRITICAL'", null)
        }
    }

    fun getBlockedCount(): Long {
        readableDatabase.rawQuery("SELECT COUNT(*) FROM $TABLE_BLOCK_EVENT", null).use { cursor ->
            if (cursor.moveToFirst()) return cursor.getLong(0)
        }
        return 0L
    }
}
