package com.eink.reader.util

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log

/**
 * Lắng nghe sự kiện hệ thống khi ứng dụng vừa được cập nhật phiên bản mới đè lên phiên bản cũ.
 * Tự động quét và xóa sạch toàn bộ các file APK tải về tạm thời.
 */
class AppUpgradeReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        if (intent?.action == Intent.ACTION_MY_PACKAGE_REPLACED) {
            Log.d("AppUpgradeReceiver", "Ứng dụng vừa được nâng cấp, tiến hành dọn dẹp file APK cài đặt...")
            AppUpdateHelper.cleanupUpdateApks(context)
        }
    }
}
