package jp.co.taito.groovecoasterzero

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

    }

}
