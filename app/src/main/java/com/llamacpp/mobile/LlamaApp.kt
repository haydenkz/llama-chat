package com.llamacpp.mobile

import android.app.Application
import com.llamacpp.mobile.di.AppContainer

class LlamaApp : Application() {

    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
    }
}
