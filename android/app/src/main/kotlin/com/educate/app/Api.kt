package com.educate.app

import android.content.Context
import android.content.SharedPreferences
import com.google.gson.FieldNamingPolicy
import com.google.gson.GsonBuilder
import com.google.gson.JsonParser
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import retrofit2.HttpException
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.*
import java.io.IOException
import java.util.concurrent.TimeUnit

// ------------------------------------------------------------ DTOs
data class UserDto(val id: Int, val name: String, val email: String, val role: String, val initials: String)
data class AuthResp(val token: String, val user: UserDto)
data class RegisterBody(val name: String, val email: String, val password: String, val role: String = "student")
data class LoginBody(val email: String, val password: String)

data class Stats(val activeCourses: Int, val pending: Int, val average: Double?)
data class ContinueDto(
    val courseId: Int, val title: String, val teacherName: String, val groupName: String,
    val completedModules: Int, val totalModules: Int, val percent: Double
)
data class AnnouncementDto(val id: Int, val title: String, val courseTitle: String, val teacherName: String, val createdAt: String)
data class UpcomingDto(val assignmentId: Int, val title: String, val courseTitle: String, val dueAt: String, val kind: String)
data class HomeDto(
    val user: UserDto, val stats: Stats, val continueLearning: ContinueDto?,
    val announcements: List<AnnouncementDto>, val upcoming: List<UpcomingDto>
)

data class MyCourseDto(
    val id: Int, val title: String, val teacherName: String, val groupName: String, val period: String,
    val completedModules: Int, val totalModules: Int, val percent: Double
)

data class CatalogCourse(
    val id: Int, val title: String, val description: String, val category: String, val level: String,
    val durationWeeks: Int?, val isFree: Boolean, val teacherName: String, val imageUrl: String?, val enrolled: Boolean
)
data class CatalogDto(val categories: List<String>, val courses: List<CatalogCourse>)

data class ItemRow(val id: Int, val type: String, val title: String, val subtitle: String, val completed: Boolean, val postsCount: Int)
data class AssignmentRow(val id: Int, val title: String, val kind: String, val points: Int, val dueAt: String, val status: String, val score: Double?)
data class ModuleDto(
    val id: Int, val position: Int, val title: String, val status: String, val availableFrom: String?,
    val items: List<ItemRow>, val assignments: List<AssignmentRow>, val resourcesCount: Int
)
data class ForumRow(val id: Int, val title: String, val postsCount: Int)
data class CourseDto(
    val id: Int, val title: String, val description: String, val category: String, val level: String,
    val teacherName: String, val groupName: String, val period: String,
    val totalModules: Int, val completedModules: Int, val percent: Double,
    val modules: List<ModuleDto>, val forums: List<ForumRow>
)

data class ResourceDto(val id: Int, val title: String, val url: String, val pages: Int?, val sizeKb: Int?)
data class NextItem(val id: Int, val title: String, val type: String)
data class ItemDetail(
    val id: Int, val type: String, val title: String, val description: String, val moduleTitle: String,
    val modulePosition: Int, val totalModules: Int, val courseId: Int, val courseTitle: String,
    val teacherName: String, val videoUrl: String?, val durationMin: Int?, val transcript: String,
    val completed: Boolean, val resources: List<ResourceDto>, val next: NextItem?
)
data class PostDto(val id: Int, val author: String, val initials: String, val body: String, val createdAt: String)
data class PostBody(val body: String)

data class SubmissionDto(val filename: String, val sizeBytes: Long, val submittedAt: String, val score: Double?, val feedback: String)
data class AssignmentDetail(
    val id: Int, val title: String, val instructions: String, val kind: String, val points: Int, val dueAt: String,
    val courseId: Int, val courseTitle: String, val status: String, val closed: Boolean, val submission: SubmissionDto?
)

data class GradeRow(val id: Int, val code: String, val title: String, val kind: String, val points: Int, val score: Double?, val status: String)
data class PrevGrade(val code: String, val percent: Int)
data class ProgressDto(val completedModules: Int, val totalModules: Int, val percent: Double)
data class GradesDto(
    val courseId: Int, val title: String, val period: String, val percent: Double?, val earned: Double,
    val possible: Double, val evaluatedCount: Int, val levelLabel: String?, val goal: Int,
    val rows: List<GradeRow>, val previous: List<PrevGrade>, val progress: ProgressDto, val hasPending: Boolean
)

data class AgendaDto(val assignmentId: Int, val title: String, val courseTitle: String, val kind: String, val dueAt: String, val submitted: Boolean)

data class TeacherCourse(val id: Int, val title: String, val groupName: String, val students: Int, val modules: Int)
data class ToReview(val submissionId: Int, val studentName: String, val initials: String, val assignmentTitle: String, val points: Int, val submittedAt: String)
data class TeacherPanel(
    val user: UserDto, val coursesCount: Int, val studentsCount: Int, val toReviewCount: Int,
    val courses: List<TeacherCourse>, val toReview: List<ToReview>
)
data class ModuleBody(val title: String, val position: Int? = null)
data class GradeBody(val score: Double, val feedback: String)
data class ApplyBody(val motivation: String)

data class CourseBody(
    val title: String,
    val description: String = "",
    val category: String = "",
    val level: String = "Principiante",
    val durationWeeks: Int? = null,
    val isFree: Boolean = true,
    val openEnrollment: Boolean = true,
    val groupName: String = "",
    val period: String = "",
    val imageUrl: String? = null
)

data class TeacherCourseItem(
    val id: Int,
    val title: String,
    val description: String,
    val category: String,
    val level: String,
    val durationWeeks: Int?,
    val isFree: Boolean,
    val openEnrollment: Boolean,
    val groupName: String,
    val period: String,
    val imageUrl: String?,
    val studentsCount: Int,
    val modulesCount: Int,
    val assignmentsCount: Int,
    val toReviewCount: Int
)

data class TeacherItemResource(val id: Int, val title: String, val url: String, val pages: Int?, val sizeKb: Int?)
data class TeacherModuleItem(
    val id: Int, val position: Int, val type: String, val title: String, val subtitle: String,
    val description: String, val videoUrl: String?, val durationMin: Int?, val transcript: String,
    val resources: List<TeacherItemResource>
)
data class TeacherModule(
    val id: Int, val position: Int, val title: String, val availableFrom: String?,
    val items: List<TeacherModuleItem>
)
data class TeacherAssignment(
    val id: Int, val moduleId: Int?, val title: String, val instructions: String,
    val kind: String, val points: Int, val dueAt: String,
    val submissionsCount: Int, val gradedCount: Int, val pendingCount: Int
)
data class EnrolledStudent(
    val id: Int, val name: String, val email: String, val initials: String, val enrolledAt: String
)
data class AnnouncementItem(
    val id: Int, val title: String, val body: String, val createdAt: String
)
data class TeacherCourseDetail(
    val id: Int, val title: String, val description: String, val category: String,
    val level: String, val durationWeeks: Int?, val isFree: Boolean, val openEnrollment: Boolean,
    val groupName: String, val period: String, val imageUrl: String?,
    val modules: List<TeacherModule>,
    val assignments: List<TeacherAssignment>,
    val students: List<EnrolledStudent>,
    val announcements: List<AnnouncementItem>
)

data class ItemBody(
    val type: String,
    val title: String,
    val subtitle: String = "",
    val description: String = "",
    val videoUrl: String? = null,
    val durationMin: Int? = null,
    val transcript: String = ""
)

data class ResourceBody(val title: String, val url: String, val pages: Int? = null, val sizeKb: Int? = null)

data class AssignmentBody(
    val title: String,
    val instructions: String = "",
    val kind: String = "tarea",
    val points: Int = 100,
    val dueAt: String,
    val moduleId: Int? = null
)

data class AnnouncementBody(val title: String, val body: String)

data class StudentSub(
    val id: Int, val userId: Int, val studentName: String, val studentEmail: String,
    val initials: String, val filename: String, val sizeBytes: Long, val submittedAt: String,
    val score: Double?, val feedback: String, val gradedAt: String?
)
data class PendingStudent(val id: Int, val name: String, val email: String, val initials: String)
data class AssignmentSubmissionsResp(
    val assignment: TeacherAssignment,
    val submissions: List<StudentSub>,
    val pendingStudents: List<PendingStudent>
)

data class RoleBody(val role: String)
data class DonationBody(val amount: Int, val email: String)
data class DonationResp(val id: Int, val amount: Int, val currency: String, val status: String)

// ---- Analytics DTOs (CAMBIO 2)
data class ModuleAnalytic(
    @com.google.gson.annotations.SerializedName("module_id") val moduleId: Int,
    val title: String, val position: Int,
    @com.google.gson.annotations.SerializedName("completed_count") val completedCount: Int,
    val percent: Double
)
data class AtRiskStudent(
    @com.google.gson.annotations.SerializedName("user_id") val userId: Int,
    val name: String, val email: String,
    @com.google.gson.annotations.SerializedName("last_activity") val lastActivity: String?
)
data class AssignmentAnalytic(
    @com.google.gson.annotations.SerializedName("assignment_id") val assignmentId: Int,
    val title: String,
    @com.google.gson.annotations.SerializedName("submitted_count") val submittedCount: Int,
    @com.google.gson.annotations.SerializedName("graded_count") val gradedCount: Int,
    @com.google.gson.annotations.SerializedName("avg_score") val avgScore: Double?
)
data class CourseAnalytics(
    @com.google.gson.annotations.SerializedName("total_students") val totalStudents: Int,
    val modules: List<ModuleAnalytic>,
    @com.google.gson.annotations.SerializedName("at_risk") val atRisk: List<AtRiskStudent>,
    @com.google.gson.annotations.SerializedName("assignment_stats") val assignmentStats: List<AssignmentAnalytic>
)

// ---- Duplicate course (CAMBIO 3)
data class DuplicateBody(
    @com.google.gson.annotations.SerializedName("group_name") val groupName: String,
    val period: String
)

// ---- Quiz DTOs (CAMBIO 5)
data class QuizQuestion(val index: Int, val question: String, val options: List<String>)
data class QuizSubmitBody(val answers: List<Int>)
data class QuizResult(val score: Double, val total: Int, val correct: Int, val percent: Double)

// ------------------------------------------------------------ API
interface Api {
    @POST("auth/register") suspend fun register(@Body b: RegisterBody): AuthResp
    @POST("auth/login") suspend fun login(@Body b: LoginBody): AuthResp
    @GET("me") suspend fun me(): UserDto
    @PUT("me/role") suspend fun updateRole(@Body b: RoleBody): UserDto

    @GET("home") suspend fun home(): HomeDto
    @GET("courses/mine") suspend fun myCourses(): List<MyCourseDto>
    @GET("catalog") suspend fun catalog(@Query("q") q: String, @Query("category") category: String): CatalogDto
    @POST("courses/{id}/enroll") suspend fun enroll(@Path("id") id: Int): Map<String, Any>
    @GET("courses/{id}") suspend fun course(@Path("id") id: Int): CourseDto
    @GET("courses/{id}/grades") suspend fun grades(@Path("id") id: Int): GradesDto

    @GET("items/{id}") suspend fun item(@Path("id") id: Int): ItemDetail
    @POST("items/{id}/complete") suspend fun completeItem(@Path("id") id: Int): ItemDetail
    @GET("items/{id}/posts") suspend fun posts(@Path("id") id: Int): List<PostDto>
    @POST("items/{id}/posts") suspend fun post(@Path("id") id: Int, @Body b: PostBody): Map<String, Any>

    @GET("assignments/{id}") suspend fun assignment(@Path("id") id: Int): AssignmentDetail
    @Multipart @POST("assignments/{id}/submit")
    suspend fun submit(@Path("id") id: Int, @Part file: MultipartBody.Part): AssignmentDetail

    @GET("agenda") suspend fun agenda(@Query("year") year: Int, @Query("month") month: Int): List<AgendaDto>

    @GET("teacher/panel") suspend fun teacherPanel(): TeacherPanel
    @GET("teacher/courses") suspend fun teacherCourses(): List<TeacherCourseItem>
    @POST("teacher/courses") suspend fun createCourse(@Body b: CourseBody): Map<String, Any>
    @GET("teacher/courses/{id}") suspend fun teacherCourseDetail(@Path("id") id: Int): TeacherCourseDetail
    @PUT("teacher/courses/{id}") suspend fun updateCourse(@Path("id") id: Int, @Body b: CourseBody): Map<String, Any>
    @DELETE("teacher/courses/{id}") suspend fun deleteCourse(@Path("id") id: Int): Map<String, Any>

    @POST("teacher/courses/{id}/modules") suspend fun createModule(@Path("id") id: Int, @Body b: ModuleBody): Map<String, Any>
    @DELETE("teacher/modules/{id}") suspend fun deleteModule(@Path("id") id: Int): Map<String, Any>

    @POST("teacher/modules/{id}/items") suspend fun createItem(@Path("id") id: Int, @Body b: ItemBody): Map<String, Any>
    @DELETE("teacher/items/{id}") suspend fun deleteItem(@Path("id") id: Int): Map<String, Any>
    @POST("teacher/items/{id}/resources") suspend fun addResource(@Path("id") id: Int, @Body b: ResourceBody): Map<String, Any>

    @POST("teacher/courses/{id}/assignments") suspend fun createAssignment(@Path("id") id: Int, @Body b: AssignmentBody): Map<String, Any>
    @DELETE("teacher/assignments/{id}") suspend fun deleteAssignment(@Path("id") id: Int): Map<String, Any>
    @GET("teacher/assignments/{id}/submissions") suspend fun assignmentSubmissions(@Path("id") id: Int): AssignmentSubmissionsResp

    @POST("teacher/courses/{id}/announcements") suspend fun createAnnouncement(@Path("id") id: Int, @Body b: AnnouncementBody): Map<String, Any>
    @DELETE("teacher/announcements/{id}") suspend fun deleteAnnouncement(@Path("id") id: Int): Map<String, Any>

    @PUT("teacher/submissions/{id}/grade") suspend fun grade(@Path("id") id: Int, @Body b: GradeBody): Map<String, Any>
    @POST("teacher/apply") suspend fun apply(@Body b: ApplyBody): Map<String, Any>

    @POST("donations") suspend fun donate(@Body b: DonationBody): DonationResp

    // ---- Analytics (CAMBIO 2)
    @GET("teacher/courses/{id}/analytics")
    suspend fun courseAnalytics(@Path("id") id: Int): CourseAnalytics

    // ---- Duplicate course (CAMBIO 3)
    @POST("teacher/courses/{id}/duplicate")
    suspend fun duplicateCourse(@Path("id") id: Int, @Body body: DuplicateBody): TeacherCourseItem

    // ---- Quiz (CAMBIO 5)
    @GET("assignments/{id}/quiz")
    suspend fun getQuiz(@Path("id") id: Int): List<QuizQuestion>

    @POST("assignments/{id}/quiz/submit")
    suspend fun submitQuiz(@Path("id") id: Int, @Body body: QuizSubmitBody): QuizResult
}

// ------------------------------------------------------------ red + sesión
object Net {
    internal lateinit var prefs: SharedPreferences
    var onUnauthorized: () -> Unit = {}

    fun init(ctx: Context) {
        prefs = ctx.getSharedPreferences("educate", Context.MODE_PRIVATE)
    }

    val token: String? get() = prefs.getString("token", null)
    val role: String get() = prefs.getString("role", "student") ?: "student"
    val userName: String get() = prefs.getString("name", "") ?: ""
    val initials: String get() = prefs.getString("initials", "") ?: ""

    fun saveSession(r: AuthResp) {
        prefs.edit().putString("token", r.token).putString("role", r.user.role)
            .putString("name", r.user.name).putString("initials", r.user.initials).apply()
    }

    fun saveUser(u: UserDto) {
        prefs.edit().putString("role", u.role)
            .putString("name", u.name).putString("initials", u.initials).apply()
    }

    fun logout() = prefs.edit().clear().apply()

    private val gson = GsonBuilder().setFieldNamingPolicy(FieldNamingPolicy.LOWER_CASE_WITH_UNDERSCORES).create()

    private val client by lazy {
        OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS).readTimeout(60, TimeUnit.SECONDS).writeTimeout(60, TimeUnit.SECONDS)
            .addInterceptor { chain ->
                val b = chain.request().newBuilder()
                token?.let { b.header("Authorization", "Bearer $it") }
                chain.proceed(b.build())
            }.build()
    }

    val api: Api by lazy {
        Retrofit.Builder().baseUrl(BuildConfig.BASE_URL).client(client)
            .addConverterFactory(GsonConverterFactory.create(gson)).build().create(Api::class.java)
    }

    /** Mensaje legible para el usuario a partir de cualquier error de red/servidor. */
    fun message(e: Throwable): String = when (e) {
        is HttpException -> {
            if (e.code() == 401 && token != null) onUnauthorized()
            val detail = try {
                JsonParser.parseString(e.response()?.errorBody()?.string() ?: "").asJsonObject.get("detail")
            } catch (_: Exception) { null }
            if (detail != null && detail.isJsonPrimitive) detail.asString
            else if (e.code() == 422) "Revisa los datos ingresados" else "Error del servidor (${e.code()})"
        }
        is IOException -> "No hay conexión con el servidor"
        else -> e.message ?: "Error inesperado"
    }
}
