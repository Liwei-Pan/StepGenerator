package com.pan.liwe.step

import android.content.Context
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.records.StepsRecord
import androidx.health.connect.client.records.metadata.Metadata
import androidx.health.connect.client.permission.HealthPermission
import java.time.Instant
import java.time.ZoneId

object HealthConnectHelper {

    val PERMISSIONS: Set<String> = setOf(
        HealthPermission.getWritePermission(StepsRecord::class)
    )

    fun sdkStatus(context: Context): Int =
        HealthConnectClient.getSdkStatus(context)

    fun isAvailable(context: Context): Boolean =
        sdkStatus(context) == HealthConnectClient.SDK_AVAILABLE

    suspend fun hasAllPermissions(context: Context): Boolean {
        val client = HealthConnectClient.getOrCreate(context)
        val granted = client.permissionController.getGrantedPermissions()
        return granted.containsAll(PERMISSIONS)
    }

    suspend fun writeSteps(context: Context, startTime: Instant, endTime: Instant, steps: Int) {
        val client = HealthConnectClient.getOrCreate(context)
        val zoneOffset = ZoneId.systemDefault().rules.getOffset(endTime)
        val record = StepsRecord(
            startTime = startTime,
            startZoneOffset = zoneOffset,
            endTime = endTime,
            endZoneOffset = zoneOffset,
            count = steps.toLong(),
            metadata = Metadata.manualEntry()
        )
        client.insertRecords(listOf(record))
    }
}
