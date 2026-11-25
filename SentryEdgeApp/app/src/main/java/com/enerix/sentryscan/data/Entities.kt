package com.enerix.sentryscan.data

import androidx.room.Entity
import androidx.room.PrimaryKey

/** One DNS domain observed for a given package. Local-only, never uploaded. */
@Entity(tableName = "domain_hits")
data class DomainHitEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val host: String,
    val packageName: String,
    val isTracker: Boolean,
    val timestampMs: Long
)

/** A completed scan's result, kept so the app has history without re-scanning. */
@Entity(tableName = "scan_results")
data class ScanResultEntity(
    @PrimaryKey val packageName: String,
    val appName: String,
    val category: String,
    val permissionRisk: Int,
    val networkRisk: Int,
    val domainRisk: Int,
    val behaviorRisk: Int,
    val score: Int,
    val tier: String,
    val isAnomalous: Boolean,
    val lastScannedMs: Long
)
