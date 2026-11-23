package com.educate.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Net.init(this)
        setContent { EduTheme { App() } }
    }
}

private data class NavTab(val route: String, val label: String, val icon: ImageVector)

private val tabs = listOf(
    NavTab("home", "Inicio", Icons.Filled.Home),
    NavTab("courses", "Cursos", Icons.Filled.MenuBook),
    NavTab("agenda", "Agenda", Icons.Filled.CalendarMonth),
    NavTab("grades", "Notas", Icons.Filled.BarChart),
)

private val teacherTabs = listOf(
    NavTab("teacher_home", "Inicio", Icons.Filled.Home),
    NavTab("teacher_courses", "Mis Cursos", Icons.Filled.MenuBook),
    NavTab("teacher_grades", "Entregas", Icons.Filled.AssignmentTurnedIn),
    NavTab("teacher_profile", "Perfil", Icons.Filled.Person),
)

private fun tabOf(route: String): String = when {
    route.startsWith("course") || route.startsWith("lesson") ||
        route.startsWith("assignment") || route == "catalog" -> "courses"
    route == "grades" -> "grades"
    route == "agenda" -> "agenda"
    else -> "home"
}

private fun teacherTabOf(route: String): String = when {
    route.startsWith("teacher_course") -> "teacher_courses"
    route.startsWith("teacher_assignment") || route == "teacher_grades" -> "teacher_grades"
    route == "teacher_profile" -> "teacher_profile"
    else -> "teacher_home"
}

@Composable
fun App() {
    var loggedIn by remember { mutableStateOf(Net.token != null) }
    var currentRole by remember { mutableStateOf(Net.role) }
    val scope = rememberCoroutineScope()

    val logout = {
        Net.logout()
        loggedIn = false
        currentRole = "student"
    }
    Net.onUnauthorized = logout

    LaunchedEffect(loggedIn) {
        if (loggedIn) {
            try {
                val me = Net.api.me()
                Net.saveUser(me)
                currentRole = me.role
            } catch (_: Exception) {}
        }
    }

    if (!loggedIn) {
        // Check onboarding
        val ctx = androidx.compose.ui.platform.LocalContext.current
        val prefs = ctx.getSharedPreferences("edu_prefs", android.content.Context.MODE_PRIVATE)
        var onboardingDone by remember { mutableStateOf(prefs.getBoolean("onboarding_done", false)) }

        if (!onboardingDone) {
            OnboardingScreen(onDone = { onboardingDone = true })
        } else {
            LoginScreen {
                loggedIn = true
                currentRole = Net.role
            }
        }
        return
    }

    key(currentRole) {
        val nav = rememberNavController()
        val route = nav.currentBackStackEntryAsState().value?.destination?.route ?: "home"
        val teacher = currentRole == "teacher"
        val currentTabs = if (teacher) teacherTabs else tabs
        val homeRoute = if (teacher) "teacher_home" else "home"

        val switchRole: (String) -> Unit = { newRole ->
            scope.launch {
                try {
                    val updated = Net.api.updateRole(RoleBody(newRole))
                    Net.saveUser(updated)
                    currentRole = updated.role
                } catch (_: Exception) {}
            }
        }

        Scaffold(
            containerColor = Bg,
            bottomBar = {
                NavigationBar(containerColor = androidx.compose.ui.graphics.Color.White) {
                    currentTabs.forEach { t ->
                        val selected = if (teacher) teacherTabOf(route) == t.route else tabOf(route) == t.route
                        NavigationBarItem(
                            selected = selected,
                            onClick = { nav.navigate(t.route) { popUpTo(homeRoute); launchSingleTop = true } },
                            icon = { Icon(t.icon, t.label) }, label = { Text(t.label) },
                            colors = NavigationBarItemDefaults.colors(
                                selectedIconColor = Navy,
                                selectedTextColor = Navy,
                                unselectedIconColor = Muted,
                                unselectedTextColor = Muted,
                                indicatorColor = Soft
                            )
                        )
                    }
                }
            }
        ) { pad ->
            val id = listOf(navArgument("id") { type = NavType.IntType })
            NavHost(nav, homeRoute, Modifier.padding(pad)) {
                // Estudiante
                composable("home") { HomeScreen(nav, logout, onSwitchToTeacher = { switchRole("teacher") }) }
                composable("courses") { MyCoursesScreen(nav) }
                composable("agenda") { AgendaScreen(nav) }
                composable("grades") { GradesScreen() }
                composable("catalog") { CatalogScreen(nav) }
                composable("donate") { DonateScreen(nav) }
                composable("course/{id}", id) { CourseScreen(it.arguments!!.getInt("id"), nav) }
                composable("lesson/{id}", id) { LessonScreen(it.arguments!!.getInt("id"), nav) }
                composable("assignment/{id}", id) { AssignmentScreen(it.arguments!!.getInt("id"), nav) }

                // Docente / Maestro
                composable("teacher_home") { TeacherHomeScreen(nav, logout) }
                composable("teacher_courses") { TeacherCoursesScreen(nav) }
                composable("teacher_grades") { TeacherSubmissionsScreen(nav) }
                composable("teacher_profile") { TeacherProfileScreen(nav, logout, onSwitchToStudent = { switchRole("student") }) }
                composable("teacher_course_new") { TeacherCourseFormScreen(nav) }
                composable("teacher_course/{id}", id) { TeacherCourseDetailScreen(it.arguments!!.getInt("id"), nav) }
                composable("teacher_course/{id}/edit", id) { TeacherCourseFormScreen(nav, it.arguments!!.getInt("id")) }
                composable("teacher_assignment/{id}", id) { TeacherAssignmentScreen(it.arguments!!.getInt("id"), nav) }
            }
        }
    }
}
