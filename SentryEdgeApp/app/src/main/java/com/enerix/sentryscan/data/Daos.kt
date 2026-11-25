package com.enerix.sentryscan.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface DomainHitDao {
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(hit: DomainHitEntity)

    @Query("SELECT * FROM domain_hits WHERE packageName = :packageName ORDER BY timestampMs DESC")
    suspend fun hitsFor(packageName: String): List<DomainHitEntity>

    @Query("SELECT DISTINCT packageName FROM domain_hits")
    suspend fun packagesWithTraffic(): List<String>

    @Query("SELECT DISTINCT host FROM domain_hits ORDER BY host")
    suspend fun distinctHosts(): List<String>

    @Query("DELETE FROM domain_hits WHERE timestampMs < :olderThanMs")
    suspend fun pruneOlderThan(olderThanMs: Long)
}

@Dao
interface ScanResultDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(result: ScanResultEntity)

    @Query("SELECT * FROM scan_results ORDER BY score DESC")
    fun observeAll(): Flow<List<ScanResultEntity>>

    @Query("SELECT * FROM scan_results WHERE packageName = :packageName")
    suspend fun get(packageName: String): ScanResultEntity?
}
