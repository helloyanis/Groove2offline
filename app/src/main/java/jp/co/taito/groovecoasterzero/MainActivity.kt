// MainActivity.kt
package jp.co.taito.groovecoasterzero

import android.app.AlertDialog
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.EditText
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Button
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import jp.co.taito.groovecoasterzero.ui.theme.Groove2SetupTheme

// Zip4j imports (make sure dependency in build.gradle)
import net.lingala.zip4j.ZipFile
import net.lingala.zip4j.model.ZipParameters
import net.lingala.zip4j.model.enums.CompressionMethod
import net.lingala.zip4j.model.enums.EncryptionMethod

class MainActivity : ComponentActivity() {

    private val targetObbName = "dev.helloyanis.gc2data.obb"
    private val includedApkAssetName = "groovecoaster.apk"

    private val secondaryObbName = "main.76.jp.co.taito.groovecoasterzero.obb"
    private val secondaryObbPassword = "eiprblFFv69R83J5"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val createDocLauncher = registerForActivityResult(
            ActivityResultContracts.CreateDocument("application/octet-stream")
        ) { uri: Uri? ->
            if (uri != null) {
                lifecycleScope.launch {
                    saveApkToUriAndPromptInstall(uri)
                }
            } else {
                Toast.makeText(this, "Save cancelled", Toast.LENGTH_SHORT).show()
            }
        }

        setContent {
            Groove2SetupTheme {

                var status by remember { mutableStateOf("Initializing...") }
                var progress by remember { mutableStateOf(0f) }
                var determinate by remember { mutableStateOf(true) }
                var obbFound by remember { mutableStateOf<Boolean?>(null) }
                var extractionDone by remember { mutableStateOf(false) }
                var deletionMessage by remember { mutableStateOf<String?>(null) }
                var initialButtonsVisible by remember { mutableStateOf(true) }

                LaunchedEffect(Unit) {
                    val result: FileUtils.Source? = withContext(Dispatchers.IO) {
                        findObbSource()
                    }

                    if (result == null) {
                        obbFound = false
                        status =
                            "OBB data could not be found. Make sure the obb data is located at [Internal storage]/Android/obb/jp.co.taito.groovecoasterzero/$targetObbName"
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
                            deletionMessage = if (deleted) {
                                "Original OBB deleted."
                            } else {
                                "Original OBB could not be deleted (read-only asset or permission)."
                            }
                            status += "\n$deletionMessage"

                        } else {
                            status = "Failed to extract OBB. If you just installed the app using zarchiver, wait for 2 minutes for the file transfer to complete, then try again. If the issue persists, check if your obb files are corrupted."
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
                                LinearProgressIndicator(
                                    progress = { progress },
                                    modifier = Modifier.fillMaxWidth()
                                )
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

                        if (initialButtonsVisible) {
                            Button(onClick = {
                                initialButtonsVisible = false
                                lifecycleScope.launch {
                                    status = "Setting up offline play..."
                                    val ok = withContext(Dispatchers.IO) {
                                        setupOfflinePlayInternal(serverUrl = "http://example.com/")
                                    }
                                    if (ok) {
                                        status = "Offline setup finished successfully."
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
                                    } else {
                                        status = "Offline setup failed. See logs / toasts."
                                    }
                                }
                            }) {
                                Text("Set up for offline play")
                            }

                            Spacer(Modifier.height(8.dp))

                            Button(onClick = {
                                initialButtonsVisible = false
                                val edit = EditText(this@MainActivity)
                                edit.hint = "https://example.com/"

                                val dialog = AlertDialog.Builder(this@MainActivity)
                                    .setTitle("Server URL")
                                    .setMessage("Enter the server base URL (include http:// or https://). You will need to connect to the internet for the 1st app launch.")
                                    .setView(edit)
                                    .setPositiveButton("OK") { _, _ ->
                                        val userUrl = edit.text.toString().trim().takeIf { it.isNotEmpty() }
                                        lifecycleScope.launch {
                                            if (userUrl == null) {
                                                Toast.makeText(this@MainActivity, "No URL provided, aborting.", Toast.LENGTH_SHORT).show()
                                                return@launch
                                            }
                                            status = "Setting up for third-party server: $userUrl"
                                            val ok = withContext(Dispatchers.IO) {
                                                setupOfflinePlayInternal(serverUrl = userUrl)
                                            }
                                            if (ok) {
                                                status = "Setup for server completed."
                                                getSharedPreferences("TunePreferences", MODE_PRIVATE)
                                                    .edit {
                                                        putString(
                                                            "DATA",
                                                            """
                                                                <root>
                                                                </root>
                                                            """.trimIndent()
                                                        )
                                                    }
                                            } else {
                                                status = "Setup for server failed. See logs / toasts."
                                            }
                                        }
                                    }
                                    .setNegativeButton("Cancel") { _, _ ->
                                        Toast.makeText(this@MainActivity, "Cancelled", Toast.LENGTH_SHORT).show()
                                    }
                                    .create()
                                dialog.show()
                            }) {
                                Text("Set up for play on a third party server")
                            }

                            Spacer(Modifier.height(20.dp))
                        }

                        if (!initialButtonsVisible) {
                            Button(onClick = {
                                createDocLauncher.launch("groovecoaster.apk")
                            }) {
                                Text("Save apk and install")
                            }

                            Spacer(Modifier.height(8.dp))

                            OutlinedButton(onClick = {
                                installapkFromCache()
                            }) {
                                Text("Install apk (fallback)")
                            }

                            Spacer(Modifier.height(16.dp))
                        }

                        Text("Version 3x", style = MaterialTheme.typography.labelSmall)

                    }


                }
            }
        }
    }

    private suspend fun saveApkToUriAndPromptInstall(uri: Uri) {
        withContext(Dispatchers.IO) {
            try {
                assets.open(includedApkAssetName).use { input ->
                    contentResolver.openOutputStream(uri).use { out ->
                        if (out == null) throw IOException("Could not open output stream for uri")
                        input.copyTo(out)
                    }
                }

                withContext(Dispatchers.Main) {
                    Toast.makeText(this@MainActivity, "Apk saved. Launching installer... (Install using zarchiver if this does not work)", Toast.LENGTH_SHORT).show()
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
                    Toast.makeText(this@MainActivity, "Failed to save apk: ${e.localizedMessage}", Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    private fun installapkFromCache() {
        try {
            val cacheFile = File(cacheDir, includedApkAssetName)
            assets.open(includedApkAssetName).use { input ->
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

    private fun findObbSource(): FileUtils.Source? {
        val obbDir = this.obbDir
        val f = File(obbDir, targetObbName)
        println(f)
        return if (f.exists()) FileUtils.Source.FileSource(f) else null
    }

    private fun extractObbAsZipWithProgress(
        source: FileUtils.Source,
        destDir: File,
        progressCallback: (bytesCopied: Long, total: Long) -> Unit
    ): Boolean {
        return try {
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

    private fun setupOfflinePlayInternal(serverUrl: String): Boolean {
        try {
            val obbFile = File(this.obbDir, secondaryObbName)
            if (!obbFile.exists()) {
                runOnUiThread {
                    Toast.makeText(this, "Game's OBB not found: ${obbFile.absolutePath}", Toast.LENGTH_LONG).show()
                }
                return false
            }

            val tmpExtract = File(filesDir, "tmp_obb_extract")
            if (tmpExtract.exists()) {
                tmpExtract.deleteRecursively()
            }
            tmpExtract.mkdirs()

            try {
                val zip = ZipFile(obbFile, secondaryObbPassword.toCharArray())
                zip.extractAll(tmpExtract.absolutePath)
            } catch (e: Exception) {
                e.printStackTrace()
                runOnUiThread {
                    Toast.makeText(this, "Failed to extract the game's obb: ${e.localizedMessage}", Toast.LENGTH_LONG).show()
                }
                return false
            }

            val settingFile = findFileByNameRecursively(tmpExtract, "setting.cfg")
                ?: File(tmpExtract, "setting.cfg") // fallback to root

            val newSettingContent = """
                ######## WebView ########
                [webview]
                # 最初に開かれるURL[Debug]
                url = ""
                
                ######## Server #########
                [server]
                serverUrl = "$serverUrl"
                #serverUrl = "http://staging2.gczero.com/"
                ### php ###
                startPhp = "start.php"
                shopList = "shop_list.php"
                dataSync = "sync.php"
                infoPhp = "info.php"
                buyCoinPhp = "buy_coin.php"
                buyContentPhp = "buy_content.php"
                pointHistoryPhp = "point_history.php"
                tTagPhp = "ttag.php"
                eventPhp = "event.php"
                eventPlayPhp = "event_play.php"
                eventClearPhp = "score.php"
                eventRankPhp = "ranking.php"
                cmPlayPhp = "cm_play.php"
                confirmTierPhp = "confirm_tier.php"
                songInfoPhp = "song_info.php"
                friendPhp = "friend.php"
                recommendPhp = "recommend.php"
                resultPhp = "result.php"
                savePhp = "save.php"
                loadPhp = "load.php"
                transferPhp = "transfer.php"
                rulrPhp = "rule.php"
                historyPhp = "history.php"
                briefingPhp = "briefing.php"
                mission_clearPhp = "mission_clear.php"
                missionPhp = "mission.php"
                statusPhp = "status.php"
                
                #SHOP周り改修時に追加
                buyByPurchasePhp = "buy_by_purchase.php"
                buyByCoinPhp = "buy_by_coin.php"
                webShopPhp = "web_shop.php"
                webShopDetailPhp = "web_shop_detail.php"
                webShopResultPhp = "web_shop_result.php"
                restorePhp = "restore.php"
                
                #バラエティパック追加時に追加
                webShopVarietySelectPhp = "web_shop_variety_select.php"
                webShopVarietyConfirmPhp = "web_shop_variety_confirm.php"
                
                #ログインボーナス実装時に追加
                loginBonusPhp = "login_bonus.php"
                
                #プレゼント機能実装時に追加
                receivePresentPhp = "receive_present.php"
                
                #GP再実装時に追加
                buyByGpointPhp = "buy_by_gpoint.php"
                gpointDescriptionPhp = "gpoint_description.php"
                grantGpointPhp = "grant_gpoint.php"
                
                #アカウント削除追加
                deleteAccountPhp = "delete_account.php"
                
                #実験
                shopWebListPhp = "shop_web/shop_web_list.php"
                ######## APP ###########
                [application]
                versionFile = "/Library/Caches/version2.cfg"
                ######## カスタムURLスキーム ######
                [customUrlScheme]
                #旧有料配信版　GrooveCorster　のカスタムURLスキーム
                id = "jp.co.taito.groovecoaster"
                urlScheme = "jp.co.taito.groovecoaster"
            """.trimIndent()

            try {
                settingFile.parentFile?.mkdirs()
                settingFile.writeText(newSettingContent)
            } catch (e: Exception) {
                e.printStackTrace()
                runOnUiThread {
                    Toast.makeText(this, "Failed to write setting.cfg: ${e.localizedMessage}", Toast.LENGTH_LONG).show()
                }
            }

            val tmpZip = File(obbFile.parentFile, obbFile.name + ".tmp")
            if (tmpZip.exists()) tmpZip.delete()

            try {
                val zipFile = ZipFile(tmpZip, secondaryObbPassword.toCharArray())

                val filesToAdd = tmpExtract.walkTopDown().filter { it.isFile }.toList()

                for (file in filesToAdd) {
                    val relative = file.relativeTo(tmpExtract).path.replace(File.separatorChar, '/')
                    val params = ZipParameters().apply {
                        compressionMethod = CompressionMethod.DEFLATE
                        isEncryptFiles = true
                        encryptionMethod = EncryptionMethod.ZIP_STANDARD
                        fileNameInZip = relative
                    }
                    zipFile.addFile(file, params)
                }

            } catch (e: Exception) {
                e.printStackTrace()
                runOnUiThread {
                    Toast.makeText(this, "Failed to create updated obb zip: ${e.localizedMessage}", Toast.LENGTH_LONG).show()
                }
                return false
            }

            try {
                val deleted = obbFile.delete()
                if (!deleted) {
                    runOnUiThread {
                        Toast.makeText(this, "Warning: original obb could not be deleted; attempting overwrite", Toast.LENGTH_SHORT).show()
                    }
                }
                val renamed = tmpZip.renameTo(obbFile)
                if (!renamed) {
                    tmpZip.copyTo(obbFile, overwrite = true)
                    tmpZip.delete()
                }
            } catch (e: Exception) {
                e.printStackTrace()
                runOnUiThread {
                    Toast.makeText(this, "Failed to replace original obb: ${e.localizedMessage}", Toast.LENGTH_LONG).show()
                }
                return false
            } finally {
                try {
                    tmpExtract.deleteRecursively()
                } catch (_: Exception) { }
            }

            return true

        } catch (e: Exception) {
            e.printStackTrace()
            runOnUiThread {
                Toast.makeText(this, "Unexpected error: ${e.localizedMessage}", Toast.LENGTH_LONG).show()
            }
            return false
        }
    }

    private fun findFileByNameRecursively(root: File, name: String): File? {
        if (!root.exists()) return null
        if (root.isFile && root.name.equals(name, ignoreCase = true)) return root
        if (root.isDirectory) {
            root.listFiles()?.forEach { f ->
                val found = findFileByNameRecursively(f, name)
                if (found != null) return found
            }
        }
        return null
    }
}
