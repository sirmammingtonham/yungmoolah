package com.yungmoolah.converter

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.yungmoolah.converter.ui.ConverterScreen
import com.yungmoolah.converter.ui.ConverterViewModel
import com.yungmoolah.converter.ui.theme.MoolahTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)

        val repository = (application as MoolahApplication).repository

        setContent {
            MoolahTheme {
                val viewModel: ConverterViewModel = viewModel(
                    factory = ConverterViewModel.Factory(repository),
                )
                val state by viewModel.uiState.collectAsStateWithLifecycle()

                ConverterScreen(
                    state = state,
                    onAmountChanged = viewModel::onAmountChanged,
                    onRowFocused = viewModel::onRowFocused,
                    onClear = viewModel::clearAmount,
                    onRemove = viewModel::removeCurrency,
                    onMove = viewModel::moveCurrency,
                    onAdd = viewModel::addCurrency,
                    onUndoRemove = viewModel::undoRemove,
                    onRefresh = { viewModel.refresh(force = true) },
                    onMessageShown = viewModel::consumeMessage,
                )
            }
        }
    }
}
