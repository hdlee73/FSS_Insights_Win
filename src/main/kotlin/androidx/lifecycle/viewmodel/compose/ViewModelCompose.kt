package androidx.lifecycle.viewmodel.compose

import androidx.compose.runtime.Composable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore

@Composable
inline fun <reified T : ViewModel> viewModel(factory: ViewModelProvider.Factory): T = ViewModelStore.get(T::class.java, factory)
