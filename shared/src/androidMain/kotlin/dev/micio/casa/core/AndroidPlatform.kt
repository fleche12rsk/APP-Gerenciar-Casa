package dev.micio.casa.core

import android.content.Context
import android.content.Intent
import android.net.Uri

class AndroidPlatformActions(private val context: Context) : PlatformActions {

    override val name = "Android"

    override fun openAlexaApp() = openApp("com.amazon.dee.app")

    /** Câmeras solares do iCSee que não aceitam RTSP só acordam pelo app do fabricante. */
    override fun openIcseeApp() = openApp("com.xm.csee")

    override fun shareText(text: String) {
        val send = Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, text)
        runCatching { context.startActivity(Intent.createChooser(send, "Enviar código").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
    }

    private fun openApp(packageName: String) {
        val intent = context.packageManager.getLaunchIntentForPackage(packageName)
            ?: Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=$packageName"))
        runCatching { context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
    }
}
