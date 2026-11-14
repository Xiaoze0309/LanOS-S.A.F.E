package com.lanos.safe.service

import android.content.Intent
import android.net.VpnService
import android.util.Log

// VPN 服务空壳：暂不实现具体隧道逻辑，仅打印生命周期日志
class SafeVpnService : VpnService() {

    override fun onStartCommand(
        intent: Intent?,
        flags: Int,
        startId: Int
    ): Int {
        // 服务启动时打印日志
        Log.d(TAG, "onStartCommand: VPN 服务已启动")
        // 返回粘性启动，被系统杀掉后自动重启
        return START_STICKY
    }

    override fun onDestroy() {
        super.onDestroy()
        // 服务销毁时打印日志
        Log.d(TAG, "onDestroy: VPN 服务已销毁")
    }

    companion object {
        // 日志标签
        private const val TAG = "SafeVpnService"
    }
}
