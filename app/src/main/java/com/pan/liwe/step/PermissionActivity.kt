package com.pan.liwe.step

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.widget.Button
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.PermissionController
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch

class PermissionActivity : AppCompatActivity() {

    private lateinit var textStatus: TextView
    private lateinit var buttonAction: Button

    private val requestHealthPermissions =
        registerForActivityResult(PermissionController.createRequestPermissionResultContract()) { granted ->
            if (granted.containsAll(HealthConnectHelper.PERMISSIONS)) {
                onPermissionGranted()
            } else {
                textStatus.text = getString(R.string.status_need_permission)
            }
        }

    private val requestRuntimePermissions =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
            startServiceIfAllowed()
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_permission)
        textStatus = findViewById(R.id.textStatus)
        buttonAction = findViewById(R.id.buttonAction)
        buttonAction.setOnClickListener { onActionClicked() }
        refreshState()
    }

    override fun onResume() {
        super.onResume()
        refreshState()
    }

    private fun refreshState() {
        val status = HealthConnectHelper.sdkStatus(this)
        if (status != HealthConnectClient.SDK_AVAILABLE) {
            textStatus.text = getString(R.string.status_unavailable)
            buttonAction.text = getString(R.string.btn_install_health_connect)
            buttonAction.isEnabled = true
            return
        }

        lifecycleScope.launch {
            if (HealthConnectHelper.hasAllPermissions(this@PermissionActivity)) {
                onPermissionGranted()
            } else {
                textStatus.text = getString(R.string.status_need_permission)
                buttonAction.text = getString(R.string.btn_grant_and_start)
                buttonAction.isEnabled = true
            }
        }
    }

    private fun onActionClicked() {
        if (HealthConnectHelper.sdkStatus(this) != HealthConnectClient.SDK_AVAILABLE) {
            openHealthConnectInPlayStore()
        } else {
            requestHealthPermissions.launch(HealthConnectHelper.PERMISSIONS)
        }
    }

    private fun onPermissionGranted() {
        buttonAction.isEnabled = false
        textStatus.text = getString(R.string.status_ready)
        val needed = mutableListOf<String>()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            needed += Manifest.permission.POST_NOTIFICATIONS
        }
        // Android 14 起，啟動 health 型前景服務時若未持有此權限會丟 SecurityException
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.ACTIVITY_RECOGNITION) != PackageManager.PERMISSION_GRANTED
        ) {
            needed += Manifest.permission.ACTIVITY_RECOGNITION
        }

        if (needed.isEmpty()) {
            startServiceIfAllowed()
        } else {
            requestRuntimePermissions.launch(needed.toTypedArray())
        }
    }

    /**
     * Android 14 起，缺少 ACTIVITY_RECOGNITION 時 health 型前景服務一定會被系統擋下。
     * 這支 App 的 manifest 只宣告了 health 一種型別，沒有可退而求其次的合法型別，
     * 因此權限不足時完全不要啟動服務 —— 一旦呼叫了 startForegroundService()，
     * 服務就必須在時限內成功 startForeground()，否則整個 App 會被系統砍掉。
     */
    private fun startServiceIfAllowed() {
        if (!canStartHealthForegroundService()) {
            textStatus.text = getString(R.string.status_need_activity_recognition)
            buttonAction.text = getString(R.string.btn_grant_and_start)
            buttonAction.isEnabled = true
            return
        }
        ContextCompat.startForegroundService(this, Intent(this, StepGeneratorService::class.java))
        finish()
    }

    private fun canStartHealthForegroundService(): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.UPSIDE_DOWN_CAKE ||
            ContextCompat.checkSelfPermission(this, Manifest.permission.ACTIVITY_RECOGNITION) ==
            PackageManager.PERMISSION_GRANTED

    private fun openHealthConnectInPlayStore() {
        val uri = Uri.parse("market://details?id=com.google.android.apps.healthdata")
        startActivity(Intent(Intent.ACTION_VIEW, uri))
    }
}
