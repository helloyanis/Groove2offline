package jp.co.taito.groovecoasterzero


import android.app.Activity
import android.content.ContentValues
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import jp.co.taito.groovecoasterzero.FileUtils
import android.provider.MediaStore
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Button
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.*
import androidx.core.content.edit

class MainActivity : ComponentActivity() {

    private val targetObbName = "dev.helloyanis.gc2data.obb"
    private val includedXapkAssetName = "groovecoaster.xapk"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // document-creation launcher: user chooses where to save the xapk
        val createDocLauncher = registerForActivityResult(
            ActivityResultContracts.CreateDocument("application/octet-stream")
        ) { uri: Uri? ->
            if (uri != null) {
                lifecycleScope.launch {
                    saveXapkToUriAndPromptInstall(uri)
                }
            } else {
                Toast.makeText(this, "Save cancelled", Toast.LENGTH_SHORT).show()
            }
        }

        setContent {
            var status by remember { mutableStateOf("Initializing...") }
            var progress by remember { mutableStateOf(0f) }
            var determinate by remember { mutableStateOf(true) }
            var obbFound by remember { mutableStateOf<Boolean?>(null) } // null = still checking
            var extractionDone by remember { mutableStateOf(false) }
            var canDeleteOriginal by remember { mutableStateOf(false) }
            var deletionMessage by remember { mutableStateOf<String?>(null) }
            val sharedPref = getSharedPreferences("TunePreferences", MODE_PRIVATE)

            LaunchedEffect(Unit) {
                initSharedPrefs()
                // Try to find the OBB (search order: internal files dir, external files dir, assets)
                val result: FileUtils.Source? = withContext(Dispatchers.IO) {
                    findObbSource()
                }



                if (result == null) {
                    obbFound = false
                    status = "OBB data could not be found. Make sure the obb data is located at [Internal storage]/Android/obb/jp.co.taito.groovecoasterzero/$targetObbName"
                } else {
                    obbFound = true
                    status = "Found OBB at: ${result.describe()}. Starting extraction..."
                    // start extraction / copy
                    val destDir = File(filesDir, "")
                    if (!destDir.exists()) destDir.mkdirs()

                    val extractResult = withContext(Dispatchers.IO) {
                        extractObbAsZipWithProgress(
                            source = result,
                            destDir = destDir,
                            progressCallback = { bytesCopied, total ->
                                if (total > 0L) {
                                    determinate = true
                                    val frac = bytesCopied.toFloat() / total.toFloat()
                                    progress = frac.coerceIn(0f, 1f)
                                } else {
                                    determinate = false
                                    progress = 0f
                                }
                            }
                        )
                    }

                    if (extractResult) {
                        extractionDone = true
                        status = "Extraction complete: ${destDir.absolutePath}"

                        val deleted = result.tryDelete()
                        canDeleteOriginal = deleted
                        deletionMessage = if (deleted) {
                            "Original OBB deleted."
                        } else {
                            "Original OBB could not be deleted (read-only asset or permission)."
                        }
                        status += "\n$deletionMessage"

                    } else {
                        status = "Failed to extract OBB."
                    }
                }

            }

            Surface(modifier = Modifier.fillMaxSize()) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text("Groove Coaster 2 setup", style = MaterialTheme.typography.labelMedium)
                    Spacer(Modifier.height(12.dp))
                    Text(status)
                    Spacer(Modifier.height(12.dp))

                    if (obbFound == true && !extractionDone) {
                        if (determinate) {
                            LinearProgressIndicator(progress = {progress}, modifier = Modifier.fillMaxWidth())
                            val percent = (progress * 100).toInt()
                            Spacer(Modifier.height(8.dp))
                            Text("Progress: $percent%")
                        } else {
                            LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                            Spacer(Modifier.height(8.dp))
                            Text("Progress: copying... (size unknown)")
                        }
                    }

                    Spacer(Modifier.height(20.dp))

                    // Always show the "Save XAPK" button so user can save/install XAPK even if OBB is not found
                    Button(onClick = {
                        // start create document flow - Ask user where to save
                        createDocLauncher.launch("groovecoaster.xapk")
                    }) {
                        Text("Save XAPK and install")
                    }

                    Spacer(Modifier.height(8.dp))

                    // secondary button: if user cancelled saving, this will attempt to install from cache file we create
                    Button(onClick = {
                        // fallback: copy xapk into cache and try to install directly
                        installXapkFromCache()
                    }) {
                        Text("Install XAPK (fallback)")
                    }

                    Spacer(Modifier.height(16.dp))

                    if (obbFound == false) {
                        Text("OBB data could not be found If the data was not copied earlier, this should not be happening. You can still save/install the XAPK below.")
                    }
                }
            }
        }
    }

    private suspend fun saveXapkToUriAndPromptInstall(uri: Uri) {
        withContext(Dispatchers.IO) {
            try {
                assets.open(includedXapkAssetName).use { input ->
                    contentResolver.openOutputStream(uri).use { out ->
                        if (out == null) throw IOException("Could not open output stream for uri")
                        input.copyTo(out)
                    }
                }

                withContext(Dispatchers.Main) {
                    Toast.makeText(this@MainActivity, "XAPK saved. Launching installer... (Install using zarchiver if this does not work)", Toast.LENGTH_SHORT).show()
                    // Launch installer for saved file
                    val mime = "application/vnd.android.package-archive"
                    val installIntent = Intent(Intent.ACTION_VIEW).apply {
                        setDataAndType(uri, mime)
                        flags = Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK
                    }
                    startActivity(installIntent)
                }

            } catch (e: Exception) {
                e.printStackTrace()
                withContext(Dispatchers.Main) {
                    Toast.makeText(this@MainActivity, "Failed to save XAPK: ${e.localizedMessage}", Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    private fun installXapkFromCache() {
        try {
            // copy asset to cache
            val cacheFile = File(cacheDir, includedXapkAssetName)
            assets.open(includedXapkAssetName).use { input ->
                FileOutputStream(cacheFile).use { out -> input.copyTo(out) }
            }

            val uri = androidx.core.content.FileProvider.getUriForFile(this, "$packageName.fileprovider", cacheFile)
            val mime = "application/vnd.android.package-archive"
            val installIntent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, mime)
                flags = Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK
            }
            startActivity(installIntent)

        } catch (e: Exception) {
            e.printStackTrace()
            Toast.makeText(this, "Failed to install fallback: ${e.localizedMessage}", Toast.LENGTH_LONG).show()
        }
    }

    // Finds the OBB source
    private fun findObbSource(): FileUtils.Source? {
        val obbDir = this.obbDir  // Correct system API
        val f = File(obbDir, targetObbName)
        println(f)
        return if (f.exists()) FileUtils.Source.FileSource(f) else null
    }

    private fun initSharedPrefs() {
        getSharedPreferences("TunePreferences", MODE_PRIVATE)
            .edit {
                putString(
                    "DATA",
                    """
                        <root>
                            <GCZ_FORCE_SEND_STAGECLEARDATA>2.0.12</GCZ_FORCE_SEND_STAGECLEARDATA>
                            <GCZ_LAST_STARTPHP_CONNECT_APP_VER>2.0.12</GCZ_LAST_STARTPHP_CONNECT_APP_VER>
                            <UUID>bbeb37ddc2284cae84bdc04c126662d00f5e45c532cc47e89b0de5bf0fc01386</UUID>
                        </root>
                    """.trimIndent()
                )
            }

    }

    private fun initCache() {

    }

    private fun extractObbAsZipWithProgress(
        source: FileUtils.Source,
        destDir: File,
        progressCallback: (bytesCopied: Long, total: Long) -> Unit
    ): Boolean {
        return try {
            // Obtain input stream from the source (your FileUtils.Source abstraction)
            val inputStream = source.openStream()
            val totalBytes = source.length()
            var bytesRead = 0L

            java.util.zip.ZipInputStream(BufferedInputStream(inputStream)).use { zis ->
                var entry = zis.nextEntry
                val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                while (entry != null) {
                    val newFile = File(destDir, entry.name)

                    if (entry.isDirectory) {
                        newFile.mkdirs()
                    } else {
                        // ensure parent dirs
                        newFile.parentFile?.mkdirs()
                        FileOutputStream(newFile).use { fos ->
                            var count: Int
                            while (zis.read(buffer).also { count = it } != -1) {
                                fos.write(buffer, 0, count)
                                bytesRead += count
                                progressCallback(bytesRead, totalBytes)
                            }
                        }
                    }

                    zis.closeEntry()
                    entry = zis.nextEntry
                }
            }
            true
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }


    // quick recursive asset listing
    private fun listAllAssets(path: String): List<String> {
        val out = mutableListOf<String>()
        try {
            val items = assets.list(path) ?: return out
            for (name in items) {
                val full = if (path.isEmpty()) name else "$path/$name"
                val sub = assets.list(full)
                if (sub == null || sub.isEmpty()) {
                    out.add(name) // only the filename
                } else {
                    out.addAll(listAllAssets(full))
                }
            }
        } catch (_: Exception) { }
        return out
    }
}
