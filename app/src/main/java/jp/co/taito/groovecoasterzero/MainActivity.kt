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
                    "&lt;root&gt;&#10;    &lt;GCZ_FORCE_SEND_STAGECLEARDATA&gt;2.0.12&lt;/GCZ_FORCE_SEND_STAGECLEARDATA&gt;&#10;    &lt;GCZ_LAST_STARTPHP_CONNECT_APP_VER&gt;2.0.12&lt;/GCZ_LAST_STARTPHP_CONNECT_APP_VER&gt;&#10;    &lt;UUID&gt;bbeb37ddc2284cae84bdc04c126662d00f5e45c532cc47e89b0de5bf0fc01386&lt;/UUID&gt;&#10;&lt;/root&gt;&#10;"
                )
            }
        getSharedPreferences("com.google.android.gms.appid", MODE_PRIVATE)
            .edit {
                putString(
                    "|S|cre",
                    "1763201846278"
                )
                putString(
                    "|S||P|",
                    "MIIBIjANBgkqhkiG9w0BAQEFAAOCAQ8AMIIBCgKCAQEA9kpzKmxruAQhsn9VMlITRb7oRzs5M3RGoJCFrj6yFTTaeSB4bLOSKpnGSelZJMo5DJVkwFDVNrk2JGubgZzM7_yYa13gprOpi1-Bju7BE_v8vKoZDBwjLAH1rSXYNai3R5YQ5Q7hsXkNWlelKoTGsJDxiNmUzVB5o8DrRSV8qg3hTTn9IIRk0eVTSOGo1_HmRfuARu_zByd0hKBOpdpwFnO5rB-_GmluN3YYmrPyK-KbPzUwHxNIT9uv-qvyRJGxzGng_BpTo0TUFtzhSfVeu7RMVs-gGv7aUtzkaJRukXyMu1jCH3yPw-susMprAhA9AKM76YA-7IXhvD_jRXeBbwIDAQAB"
                )
                putString(
                    "|T|983165573106|*",
                    "{&quot;token&quot;:&quot;cE8QjnMsw-0:APA91bGjaiK29wcWUVoF6y3roWRPUi41JcXQaHR3Y1taF7lIANzjevr-OiIBtGKLnxQ9xybNxv0tYjTlZh_KZNH1cvZjNR9xqziRTUCVbTJwh-j-fY7QJOE&quot;,&quot;appVersion&quot;:&quot;76&quot;,&quot;timestamp&quot;:1763201848412}</string>"
                )
                putString(
                    "|S||K|",
                    "MIIEvgIBADANBgkqhkiG9w0BAQEFAASCBKgwggSkAgEAAoIBAQD2SnMqbGu4BCGyf1UyUhNFvuhHOzkzdEagkIWuPrIVNNp5IHhss5IqmcZJ6VkkyjkMlWTAUNU2uTYka5uBnMzv_JhrXeCms6mLX4GO7sET-_y8qhkMHCMsAfWtJdg1qLdHlhDlDuGxeQ1aV6UqhMawkPGI2ZTNUHmjwOtFJXyqDeFNOf0ghGTR5VNI4ajX8eZF-4BG7_MHJ3SEoE6l2nAWc7msH78aaW43dhias_Ir4ps_NTAfE0hP26_6q_JEkbHMaeD8GlOjRNQW3OFJ9V67tExWz6Aa_tpS3ORolG6RfIy7WMIffI_D6y6wymsCED0AozvpgD7sheG8P-NFd4FvAgMBAAECggEAHz0dM2XSGeKIRaQp8bqAUEnhG1vTKwgWBzqdghbYrqDoDxIDxEHYice8Y1aSJHzz1HlEcLIwAQNn7yGo9T0mr0_aI1AidPJ30EI6ZB87ZSYCjgmDKMqO2X-cIiyZKmEucgmCNhN3o_OHMozIWcbCjtWrlSCH46zP6OnIzZnuIIFnfgEu0MT4hrcrNFBwgJHb00xtcOxZpdu6KrX3xYO_d54MfhaSVJo-h5YqD2t4nAmMiWf4T8kav6CsUdNgL3I0uBnJ4sAVMZFeoCK1XjHdEZpzv42FpgPh279k_Fa0anfNPO3kZV2bZKjMS6AQz0zOCHY_BfQ6zDg-pFAWN8M7NQKBgQD8ATOfYip5swdfyYpgLltspzToJamBZJsOnVDvRTleMjbDGP2QL8kLDvSFKINd8-DFcpuaXEVzAjpwW8fLaOPEHC9JMQUcRLBfXjIsZL0__yzpX5Jxw6qByrcbiSorZPu3mCT4uy0tMYE-MllJaAk0_9aJRPlwHVIYzhJM8WLGcwKBgQD6Mg6_nizKr4Oz-qm_maOk3qgFVzj3z5-pKoU0zRh4azx7gXgvtj0QHo2BWaizpZfoGBFZCWyTRHB7rMJd_X9zYlPgsEZqjKZ3WrXW8cb6rJc8eCmKr0zV00zENxaYMvLSQDl1bmlS05MKFqz8ufXd-PARkXiKhMO_66VLT8VeFQKBgQChAeOJoZ6hwtCjUpEmgnfHI82ZxPZXxX-MBtb_CKtuk4aJgB4BUYaRmiyAJzJHhNnHTUI9jVaR9IqB3yH3xDxBwAA2MyugtAI77GMCGhsQGGkJchaOuQTniC0VWr2mnA53bq2wfWaPyWFZ67FARUgcpJjde0QjbZhWYNMwdck2IQKBgAi__2wMKBzejoiY157vzJ1TfCTTrBZemILeDdKO6bAsb-0R1hY1FWWe6-v-Krw9qlZfoRuwDLAJ0LVCkXmgB_kNE0nkYFIRoTDDZ2ChDAhwSMnAmhNTlihUP3cNRikEfyGDRX8p4V0YMShFKr-b8VFWB29V2xVdF0t6_kjn_UsRAoGBALrzTo3TWa3zN-nFVYOrokKA6lMGy3Re8RaNgzZ1AXTOkNTGS4NV1u3XMW9LQ1eYVhPKu4cEUXWsr58DsJMnczn8kF8MmLbSjgANkIvJy5fpP2JRbwUF_VK6pb0Qr9ONnRX4Kw1diqpX7iQeB6_prrK_wI0vW1lqvrl6rAYwDCjF"
                )
            }

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
