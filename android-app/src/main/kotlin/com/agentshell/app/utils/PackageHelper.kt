package com.agentshell.app.utils

import android.content.Context
import android.net.Uri
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

private const val TAG = "PackageHelper"

object PackageHelper {

    /**
     * Export app data (databases, shared prefs, agent configs) as a ZIP archive.
     * Returns the URI of the created ZIP file, or null on failure.
     */
    suspend fun exportPackage(context: Context): Uri? = withContext(Dispatchers.IO) {
        runCatching {
            val exportFile = File(context.cacheDir, "agentshell_export_${System.currentTimeMillis()}.zip")
            ZipOutputStream(exportFile.outputStream().buffered()).use { zip ->
                // Export databases
                val dbDir = context.getDatabasePath("agentshell.db").parentFile
                dbDir?.listFiles()?.forEach { dbFile ->
                    addFileToZip(zip, dbFile, "db/${dbFile.name}")
                }
                // Export shared prefs
                val prefsDir = File(context.filesDir.parent, "shared_prefs")
                prefsDir.listFiles()?.filter { it.name.startsWith("agentshell") }?.forEach { pref ->
                    addFileToZip(zip, pref, "prefs/${pref.name}")
                }
                // Export agent resource configs
                val agentConfigDir = File(context.filesDir, "agents")
                if (agentConfigDir.exists()) {
                    agentConfigDir.walkTopDown().filter { it.isFile }.forEach { f ->
                        addFileToZip(zip, f, "agents/${f.relativeTo(agentConfigDir).path}")
                    }
                }
            }
            Log.i(TAG, "Package exported to ${exportFile.absolutePath}")
            Uri.fromFile(exportFile)
        }.getOrElse { e ->
            Log.e(TAG, "Export failed", e)
            null
        }
    }

    /**
     * Import app data from a ZIP archive URI, restoring databases and configs.
     */
    suspend fun importPackage(context: Context, uri: Uri): Boolean = withContext(Dispatchers.IO) {
        runCatching {
            context.contentResolver.openInputStream(uri)?.use { inputStream ->
                ZipInputStream(inputStream.buffered()).use { zip ->
                    var entry: ZipEntry? = zip.nextEntry
                    while (entry != null) {
                        val name = entry.name
                        val target: File = when {
                            name.startsWith("db/") -> File(context.getDatabasePath("agentshell.db").parent, name.removePrefix("db/"))
                            name.startsWith("prefs/") -> File(context.filesDir.parent, "shared_prefs/${name.removePrefix("prefs/")}")
                            name.startsWith("agents/") -> File(context.filesDir, name)
                            else -> { zip.closeEntry(); entry = zip.nextEntry; continue }
                        }
                        target.parentFile?.mkdirs()
                        target.outputStream().use { zip.copyTo(it) }
                        zip.closeEntry()
                        entry = zip.nextEntry
                    }
                }
            }
            Log.i(TAG, "Package imported from $uri")
            true
        }.getOrElse { e ->
            Log.e(TAG, "Import failed", e)
            false
        }
    }

    private fun addFileToZip(zip: ZipOutputStream, file: File, entryName: String) {
        if (!file.exists()) return
        zip.putNextEntry(ZipEntry(entryName))
        file.inputStream().use { it.copyTo(zip) }
        zip.closeEntry()
    }
}
