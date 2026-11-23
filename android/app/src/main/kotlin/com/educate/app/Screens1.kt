package com.educate.app

import android.annotation.SuppressLint
import android.content.Intent
import android.net.Uri
import android.webkit.WebView
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Article
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.navigation.NavController
import kotlinx.coroutines.launch
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.TemporalAdjusters

fun isoDate(iso: String): LocalDate =
    Instant.parse(iso.replace("+00:00", "Z")).atZone(ZoneId.systemDefault()).toLocalDate()

private val monthsEs = listOf("enero", "febrero", "marzo", "abril", "mayo", "junio", "julio", "agosto", "septiembre", "octubre", "noviembre", "diciembre")

// ============================================================ P-00 · Onboarding (CAMBIO 1)
private data class OnboardingPage(
    val icon: androidx.compose.ui.graphics.vector.ImageVector,
    val title: String,
    val subtitle: String
)

@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
fun OnboardingScreen(onDone: () -> Unit) {
    val ctx = LocalContext.current
    val pages = listOf(
        OnboardingPage(Icons.Filled.School, "Bienvenido a EDUcate", "Tu campus en línea, siempre contigo"),
        OnboardingPage(Icons.Filled.MenuBook, "Explora cursos gratuitos", "Accede a cientos de cursos impartidos por profesores voluntarios, sin costo alguno."),
        OnboardingPage(Icons.Filled.Assignment, "Entrega tus actividades", "Sube tus tareas y cuestionarios directamente desde tu teléfono y recibe retroalimentación inmediata."),
        OnboardingPage(Icons.Filled.EmojiEvents, "Obtén tu certificado", "Completa los módulos y consigue tu certificado de aprendizaje para demostrar tus logros.")
    )
    val pagerState = androidx.compose.foundation.pager.rememberPagerState(pageCount = { pages.size })
    val scope = rememberCoroutineScope()

    Column(Modifier.fillMaxSize().background(Navy)) {
        // Top section — 40% height with Navy background
        Box(
            Modifier.fillMaxWidth().fillMaxHeight(0.40f),
            Alignment.Center
        ) {
            HorizontalPager(state = pagerState, modifier = Modifier.fillMaxSize()) { page ->
                val p = pages[page]
                Column(
                    Modifier.fillMaxSize().padding(horizontal = 32.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Box(
                        Modifier.size(80.dp).clip(RoundedCornerShape(24.dp)).background(Soft),
                        Alignment.Center
                    ) {
                        Icon(p.icon, null, tint = Navy, modifier = Modifier.size(48.dp))
                    }
                    Spacer(Modifier.height(16.dp))
                    Text(
                        p.title,
                        color = Color.White,
                        fontSize = 24.sp,
                        fontWeight = FontWeight.ExtraBold,
                        textAlign = TextAlign.Center
                    )
                }
            }
        }

        // Bottom section — white with rounded top corners
        Column(
            Modifier
                .fillMaxSize()
                .clip(RoundedCornerShape(topStart = 32.dp, topEnd = 32.dp))
                .background(Color.White)
                .padding(horizontal = 28.dp, vertical = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Dots indicator
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                pages.indices.forEach { i ->
                    val selected = pagerState.currentPage == i
                    Box(
                        Modifier
                            .height(8.dp)
                            .clip(CircleShape)
                            .background(if (selected) Navy else Line)
                            .run { if (selected) width(24.dp) else width(8.dp) }
                    )
                }
            }

            Spacer(Modifier.height(20.dp))

            // Subtitle from current page
            val currentSubtitle = pages[pagerState.currentPage].subtitle
            Text(
                currentSubtitle,
                color = Muted,
                fontSize = 15.sp,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(Modifier.height(32.dp))

            if (pagerState.currentPage == pages.size - 1) {
                PrimaryButton("Comenzar") {
                    ctx.getSharedPreferences("edu_prefs", android.content.Context.MODE_PRIVATE)
                        .edit().putBoolean("onboarding_done", true).apply()
                    onDone()
                }
            } else {
                PrimaryButton("Siguiente") {
                    scope.launch {
                        pagerState.animateScrollToPage(pagerState.currentPage + 1)
                    }
                }
                Spacer(Modifier.height(8.dp))
                TextButton(
                    onClick = {
                        ctx.getSharedPreferences("edu_prefs", android.content.Context.MODE_PRIVATE)
                            .edit().putBoolean("onboarding_done", true).apply()
                        onDone()
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Omitir", color = Muted, fontWeight = FontWeight.SemiBold)
                }
            }
        }
    }
}

// ============================================================ P-01 · Inicio de sesión
@Composable
fun LoginScreen(onDone: () -> Unit) {
    var register by remember { mutableStateOf(false) }
    var role by remember { mutableStateOf("student") }
    var name by remember { mutableStateOf("") }
    var email by remember { mutableStateOf("") }
    var pass by remember { mutableStateOf("") }
    var show by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

    val emailValid = email.isEmpty() || isValidEmail(email)
    val passValid = if (register) pass.length >= 8 else pass.isNotEmpty()
    val canSubmit = !busy && isValidEmail(email) && passValid && (!register || name.isNotBlank())

    Column(Modifier.fillMaxSize().background(Navy).verticalScroll(rememberScrollState())) {
        Column(
            Modifier.fillMaxWidth().padding(top = 54.dp, bottom = 32.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(
                Modifier.size(68.dp).clip(RoundedCornerShape(20.dp)).background(Soft),
                Alignment.Center
            ) {
                Icon(Icons.Filled.School, null, tint = Navy, modifier = Modifier.size(38.dp))
            }
            Spacer(Modifier.height(12.dp))
            Text("EDUcate", color = Color.White, fontSize = 32.sp, fontWeight = FontWeight.ExtraBold)
            Text("Aprender nos conecta.", color = Soft, fontSize = 13.sp)
        }

        Column(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(topStart = 32.dp, topEnd = 32.dp))
                .background(Color.White).padding(24.dp).heightIn(min = 480.dp)
        ) {
            Text(
                if (register) "Crea tu cuenta" else "Bienvenido a tu campus",
                fontSize = 22.sp, fontWeight = FontWeight.Bold, color = Navy
            )
            Sub(
                if (register) "Regístrate con tu correo para empezar a aprender o enseñar."
                else "Tu aprendizaje, en un solo lugar."
            )
            Spacer(Modifier.height(18.dp))

            if (register) {
                Text("Tipo de perfil:", fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = Navy)
                Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(
                        selected = role == "student",
                        onClick = { role = "student" },
                        label = { Text("Estudiante") },
                        leadingIcon = { Icon(Icons.Filled.School, null, Modifier.size(16.dp)) },
                        modifier = Modifier.weight(1f)
                    )
                    FilterChip(
                        selected = role == "teacher",
                        onClick = { role = "teacher" },
                        label = { Text("Maestro") },
                        leadingIcon = { Icon(Icons.Filled.Person, null, Modifier.size(16.dp)) },
                        modifier = Modifier.weight(1f)
                    )
                }
                Sub(
                    if (role == "teacher") "Acceso completo para crear cursos, temarios, tareas y calificar entregas de alumnos."
                    else "Acceso para aprender, inscribirte a cursos y entregar tus actividades."
                )
                Spacer(Modifier.height(12.dp))

                OutlinedTextField(
                    name, { name = it; error = null },
                    label = { Text("Nombre completo") },
                    leadingIcon = { Icon(Icons.Filled.PersonOutline, null, tint = Muted) },
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(10.dp))
            }

            OutlinedTextField(
                email, { email = it.trim(); error = null },
                label = { Text("Correo institucional") },
                placeholder = { Text("ana.lopez@universidad.edu") },
                leadingIcon = { Icon(Icons.Filled.MailOutline, null, tint = Muted) },
                singleLine = true,
                shape = RoundedCornerShape(12.dp),
                isError = !emailValid,
                supportingText = {
                    if (!emailValid) Text("Ingresa un correo válido (ej. usuario@ejemplo.com)", color = ErrColor)
                },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(10.dp))

            OutlinedTextField(
                pass, { pass = it; error = null },
                label = { Text("Contraseña") },
                leadingIcon = { Icon(Icons.Filled.Lock, null, tint = Muted) },
                singleLine = true,
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth(),
                visualTransformation = if (show) VisualTransformation.None else PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                trailingIcon = {
                    IconButton({ show = !show }) { Icon(if (show) Icons.Filled.VisibilityOff else Icons.Filled.Visibility, "Mostrar", tint = Muted) }
                },
                supportingText = { if (register) Text("Mínimo 8 caracteres") }
            )

            error?.let { Text(it, color = ErrColor, fontSize = 13.sp, modifier = Modifier.padding(top = 4.dp)) }
            Spacer(Modifier.height(16.dp))

            PrimaryButton(
                if (busy) "Un momento…" else if (register) (if (role == "teacher") "Crear cuenta de Maestro" else "Crear cuenta") else "Iniciar sesión",
                enabled = canSubmit
            ) {
                busy = true; error = null
                scope.launch {
                    try {
                        val r = if (register) Net.api.register(RegisterBody(name.trim(), email, pass, role))
                        else Net.api.login(LoginBody(email, pass))
                        Net.saveSession(r)
                        onDone()
                    } catch (e: Exception) {
                        error = Net.message(e)
                    }
                    busy = false
                }
            }

            if (!register) {
                TextButton(
                    onClick = { error = "Para restablecer tu contraseña, contacta al soporte institucional." },
                    modifier = Modifier.align(Alignment.CenterHorizontally).padding(top = 4.dp)
                ) {
                    Text("¿Olvidaste tu contraseña?", color = Navy, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                }

                Row(
                    Modifier.fillMaxWidth().padding(vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    HorizontalDivider(Modifier.weight(1f), color = Line)
                    Text("  o usa tu cuenta institucional  ", color = Muted, fontSize = 12.sp)
                    HorizontalDivider(Modifier.weight(1f), color = Line)
                }

                OutlineButton("Iniciar sesión con Google/Microsoft") {
                    error = "El acceso directo con cuenta institucional está en despliegue."
                }
            }

            Spacer(Modifier.height(8.dp))

            TextButton({ register = !register; error = null }, Modifier.align(Alignment.CenterHorizontally)) {
                Text(
                    if (register) "¿Ya tienes cuenta? Inicia sesión" else "¿No tienes cuenta? Regístrate",
                    color = Navy, fontWeight = FontWeight.Bold, fontSize = 14.sp
                )
            }

            Spacer(Modifier.height(12.dp))
            Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                Text("Acceso para estudiantes y profesores", color = Muted, fontSize = 11.sp)
                Text("¿Necesitas ayuda? Contacta a soporte", color = Navy, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
            }
        }
    }
}

// ============================================================ P-02 · Inicio del estudiante
@Composable
fun HomeScreen(nav: NavController, onLogout: () -> Unit, onSwitchToTeacher: () -> Unit = {}) {
    var profileOpen by remember { mutableStateOf(false) }

    Load(Unit, { Net.api.home() }) { h, _ ->
        val today = LocalDate.now()
        val monday = today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
        val due = h.upcoming.map { isoDate(it.dueAt) }.toSet()
        LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            item {
                Row(Modifier.fillMaxWidth().padding(bottom = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.School, null, tint = Navy, modifier = Modifier.size(26.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("EDUcate", color = Navy, fontWeight = FontWeight.Bold, fontSize = 20.sp, modifier = Modifier.weight(1f))
                    Box(Modifier.clickable { profileOpen = true }) { Avatar(h.user.initials, 38.dp) }
                }
            }
            item {
                Column {
                    H1("Hola, ${h.user.name.substringBefore(" ")}")
                    val monthName = monthsEs[today.monthValue - 1].replaceFirstChar { it.uppercase() }
                    Sub("${today.dayOfWeek.name.lowercase().replaceFirstChar { it.uppercase() }}, ${today.dayOfMonth} de $monthName de ${today.year}")
                }
            }
            item {
                Card2(color = Soft, border = false) {
                    Row(Modifier.fillMaxWidth(), Arrangement.SpaceEvenly) {
                        Stat(h.stats.activeCourses.toString(), "Cursos activos")
                        Stat(h.stats.pending.toString(), "Pendientes")
                        Stat(h.stats.average?.let { "${it.toInt()}%" } ?: "—", "Promedio actual")
                    }
                }
            }
            item {
                Card2(color = Soft, border = false) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Filled.School, null, tint = Navy, modifier = Modifier.size(26.dp))
                        Spacer(Modifier.width(10.dp))
                        Column(Modifier.weight(1f)) {
                            Text("¿Eres docente o profesor?", fontWeight = FontWeight.Bold, color = Navy, fontSize = 14.sp)
                            Sub("Accede a tu panel para crear cursos, temas y calificar tareas.")
                        }
                        Spacer(Modifier.width(6.dp))
                        Button(
                            onClick = onSwitchToTeacher,
                            colors = ButtonDefaults.buttonColors(containerColor = Navy),
                            shape = RoundedCornerShape(8.dp),
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp)
                        ) {
                            Text("Modo Maestro", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
            item {
                val c = h.continueLearning
                if (c == null) {
                    Card2(onClick = { nav.navigate("catalog") }) {
                        H2("Aún no tienes cursos")
                        Sub("Explora el catálogo e inscríbete en tu primer curso.")
                    }
                } else Card2(color = Navy, border = false, onClick = { nav.navigate("course/${c.courseId}") }) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("CONTINÚA APRENDIENDO", color = Soft, fontSize = 10.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                        Icon(Icons.Filled.OpenInNew, null, tint = Soft, modifier = Modifier.size(16.dp))
                    }
                    Spacer(Modifier.height(6.dp))
                    Text(c.title, color = Color.White, fontSize = 21.sp, fontWeight = FontWeight.Bold)
                    Text("${c.teacherName} · ${c.groupName}", color = Soft, fontSize = 12.sp)
                    Spacer(Modifier.height(10.dp))
                    ProgressBar(c.percent, track = Color(0x40FFFFFF), fill = Color.White)
                    Spacer(Modifier.height(6.dp))
                    Row {
                        Text("${c.completedModules} de ${c.totalModules} temas completados", color = Soft, fontSize = 12.sp, modifier = Modifier.weight(1f))
                        Text("${c.percent.toInt()}%", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                    }
                }
            }
            item {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    H2("Avisos recientes", Modifier.weight(1f))
                    Text("Ver todos", color = Navy, fontWeight = FontWeight.SemiBold, fontSize = 12.sp)
                }
            }
            if (h.announcements.isEmpty()) item { EmptyState("No hay avisos por ahora") }
            items(h.announcements) { a ->
                Card2 {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Filled.Campaign, null, tint = Navy, modifier = Modifier.size(24.dp))
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text(a.title, fontWeight = FontWeight.SemiBold, color = Navy, fontSize = 14.sp)
                            Sub("${a.teacherName} · ${a.courseTitle} · ${fmtDateTime(a.createdAt)}")
                        }
                    }
                }
            }
            item {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    H2("Tus próximas fechas", Modifier.weight(1f))
                    Text("${monthsEs[today.monthValue - 1].replaceFirstChar { it.uppercase() }} ${today.year}", color = Muted, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                }
            }
            item {
                Row(Modifier.fillMaxWidth(), Arrangement.SpaceBetween) {
                    for (i in 0..6) {
                        val d = monday.plusDays(i.toLong())
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Sub("LMXJVSD"[i].toString())
                            Spacer(Modifier.height(4.dp))
                            val sel = d == today
                            Box(
                                Modifier.size(36.dp).clip(CircleShape)
                                    .background(if (sel) Navy else if (d in due) Soft else Color.Transparent),
                                Alignment.Center
                            ) { Text("${d.dayOfMonth}", color = if (sel) Color.White else Navy, fontWeight = FontWeight.SemiBold) }
                        }
                    }
                }
            }
            if (h.upcoming.isEmpty()) item { EmptyState("No tienes entregas pendientes") }
            items(h.upcoming) { u ->
                Card2(onClick = { nav.navigate("assignment/${u.assignmentId}") }) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(fmtDate(u.dueAt), fontWeight = FontWeight.Bold, color = Navy, fontSize = 12.sp, modifier = Modifier.width(58.dp))
                        Spacer(Modifier.width(8.dp))
                        Column(Modifier.weight(1f)) {
                            Text(u.title, fontWeight = FontWeight.SemiBold, color = Navy, fontSize = 14.sp)
                            Sub("${u.courseTitle} · ${fmtTime(u.dueAt)}")
                        }
                        Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null, tint = Muted)
                    }
                }
            }
            item {
                OutlineButton("Apoya a EDUcate") { nav.navigate("donate") }
            }
            item {
                TextButton(onLogout, Modifier.fillMaxWidth()) { Text("Cerrar sesión", color = Muted) }
            }
        }

        if (profileOpen) {
            AlertDialog(
                onDismissRequest = { profileOpen = false },
                title = { Text("Mi Cuenta", fontWeight = FontWeight.Bold, color = Navy) },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Avatar(h.user.initials, 48.dp)
                            Spacer(Modifier.width(12.dp))
                            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                Text(h.user.name, fontWeight = FontWeight.Bold, color = Navy, fontSize = 16.sp)
                                Sub(h.user.email)
                                Pill("ESTUDIANTE", Soft, Navy)
                            }
                        }
                        Spacer(Modifier.height(8.dp))
                        Button(
                            onClick = {
                                profileOpen = false
                                onSwitchToTeacher()
                            },
                            modifier = Modifier.fillMaxWidth(),
                            colors = ButtonDefaults.buttonColors(containerColor = Navy),
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Icon(Icons.Filled.School, null, Modifier.size(18.dp))
                            Spacer(Modifier.width(8.dp))
                            Text("Cambiar a Modo Maestro", fontWeight = FontWeight.Bold)
                        }
                    }
                },
                confirmButton = {
                    TextButton(onClick = {
                        profileOpen = false
                        onLogout()
                    }) {
                        Text("Cerrar sesión", color = ErrColor, fontWeight = FontWeight.SemiBold)
                    }
                },
                dismissButton = {
                    TextButton({ profileOpen = false }) { Text("Cerrar") }
                }
            )
        }
    }
}

@Composable
private fun Stat(value: String, label: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, fontSize = 24.sp, fontWeight = FontWeight.Bold, color = Navy)
        Sub(label)
    }
}

// ============================================================ Cursos (mis cursos)
@Composable
fun MyCoursesScreen(nav: NavController) {
    Load(Unit, { Net.api.myCourses() }) { list, _ ->
        LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            item { H1("Mis cursos") }
            if (list.isEmpty()) item { EmptyState("Aún no estás inscrito en ningún curso") }
            items(list) { c ->
                Card2(onClick = { nav.navigate("course/${c.id}") }) {
                    Text(c.title, fontSize = 17.sp, fontWeight = FontWeight.Bold, color = Navy)
                    Sub("${c.teacherName} · ${c.groupName} ${c.period}".trim())
                    Spacer(Modifier.height(10.dp))
                    ProgressBar(c.percent)
                    Spacer(Modifier.height(4.dp))
                    Sub("${c.completedModules} de ${c.totalModules} temas · ${c.percent.toInt()}%")
                }
            }
            item { PrimaryButton("Explorar cursos") { nav.navigate("catalog") } }
        }
    }
}

// ============================================================ P-03 · Curso y módulos
@Composable
fun CourseScreen(id: Int, nav: NavController) {
    Load(id, { Net.api.course(id) }) { c, _ ->
        var tab by remember { mutableIntStateOf(0) }
        Column(Modifier.fillMaxSize()) {
            TopBar("Mi curso", onBack = { nav.popBackStack() })
            Column(Modifier.padding(horizontal = 16.dp)) {
                H1(c.title)
                Sub(listOf(c.teacherName, c.groupName, c.period).filter { it.isNotBlank() }.joinToString(" · "))
                Spacer(Modifier.height(10.dp))
                Row {
                    Sub("${c.completedModules} de ${c.totalModules} temas · ${c.percent.toInt()}%")
                }
                Spacer(Modifier.height(4.dp))
                ProgressBar(c.percent)
            }
            TabRow(tab, containerColor = Color.Transparent, contentColor = Navy) {
                listOf("Contenido", "Foro", "Información").forEachIndexed { i, t ->
                    Tab(tab == i, { tab = i }, text = { Text(t, fontWeight = FontWeight.SemiBold) })
                }
            }
            LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                when (tab) {
                    0 -> {
                        if (c.modules.isEmpty()) item { EmptyState("El docente aún no publica contenido") }
                        items(c.modules, key = { it.id }) { m -> ModuleCard(m, nav) }
                    }
                    1 -> {
                        if (c.forums.isEmpty()) item { EmptyState("Este curso no tiene foros") }
                        items(c.forums) { f ->
                            Card2(onClick = { nav.navigate("lesson/${f.id}") }) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Filled.Forum, null, tint = Navy)
                                    Spacer(Modifier.width(10.dp))
                                    Column(Modifier.weight(1f)) {
                                        Text(f.title, fontWeight = FontWeight.SemiBold, color = Navy)
                                        Sub("${f.postsCount} aportes")
                                    }
                                    Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null, tint = Muted)
                                }
                            }
                        }
                    }
                    else -> item {
                        Card2 {
                            if (c.description.isNotBlank()) { Text(c.description, color = Navy); Spacer(Modifier.height(10.dp)) }
                            InfoRow("Docente", c.teacherName)
                            if (c.category.isNotBlank()) InfoRow("Categoría", c.category)
                            InfoRow("Nivel", c.level)
                            if (c.groupName.isNotBlank()) InfoRow("Grupo", c.groupName)
                            if (c.period.isNotBlank()) InfoRow("Periodo", c.period)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun InfoRow(k: String, v: String) {
    Row(Modifier.padding(vertical = 3.dp)) {
        Sub(k, Modifier.width(90.dp)); Text(v, color = Navy, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun ModuleCard(m: ModuleDto, nav: NavController) {
    var open by remember { mutableStateOf(m.status == "in_progress") }
    val upcoming = m.status == "upcoming"
    Card2(color = if (m.status == "in_progress") Color.White else Soft.copy(alpha = 0.6f)) {
        Row(Modifier.fillMaxWidth().clickable(enabled = !upcoming) { open = !open }, verticalAlignment = Alignment.CenterVertically) {
            Icon(
                when (m.status) {
                    "completed" -> Icons.Filled.CheckCircle
                    "upcoming" -> Icons.Filled.Schedule
                    else -> Icons.Filled.RadioButtonUnchecked
                }, null, tint = if (m.status == "completed") Ok else Navy
            )
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text("Semana ${m.position} · ${m.title}", fontWeight = FontWeight.SemiBold, color = Navy)
                Sub(
                    when (m.status) {
                        "completed" -> "Completada · ${m.resourcesCount} recursos"
                        "upcoming" -> "Disponible el ${m.availableFrom?.let { fmtDateLong(it) } ?: "—"}"
                        "in_progress" -> "En curso"
                        else -> "${m.resourcesCount} recursos"
                    }
                )
            }
            if (!upcoming) Icon(if (open) Icons.Filled.KeyboardArrowUp else Icons.Filled.KeyboardArrowDown, null, tint = Muted)
        }
        if (open && !upcoming) {
            Spacer(Modifier.height(8.dp))
            m.items.forEach { i ->
                Row(Modifier.fillMaxWidth().clickable { nav.navigate("lesson/${i.id}") }.padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        when (i.type) { "video" -> Icons.Filled.PlayCircle; "forum" -> Icons.Filled.Forum; else -> Icons.AutoMirrored.Filled.Article },
                        null, tint = Navy
                    )
                    Spacer(Modifier.width(10.dp))
                    Column(Modifier.weight(1f)) {
                        Text(i.title, color = Navy, fontWeight = FontWeight.Medium)
                        val sub = if (i.type == "forum") "${i.postsCount} aportes" else i.subtitle
                        if (sub.isNotBlank()) Sub(sub)
                    }
                    if (i.completed) Icon(Icons.Filled.Check, null, tint = Ok) else Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null, tint = Muted)
                }
            }
            m.assignments.forEach { a ->
                Spacer(Modifier.height(6.dp))
                val pending = a.status == "pendiente"
                Card2(
                    color = if (pending) WarnBg else OkBg,
                    border = false,
                    onClick = { nav.navigate("assignment/${a.id}") }
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Pill(
                                when (a.status) { "pendiente" -> "Pendiente de entrega"; "entregada" -> "Entregada"; else -> "Evaluada" },
                                if (pending) Color(0xFFFDEACB) else OkBg,
                                if (pending) Color(0xFFA06700) else Ok
                            )
                            Spacer(Modifier.height(6.dp))
                            Text(a.title, fontWeight = FontWeight.Bold, color = Navy, fontSize = 15.sp)
                            Sub("${fmtDate(a.dueAt)}, ${fmtTime(a.dueAt)} · Valor: ${a.points} puntos" + (a.score?.let { " · Obtuviste ${fmtScore(it)}" } ?: ""))
                        }
                        Icon(Icons.Filled.ArrowForward, null, tint = Navy, modifier = Modifier.size(20.dp))
                    }
                }
            }
        }
    }
}

fun fmtScore(d: Double): String = if (d % 1.0 == 0.0) d.toInt().toString() else "%.1f".format(d)

// ============================================================ P-07 · Lección / contenido
private fun embedUrl(url: String): String {
    val id = Regex("(?:youtu\\.be/|v=|embed/)([A-Za-z0-9_-]{6,})").find(url)?.groupValues?.get(1)
    return if (id != null && url.contains("youtu")) "https://www.youtube.com/embed/$id" else url
}

@SuppressLint("SetJavaScriptEnabled")
@Composable
fun LessonScreen(id: Int, nav: NavController) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    Load(id, { Net.api.item(id) }) { it, reload ->
        var tab by remember { mutableIntStateOf(0) }
        Column(Modifier.fillMaxSize()) {
            TopBar("Mi lección", onBack = { nav.popBackStack() })
            Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp)) {
                H1("Semana ${it.modulePosition}: ${it.moduleTitle}")
                Sub("${it.courseTitle} · ${it.teacherName}")
                Spacer(Modifier.height(12.dp))
                if (it.type == "video" && !it.videoUrl.isNullOrBlank()) {
                    AndroidView(
                        factory = { c ->
                            WebView(c).apply {
                                settings.javaScriptEnabled = true
                                settings.mediaPlaybackRequiresUserGesture = true
                                loadUrl(embedUrl(it.videoUrl))
                            }
                        },
                        modifier = Modifier.fillMaxWidth().aspectRatio(16f / 9f).clip(RoundedCornerShape(14.dp))
                    )
                    Spacer(Modifier.height(10.dp))
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Pill(if (it.completed) "Completado" else "En curso", if (it.completed) OkBg else Soft, if (it.completed) Ok else Navy)
                    Spacer(Modifier.width(8.dp))
                    val kind = when (it.type) { "video" -> "Video"; "forum" -> "Foro"; else -> "Lectura" }
                    Sub(kind + (it.durationMin?.let { m -> " · $m min" } ?: "") + " · Semana ${it.modulePosition} de ${it.totalModules}")
                }
                Spacer(Modifier.height(8.dp))
                Text(it.title, fontSize = 18.sp, fontWeight = FontWeight.Bold, color = Navy)
                if (it.description.isNotBlank()) Sub(it.description, Modifier.padding(top = 4.dp))
                Spacer(Modifier.height(12.dp))

                if (it.type == "forum") {
                    ForumSection(it.id)
                } else {
                    TabRow(tab, containerColor = Color.Transparent, contentColor = Navy) {
                        Tab(tab == 0, { tab = 0 }, text = { Text("Recursos PDF") })
                        Tab(tab == 1, { tab = 1 }, text = { Text("Transcripción") })
                    }
                    Spacer(Modifier.height(10.dp))
                    if (tab == 0) {
                        if (it.resources.isEmpty()) EmptyState("Sin recursos adjuntos")
                        it.resources.forEach { r ->
                            Card2(onClick = { ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(r.url))) }) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Filled.PictureAsPdf, null, tint = Navy)
                                    Spacer(Modifier.width(10.dp))
                                    Column(Modifier.weight(1f)) {
                                        Text(r.title, fontWeight = FontWeight.Medium, color = Navy)
                                        val meta = listOfNotNull(r.pages?.let { p -> "PDF · $p páginas" }, r.sizeKb?.let { s -> "$s KB" }).joinToString(" · ")
                                        if (meta.isNotBlank()) Sub(meta)
                                    }
                                    Icon(Icons.Filled.Download, null, tint = Navy)
                                }
                            }
                            Spacer(Modifier.height(8.dp))
                        }
                    } else {
                        if (it.transcript.isBlank()) EmptyState("Sin transcripción disponible") else Text(it.transcript, color = Navy)
                    }
                }
                Spacer(Modifier.height(16.dp))
            }
            Column(Modifier.padding(16.dp)) {
                error?.let { e -> Text(e, color = Color(0xFFB3261E), fontSize = 13.sp) }
                PrimaryButton(if (it.next != null) "Completar y continuar" else "Completar", enabled = !busy) {
                    busy = true; error = null
                    scope.launch {
                        try {
                            val done = Net.api.completeItem(it.id)
                            val n = done.next
                            if (n != null) nav.navigate("lesson/${n.id}") { popUpTo("lesson/{id}") { inclusive = true } }
                            else nav.popBackStack()
                        } catch (e: Exception) {
                            error = Net.message(e)
                        }
                        busy = false
                    }
                }
                it.next?.let { n -> Sub("Siguiente: ${n.title}", Modifier.fillMaxWidth().padding(top = 6.dp)) }
            }
        }
    }
}

@Composable
private fun ForumSection(itemId: Int) {
    val scope = rememberCoroutineScope()
    var text by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    Load(itemId, { Net.api.posts(itemId) }) { posts, reload ->
        Column {
            if (posts.isEmpty()) EmptyState("Aún no hay aportes. ¡Sé la primera persona en participar!")
            posts.forEach { p ->
                Card2 {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Avatar(p.initials, 30.dp); Spacer(Modifier.width(8.dp))
                        Column { Text(p.author, fontWeight = FontWeight.SemiBold, color = Navy, fontSize = 13.sp); Sub(fmtDateTime(p.createdAt)) }
                    }
                    Spacer(Modifier.height(6.dp))
                    Text(p.body, color = Navy)
                }
                Spacer(Modifier.height(8.dp))
            }
            OutlinedTextField(text, { text = it }, label = { Text("Escribe tu aporte") }, modifier = Modifier.fillMaxWidth(), minLines = 2)
            Spacer(Modifier.height(8.dp))
            PrimaryButton("Publicar aporte", enabled = !busy && text.isNotBlank()) {
                busy = true
                scope.launch {
                    try { Net.api.post(itemId, PostBody(text.trim())); text = ""; reload() } catch (_: Exception) {}
                    busy = false
                }
            }
        }
    }
}
