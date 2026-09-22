package com.llamacpp.mobile.di

import android.content.Context
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.preferencesDataStoreFile
import androidx.room.Room
import com.llamacpp.mobile.data.local.AppDatabase
import com.llamacpp.mobile.data.remote.LlamaApi
import com.llamacpp.mobile.data.repo.ChatRepository
import com.llamacpp.mobile.data.repo.ServerRepository
import com.llamacpp.mobile.data.repo.SettingsRepository
import com.llamacpp.mobile.data.tools.WebSearchTool
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

    private val serverDataStore = PreferenceDataStoreFactory.create(
        produceFile = { appContext.preferencesDataStoreFile("servers") },
    )

    private val settingsDataStore = PreferenceDataStoreFactory.create(
        produceFile = { appContext.preferencesDataStoreFile("settings") },
    )

    val serverRepository = ServerRepository(serverDataStore, json)
    val settingsRepository = SettingsRepository(settingsDataStore, json)

    /** Dedicated client for the web-search tool (browser UA, follows redirects). */
    val webSearchTool = WebSearchTool(
        OkHttpClient.Builder()
            .connectTimeout(20, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .followRedirects(true)
            .build(),
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
