package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.ui.AppViewModel
import com.example.ui.navigation.StoreAppRoot
import com.example.ui.theme.GestionMagasinTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            GestionMagasinTheme {
                val appViewModel: AppViewModel = viewModel()
                StoreAppRoot(viewModel = appViewModel)
            }
        }
    }
}
