package com.movedados.witon.ui.navigation

import android.net.Uri
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.movedados.witon.ui.admin.AdminScreen
import com.movedados.witon.ui.ar.ArCaptureScreen
import com.movedados.witon.ui.auth.AuthViewModel
import com.movedados.witon.ui.auth.Gate
import com.movedados.witon.ui.auth.LoginScreen
import com.movedados.witon.ui.auth.PendingScreen
import com.movedados.witon.ui.auth.RejectedScreen
import com.movedados.witon.ui.auth.SignUpScreen
import com.movedados.witon.ui.auth.SuspendedScreen
import com.movedados.witon.ui.components.FullScreenLoader
import com.movedados.witon.ui.home.HomeScreen
import com.movedados.witon.ui.survey.NewSurveyScreen
import com.movedados.witon.ui.survey.SurveyResultScreen

object Routes {
    const val LOGIN = "login"
    const val SIGNUP = "signup"
    const val HOME = "home"
    const val ADMIN = "admin"
    const val NEW_SURVEY = "new_survey"
    const val CAPTURE = "capture/{surveyName}"
    const val RESULT = "result/{surveyLocalId}"

    fun capture(surveyName: String) = "capture/${Uri.encode(surveyName)}"
    fun result(surveyLocalId: String) = "result/${Uri.encode(surveyLocalId)}"
}

/**
 * A navegacao e dirigida pelo Gate, nao por cliques.
 * Assim nenhuma tela protegida existe na pilha se o acesso nao estiver liberado.
 */
@Composable
fun WiTonRoot(authViewModel: AuthViewModel = viewModel()) {
    val state by authViewModel.state.collectAsState()

    Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        when (val gate = state.gate) {
            Gate.Loading -> Box(Modifier.fillMaxSize(), Alignment.Center) {
                FullScreenLoader("Verificando seu acesso...")
            }

            Gate.SignedOut -> AuthFlow(state, authViewModel)

            Gate.Pending -> PendingScreen(
                onRefresh = authViewModel::refreshGate,
                onSignOut = authViewModel::signOut
            )

            is Gate.Rejected -> RejectedScreen(
                reason = gate.reason,
                onSignOut = authViewModel::signOut
            )

            Gate.Suspended -> SuspendedScreen(onSignOut = authViewModel::signOut)

            is Gate.Allowed -> ApprovedFlow(
                isAdmin = gate.isAdmin,
                onSignOut = authViewModel::signOut
            )
        }
    }
}

@Composable
private fun AuthFlow(
    state: com.movedados.witon.ui.auth.AuthUiState,
    vm: AuthViewModel
) {
    val nav = rememberNavController()
    NavHost(navController = nav, startDestination = Routes.LOGIN) {
        composable(Routes.LOGIN) {
            LoginScreen(
                state = state,
                onSignIn = vm::signIn,
                onGoToSignUp = {
                    vm.clearMessages()
                    nav.navigate(Routes.SIGNUP)
                }
            )
        }
        composable(Routes.SIGNUP) {
            SignUpScreen(
                state = state,
                onSignUp = { form -> vm.signUp(form) { nav.popBackStack() } },
                onBackToLogin = {
                    vm.clearMessages()
                    nav.popBackStack()
                }
            )
        }
    }
}

@Composable
private fun ApprovedFlow(isAdmin: Boolean, onSignOut: () -> Unit) {
    val nav = rememberNavController()
    NavHost(navController = nav, startDestination = Routes.HOME) {
        composable(Routes.HOME) {
            HomeScreen(
                isAdmin = isAdmin,
                onNewSurvey = { nav.navigate(Routes.NEW_SURVEY) },
                onOpenAdmin = { nav.navigate(Routes.ADMIN) },
                onSignOut = onSignOut
            )
        }
        composable(Routes.ADMIN) {
            AdminScreen(onBack = { nav.popBackStack() })
        }
        composable(Routes.NEW_SURVEY) {
            NewSurveyScreen(
                onStart = { name ->
                    // popUpTo remove a tela de nome da pilha: o botao "voltar"
                    // durante a captura leva para a Home, nao de volta pro formulario.
                    nav.navigate(Routes.capture(name)) {
                        popUpTo(Routes.NEW_SURVEY) { inclusive = true }
                    }
                },
                onBack = { nav.popBackStack() }
            )
        }
        composable(
            route = Routes.CAPTURE,
            arguments = listOf(navArgument("surveyName") { type = NavType.StringType })
        ) { backStackEntry ->
            val surveyName = backStackEntry.arguments?.getString("surveyName").orEmpty()
            ArCaptureScreen(
                surveyName = surveyName,
                onFinished = { surveyLocalId ->
                    nav.navigate(Routes.result(surveyLocalId)) {
                        popUpTo(Routes.HOME)
                    }
                }
            )
        }
        composable(
            route = Routes.RESULT,
            arguments = listOf(navArgument("surveyLocalId") { type = NavType.StringType })
        ) { backStackEntry ->
            val surveyLocalId = backStackEntry.arguments?.getString("surveyLocalId").orEmpty()
            SurveyResultScreen(
                surveyLocalId = surveyLocalId,
                onDone = { nav.navigate(Routes.HOME) { popUpTo(Routes.HOME) { inclusive = true } } }
            )
        }
    }
}
