package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.lifecycle.ViewModelProvider
import com.example.data.AppDatabase
import com.example.data.DashboardRepository
import com.example.ui.DashboardViewModel
import com.example.ui.DashboardViewModelFactory
import com.example.ui.MainDashboardScreen
import com.example.ui.theme.MyApplicationTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // Initialize local Room database client
        val database = AppDatabase.getDatabase(this)
        val repository = DashboardRepository(database.dashboardDao())
        
        // Instantiate the centralized view model using our factory custom builder
        val viewModelFactory = DashboardViewModelFactory(application, repository)
        val viewModel = ViewModelProvider(this, viewModelFactory)[DashboardViewModel::class.java]
        
        enableEdgeToEdge()
        setContent {
            MyApplicationTheme {
                MainDashboardScreen(
                    viewModel = viewModel,
                    modifier = Modifier.fillMaxSize()
                )
            }
        }
    }
}
