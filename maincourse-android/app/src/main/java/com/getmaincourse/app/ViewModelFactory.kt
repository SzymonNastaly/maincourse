package com.getmaincourse.app

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider

fun <T : ViewModel> simpleViewModelFactory(create: () -> T): ViewModelProvider.Factory =
    object : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <VM : ViewModel> create(modelClass: Class<VM>): VM {
            val viewModel = create()
            require(modelClass.isInstance(viewModel)) {
                "Factory created ${viewModel::class.java.name} for ${modelClass.name}"
            }
            return viewModel as VM
        }
    }
