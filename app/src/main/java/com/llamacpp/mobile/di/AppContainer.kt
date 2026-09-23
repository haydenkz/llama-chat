package com.llamacpp.mobile.di

import android.content.Context
import androidx.datastore.core.handlers.ReplaceFileCorruptionHandler
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.preferencesDataStoreFile
import androidx.room.Room
import com.llamacpp.mobile.data.local.AppDatabase
import com.llamacpp.mobile.data.remote.LlamaApi
import com.llamacpp.mobile.data.repo.ChatRepository
import com.llamacpp.mobile.data.repo.ServerRepository
import com.llamacpp.mobile.data.repo.SettingsRepository
import com.llamacpp.mobile.data.tools.CalculatorTool
import com.llamacpp.mobile.data.tools.DateTimeTool
import com.llamacpp.mobile.data.tools.FileTool
import com.llamacpp.mobile.data.tools.PythonTool
import com.llamacpp.mobile.data.tools.ToolRegistry
import com.llamacpp.mobile.data.tools.WebSearchTool
import com.llamacpp.mobile.data.tools.WeatherTool
import com.llamacpp.mobile.data.tools.WikipediaTool
import kotlinx.coroutines.flow.first
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit

/** Manual dependency container (no DI framework). */
class AppContainer(context: Context) {

    private val appContext = context.applicationContext

    val json: Json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        explicitNulls = false
        // Must be true: request DTOs rely on defaults (e.g. `stream = true`)
        // being serialized, since llama-server defaults absent `stream` to false.
        encodeDefaults = true
        // Treat JSON nulls for non-nullable fields with defaults as "use default"
        // (e.g. server-level /props returns "params": null).
        coerceInputValues = true
    }

    private val plainClient: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(120, TimeUnit.SECONDS)
        .writeTimeout(60, TimeUnit.SECONDS)
        .build()

    /** No read timeout: generations may stream for minutes. */
    private val streamClient: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(0, TimeUnit.SECONDS)
        .writeTimeout(60, TimeUnit.SECONDS)
        .build()

    val api: LlamaApi = LlamaApi(plainClient, streamClient, json)

    // A corrupt preferences file is replaced with empty prefs instead of crash-looping
    // every collector on the first read.
    private val serverDataStore = PreferenceDataStoreFactory.create(
        corruptionHandler = ReplaceFileCorruptionHandler { emptyPreferences() },
        produceFile = { appContext.preferencesDataStoreFile("servers") },
    )

    private val settingsDataStore = PreferenceDataStoreFactory.create(
        corruptionHandler = ReplaceFileCorruptionHandler { emptyPreferences() },
        produceFile = { appContext.preferencesDataStoreFile("settings") },
    )

    val serverRepository = ServerRepository(serverDataStore, json)
    val settingsRepository = SettingsRepository(settingsDataStore, json)

    /** Dedicated client for network tools (browser UA, follows redirects). */
    private val toolClient = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .followRedirects(true)
        .build()

    /** Tools offered to the model (all enabled by default). */
    val toolRegistry = ToolRegistry(
        listOf(
            WebSearchTool(toolClient),
            WeatherTool(toolClient, json),
            WikipediaTool(toolClient, json),
            DateTimeTool(),
            CalculatorTool(),
            FileTool(appContext),
            PythonTool(toolClient, json) {
                // Prefer the configured Piston URL; otherwise assume Piston runs on
                // the llama-server host at its default port.
                val configured = settingsRepository.pistonUrl.first()
                if (configured.isNotBlank()) {
                    configured
                } else {
                    val server = serverRepository.activeServer.first()
                    runCatching { java.net.URI(server.normalizedBaseUrl).host }
                        .getOrNull()
                        ?.let { "http://$it:2000" }
                        .orEmpty()
                }
            },
        ),
    )

    private val database = Room.databaseBuilder(
        appContext,
        AppDatabase::class.java,
        "llama-chat.db",
    )
        .addMigrations(*AppDatabase.MIGRATIONS)
        .build()

    val chatRepository = ChatRepository(database.chatDao(), json)
}
