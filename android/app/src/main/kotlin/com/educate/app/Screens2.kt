package com.educate.app

import android.provider.OpenableColumns
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import coil.compose.AsyncImage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.toRequestBody
import java.time.LocalDate

private val ERR = Color(0xFFB3261E)

private data class Picked(val uri: Uri, val name: String, val size: Long)

// ============================================================ P-04 · Entrega de actividad
@Composable
fun AssignmentScreen(id: Int, nav: NavController) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var picked by remember { mutableStateOf<Picked?>(null) }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            var name = "archivo"; var size = 0L
            ctx.contentResolver.query(uri, null, null, null, null)?.use { c ->
                if (c.moveToFirst()) {
                    c.getColumnIndex(OpenableColumns.DISPLAY_NAME).takeIf { it >= 0 }?.let { name = c.getString(it) }
                    c.getColumnIndex(OpenableColumns.SIZE).takeIf { it >= 0 }?.let { size = c.getLong(it) }
                }
            }
            val ext = name.substringAfterLast('.', "").lowercase()
            error = when {
                ext !in listOf("pdf", "zip", "docx") -> "Formato no permitido (PDF, ZIP o DOCX)"
                size > 25L * 1024 * 1024 -> "El archivo supera los 25 MB"
                else -> null
            }
            picked = if (error == null) Picked(uri, name, size) else null
        }
    }

    Load(id, { Net.api.assignment(id) }) { a, reload ->
        Column(Modifier.fillMaxSize()) {
            TopBar("Entrega de actividad", onBack = { nav.popBackStack() })
            Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Column {
                    Sub("${a.courseTitle} · ${a.kind.uppercase()}")
                    H1(a.title)
                    Sub("Valor: ${a.points} puntos")
                }
                if (a.submission != null) {
                    val s = a.submission
                    Card2(color = OkBg, border = false) {
                        Text(if (s.score != null) "Actividad evaluada" else "Entrega exitosa", fontWeight = FontWeight.Bold, color = Ok)
                        Sub("${s.filename} · ${fmtSize(s.sizeBytes)} · ${fmtDateTime(s.submittedAt)}")
                        if (s.score != null) {
                            Spacer(Modifier.height(6.dp))
                            Text("Calificación: ${fmtScore(s.score)} / ${a.points}", fontWeight = FontWeight.Bold, color = Navy)
                            if (s.feedback.isNotBlank()) Text(s.feedback, color = Navy, fontSize = 13.sp)
                        } else Sub("Pendiente de revisión")
                    }
                }
                Card2(color = Soft, border = false) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Filled.CalendarMonth, null, tint = Navy)
                        Spacer(Modifier.width(8.dp))
                        Text("Fecha límite: ${fmtDateLong(a.dueAt)}, ${fmtTime(a.dueAt)}", color = Navy, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                    }
                }
                if (a.instructions.isNotBlank()) Card2 {
                    H2("¿Qué debes entregar?")
                    Spacer(Modifier.height(6.dp))
                    Text(a.instructions, color = Navy)
                }
                val evaluated = a.submission?.score != null
                if (a.closed && a.submission == null) {
                    Card2(color = WarnBg, border = false) { Text("La fecha límite ya pasó y no se recibió una entrega.", color = Warn) }
                } else if (!a.closed && !evaluated && a.kind == "cuestionario" && a.submission == null) {
                    QuizSection(a.id, reload)
                } else if (!a.closed && !evaluated) {
                    Box(
                        Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp))
                            .border(1.5.dp, Navy.copy(alpha = 0.25f), RoundedCornerShape(16.dp))
                            .clickable {
                                launcher.launch(arrayOf("application/pdf", "application/zip", "application/x-zip-compressed",
                                    "application/vnd.openxmlformats-officedocument.wordprocessingml.document"))
                            }.padding(22.dp),
                        Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(Icons.Filled.CloudUpload, null, tint = Navy, modifier = Modifier.size(38.dp))
                            Spacer(Modifier.height(8.dp))
                            Text(if (a.submission != null) "Reemplazar archivo" else "Seleccionar archivo", fontWeight = FontWeight.Bold, color = Navy, fontSize = 16.sp)
                            Spacer(Modifier.height(8.dp))
                            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                Pill("PDF", Soft, Navy)
                                Pill("ZIP", Soft, Navy)
                                Pill("DOCX", Soft, Navy)
                            }
                            Spacer(Modifier.height(6.dp))
                            Sub("Máximo 25MB")
                        }
                    }
                    picked?.let { p ->
                        Card2 {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Filled.Description, null, tint = Navy, modifier = Modifier.size(24.dp))
                                Spacer(Modifier.width(10.dp))
                                Column(Modifier.weight(1f)) {
                                    Text(p.name, color = Navy, fontWeight = FontWeight.Medium, fontSize = 14.sp)
                                    Sub("${fmtSize(p.size)} · Listo para enviar")
                                }
                                IconButton({ picked = null }) { Icon(Icons.Filled.Close, "Quitar", tint = Muted) }
                            }
                        }
                    }
                }
                error?.let { Text(it, color = ERR, fontSize = 13.sp) }
                Spacer(Modifier.height(4.dp))
            }
            val p = picked
            if (p != null && !a.closed && a.submission?.score == null) Column(Modifier.padding(16.dp)) {
                PrimaryButton(
                    if (busy) "Enviando…" else "Confirmar Entrega",
                    icon = Icons.Filled.Send,
                    enabled = !busy
                ) {
                    busy = true; error = null
                    scope.launch {
                        try {
                            val bytes = withContext(Dispatchers.IO) { ctx.contentResolver.openInputStream(p.uri)!!.use { it.readBytes() } }
                            val mime = ctx.contentResolver.getType(p.uri)?.toMediaTypeOrNull()
                            val part = MultipartBody.Part.createFormData("file", p.name, bytes.toRequestBody(mime))
                            Net.api.submit(a.id, part)
                            picked = null
                            reload()
                        } catch (e: Exception) {
                            error = Net.message(e)
                        }
                        busy = false
                    }
                }
                Sub("Al confirmar, el archivo se enviará a tu docente.", Modifier.fillMaxWidth().padding(top = 8.dp), textAlign = TextAlign.Center)
            }
        }
    }
}

@Composable
fun QuizSection(assignmentId: Int, onSubmitted: () -> Unit) {
    val scope = rememberCoroutineScope()
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var resultDialog by remember { mutableStateOf<QuizResult?>(null) }

    Load(assignmentId, { Net.api.getQuiz(assignmentId) }) { questions, _ ->
        if (questions.isEmpty()) {
            Card2 {
                EmptyState("El docente aún no ha configurado las preguntas de este cuestionario.")
            }
        } else {
            val selected = remember(questions) { mutableStateMapOf<Int, Int>() }
            val allAnswered = questions.indices.all { selected.containsKey(it) }

            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                questions.forEachIndexed { qIdx, q ->
                    Card2 {
                        Text(
                            "${qIdx + 1}. ${q.question}",
                            fontWeight = FontWeight.Bold,
                            color = Navy,
                            fontSize = 15.sp
                        )
                        Spacer(Modifier.height(8.dp))
                        q.options.forEachIndexed { optIdx, opt ->
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { selected[qIdx] = optIdx }
                                    .padding(vertical = 4.dp)
                            ) {
                                RadioButton(
                                    selected = selected[qIdx] == optIdx,
                                    onClick = { selected[qIdx] = optIdx },
                                    colors = RadioButtonDefaults.colors(selectedColor = Navy)
                                )
                                Spacer(Modifier.width(8.dp))
                                Text(opt, color = Navy, fontSize = 14.sp)
                            }
                        }
                    }
                }

                error?.let { Text(it, color = ERR, fontSize = 13.sp) }

                PrimaryButton(
                    if (busy) "Calificando…" else "Enviar cuestionario",
                    enabled = !busy && allAnswered
                ) {
                    busy = true
                    error = null
                    scope.launch {
                        try {
                            val answers = (0 until questions.size).map { selected[it] ?: 0 }
                            val res = Net.api.submitQuiz(assignmentId, QuizSubmitBody(answers))
                            resultDialog = res
                        } catch (e: Exception) {
                            error = Net.message(e)
                        }
                        busy = false
                    }
                }
            }

            resultDialog?.let { res ->
                AlertDialog(
                    onDismissRequest = {
                        resultDialog = null
                        onSubmitted()
                    },
                    title = { Text("Resultado del Cuestionario", fontWeight = FontWeight.Bold, color = Navy) },
                    text = {
                        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text("Calificación obtenida: ${fmtScore(res.score)} pts", fontWeight = FontWeight.Bold, color = if (res.percent >= 70) Ok else Warn, fontSize = 18.sp)
                            Text("Aciertos: ${res.correct} de ${res.total} preguntas (${res.percent.toInt()}%)", color = Navy)
                            Text("Tu calificación ha sido registrada con éxito.", color = Muted, fontSize = 12.sp)
                        }
                    },
                    confirmButton = {
                        Button(
                            onClick = {
                                resultDialog = null
                                onSubmitted()
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = Navy)
                        ) {
                            Text("Entendido")
                        }
                    }
                )
            }
        }
    }
}

// ============================================================ P-05 · Calificaciones y analíticas
@Composable
fun GradesScreen() {
    Load(Unit, { Net.api.myCourses() }) { courses, _ ->
        if (courses.isEmpty()) {
            Column(Modifier.fillMaxSize().padding(16.dp)) { H1("Mis resultados"); EmptyState("Inscríbete en un curso para ver tus calificaciones") }
            return@Load
        }
        var selected by remember { mutableIntStateOf(courses.first().id) }
        Column(Modifier.fillMaxSize()) {
            TopBar("Mis resultados")
            if (courses.size > 1) Row(Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                courses.forEach { c ->
                    FilterChip(selected == c.id, { selected = c.id }, label = { Text(c.title) })
                }
            }
            Load(selected, { Net.api.grades(selected) }) { g, _ ->
                Column(Modifier.verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    Column {
                        H1("Calificaciones y analíticas")
                        Sub("${g.title}${if (g.period.isNotBlank()) " · Periodo ${g.period}" else ""}")
                    }

                    Card2(color = Navy, border = false) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("BOLETA DIGITAL", color = Soft, fontSize = 10.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                            Icon(Icons.Filled.FileDownload, null, tint = Soft, modifier = Modifier.size(20.dp))
                        }
                        Spacer(Modifier.height(8.dp))
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                            Column {
                                Text(g.percent?.let { "${it.toInt()}%" } ?: "—", color = Color.White, fontSize = 38.sp, fontWeight = FontWeight.ExtraBold)
                                if (g.percent != null) Text("${fmtScore(g.earned)}/${fmtScore(g.possible)} pts", color = Soft, fontSize = 13.sp)
                            }
                            Column(horizontalAlignment = Alignment.End) {
                                Text(
                                    g.levelLabel ?: "Sin actividades evaluadas aún",
                                    color = Color.White, fontWeight = FontWeight.Bold, fontSize = 15.sp
                                )
                                if (g.evaluatedCount > 0) Text("${g.evaluatedCount} actividades evaluadas", color = Soft, fontSize = 12.sp)
                            }
                        }
                    }

                    H2("Seguimiento académico")
                    if (g.rows.isEmpty()) EmptyState("Este curso aún no tiene actividades")
                    else Card2 {
                        Row(Modifier.fillMaxWidth().padding(bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text("Actividad", Modifier.weight(1f), color = Muted, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                            Text("Puntos", Modifier.width(60.dp), color = Muted, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, textAlign = TextAlign.Center)
                            Text("Estado", Modifier.width(74.dp), color = Muted, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, textAlign = TextAlign.End)
                        }
                        HorizontalDivider(Modifier.padding(bottom = 6.dp), color = Line)
                        g.rows.forEach { r ->
                            Row(Modifier.padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                                Text("${r.code} · ${r.title}", Modifier.weight(1f), color = Navy, fontSize = 13.sp)
                                Text(r.score?.let { "${fmtScore(it)}/${r.points}" } ?: "—", Modifier.width(60.dp), color = Navy, fontWeight = FontWeight.SemiBold, fontSize = 13.sp, textAlign = TextAlign.Center)
                                Box(Modifier.width(74.dp), Alignment.CenterEnd) {
                                    when (r.status) {
                                        "evaluada" -> Text("Evaluada", color = Ok, fontWeight = FontWeight.SemiBold, fontSize = 12.sp)
                                        "entregada" -> Text("Entregada", color = Navy, fontWeight = FontWeight.SemiBold, fontSize = 12.sp)
                                        else -> Text("Pendiente", color = Warn, fontWeight = FontWeight.SemiBold, fontSize = 12.sp)
                                    }
                                }
                            }
                        }
                    }

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        H2("Tu progreso", Modifier.weight(1f)); Sub("Meta ≥ ${g.goal}%")
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Card2(Modifier.weight(1f)) {
                            Text("Notas previas", fontWeight = FontWeight.SemiBold, color = Navy, fontSize = 13.sp)
                            if (g.previous.isEmpty()) Sub("Sin notas aún")
                            g.previous.forEach { p ->
                                Row(Modifier.padding(top = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                                    Sub(p.code, Modifier.width(26.dp))
                                    ProgressBar(p.percent.toDouble(), Modifier.weight(1f))
                                    Sub("${p.percent}%", Modifier.padding(start = 6.dp))
                                }
                            }
                        }
                        Card2(Modifier.weight(1f)) {
                            Text("Avance del curso", fontWeight = FontWeight.SemiBold, color = Navy, fontSize = 13.sp)
                            Text("${g.progress.percent.toInt()}%", fontSize = 28.sp, fontWeight = FontWeight.Bold, color = Navy)
                            ProgressBar(g.progress.percent)
                            Sub("${g.progress.completedModules} de ${g.progress.totalModules} temas completados", Modifier.padding(top = 4.dp))
                        }
                    }
                    if (g.hasPending) Sub("La Tarea 2 no se incluye en el promedio hasta su revisión.", Modifier.padding(top = 2.dp))

                    val completedCoursesCount = courses.count { it.percent >= 100.0 }
                    val completedModulesCount = courses.sumOf { it.completedModules }
                    val badges = badgesForStudent(completedCoursesCount, completedModulesCount)

                    H2("Mis insignias y logros")
                    Row(
                        Modifier.horizontalScroll(rememberScrollState()).fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        badges.forEach { b ->
                            Card2(
                                modifier = Modifier.width(130.dp),
                                color = if (b.unlocked) Color.White else Soft.copy(alpha = 0.5f)
                            ) {
                                Column(
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)
                                ) {
                                    Box(
                                        Modifier.size(44.dp).clip(RoundedCornerShape(12.dp))
                                            .background(if (b.unlocked) Soft else Line),
                                        Alignment.Center
                                    ) {
                                        Icon(
                                            b.icon, null,
                                            tint = if (b.unlocked) Navy else Muted,
                                            modifier = Modifier.size(24.dp)
                                        )
                                    }
                                    Spacer(Modifier.height(8.dp))
                                    Text(
                                        b.title,
                                        fontWeight = FontWeight.Bold,
                                        color = if (b.unlocked) Navy else Muted,
                                        fontSize = 12.sp,
                                        textAlign = TextAlign.Center
                                    )
                                    Spacer(Modifier.height(2.dp))
                                    Text(
                                        b.description,
                                        fontSize = 10.sp,
                                        color = Muted,
                                        textAlign = TextAlign.Center,
                                        maxLines = 2
                                    )
                                    Spacer(Modifier.height(6.dp))
                                    Pill(
                                        if (b.unlocked) "Obtenida" else "Bloqueada",
                                        if (b.unlocked) OkBg else Line,
                                        if (b.unlocked) Ok else Muted
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

// ============================================================ P-06 · Catálogo
@Composable
fun CatalogScreen(nav: NavController) {
    var query by remember { mutableStateOf("") }
    var applied by remember { mutableStateOf("") }
    var category by remember { mutableStateOf("") }
    val scope = rememberCoroutineScope()
    var error by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(query) { delay(400); applied = query }

    Column(Modifier.fillMaxSize()) {
        TopBar("Explorar Cursos", onBack = { nav.popBackStack() })
        OutlinedTextField(
            query, { query = it }, singleLine = true, placeholder = { Text("¿Qué quieres aprender?") },
            leadingIcon = { Icon(Icons.Filled.Search, null, tint = Muted) },
            trailingIcon = { Icon(Icons.Filled.Tune, null, tint = Navy) },
            shape = RoundedCornerShape(14.dp),
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)
        )
        Load(applied to category, { Net.api.catalog(applied, category) }) { cat, reload ->
            LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                if (cat.categories.isNotEmpty()) item {
                    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilterChip(category.isEmpty(), { category = "" }, label = { Text("Todos") })
                        cat.categories.forEach { c -> FilterChip(category == c, { category = if (category == c) "" else c }, label = { Text(c) }) }
                    }
                }
                item {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        H2("Cursos destacados", Modifier.weight(1f))
                        Text("Inscripciones abiertas", color = Muted, fontSize = 12.sp)
                    }
                }
                error?.let { e -> item { Text(e, color = ERR) } }
                if (cat.courses.isEmpty()) item { EmptyState("No hay cursos disponibles con ese criterio") }
                items(cat.courses, key = { it.id }) { c ->
                    Card2(Modifier, border = true) {
                        if (!c.imageUrl.isNullOrBlank()) AsyncImage(
                            c.imageUrl, null, contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxWidth().height(140.dp).clip(RoundedCornerShape(12.dp))
                        ) else Box(Modifier.fillMaxWidth().height(120.dp).clip(RoundedCornerShape(12.dp)).background(Soft), Alignment.Center) {
                            Icon(Icons.Filled.School, null, tint = Navy, modifier = Modifier.size(36.dp))
                        }
                        Spacer(Modifier.height(10.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            if (c.isFree) Pill("100% Gratuito", OkBg, Ok)
                            Pill("Profesor Voluntario", Soft, Navy)
                            if (c.category.isNotBlank()) Pill(c.category)
                        }
                        Spacer(Modifier.height(8.dp))
                        Text(c.title, fontSize = 18.sp, fontWeight = FontWeight.Bold, color = Navy)
                        Spacer(Modifier.height(2.dp))
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text(c.teacherName, color = Muted, fontSize = 13.sp)
                            Text(listOfNotNull(c.level, c.durationWeeks?.let { "$it semanas" }).joinToString(" · "), color = Muted, fontSize = 13.sp)
                        }
                        if (c.description.isNotBlank()) Text(c.description, fontSize = 13.sp, color = Navy, maxLines = 2, modifier = Modifier.padding(top = 4.dp))
                        Spacer(Modifier.height(12.dp))
                        if (c.enrolled) OutlineButton("Ir al curso") { nav.navigate("course/${c.id}") }
                        else PrimaryButton("Inscribirse") {
                            scope.launch {
                                try { Net.api.enroll(c.id); nav.navigate("course/${c.id}") } catch (e: Exception) { error = Net.message(e); reload() }
                            }
                        }
                    }
                }
            }
        }
    }
}

// ============================================================ Agenda
@Composable
fun AgendaScreen(nav: NavController) {
    var month by remember { mutableStateOf(LocalDate.now().withDayOfMonth(1)) }
    val names = listOf("Enero", "Febrero", "Marzo", "Abril", "Mayo", "Junio", "Julio", "Agosto", "Septiembre", "Octubre", "Noviembre", "Diciembre")
    Column(Modifier.fillMaxSize()) {
        TopBar("Agenda")
        Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton({ month = month.minusMonths(1) }) { Icon(Icons.Filled.ChevronLeft, "Mes anterior") }
            Text("${names[month.monthValue - 1]} ${month.year}", Modifier.weight(1f), textAlign = TextAlign.Center, fontWeight = FontWeight.Bold, color = Navy)
            IconButton({ month = month.plusMonths(1) }) { Icon(Icons.Filled.ChevronRight, "Mes siguiente") }
        }
        Load(month, { Net.api.agenda(month.year, month.monthValue) }) { list, _ ->
            val inMonth = list.filter { isoDate(it.dueAt).let { d -> d.month == month.month && d.year == month.year } }
            LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                if (inMonth.isEmpty()) item { EmptyState("No hay entregas este mes") }
                items(inMonth) { a ->
                    Card2(onClick = { nav.navigate("assignment/${a.assignmentId}") }) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text(a.title, fontWeight = FontWeight.SemiBold, color = Navy)
                                Sub("${a.courseTitle} · ${fmtDateTime(a.dueAt)}")
                            }
                            if (a.submitted) Pill("Entregada", OkBg, Ok) else Pill(fmtDate(a.dueAt))
                        }
                    }
                }
            }
        }
    }
}

// ============================================================ P-08 · Panel docente
@Composable
fun TeacherScreen(onLogout: () -> Unit) {
    val scope = rememberCoroutineScope()
    var gradeFor by remember { mutableStateOf<ToReview?>(null) }
    var newModule by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    Load(Unit, { Net.api.teacherPanel() }) { p, reload ->
        LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            item {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) { H1("Panel Docente"); Sub("Hola, ${p.user.name}") }
                    Avatar(p.user.initials, 40.dp)
                }
            }
            item {
                Card2(color = Soft, border = false) {
                    Row(Modifier.fillMaxWidth(), Arrangement.SpaceEvenly) {
                        TStat(p.coursesCount, "Cursos impartidos")
                        TStat(p.studentsCount, "Alumnos activos")
                        TStat(p.toReviewCount, "Tareas por revisar")
                    }
                }
            }
            item { H2("Mis cursos asignados") }
            if (p.courses.isEmpty()) item { EmptyState("Aún no tienes cursos asignados") }
            items(p.courses) { c ->
                Card2 {
                    Text(c.title, fontWeight = FontWeight.SemiBold, color = Navy)
                    Sub("${if (c.groupName.isNotBlank()) c.groupName + " · " else ""}${c.students} alumnos · ${c.modules} módulos")
                }
            }
            item { H2("Por calificar") }
            if (p.toReview.isEmpty()) item { EmptyState("No hay entregas pendientes de revisión") }
            items(p.toReview) { r ->
                Card2 {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Avatar(r.initials)
                        Spacer(Modifier.width(10.dp))
                        Column(Modifier.weight(1f)) {
                            Text(r.studentName, fontWeight = FontWeight.SemiBold, color = Navy)
                            Sub("${r.assignmentTitle} · ${fmtDateTime(r.submittedAt)}")
                        }
                        Button({ gradeFor = r }, colors = ButtonDefaults.buttonColors(containerColor = WarnBg, contentColor = Warn)) { Text("Revisar") }
                    }
                }
            }
            error?.let { e -> item { Text(e, color = ERR) } }
            item { PrimaryButton("Crear nuevo módulo", enabled = p.courses.isNotEmpty()) { newModule = true } }
            item { TextButton(onLogout, Modifier.fillMaxWidth()) { Text("Cerrar sesión", color = Muted) } }
        }

        gradeFor?.let { r ->
            var score by remember(r) { mutableStateOf("") }
            var fb by remember(r) { mutableStateOf("") }
            AlertDialog(
                onDismissRequest = { gradeFor = null },
                title = { Text("Calificar a ${r.studentName}") },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Sub(r.assignmentTitle)
                        OutlinedTextField(score, { score = it }, label = { Text("Calificación (0–${r.points})") }, singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal))
                        OutlinedTextField(fb, { fb = it }, label = { Text("Comentarios (opcional)") })
                    }
                },
                confirmButton = {
                    TextButton(enabled = score.toDoubleOrNull() != null, onClick = {
                        val s = score.toDouble()
                        scope.launch {
                            try { Net.api.grade(r.submissionId, GradeBody(s, fb)); error = null; reload() } catch (e: Exception) { error = Net.message(e) }
                            gradeFor = null
                        }
                    }) { Text("Guardar") }
                },
                dismissButton = { TextButton({ gradeFor = null }) { Text("Cancelar") } }
            )
        }

        if (newModule) {
            var title by remember { mutableStateOf("") }
            var courseId by remember { mutableIntStateOf(p.courses.first().id) }
            AlertDialog(
                onDismissRequest = { newModule = false },
                title = { Text("Nuevo módulo") },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        if (p.courses.size > 1) p.courses.forEach { c ->
                            Row(Modifier.clickable { courseId = c.id }, verticalAlignment = Alignment.CenterVertically) {
                                RadioButton(courseId == c.id, { courseId = c.id }); Text(c.title, color = Navy)
                            }
                        }
                        OutlinedTextField(title, { title = it }, label = { Text("Título del módulo") }, singleLine = true)
                    }
                },
                confirmButton = {
                    TextButton(enabled = title.isNotBlank(), onClick = {
                        scope.launch {
                            try { Net.api.createModule(courseId, ModuleBody(title.trim())); error = null; reload() } catch (e: Exception) { error = Net.message(e) }
                            newModule = false
                        }
                    }) { Text("Crear") }
                },
                dismissButton = { TextButton({ newModule = false }) { Text("Cancelar") } }
            )
        }
    }
}

@Composable
private fun TStat(v: Int, label: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text("$v", fontSize = 24.sp, fontWeight = FontWeight.Bold, color = Navy)
        Sub(label)
    }
}

// ============================================================ P-09 · Donaciones y voluntariado
@Composable
fun DonateScreen(nav: NavController) {
    val scope = rememberCoroutineScope()
    var amount by remember { mutableIntStateOf(100) }
    var email by remember { mutableStateOf("") }
    var msg by remember { mutableStateOf<String?>(null) }
    var err by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }
    var apply by remember { mutableStateOf(false) }

    Column(Modifier.fillMaxSize()) {
        TopBar("Apoya a EDUcate", onBack = { nav.popBackStack() })
        Column(Modifier.verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Card2(color = Navy, border = false) {
                Text("ODS 17 · ALIANZAS PARA LOGRAR LOS OBJETIVOS", color = Soft, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(6.dp))
                Text("Educación de calidad gratuita para todos", color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.Bold)
                Text("Tu apoyo mantiene el aprendizaje abierto y conecta a más personas con nuevas oportunidades.", color = Soft, fontSize = 13.sp, modifier = Modifier.padding(top = 6.dp))
            }
            Card2(color = Soft, border = false) {
                H2("Haz una donación")
                Sub("Elige tu aportación única (MXN)")
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(50, 100, 200).forEach { v ->
                        FilterChip(amount == v, { amount = v }, label = { Text("$$v", Modifier.padding(vertical = 6.dp)) }, modifier = Modifier.weight(1f))
                    }
                }
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(email, { email = it.trim() }, label = { Text("Correo electrónico para tu recibo") }, singleLine = true,
                    isError = email.isNotEmpty() && !isValidEmail(email),
                    supportingText = { if (email.isNotEmpty() && !isValidEmail(email)) Text("Ingresa un correo válido", color = ERR) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email), modifier = Modifier.fillMaxWidth())
                Spacer(Modifier.height(10.dp))
                PrimaryButton(if (busy) "Registrando…" else "Registrar donación", enabled = !busy && isValidEmail(email)) {
                    busy = true; err = null; msg = null
                    scope.launch {
                        try {
                            val r = Net.api.donate(DonationBody(amount, email))
                            msg = "Gracias. Registramos tu donación de $${r.amount} ${r.currency}. El cobro se confirmará cuando la pasarela de pago esté activa."
                        } catch (e: Exception) { err = Net.message(e) }
                        busy = false
                    }
                }
                msg?.let { Text(it, color = Ok, fontSize = 13.sp, modifier = Modifier.padding(top = 8.dp)) }
                err?.let { Text(it, color = ERR, fontSize = 13.sp, modifier = Modifier.padding(top = 8.dp)) }
            }
            if (Net.role != "teacher") Card2 {
                H2("Sé un Profesor Voluntario")
                Sub("Comparte lo que sabes. Dona tu tiempo y acompaña a estudiantes a alcanzar sus metas.")
                Spacer(Modifier.height(10.dp))
                OutlineButton("Postularme como Docente") { apply = true }
            }
        }
    }

    if (apply) {
        var why by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { apply = false },
            title = { Text("Postulación como docente") },
            text = { OutlinedTextField(why, { why = it }, label = { Text("Cuéntanos qué te gustaría enseñar") }, minLines = 3) },
            confirmButton = {
                TextButton({
                    scope.launch {
                        msg = try { Net.api.apply(ApplyBody(why)); "Postulación enviada. Te avisaremos cuando sea revisada." } catch (e: Exception) { Net.message(e) }
                        apply = false
                    }
                }) { Text("Enviar") }
            },
            dismissButton = { TextButton({ apply = false }) { Text("Cancelar") } }
        )
    }
}
