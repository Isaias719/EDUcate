package com.educate.app

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Article
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

private val categoriesList = listOf("Programación", "Matemáticas", "Ciencias", "Diseño", "Negocios", "Idiomas", "Humanidades")
private val levelsList = listOf("Principiante", "Intermedio", "Avanzado")

// ============================================================ T-01 · Inicio del Docente
@Composable
fun TeacherHomeScreen(nav: NavController, onLogout: () -> Unit) {
    val scope = rememberCoroutineScope()
    var gradeFor by remember { mutableStateOf<ToReview?>(null) }
    var actionError by remember { mutableStateOf<String?>(null) }

    Load(Unit, { Net.api.teacherPanel() }) { p, reload ->
        LazyColumn(
            Modifier.fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            item {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Panel Docente", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = Navy, modifier = Modifier.weight(1f))
                    Box(Modifier.clickable { nav.navigate("teacher_profile") }) {
                        Avatar(p.user.initials, 38.dp)
                    }
                }
            }

            item {
                Column {
                    H1("Hola, ${p.user.name}")
                    Sub("Profesor/a · Periodo activo")
                }
            }

            item {
                Card2(color = Soft, border = false) {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Filled.MenuBook, null, tint = Navy, modifier = Modifier.size(20.dp))
                            Spacer(Modifier.width(10.dp))
                            Text("Cursos impartidos: ${p.coursesCount}", fontWeight = FontWeight.SemiBold, color = Navy, fontSize = 14.sp)
                        }
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Filled.People, null, tint = Navy, modifier = Modifier.size(20.dp))
                            Spacer(Modifier.width(10.dp))
                            Text("Alumnos activos: ${p.studentsCount}", fontWeight = FontWeight.SemiBold, color = Navy, fontSize = 14.sp)
                        }
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Filled.CheckCircleOutline, null, tint = Navy, modifier = Modifier.size(20.dp))
                            Spacer(Modifier.width(10.dp))
                            Text("Tareas por revisar: ${p.toReviewCount}", fontWeight = FontWeight.SemiBold, color = Navy, fontSize = 14.sp)
                        }
                    }
                }
            }

            item {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    H2("Mis cursos asignados", Modifier.weight(1f))
                    TextButton(onClick = { nav.navigate("teacher_courses") }) {
                        Text("Ver todos", color = Navy, fontWeight = FontWeight.SemiBold, fontSize = 12.sp)
                    }
                }
            }

            if (p.courses.isEmpty()) {
                item {
                    Card2(onClick = { nav.navigate("teacher_course_new") }) {
                        H2("Aún no tienes cursos creados")
                        Sub("Pulsa aquí para crear tu primer curso y comenzar a subir temas y actividades.")
                    }
                }
            } else {
                items(p.courses.take(3)) { c ->
                    Card2(onClick = { nav.navigate("teacher_course/${c.id}") }) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Filled.MenuBook, null, tint = Navy, modifier = Modifier.size(22.dp))
                            Spacer(Modifier.width(12.dp))
                            Column(Modifier.weight(1f)) {
                                Text(c.title, fontWeight = FontWeight.Bold, color = Navy, fontSize = 15.sp)
                                Sub("${if (c.groupName.isNotBlank()) "${c.groupName} · " else ""}${c.students} alumnos · ${c.modules} módulos")
                            }
                            Icon(Icons.Filled.ChevronRight, "Ver curso", tint = Muted)
                        }
                    }
                }
            }

            item {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    H2("Por calificar", Modifier.weight(1f))
                    if (p.toReview.isNotEmpty()) {
                        TextButton(onClick = { nav.navigate("teacher_grades") }) {
                            Text("Ver las ${p.toReview.size}", color = Navy, fontWeight = FontWeight.SemiBold, fontSize = 12.sp)
                        }
                    }
                }
            }

            if (p.toReview.isEmpty()) {
                item { EmptyState("¡Al día! No hay tareas pendientes de revisión.") }
            } else {
                items(p.toReview.take(5)) { r ->
                    Card2 {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Avatar(r.initials)
                            Spacer(Modifier.width(10.dp))
                            Column(Modifier.weight(1f)) {
                                Text(r.studentName, fontWeight = FontWeight.Bold, color = Navy, fontSize = 14.sp)
                                Sub("${r.assignmentTitle} · ${fmtDateTime(r.submittedAt)}")
                            }
                            Button(
                                onClick = { gradeFor = r },
                                colors = ButtonDefaults.buttonColors(containerColor = WarnBg, contentColor = Warn),
                                shape = RoundedCornerShape(8.dp),
                                contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp)
                            ) {
                                Text("Revisar", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                            }
                        }
                    }
                }
            }

            actionError?.let { e -> item { Text(e, color = ErrColor, fontSize = 13.sp) } }

            item {
                Spacer(Modifier.height(4.dp))
                PrimaryButton("Crear nuevo curso", icon = Icons.Filled.Add) {
                    nav.navigate("teacher_course_new")
                }
            }
        }

        gradeFor?.let { r ->
            var score by remember(r) { mutableStateOf("") }
            var fb by remember(r) { mutableStateOf("") }
            AlertDialog(
                onDismissRequest = { gradeFor = null },
                title = { Text("Calificar a ${r.studentName}", fontWeight = FontWeight.Bold, color = Navy) },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Sub("Actividad: ${r.assignmentTitle}")
                        OutlinedTextField(
                            score, { score = it },
                            label = { Text("Calificación (0 a ${r.points})") },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                            modifier = Modifier.fillMaxWidth()
                        )
                        OutlinedTextField(
                            fb, { fb = it },
                            label = { Text("Retroalimentación (opcional)") },
                            modifier = Modifier.fillMaxWidth(),
                            minLines = 2
                        )
                    }
                },
                confirmButton = {
                    val sVal = score.toDoubleOrNull()
                    val valid = sVal != null && sVal in 0.0..r.points.toDouble()
                    Button(
                        enabled = valid,
                        onClick = {
                            if (sVal != null) {
                                scope.launch {
                                    try {
                                        Net.api.grade(r.submissionId, GradeBody(sVal, fb))
                                        actionError = null
                                        reload()
                                    } catch (e: Exception) {
                                        actionError = Net.message(e)
                                    }
                                    gradeFor = null
                                }
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = Navy)
                    ) { Text("Guardar nota") }
                },
                dismissButton = { TextButton({ gradeFor = null }) { Text("Cancelar") } }
            )
        }
    }
}

// ============================================================ T-02 · Gestión de Cursos
@Composable
fun TeacherCoursesScreen(nav: NavController) {
    Load(Unit, { Net.api.teacherCourses() }) { list, reload ->
        Column(Modifier.fillMaxSize()) {
            TopBar("Mis Cursos", actions = {
                IconButton(onClick = { nav.navigate("teacher_course_new") }) {
                    Icon(Icons.Filled.Add, "Crear curso", tint = Navy)
                }
            })
            if (list.isEmpty()) {
                Box(Modifier.fillMaxSize().padding(24.dp), Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Icon(Icons.Filled.School, null, tint = Navy, modifier = Modifier.size(54.dp))
                        H2("No tienes cursos creados")
                        Sub("Crea tu primer curso para empezar a estructurar temarios, tareas y contenidos.")
                        PrimaryButton("Crear mi primer curso", modifier = Modifier.width(220.dp)) {
                            nav.navigate("teacher_course_new")
                        }
                    }
                }
            } else {
                LazyColumn(
                    Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(list) { c ->
                        Card2(onClick = { nav.navigate("teacher_course/${c.id}") }) {
                            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Column(Modifier.weight(1f)) {
                                        Text(c.title, fontWeight = FontWeight.Bold, color = Navy, fontSize = 17.sp)
                                        Sub("${if (c.groupName.isNotBlank()) c.groupName else "Sin grupo"}${if (c.period.isNotBlank()) " · ${c.period}" else ""}")
                                    }
                                    if (c.category.isNotBlank()) {
                                        Pill(c.category, Soft, Navy)
                                    }
                                }
                                if (c.description.isNotBlank()) {
                                    Text(c.description, color = Muted, fontSize = 12.sp, maxLines = 2)
                                }
                                HorizontalDivider(Modifier.padding(vertical = 4.dp), color = Line)
                                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Icon(Icons.Filled.People, null, tint = Navy, modifier = Modifier.size(16.dp))
                                            Spacer(Modifier.width(4.dp))
                                            Text("${c.studentsCount} alumnos", fontSize = 12.sp, color = Navy)
                                        }
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Icon(Icons.Filled.MenuBook, null, tint = Navy, modifier = Modifier.size(16.dp))
                                            Spacer(Modifier.width(4.dp))
                                            Text("${c.modulesCount} módulos", fontSize = 12.sp, color = Navy)
                                        }
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Icon(Icons.Filled.Assignment, null, tint = Navy, modifier = Modifier.size(16.dp))
                                            Spacer(Modifier.width(4.dp))
                                            Text("${c.assignmentsCount} tareas", fontSize = 12.sp, color = Navy)
                                        }
                                    }
                                    if (c.toReviewCount > 0) {
                                        Pill("${c.toReviewCount} por calificar", WarnBg, Warn)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

// ============================================================ T-03 · Crear / Editar Curso
@Composable
fun TeacherCourseFormScreen(nav: NavController, courseId: Int? = null) {
    val isEdit = courseId != null
    val scope = rememberCoroutineScope()
    var title by remember { mutableStateOf("") }
    var description by remember { mutableStateOf("") }
    var category by remember { mutableStateOf(categoriesList.first()) }
    var level by remember { mutableStateOf(levelsList.first()) }
    var groupName by remember { mutableStateOf("") }
    var period by remember { mutableStateOf("") }
    var durationWeeks by remember { mutableStateOf("") }
    var openEnrollment by remember { mutableStateOf(true) }
    var isFree by remember { mutableStateOf(true) }
    var imageUrl by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var loaded by remember { mutableStateOf(false) }

    LaunchedEffect(courseId) {
        if (isEdit && !loaded) {
            try {
                val c = Net.api.teacherCourseDetail(courseId!!)
                title = c.title
                description = c.description
                category = if (c.category in categoriesList) c.category else categoriesList.first()
                level = if (c.level in levelsList) c.level else levelsList.first()
                groupName = c.groupName
                period = c.period
                durationWeeks = c.durationWeeks?.toString() ?: ""
                openEnrollment = c.openEnrollment
                isFree = c.isFree
                imageUrl = c.imageUrl ?: ""
                loaded = true
            } catch (e: Exception) {
                error = Net.message(e)
            }
        }
    }

    Column(Modifier.fillMaxSize()) {
        TopBar(if (isEdit) "Editar Curso" else "Crear Nuevo Curso", onBack = { nav.popBackStack() })
        Column(
            Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Text("Información principal", fontWeight = FontWeight.Bold, color = Navy, fontSize = 16.sp)

            OutlinedTextField(
                title, { title = it; error = null },
                label = { Text("Título del curso *") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )

            OutlinedTextField(
                description, { description = it },
                label = { Text("Descripción del curso") },
                modifier = Modifier.fillMaxWidth(),
                minLines = 3
            )

            Text("Categoría:", fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = Navy)
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                categoriesList.forEach { cat ->
                    FilterChip(
                        selected = category == cat,
                        onClick = { category = cat },
                        label = { Text(cat, fontSize = 12.sp) }
                    )
                }
            }

            Text("Nivel:", fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = Navy)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                levelsList.forEach { lev ->
                    FilterChip(
                        selected = level == lev,
                        onClick = { level = lev },
                        label = { Text(lev, fontSize = 12.sp) },
                        modifier = Modifier.weight(1f)
                    )
                }
            }

            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    groupName, { groupName = it },
                    label = { Text("Grupo (ej. 101, A)") },
                    singleLine = true,
                    modifier = Modifier.weight(1f)
                )
                OutlinedTextField(
                    period, { period = it },
                    label = { Text("Periodo (ej. 2026-1)") },
                    singleLine = true,
                    modifier = Modifier.weight(1f)
                )
            }

            OutlinedTextField(
                durationWeeks, { durationWeeks = it },
                label = { Text("Duración estimada en semanas") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )

            OutlinedTextField(
                imageUrl, { imageUrl = it },
                label = { Text("URL de imagen de portada (opcional)") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )

            Card2(color = Soft, border = false) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("Inscripción abierta", fontWeight = FontWeight.SemiBold, color = Navy)
                        Sub("Permite que los alumnos encuentren este curso en el catálogo y se inscriban.")
                    }
                    Switch(checked = openEnrollment, onCheckedChange = { openEnrollment = it })
                }
            }

            error?.let { Text(it, color = ErrColor, fontSize = 13.sp) }

            PrimaryButton(
                if (busy) "Guardando…" else if (isEdit) "Actualizar Curso" else "Crear Curso",
                enabled = !busy && title.trim().length >= 3
            ) {
                busy = true; error = null
                scope.launch {
                    try {
                        val body = CourseBody(
                            title = title.trim(),
                            description = description.trim(),
                            category = category,
                            level = level,
                            durationWeeks = durationWeeks.toIntOrNull(),
                            isFree = isFree,
                            openEnrollment = openEnrollment,
                            groupName = groupName.trim(),
                            period = period.trim(),
                            imageUrl = imageUrl.trim().ifEmpty { null }
                        )
                        if (isEdit) {
                            Net.api.updateCourse(courseId!!, body)
                        } else {
                            Net.api.createCourse(body)
                        }
                        nav.popBackStack()
                    } catch (e: Exception) {
                        error = Net.message(e)
                    }
                    busy = false
                }
            }
        }
    }
}

// ============================================================ T-04 · Detalle y Gestión del Curso
@Composable
fun TeacherCourseDetailScreen(courseId: Int, nav: NavController) {
    val scope = rememberCoroutineScope()
    var tabIndex by remember { mutableIntStateOf(0) }
    var newModuleOpen by remember { mutableStateOf(false) }
    var newItemModuleId by remember { mutableStateOf<Int?>(null) }
    var newAssignmentOpen by remember { mutableStateOf(false) }
    var newAnnouncementOpen by remember { mutableStateOf(false) }
    var confirmDeleteCourse by remember { mutableStateOf(false) }
    var actionError by remember { mutableStateOf<String?>(null) }
    var duplicateOpen by remember { mutableStateOf(false) }

    val tabs = listOf("Temario", "Tareas", "Alumnos", "Avisos", "Analíticas")

    Load(courseId, { Net.api.teacherCourseDetail(courseId) }) { c, reload ->
        Column(Modifier.fillMaxSize()) {
            TopBar(c.title, onBack = { nav.popBackStack() }, actions = {
                IconButton(onClick = { duplicateOpen = true }) {
                    Icon(Icons.Filled.ContentCopy, "Duplicar curso", tint = Navy)
                }
                IconButton(onClick = { nav.navigate("teacher_course/$courseId/edit") }) {
                    Icon(Icons.Filled.Edit, "Editar curso", tint = Navy)
                }
                IconButton(onClick = { confirmDeleteCourse = true }) {
                    Icon(Icons.Filled.Delete, "Eliminar curso", tint = ErrColor)
                }
            })

            TabRow(selectedTabIndex = tabIndex, containerColor = Color.White, contentColor = Navy) {
                tabs.forEachIndexed { i, title ->
                    Tab(
                        selected = tabIndex == i,
                        onClick = { tabIndex = i },
                        text = { Text(title, fontWeight = FontWeight.Bold, fontSize = 13.sp) }
                    )
                }
            }

            actionError?.let { Text(it, color = ErrColor, modifier = Modifier.padding(12.dp)) }

            when (tabIndex) {
                0 -> { // Temario / Módulos
                    LazyColumn(
                        Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(16.dp),
                        verticalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        item {
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                                H2("Módulos del curso (${c.modules.size})")
                                Button(
                                    onClick = { newModuleOpen = true },
                                    colors = ButtonDefaults.buttonColors(containerColor = Navy),
                                    shape = RoundedCornerShape(8.dp),
                                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                                ) {
                                    Icon(Icons.Filled.Add, null, Modifier.size(16.dp))
                                    Spacer(Modifier.width(4.dp))
                                    Text("Agregar Módulo", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                }
                            }
                        }

                        if (c.modules.isEmpty()) {
                            item { EmptyState("Este curso aún no tiene módulos. Pulsa 'Agregar Módulo' para comenzar.") }
                        }

                        items(c.modules) { m ->
                            Card2 {
                                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text(m.title, fontWeight = FontWeight.Bold, color = Navy, fontSize = 16.sp, modifier = Modifier.weight(1f))
                                        IconButton(onClick = { newItemModuleId = m.id }) {
                                            Icon(Icons.Filled.AddCircleOutline, "Agregar contenido", tint = Navy)
                                        }
                                        IconButton(onClick = {
                                            scope.launch {
                                                try { Net.api.deleteModule(m.id); reload() } catch (e: Exception) { actionError = Net.message(e) }
                                            }
                                        }) {
                                            Icon(Icons.Filled.DeleteOutline, "Borrar módulo", tint = Muted)
                                        }
                                    }

                                    if (m.items.isEmpty()) {
                                        Sub("Sin contenidos en este módulo aún. Pulsa el icono (+) para añadir lecturas, videos o foros.")
                                    } else {
                                        m.items.forEach { itm ->
                                            Row(
                                                Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp)).background(Soft.copy(alpha = 0.5f)).padding(8.dp),
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                val icon = when (itm.type) {
                                                    "video" -> Icons.Filled.PlayCircle
                                                    "forum" -> Icons.Filled.Forum
                                                    else -> Icons.AutoMirrored.Filled.Article
                                                }
                                                Icon(icon, null, tint = Navy, modifier = Modifier.size(20.dp))
                                                Spacer(Modifier.width(8.dp))
                                                Column(Modifier.weight(1f)) {
                                                    Text(itm.title, fontWeight = FontWeight.SemiBold, color = Navy, fontSize = 13.sp)
                                                    Sub("${itm.type.uppercase()}${if (itm.durationMin != null) " · ${itm.durationMin} min" else ""}")
                                                }
                                                IconButton(onClick = {
                                                    scope.launch {
                                                        try { Net.api.deleteItem(itm.id); reload() } catch (e: Exception) { actionError = Net.message(e) }
                                                    }
                                                }, modifier = Modifier.size(28.dp)) {
                                                    Icon(Icons.Filled.Close, "Eliminar", tint = Muted, modifier = Modifier.size(16.dp))
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                1 -> { // Tareas
                    LazyColumn(
                        Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(16.dp),
                        verticalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        item {
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                                H2("Tareas y Actividades (${c.assignments.size})")
                                Button(
                                    onClick = { newAssignmentOpen = true },
                                    colors = ButtonDefaults.buttonColors(containerColor = Navy),
                                    shape = RoundedCornerShape(8.dp),
                                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                                ) {
                                    Icon(Icons.Filled.Add, null, Modifier.size(16.dp))
                                    Spacer(Modifier.width(4.dp))
                                    Text("Nueva Tarea", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                }
                            }
                        }

                        if (c.assignments.isEmpty()) {
                            item { EmptyState("No has publicado actividades ni tareas en este curso aún.") }
                        }

                        items(c.assignments) { a ->
                            Card2(onClick = { nav.navigate("teacher_assignment/${a.id}") }) {
                                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Column(Modifier.weight(1f)) {
                                            Text(a.title, fontWeight = FontWeight.Bold, color = Navy, fontSize = 15.sp)
                                            Sub("${a.kind.uppercase()} · ${a.points} puntos · Límite: ${fmtDateTime(a.dueAt)}")
                                        }
                                        Icon(Icons.Filled.ChevronRight, "Ver entregas", tint = Muted)
                                    }
                                    if (a.instructions.isNotBlank()) {
                                        Text(a.instructions, color = Muted, fontSize = 12.sp, maxLines = 2)
                                    }
                                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                        Text("${a.submissionsCount} entregadas · ${a.gradedCount} calificadas", fontSize = 12.sp, color = Navy)
                                        if (a.pendingCount > 0) {
                                            Pill("${a.pendingCount} por calificar", WarnBg, Warn)
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                2 -> { // Alumnos
                    LazyColumn(
                        Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(16.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        item {
                            H2("Alumnos inscritos (${c.students.size})")
                        }
                        if (c.students.isEmpty()) {
                            item { EmptyState("Aún no hay alumnos inscritos en este curso.") }
                        }
                        items(c.students) { s ->
                            Card2 {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Avatar(s.initials)
                                    Spacer(Modifier.width(12.dp))
                                    Column(Modifier.weight(1f)) {
                                        Text(s.name, fontWeight = FontWeight.Bold, color = Navy, fontSize = 15.sp)
                                        Sub(s.email)
                                    }
                                    Sub("Inscrito: ${fmtDate(s.enrolledAt)}")
                                }
                            }
                        }
                    }
                }

                3 -> { // Avisos
                    LazyColumn(
                        Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(16.dp),
                        verticalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        item {
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                                H2("Avisos y Comunicados (${c.announcements.size})")
                                Button(
                                    onClick = { newAnnouncementOpen = true },
                                    colors = ButtonDefaults.buttonColors(containerColor = Navy),
                                    shape = RoundedCornerShape(8.dp),
                                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                                ) {
                                    Icon(Icons.Filled.Campaign, null, Modifier.size(16.dp))
                                    Spacer(Modifier.width(4.dp))
                                    Text("Publicar Aviso", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                }
                            }
                        }

                        if (c.announcements.isEmpty()) {
                            item { EmptyState("No se han publicado avisos para este grupo.") }
                        }

                        items(c.announcements) { an ->
                            Card2 {
                                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text(an.title, fontWeight = FontWeight.Bold, color = Navy, fontSize = 15.sp, modifier = Modifier.weight(1f))
                                        IconButton(onClick = {
                                            scope.launch {
                                                try { Net.api.deleteAnnouncement(an.id); reload() } catch (e: Exception) { actionError = Net.message(e) }
                                            }
                                        }, modifier = Modifier.size(24.dp)) {
                                            Icon(Icons.Filled.Close, "Eliminar aviso", tint = Muted, modifier = Modifier.size(16.dp))
                                        }
                                    }
                                    Text(an.body, color = Navy, fontSize = 13.sp)
                                    Sub(fmtDateTime(an.createdAt))
                                }
                            }
                        }
                    }
                }

                4 -> { // Analíticas (CAMBIO 2)
                    Load(courseId, { Net.api.courseAnalytics(courseId) }) { analytics, _ ->
                        Column(
                            Modifier.fillMaxSize().verticalScroll(rememberScrollState()),
                            verticalArrangement = Arrangement.spacedBy(14.dp)
                        ) {
                            Spacer(Modifier.height(4.dp))
                            // Total students card
                            Card2(color = Navy, border = false) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Filled.People, null, tint = Soft, modifier = Modifier.size(28.dp))
                                    Spacer(Modifier.width(12.dp))
                                    Column {
                                        Text("${analytics.totalStudents} alumnos inscritos", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 18.sp)
                                        Sub("Total de estudiantes en este curso")
                                    }
                                }
                            }

                            // Module progress section
                            if (analytics.modules.isNotEmpty()) {
                                Text("Avance por módulo", fontWeight = FontWeight.Bold, color = Navy, fontSize = 15.sp, modifier = Modifier.padding(horizontal = 16.dp))
                                analytics.modules.forEach { mod ->
                                    Card2 {
                                        Text("Sem. ${mod.position} · ${mod.title}", fontWeight = FontWeight.SemiBold, color = Navy)
                                        Spacer(Modifier.height(6.dp))
                                        ProgressBar(mod.percent)
                                        Spacer(Modifier.height(4.dp))
                                        Sub("${mod.completedCount} alumnos completaron · ${mod.percent.toInt()}% completado")
                                    }
                                }
                            }

                            // At-risk students section
                            if (analytics.atRisk.isNotEmpty()) {
                                Text("Alumnos en riesgo", fontWeight = FontWeight.Bold, color = Warn, fontSize = 15.sp, modifier = Modifier.padding(horizontal = 16.dp))
                                analytics.atRisk.forEach { student ->
                                    Card2(color = WarnBg, border = false) {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Avatar(student.name.take(2).uppercase())
                                            Spacer(Modifier.width(10.dp))
                                            Column(Modifier.weight(1f)) {
                                                Text(student.name, fontWeight = FontWeight.SemiBold, color = Navy)
                                                Sub(student.email)
                                                if (student.lastActivity != null)
                                                    Sub("Sin actividad desde ${student.lastActivity}")
                                                else
                                                    Sub("Sin actividad registrada")
                                            }
                                            Pill("En riesgo", WarnBg, Warn)
                                        }
                                    }
                                }
                            }

                            // Assignment stats section
                            if (analytics.assignmentStats.isNotEmpty()) {
                                Text("Estadísticas de tareas", fontWeight = FontWeight.Bold, color = Navy, fontSize = 15.sp, modifier = Modifier.padding(horizontal = 16.dp))
                                analytics.assignmentStats.forEach { asg ->
                                    Card2 {
                                        Text(asg.title, fontWeight = FontWeight.SemiBold, color = Navy)
                                        Spacer(Modifier.height(4.dp))
                                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                            Sub("Entregas: ${asg.submittedCount}")
                                            Sub("Calificadas: ${asg.gradedCount}")
                                            Sub(asg.avgScore?.let { "Promedio: ${"%.1f".format(it)}" } ?: "Sin promedio")
                                        }
                                    }
                                }
                            }

                            Spacer(Modifier.height(16.dp))
                        }
                    }
                }
            }
        }

        // Dialog: Nuevo Módulo
        if (newModuleOpen) {
            var modTitle by remember { mutableStateOf("") }
            AlertDialog(
                onDismissRequest = { newModuleOpen = false },
                title = { Text("Nuevo Módulo / Tema", fontWeight = FontWeight.Bold, color = Navy) },
                text = {
                    OutlinedTextField(
                        modTitle, { modTitle = it },
                        label = { Text("Título del módulo (ej. Tema 1: Introducción)") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                },
                confirmButton = {
                    Button(
                        enabled = modTitle.isNotBlank(),
                        onClick = {
                            scope.launch {
                                try {
                                    Net.api.createModule(courseId, ModuleBody(modTitle.trim()))
                                    reload()
                                } catch (e: Exception) {
                                    actionError = Net.message(e)
                                }
                                newModuleOpen = false
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = Navy)
                    ) { Text("Crear") }
                },
                dismissButton = { TextButton({ newModuleOpen = false }) { Text("Cancelar") } }
            )
        }

        // Dialog: Nuevo Contenido en Módulo
        newItemModuleId?.let { modId ->
            var itemType by remember { mutableStateOf("reading") }
            var itemTitle by remember { mutableStateOf("") }
            var itemSubtitle by remember { mutableStateOf("") }
            var itemDesc by remember { mutableStateOf("") }
            var itemVideoUrl by remember { mutableStateOf("") }
            var itemDuration by remember { mutableStateOf("") }

            AlertDialog(
                onDismissRequest = { newItemModuleId = null },
                title = { Text("Añadir Contenido", fontWeight = FontWeight.Bold, color = Navy) },
                text = {
                    Column(
                        Modifier.verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text("Tipo de contenido:", fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = Navy)
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            listOf("reading" to "Lectura", "video" to "Video", "forum" to "Foro").forEach { (t, label) ->
                                FilterChip(
                                    selected = itemType == t,
                                    onClick = { itemType = t },
                                    label = { Text(label, fontSize = 12.sp) }
                                )
                            }
                        }

                        OutlinedTextField(
                            itemTitle, { itemTitle = it },
                            label = { Text("Título del contenido *") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )

                        OutlinedTextField(
                            itemSubtitle, { itemSubtitle = it },
                            label = { Text("Subtítulo (opcional)") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )

                        OutlinedTextField(
                            itemDesc, { itemDesc = it },
                            label = { Text(if (itemType == "forum") "Pregunta / Consigna de debate" else "Descripción o contenido") },
                            modifier = Modifier.fillMaxWidth(),
                            minLines = 2
                        )

                        if (itemType == "video") {
                            OutlinedTextField(
                                itemVideoUrl, { itemVideoUrl = it },
                                label = { Text("URL del video (YouTube / Vimeo)") },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth()
                            )
                            OutlinedTextField(
                                itemDuration, { itemDuration = it },
                                label = { Text("Duración en minutos") },
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                    }
                },
                confirmButton = {
                    Button(
                        enabled = itemTitle.isNotBlank(),
                        onClick = {
                            scope.launch {
                                try {
                                    val body = ItemBody(
                                        type = itemType,
                                        title = itemTitle.trim(),
                                        subtitle = itemSubtitle.trim(),
                                        description = itemDesc.trim(),
                                        videoUrl = itemVideoUrl.trim().ifEmpty { null },
                                        durationMin = itemDuration.toIntOrNull()
                                    )
                                    Net.api.createItem(modId, body)
                                    reload()
                                } catch (e: Exception) {
                                    actionError = Net.message(e)
                                }
                                newItemModuleId = null
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = Navy)
                    ) { Text("Añadir") }
                },
                dismissButton = { TextButton({ newItemModuleId = null }) { Text("Cancelar") } }
            )
        }

        // Dialog: Nueva Tarea
        if (newAssignmentOpen) {
            var asgTitle by remember { mutableStateOf("") }
            var asgInstructions by remember { mutableStateOf("") }
            var asgKind by remember { mutableStateOf("tarea") }
            var asgPoints by remember { mutableStateOf("100") }
            var asgDays by remember { mutableStateOf("7") }
            var selectedModuleId by remember { mutableStateOf(c.modules.firstOrNull()?.id) }

            AlertDialog(
                onDismissRequest = { newAssignmentOpen = false },
                title = { Text("Nueva Tarea / Actividad", fontWeight = FontWeight.Bold, color = Navy) },
                text = {
                    Column(
                        Modifier.verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedTextField(
                            asgTitle, { asgTitle = it },
                            label = { Text("Título de la actividad *") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )

                        OutlinedTextField(
                            asgInstructions, { asgInstructions = it },
                            label = { Text("Instrucciones de entrega") },
                            modifier = Modifier.fillMaxWidth(),
                            minLines = 2
                        )

                        Text("Tipo de actividad:", fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = Navy)
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            listOf("tarea" to "Tarea (Archivo)", "cuestionario" to "Cuestionario", "foro" to "Foro").forEach { (k, label) ->
                                FilterChip(
                                    selected = asgKind == k,
                                    onClick = { asgKind = k },
                                    label = { Text(label, fontSize = 12.sp) }
                                )
                            }
                        }

                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedTextField(
                                asgPoints, { asgPoints = it },
                                label = { Text("Puntos máx.") },
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                singleLine = true,
                                modifier = Modifier.weight(1f)
                            )
                            OutlinedTextField(
                                asgDays, { asgDays = it },
                                label = { Text("Plazo (días)") },
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                singleLine = true,
                                modifier = Modifier.weight(1f)
                            )
                        }

                        if (c.modules.isNotEmpty()) {
                            Text("Módulo asignado:", fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = Navy)
                            c.modules.forEach { m ->
                                Row(
                                    Modifier.fillMaxWidth().clickable { selectedModuleId = m.id },
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    RadioButton(selectedModuleId == m.id, { selectedModuleId = m.id })
                                    Text(m.title, color = Navy, fontSize = 13.sp)
                                }
                            }
                        }
                    }
                },
                confirmButton = {
                    Button(
                        enabled = asgTitle.isNotBlank() && (asgPoints.toIntOrNull() ?: 0) > 0,
                        onClick = {
                            scope.launch {
                                try {
                                    val days = asgDays.toLongOrNull() ?: 7L
                                    val dueIso = LocalDateTime.now().plusDays(days)
                                        .format(DateTimeFormatter.ISO_LOCAL_DATE_TIME) + "Z"
                                    val body = AssignmentBody(
                                        title = asgTitle.trim(),
                                        instructions = asgInstructions.trim(),
                                        kind = asgKind,
                                        points = asgPoints.toIntOrNull() ?: 100,
                                        dueAt = dueIso,
                                        moduleId = selectedModuleId
                                    )
                                    Net.api.createAssignment(courseId, body)
                                    reload()
                                } catch (e: Exception) {
                                    actionError = Net.message(e)
                                }
                                newAssignmentOpen = false
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = Navy)
                    ) { Text("Publicar Tarea") }
                },
                dismissButton = { TextButton({ newAssignmentOpen = false }) { Text("Cancelar") } }
            )
        }

        // Dialog: Nuevo Aviso
        if (newAnnouncementOpen) {
            var annTitle by remember { mutableStateOf("") }
            var annBody by remember { mutableStateOf("") }
            AlertDialog(
                onDismissRequest = { newAnnouncementOpen = false },
                title = { Text("Publicar Aviso al Grupo", fontWeight = FontWeight.Bold, color = Navy) },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(
                            annTitle, { annTitle = it },
                            label = { Text("Título del comunicado") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                        OutlinedTextField(
                            annBody, { annBody = it },
                            label = { Text("Mensaje") },
                            modifier = Modifier.fillMaxWidth(),
                            minLines = 3
                        )
                    }
                },
                confirmButton = {
                    Button(
                        enabled = annTitle.isNotBlank() && annBody.isNotBlank(),
                        onClick = {
                            scope.launch {
                                try {
                                    Net.api.createAnnouncement(courseId, AnnouncementBody(annTitle.trim(), annBody.trim()))
                                    reload()
                                } catch (e: Exception) {
                                    actionError = Net.message(e)
                                }
                                newAnnouncementOpen = false
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = Navy)
                    ) { Text("Publicar") }
                },
                dismissButton = { TextButton({ newAnnouncementOpen = false }) { Text("Cancelar") } }
            )
        }

        // Dialog: Confirmar Borrado de Curso
        if (confirmDeleteCourse) {
            AlertDialog(
                onDismissRequest = { confirmDeleteCourse = false },
                title = { Text("¿Eliminar este curso?", fontWeight = FontWeight.Bold, color = ErrColor) },
                text = { Text("Se eliminarán todos los módulos, tareas y avisos de '${c.title}'. Esta acción no se puede deshacer.") },
                confirmButton = {
                    Button(
                        onClick = {
                            scope.launch {
                                try {
                                    Net.api.deleteCourse(courseId)
                                    nav.popBackStack()
                                } catch (e: Exception) {
                                    actionError = Net.message(e)
                                }
                                confirmDeleteCourse = false
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = ErrColor)
                    ) { Text("Sí, eliminar curso") }
                },
                dismissButton = { TextButton({ confirmDeleteCourse = false }) { Text("Cancelar") } }
            )
        }
    }
}

// ============================================================ T-05 · Revisión de Entregas por Tarea
@Composable
fun TeacherAssignmentScreen(assignmentId: Int, nav: NavController) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var selectedTab by remember { mutableIntStateOf(0) }
    var gradeForSub by remember { mutableStateOf<StudentSub?>(null) }
    var actionError by remember { mutableStateOf<String?>(null) }

    Load(assignmentId, { Net.api.assignmentSubmissions(assignmentId) }) { data, reload ->
        val a = data.assignment
        val subs = data.submissions
        val pendingStudents = data.pendingStudents

        Column(Modifier.fillMaxSize()) {
            TopBar("Revisión de Tarea", onBack = { nav.popBackStack() })

            Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Card2(color = Navy, border = false) {
                    Text(a.title, fontWeight = FontWeight.Bold, color = Color.White, fontSize = 18.sp)
                    Sub("Valor: ${a.points} puntos · Límite: ${fmtDateTime(a.dueAt)}", Modifier.padding(top = 2.dp))
                    if (a.instructions.isNotBlank()) {
                        Spacer(Modifier.height(6.dp))
                        Text(a.instructions, color = Soft, fontSize = 13.sp)
                    }
                }

                Row(Modifier.fillMaxWidth(), Arrangement.SpaceEvenly) {
                    TStat(subs.size, "Entregadas")
                    TStat(subs.count { it.score == null }, "Por calificar")
                    TStat(subs.count { it.score != null }, "Calificadas")
                    TStat(pendingStudents.size, "Sin entrega")
                }

                TabRow(selectedTabIndex = selectedTab, containerColor = Color.White, contentColor = Navy) {
                    Tab(
                        selected = selectedTab == 0,
                        onClick = { selectedTab = 0 },
                        text = { Text("Entregas (${subs.size})", fontSize = 13.sp, fontWeight = FontWeight.Bold) }
                    )
                    Tab(
                        selected = selectedTab == 1,
                        onClick = { selectedTab = 1 },
                        text = { Text("Sin entrega (${pendingStudents.size})", fontSize = 13.sp, fontWeight = FontWeight.Bold) }
                    )
                }
            }

            actionError?.let { Text(it, color = ErrColor, modifier = Modifier.padding(horizontal = 16.dp)) }

            if (selectedTab == 0) {
                if (subs.isEmpty()) {
                    EmptyState("Aún no se han recibido entregas para esta tarea.")
                } else {
                    LazyColumn(
                        Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(16.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        items(subs) { s ->
                            Card2 {
                                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Avatar(s.initials)
                                        Spacer(Modifier.width(10.dp))
                                        Column(Modifier.weight(1f)) {
                                            Text(s.studentName, fontWeight = FontWeight.Bold, color = Navy)
                                            Sub("${s.studentEmail} · ${fmtDateTime(s.submittedAt)}")
                                        }
                                        if (s.score != null) {
                                            Pill("Nota: ${s.score.toInt()}/${a.points}", OkBg, Ok)
                                        } else {
                                            Pill("Pendiente", WarnBg, Warn)
                                        }
                                    }

                                    Row(
                                        Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp)).background(Soft.copy(alpha = 0.5f)).padding(8.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Icon(Icons.Filled.Description, null, tint = Navy, modifier = Modifier.size(20.dp))
                                        Spacer(Modifier.width(8.dp))
                                        Column(Modifier.weight(1f)) {
                                            Text(s.filename, fontWeight = FontWeight.Medium, color = Navy, fontSize = 12.sp)
                                            Sub(fmtSize(s.sizeBytes))
                                        }
                                        TextButton(onClick = {
                                            val url = "${BuildConfig.BASE_URL}submissions/${s.id}/file"
                                            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
                                            try { ctx.startActivity(intent) } catch (_: Exception) {}
                                        }) {
                                            Text("Abrir archivo", fontSize = 12.sp, color = Navy, fontWeight = FontWeight.Bold)
                                        }
                                    }

                                    if (s.score != null && s.feedback.isNotBlank()) {
                                        Text("Retroalimentación: ${s.feedback}", color = Muted, fontSize = 12.sp)
                                    }

                                    Button(
                                        onClick = { gradeForSub = s },
                                        modifier = Modifier.fillMaxWidth(),
                                        colors = ButtonDefaults.buttonColors(containerColor = if (s.score != null) Soft else Navy),
                                        shape = RoundedCornerShape(8.dp)
                                    ) {
                                        Text(
                                            if (s.score != null) "Modificar calificación" else "Calificar entrega",
                                            color = if (s.score != null) Navy else Color.White,
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 13.sp
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            } else {
                if (pendingStudents.isEmpty()) {
                    EmptyState("¡Todos los alumnos inscritos han entregado esta actividad!")
                } else {
                    LazyColumn(
                        Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(pendingStudents) { st ->
                            Card2 {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Avatar(st.initials)
                                    Spacer(Modifier.width(10.dp))
                                    Column(Modifier.weight(1f)) {
                                        Text(st.name, fontWeight = FontWeight.SemiBold, color = Navy)
                                        Sub(st.email)
                                    }
                                    Pill("Sin entrega", WarnBg, Warn)
                                }
                            }
                        }
                    }
                }
            }
        }

        gradeForSub?.let { sub ->
            var scoreVal by remember(sub) { mutableStateOf(sub.score?.toString() ?: "") }
            var feedbackVal by remember(sub) { mutableStateOf(sub.feedback) }

            AlertDialog(
                onDismissRequest = { gradeForSub = null },
                title = { Text("Calificar a ${sub.studentName}", fontWeight = FontWeight.Bold, color = Navy) },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Sub("Puntos posibles: 0 a ${a.points}")
                        OutlinedTextField(
                            scoreVal, { scoreVal = it },
                            label = { Text("Calificación obtenida") },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                            modifier = Modifier.fillMaxWidth()
                        )
                        OutlinedTextField(
                            feedbackVal, { feedbackVal = it },
                            label = { Text("Comentarios y retroalimentación") },
                            modifier = Modifier.fillMaxWidth(),
                            minLines = 3
                        )
                    }
                },
                confirmButton = {
                    val sNum = scoreVal.toDoubleOrNull()
                    val canSave = sNum != null && sNum in 0.0..a.points.toDouble()
                    Button(
                        enabled = canSave,
                        onClick = {
                            if (sNum != null) {
                                scope.launch {
                                    try {
                                        Net.api.grade(sub.id, GradeBody(sNum, feedbackVal.trim()))
                                        reload()
                                    } catch (e: Exception) {
                                        actionError = Net.message(e)
                                    }
                                    gradeForSub = null
                                }
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = Navy)
                    ) { Text("Guardar nota") }
                },
                dismissButton = { TextButton({ gradeForSub = null }) { Text("Cancelar") } }
            )
        }
    }
}

// ============================================================ T-06 · Bandeja Global de Entregas
@Composable
fun TeacherSubmissionsScreen(nav: NavController) {
    val scope = rememberCoroutineScope()
    var gradeFor by remember { mutableStateOf<ToReview?>(null) }
    var actionError by remember { mutableStateOf<String?>(null) }

    Load(Unit, { Net.api.teacherPanel() }) { panel, reload ->
        Column(Modifier.fillMaxSize()) {
            TopBar("Entregas por Calificar")

            actionError?.let { Text(it, color = ErrColor, modifier = Modifier.padding(16.dp)) }

            if (panel.toReview.isEmpty()) {
                Box(Modifier.fillMaxSize().padding(24.dp), Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Icon(Icons.Filled.CheckCircle, null, tint = Ok, modifier = Modifier.size(54.dp))
                        H2("Todo revisado")
                        Sub("No hay entregas pendientes de calificación en ninguno de tus cursos.")
                    }
                }
            } else {
                LazyColumn(
                    Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    item {
                        Card2(color = Soft, border = false) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Filled.AssignmentTurnedIn, null, tint = Navy)
                                Spacer(Modifier.width(8.dp))
                                Text("${panel.toReview.size} entregas esperan tu retroalimentación.", color = Navy, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                            }
                        }
                    }

                    items(panel.toReview) { r ->
                        Card2 {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Avatar(r.initials)
                                Spacer(Modifier.width(10.dp))
                                Column(Modifier.weight(1f)) {
                                    Text(r.studentName, fontWeight = FontWeight.Bold, color = Navy)
                                    Sub("${r.assignmentTitle} · ${fmtDateTime(r.submittedAt)}")
                                }
                                Button(
                                    onClick = { gradeFor = r },
                                    colors = ButtonDefaults.buttonColors(containerColor = WarnBg, contentColor = Warn),
                                    shape = RoundedCornerShape(8.dp)
                                ) {
                                    Text("Calificar", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                                }
                            }
                        }
                    }
                }
            }
        }

        gradeFor?.let { r ->
            var score by remember(r) { mutableStateOf("") }
            var fb by remember(r) { mutableStateOf("") }
            AlertDialog(
                onDismissRequest = { gradeFor = null },
                title = { Text("Calificar a ${r.studentName}", fontWeight = FontWeight.Bold, color = Navy) },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Sub("Actividad: ${r.assignmentTitle}")
                        OutlinedTextField(
                            score, { score = it },
                            label = { Text("Calificación (0 a ${r.points})") },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                            modifier = Modifier.fillMaxWidth()
                        )
                        OutlinedTextField(
                            fb, { fb = it },
                            label = { Text("Retroalimentación") },
                            modifier = Modifier.fillMaxWidth(),
                            minLines = 2
                        )
                    }
                },
                confirmButton = {
                    val sVal = score.toDoubleOrNull()
                    val valid = sVal != null && sVal in 0.0..r.points.toDouble()
                    Button(
                        enabled = valid,
                        onClick = {
                            if (sVal != null) {
                                scope.launch {
                                    try {
                                        Net.api.grade(r.submissionId, GradeBody(sVal, fb))
                                        reload()
                                    } catch (e: Exception) {
                                        actionError = Net.message(e)
                                    }
                                    gradeFor = null
                                }
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = Navy)
                    ) { Text("Guardar nota") }
                },
                dismissButton = { TextButton({ gradeFor = null }) { Text("Cancelar") } }
            )
        }
    }
}

// ============================================================ T-07 · Perfil del Docente
@Composable
fun TeacherProfileScreen(nav: NavController, onLogout: () -> Unit, onSwitchToStudent: () -> Unit = {}) {
    Column(Modifier.fillMaxSize()) {
        TopBar("Mi Perfil Docente", onBack = { nav.popBackStack() })
        Column(
            Modifier.fillMaxSize().padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Spacer(Modifier.height(12.dp))
            Avatar(Net.initials, 72.dp)
            Text(Net.userName, fontSize = 22.sp, fontWeight = FontWeight.Bold, color = Navy)
            Pill("DOCENTE / PROFESOR", Soft, Navy)

            Card2(Modifier.fillMaxWidth()) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Privilegios de tu cuenta:", fontWeight = FontWeight.Bold, color = Navy, fontSize = 14.sp)
                    Text("✓ Creación y publicación de cursos completos.", color = Muted, fontSize = 13.sp)
                    Text("✓ Organización de temarios en módulos y semanas.", color = Muted, fontSize = 13.sp)
                    Text("✓ Contenidos multimedia (lecturas, videos y foros).", color = Muted, fontSize = 13.sp)
                    Text("✓ Asignación de tareas con fechas límites y puntos.", color = Muted, fontSize = 13.sp)
                    Text("✓ Revisión y calificación de entregas de estudiantes.", color = Muted, fontSize = 13.sp)
                    Text("✓ Publicación de comunicados y avisos oficiales.", color = Muted, fontSize = 13.sp)
                }
            }

            Spacer(Modifier.weight(1f))

            Button(
                onClick = onSwitchToStudent,
                modifier = Modifier.fillMaxWidth().height(48.dp),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Soft, contentColor = Navy)
            ) {
                Icon(Icons.Filled.School, null, Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text("Cambiar a Modo Estudiante", fontWeight = FontWeight.Bold)
            }

            OutlineButton("Cerrar sesión") {
                onLogout()
            }
            Spacer(Modifier.height(12.dp))
        }
    }
}

@Composable
private fun TStat(v: Int, label: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text("$v", fontSize = 22.sp, fontWeight = FontWeight.Bold, color = Navy)
        Sub(label)
    }
}
