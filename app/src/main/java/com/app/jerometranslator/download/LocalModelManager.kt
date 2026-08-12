package com.app.jerometranslator.download

import android.content.Context
import android.content.SharedPreferences
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.util.UUID

/**
 * Manages local GGUF model files imported from device storage via SAF.
 */
class LocalModelManager(private val context: Context) {

    private val prefs: SharedPreferences =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    private val MIN_MODEL_SIZE = 1_000_000L

    val localModelDir: File
        get() = File(context.getExternalFilesDir(null), LOCAL_DIR).also {
            if (!it.exists()) it.mkdirs()
        }

    data class LocalModel(
        val id: String,
        val displayName: String,
        val filename: String,
        val absolutePath: String,
        val sizeBytes: Long,
    )

    suspend fun importModel(uri: Uri, suggestedName: String? = null): LocalModel? =
        withContext(Dispatchers.IO) {
            try {
                val inputStream = context.contentResolver.openInputStream(uri) ?: return@withContext null
                var filename = suggestedName ?: uri.lastPathSegment ?: "local_model.gguf"
                if (!filename.lowercase().endsWith(".gguf")) filename += ".gguf"
                val destFile = File(localModelDir, filename)
                FileOutputStream(destFile).use { out ->
                    inputStream.use { inp -> inp.copyTo(out, bufferSize = 8192) }
                }
                val size = destFile.length()
                if (size < MIN_MODEL_SIZE) { destFile.delete(); return@withContext null }
                val model = LocalModel(
                    id = UUID.randomUUID().toString(),
                    displayName = filename.removeSuffix(".gguf").replace("-", " ").replace("_", " "),
                    filename = filename,
                    absolutePath = destFile.absolutePath,
                    sizeBytes = size,
                )
                saveModel(model)
                model
            } catch (e: Exception) { e.printStackTrace(); null }
        }

    fun listModels(): List<LocalModel> {
        val json = prefs.getString(KEY_MODELS, "[]") ?: "[]"
        return try {
            val arr = JSONArray(json)
            (0 until arr.length()).map { i ->
                val o = arr.getJSONObject(i)
                LocalModel(o.getString("id"), o.getString("displayName"),
                    o.getString("filename"), o.getString("absolutePath"), o.getLong("sizeBytes"))
            }.filter { File(it.absolutePath).exists() }
        } catch (e: Exception) { emptyList() }
    }

    fun removeModel(id: String) {
        val models = listModels().toMutableList()
        models.removeAll { it.id == id }
        models.forEach { if (it.id == id) File(it.absolutePath).delete() }
        saveAllModels(models)
    }

    private fun saveModel(model: LocalModel) {
        val models = listModels().toMutableList()
        models.removeAll { it.absolutePath == model.absolutePath }
        models.add(model)
        saveAllModels(models)
    }

    private fun saveAllModels(models: List<LocalModel>) {
        val arr = JSONArray()
        models.forEach { m ->
            arr.put(JSONObject().apply {
                put("id", m.id); put("displayName", m.displayName)
                put("filename", m.filename); put("absolutePath", m.absolutePath)
                put("sizeBytes", m.sizeBytes)
            })
        }
        prefs.edit().putString(KEY_MODELS, arr.toString()).apply()
    }

    companion object {
        private const val PREFS_NAME = "local_models"
        private const val KEY_MODELS = "models"
        private const val LOCAL_DIR = "local_models"
    }
}
