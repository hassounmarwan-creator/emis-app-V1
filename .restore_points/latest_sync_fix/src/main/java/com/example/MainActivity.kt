package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import com.example.ui.screens.ContractHoursScreen
import com.example.ui.screens.DashboardScreen
import com.example.ui.screens.ExamUploadScreen
import com.example.ui.screens.GradesScreen
import com.example.ui.screens.LoginScreen
import com.example.ui.screens.MissingGradesScreen
import com.example.ui.screens.SettingsScreen
import com.example.ui.screens.StudentPortalQrScreen
import com.example.ui.screens.StudentReportsScreen
import com.example.ui.screens.StudentWebPortalView
import com.example.ui.screens.WelcomeScreen
import com.example.ui.theme.MyApplicationTheme
import android.content.Intent
import com.example.viewmodel.AppScreen
import com.example.viewmodel.EmisViewModel

class MainActivity : ComponentActivity() {

    private val viewModel: EmisViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        handleIntent(intent)
        enableEdgeToEdge()
        setContent {
            MyApplicationTheme {
                // Force Right-To-Left (RTL) Layout Direction for Arabic language
                CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
                    Surface(modifier = Modifier.fillMaxSize()) {
                        EmisApp(viewModel = viewModel)
                    }
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleIntent(intent)
    }

    private fun handleIntent(intent: Intent?) {
        val data = intent?.data ?: return
        val host = data.host ?: ""
        val scheme = data.scheme ?: ""
        val path = data.path ?: ""
        if (host.contains("emis-school") || scheme == "emis" || path.contains("student") || path.contains("results")) {
            viewModel.navigateTo(AppScreen.STUDENT_WEB_PORTAL)
        }
    }
}

@Composable
fun EmisApp(viewModel: EmisViewModel) {
    val currentScreen by viewModel.currentScreen.collectAsState()
    val teacher by viewModel.currentTeacher.collectAsState()

    // Handle back button on device
    BackHandler(enabled = currentScreen != AppScreen.LOGIN && currentScreen != AppScreen.WELCOME) {
        when (currentScreen) {
            AppScreen.STUDENT_WEB_PORTAL -> viewModel.navigateTo(AppScreen.STUDENT_PORTAL_QR)
            AppScreen.DASHBOARD -> viewModel.logout()
            else -> viewModel.navigateTo(AppScreen.DASHBOARD)
        }
    }

    when (currentScreen) {
        AppScreen.LOGIN -> LoginScreen(viewModel = viewModel)
        AppScreen.WELCOME -> WelcomeScreen(viewModel = viewModel)
        AppScreen.DASHBOARD -> DashboardScreen(viewModel = viewModel)
        AppScreen.GRADES -> GradesScreen(viewModel = viewModel)
        AppScreen.CONTRACT_HOURS -> ContractHoursScreen(viewModel = viewModel)
        AppScreen.EXAM_UPLOAD -> ExamUploadScreen(viewModel = viewModel)
        AppScreen.MISSING_GRADES -> {
            if (teacher?.isAdmin == true) {
                MissingGradesScreen(viewModel = viewModel)
            } else {
                DashboardScreen(viewModel = viewModel)
            }
        }
        AppScreen.STUDENT_REPORTS -> {
            if (teacher?.isAdmin == true) {
                StudentReportsScreen(viewModel = viewModel)
            } else {
                DashboardScreen(viewModel = viewModel)
            }
        }
        AppScreen.SETTINGS -> {
            if (teacher?.isAdmin == true) {
                SettingsScreen(viewModel = viewModel)
            } else {
                DashboardScreen(viewModel = viewModel)
            }
        }
        AppScreen.STUDENT_PORTAL_QR -> {
            if (teacher?.isAdmin == true) {
                StudentPortalQrScreen(viewModel = viewModel)
            } else {
                DashboardScreen(viewModel = viewModel)
            }
        }
        AppScreen.STUDENT_WEB_PORTAL -> {
            if (teacher?.isAdmin == true) {
                StudentWebPortalView(
                    viewModel = viewModel,
                    onBack = { viewModel.navigateTo(AppScreen.STUDENT_PORTAL_QR) }
                )
            } else {
                DashboardScreen(viewModel = viewModel)
            }
        }
    }
}
