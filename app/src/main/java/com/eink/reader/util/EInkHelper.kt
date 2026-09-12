package com.eink.reader.util

import android.content.Context
import android.content.Intent
import android.view.View
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

object EInkHelper {

    /**
     * Kích hoạt làm mới phần cứng màn hình E-Ink (Dành cho dòng máy Onyx Boox Palma, Page, Poke...).
     * Sử dụng Reflection an toàn để không yêu cầu thêm file thư viện ngoài lúc compile.
     */
    fun refreshHardwareEpd(view: View? = null, context: Context? = null): Boolean {
        var success = false

        // 1. Thử gọi EpdController của Onyx SDK qua Reflection
        try {
            val epdClass = Class.forName("com.onyx.android.sdk.api.device.epd.EpdController")
            val updateModeClass = Class.forName("com.onyx.android.sdk.api.device.epd.UpdateMode")

            val gcMode = try {
                updateModeClass.getField("GC16").get(null)
            } catch (e: Exception) {
                try {
                    updateModeClass.getField("GC").get(null)
                } catch (e2: Exception) {
                    null
                }
            }

            if (view != null && gcMode != null) {
                val refreshMethod = epdClass.getMethod("refreshScreen", View::class.java, updateModeClass)
                refreshMethod.invoke(null, view, gcMode)
                success = true
            }
        } catch (ignored: Throwable) {}

        // 2. Thử phát Broadcast Action của các hãng máy E-Ink Android (Boox, Bigme, Meebook/Boyue, Hisense...)
        try {
            val broadcastContext = context ?: view?.context
            broadcastContext?.let { ctx ->
                val refreshIntents = listOf(
                    "android.intent.action.SCREEN_REFRESH",
                    "action.onyx.screen.refresh",
                    "com.bigme.action.REFRESH_SCREEN",
                    "com.bigme.refresh",
                    "action.bigme.refresh",
                    "com.eink.action.REFRESH",
                    "com.boyue.action.REFRESH_SCREEN",
                    "android.intent.action.EINK_REFRESH",
                    "com.eink.refresh"
                )
                for (act in refreshIntents) {
                    try {
                        ctx.sendBroadcast(Intent(act))
                    } catch (ignored: Throwable) {}
                }
                success = true
            }
        } catch (ignored: Throwable) {}

        return success
    }

    /**
     * Nhận diện xem thiết bị có phải là dòng máy đọc sách Bigme (như B751C, B751C S...) hay không.
     */
    fun isBigmeDevice(): Boolean {
        val manufacturer = android.os.Build.MANUFACTURER ?: ""
        val brand = android.os.Build.BRAND ?: ""
        val model = android.os.Build.MODEL ?: ""
        return manufacturer.contains("bigme", ignoreCase = true) ||
               brand.contains("bigme", ignoreCase = true) ||
               model.contains("b751", ignoreCase = true) ||
               model.contains("bigme", ignoreCase = true)
    }

    /**
     * Kích hoạt khử bóng ma (Ghosting Elimination) toàn diện:
     * Kết hợp gọi phần cứng (nếu có) + chớp nháy màu phần mềm (Đen -> Trắng)
     * để đảo các hạt mực e-ink micro-capsules, trả lại nền trắng tinh khiết.
     */
    fun triggerFullEInkRefresh(
        scope: CoroutineScope,
        view: View? = null,
        context: Context? = null,
        onFlashStateChange: (Boolean) -> Unit
    ) {
        // Gọi lệnh phần cứng trước
        refreshHardwareEpd(view, context)

        // Chớp nháy phần mềm đảo hạt mực
        scope.launch {
            onFlashStateChange(true)
            delay(100)
            onFlashStateChange(false)
        }
    }
}
