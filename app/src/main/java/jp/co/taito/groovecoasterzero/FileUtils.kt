package jp.co.taito.groovecoasterzero

import android.content.res.AssetManager
import java.io.*

object FileUtils {

    // We support two kinds of sources:
    sealed class Source {
        abstract fun openStream(): InputStream
        abstract fun length(): Long
        abstract fun describe(): String
        abstract fun tryDelete(): Boolean

        data class FileSource(val file: java.io.File) : Source() {
            override fun openStream(): InputStream = FileInputStream(file)
            override fun length(): Long = file.length()
            override fun describe(): String = file.absolutePath
            override fun tryDelete(): Boolean = file.delete()
        }

        data class AssetSource(val assetPath: String, val resources: AssetManager) : Source() {
            override fun openStream(): InputStream = resources.open(assetPath)
            override fun length(): Long {
                return try {
                    // openFd only works for uncompressed assets
                    val afd = resources.openFd(assetPath)
                    val len = afd.length
                    afd.close()
                    len
                } catch (e: Exception) {
                    -1L
                }
            }

            override fun describe(): String = "asset:$assetPath"
            override fun tryDelete(): Boolean = false // cannot delete assets inside the APK
        }
    }

    // Copy source to destination with progress callback
    // progressCallback(bytesCopied, totalBytes) where totalBytes may be <=0 if unknown
    fun copyWithProgress(
        source: Source,
        dest: File,
        progressCallback: ((Long, Long) -> Unit)?
    ): Boolean {
        val tmp = File(dest.parentFile, dest.name + ".tmp")
        try {
            var total = source.length()
            source.openStream().use { input ->
                FileOutputStream(tmp).use { out ->
                    val buf = ByteArray(64 * 1024)
                    var read: Int
                    var copied = 0L
                    while (true) {
                        read = input.read(buf)
                        if (read <= 0) break
                        out.write(buf, 0, read)
                        copied += read.toLong()
                        progressCallback?.invoke(copied, total)
                    }
                    out.flush()
                }
            }
            // atomically rename
            if (tmp.exists()) {
                tmp.renameTo(dest)
            }
            return true
        } catch (e: Exception) {
            e.printStackTrace()
            tmp.delete()
            return false
        }
    }
}
