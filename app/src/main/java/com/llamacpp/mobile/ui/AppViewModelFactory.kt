package com.llamacpp.mobile.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.llamacpp.mobile.di.AppContainer

/** Builds a [ViewModelProvider.Factory] that injects [AppContainer]. */
inline fun <reified VM : ViewModel> appVmFactory(
    container: AppContainer,
    crossinline create: (AppContainer) -> VM,
): ViewModelProvider.Factory = viewModelFactory {
    initializer { create(container) }
}
