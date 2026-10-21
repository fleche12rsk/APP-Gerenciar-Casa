package dev.micio.casa.core

import kotlinx.cinterop.BetaInteropApi
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.usePinned
import platform.Foundation.NSApplicationSupportDirectory
import platform.Foundation.NSData
import platform.Foundation.NSDataWritingAtomic
import platform.Foundation.NSDataWritingFileProtectionCompleteUntilFirstUserAuthentication
import platform.Foundation.NSFileManager
import platform.Foundation.NSSearchPathForDirectoriesInDomains
import platform.Foundation.NSString
import platform.Foundation.NSURL
import platform.Foundation.NSUTF8StringEncoding
import platform.Foundation.NSUserDomainMask
import platform.Foundation.create
import platform.Foundation.stringWithContentsOfFile
import platform.Foundation.writeToFile
import platform.UIKit.UIApplication

/**
 * Configuração num arquivo do app com proteção de dados do iOS: fica cifrado pelo
 * sistema e inacessível a outros apps e enquanto o aparelho não for desbloqueado.
 */
@OptIn(ExperimentalForeignApi::class, BetaInteropApi::class)
class FileConfigStorage : ConfigStorage {

    private val path: String by lazy {
        val dir = NSSearchPathForDirectoriesInDomains(NSApplicationSupportDirectory, NSUserDomainMask, true).first() as String
        NSFileManager.defaultManager.createDirectoryAtPath(dir, withIntermediateDirectories = true, attributes = null, error = null)
        "$dir/config.json"
    }

    override fun read(): String? = NSString.stringWithContentsOfFile(path, NSUTF8StringEncoding, null)

    override fun write(text: String) {
        val bytes = text.encodeToByteArray()
        val data = bytes.usePinned { NSData.create(bytes = it.addressOf(0), length = bytes.size.toULong()) }
        data.writeToFile(path, NSDataWritingAtomic or NSDataWritingFileProtectionCompleteUntilFirstUserAuthentication, null)
    }
}

class IosPlatformActions : PlatformActions {

    override val name = "iOS"

    override fun openAlexaApp() = open("alexa://", APP_STORE_ALEXA)

    /** O iCSee não publica um link próprio; cai na busca da App Store se não estiver instalado. */
    override fun openIcseeApp() = open("icsee://", APP_STORE_SEARCH_ICSEE)

    private fun open(url: String, fallback: String) {
        val app = UIApplication.sharedApplication
        val target = NSURL.URLWithString(url)
        val destination = if (target != null && app.canOpenURL(target)) target else NSURL.URLWithString(fallback)
        destination?.let { app.openURL(it, options = emptyMap<Any?, Any>(), completionHandler = null) }
    }

    private companion object {
        const val APP_STORE_ALEXA = "itms-apps://apps.apple.com/app/id944011620"
        const val APP_STORE_SEARCH_ICSEE = "itms-apps://search.itunes.apple.com/WebObjects/MZSearch.woa/wa/search?media=software&term=iCSee"
    }
}
