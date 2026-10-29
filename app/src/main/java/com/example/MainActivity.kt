package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.room.Room
import com.example.data.local.SmcLabDatabase
import com.example.ui.SmcLabViewModel
import com.example.ui.screens.SmcLabMainScreen
import com.example.ui.theme.MyApplicationTheme

class MainActivity : ComponentActivity() {

    private val database: SmcLabDatabase by lazy {
        Room.databaseBuilder(
            applicationContext,
            SmcLabDatabase::class.java,
            "smc_lab_offline.db"
        )
            .addMigrations(SmcLabDatabase.MIGRATION_1_2)
            .fallbackToDestructiveMigration(dropAllTables = true)
            .build()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MyApplicationTheme {
                val vm: SmcLabViewModel = viewModel(
                    factory = object : ViewModelProvider.Factory {
                        @Suppress("UNCHECKED_CAST")
                        override fun <T : ViewModel> create(modelClass: Class<T>): T {
                            return SmcLabViewModel(database.dao()) as T
                        }
                    }
                )
                SmcLabMainScreen(viewModel = vm)
            }
        }
    }
}
