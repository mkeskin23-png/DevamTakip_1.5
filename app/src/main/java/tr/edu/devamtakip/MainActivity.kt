package tr.edu.devamtakip

import android.app.DatePickerDialog
import android.app.Activity
import android.content.ContentResolver
import android.content.Context
import android.content.ContextWrapper
import android.content.res.Configuration
import android.content.ContentValues
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import android.net.Uri
import android.os.Bundle
import android.provider.OpenableColumns
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.clickable
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Assessment
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.People
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Backup
import androidx.compose.material.icons.filled.ExitToApp
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.MenuBook
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Divider
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Shapes
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.lightColorScheme
import androidx.compose.material3.Slider
import androidx.compose.material3.TextButton
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.w3c.dom.Element
import org.w3c.dom.Node
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.math.BigDecimal
import java.text.Normalizer
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.time.temporal.TemporalAdjusters
import java.util.Locale
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream
import kotlin.math.ceil
import javax.xml.parsers.DocumentBuilderFactory

private val dayNames = mapOf(
    1 to "Pazartesi", 2 to "Salı", 3 to "Çarşamba", 4 to "Perşembe", 5 to "Cuma"
)
private val dayShortNames = mapOf(1 to "Pzt", 2 to "Sal", 3 to "Çar", 4 to "Per", 5 to "Cum")
private val dateFormatter = DateTimeFormatter.ofPattern("dd.MM.yyyy", Locale("tr", "TR"))

private data class Student(val studentNo: String, val firstName: String, val lastName: String) {
    val fullName: String get() = "$firstName $lastName".trim()
}

private fun normalizeStudentIdentityName(value: String): String =
    value.trim().replace(Regex("\\s+"), " ").lowercase(Locale("tr", "TR"))

private fun sameStudentIdentity(first: Student, second: Student): Boolean =
    first.studentNo.trim() == second.studentNo.trim() &&
        normalizeStudentIdentityName(first.fullName) == normalizeStudentIdentityName(second.fullName)

private data class AppConfig(
    val id: Long = 0L,
    val courseName: String = "",
    val unit: String = "Bolvadin MYO",
    val startDate: LocalDate = LocalDate.now().with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY)),
    val midtermWeek: Int = 7,
    val hoursByDay: Map<Int, Int> = (1..5).associateWith { 0 },
    val absenceLimitPercent: Int = 20,
    val applicationNotePercent: Int = 0
)

private data class AbsenceRecord(val courseId: Long, val studentNo: String, val date: LocalDate, val hour: Int)
private data class ApplicationNoteRecord(val courseId: Long, val studentNo: String, val week: Int, val hour: Int, val score: Int)
private data class StudentSummary(
    val student: Student,
    val scheduledHours: Int,
    val absentHours: Int,
    val recordedHours: Int
) {
    val attendedHours: Int get() = (recordedHours - absentHours).coerceAtLeast(0)
    val attendancePercent: Int get() = if (recordedHours == 0) 0 else ((attendedHours * 100.0) / recordedHours).toInt()
}

private enum class ReportFormat { PDF, EXCEL }
private enum class ReportType { SUMMARY, DETAILED }

private data class ReportData(
    val summaries: List<StudentSummary>,
    val absences: List<AbsenceRecord>,
    val expectedHoursPerStudent: Int
)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val db = AttendanceDb(this)
        setContent {
            MaterialTheme(
                colorScheme = lightColorScheme(
                    primary = Color(0xFFC8102E),
                    secondary = Color(0xFFFFB81C),
                    tertiary = Color(0xFF9A1232),
                    background = Color(0xFFF9F7F5),
                    surface = Color(0xFFFFFFFF)
                ),
                shapes = Shapes(
                    extraSmall = RoundedCornerShape(4.dp),
                    small = RoundedCornerShape(6.dp),
                    medium = RoundedCornerShape(8.dp),
                    large = RoundedCornerShape(10.dp),
                    extraLarge = RoundedCornerShape(12.dp)
                )
            ) {
                Box(modifier = Modifier.fillMaxSize()) {
                    val orientation = LocalConfiguration.current.orientation
                    val watermarkResource = if (orientation == Configuration.ORIENTATION_LANDSCAPE) {
                        tr.edu.devamtakip.R.drawable.gs_watermark_landscape
                    } else {
                        tr.edu.devamtakip.R.drawable.gs_watermark
                    }
                    Image(
                        painter = painterResource(id = watermarkResource),
                        contentDescription = null,
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Fit,
                        alpha = 0.40f
                    )
                    Surface(modifier = Modifier.fillMaxSize(), color = Color.Transparent) {
                        AttendanceApp(db)
                    }
                }
            }
        }
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        // Orientation changes are handled in place; Compose state remains on the current screen.
    }
}

private class AttendanceDb(context: Context) : SQLiteOpenHelper(context, "devam_takip.db", null, 7) {
    private val prefs = context.getSharedPreferences("course_config", Context.MODE_PRIVATE)

    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL("CREATE TABLE students(student_no TEXT PRIMARY KEY NOT NULL, first_name TEXT NOT NULL, last_name TEXT NOT NULL)")
        createCoursesTable(db)
        createAbsencesTable(db)
        createCourseStudentsTable(db)
        createAttendanceSessionsTable(db)
        createApplicationNotesTable(db)
    }

    private fun createCoursesTable(db: SQLiteDatabase) {
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS courses(" +
                "id INTEGER PRIMARY KEY AUTOINCREMENT, " +
                "course_name TEXT NOT NULL, unit TEXT NOT NULL, start_epoch_day INTEGER NOT NULL, " +
                "midterm_week INTEGER NOT NULL, hours_1 INTEGER NOT NULL DEFAULT 0, " +
                "hours_2 INTEGER NOT NULL DEFAULT 0, hours_3 INTEGER NOT NULL DEFAULT 0, " +
                "hours_4 INTEGER NOT NULL DEFAULT 0, hours_5 INTEGER NOT NULL DEFAULT 0, " +
                "absence_limit_percent INTEGER NOT NULL DEFAULT 20, " +
                "application_note_percent INTEGER NOT NULL DEFAULT 0)"
        )
    }

    private fun createApplicationNotesTable(db: SQLiteDatabase) {
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS application_notes(" +
                "course_id INTEGER NOT NULL, student_no TEXT NOT NULL, week_no INTEGER NOT NULL, " +
                "hour_no INTEGER NOT NULL, score INTEGER NOT NULL DEFAULT 0, " +
                "PRIMARY KEY(course_id, student_no, week_no, hour_no))"
        )
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_application_notes_course_week ON application_notes(course_id, week_no)")
    }

    private fun createAbsencesTable(db: SQLiteDatabase) {
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS absences(" +
                "student_no TEXT NOT NULL, course_id INTEGER NOT NULL, attendance_date TEXT NOT NULL, " +
                "hour_no INTEGER NOT NULL, PRIMARY KEY(student_no, course_id, attendance_date, hour_no))"
        )
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_absences_date ON absences(course_id, attendance_date)")
    }

    private fun createCourseStudentsTable(db: SQLiteDatabase) {
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS course_students(" +
                "course_id INTEGER NOT NULL, student_no TEXT NOT NULL, first_name TEXT NOT NULL, last_name TEXT NOT NULL, " +
                "PRIMARY KEY(course_id, student_no))"
        )
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_course_students_no ON course_students(student_no)")
    }

    private fun createAttendanceSessionsTable(db: SQLiteDatabase) {
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS attendance_sessions(" +
                "course_id INTEGER NOT NULL, attendance_date TEXT NOT NULL, saved_at INTEGER NOT NULL, " +
                "PRIMARY KEY(course_id, attendance_date))"
        )
    }

    private fun hasColumn(db: SQLiteDatabase, table: String, column: String): Boolean {
        db.rawQuery("PRAGMA table_info($table)", null).use { cursor ->
            val nameIndex = cursor.getColumnIndex("name")
            while (cursor.moveToNext()) if (nameIndex >= 0 && cursor.getString(nameIndex).equals(column, ignoreCase = true)) return true
        }
        return false
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        if (oldVersion < 2) {
            createCoursesTable(db)
            val oldName = prefs.getString("course_name", "")?.trim().orEmpty()
            var migratedCourseId = 0L
            if (oldName.isNotBlank()) {
                val defaultStart = LocalDate.now().with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
                val start = runCatching { LocalDate.ofEpochDay(prefs.getLong("start_epoch_day", defaultStart.toEpochDay())) }.getOrDefault(defaultStart)
                val values = ContentValues().apply {
                    put("course_name", oldName); put("unit", "Bolvadin MYO"); put("start_epoch_day", start.toEpochDay())
                    put("midterm_week", prefs.getInt("midterm_week", 7).coerceIn(1, 15))
                    (1..5).forEach { day -> put("hours_$day", prefs.getInt("hours_$day", 0).coerceIn(0, 8)) }
                    put("absence_limit_percent", 20)
                }
                migratedCourseId = db.insert("courses", null, values)
            }
            db.execSQL("DROP INDEX IF EXISTS idx_absences_date")
            db.execSQL("ALTER TABLE absences RENAME TO absences_v1")
            createAbsencesTable(db)
            db.execSQL(
                "INSERT INTO absences(student_no, course_id, attendance_date, hour_no) " +
                    "SELECT student_no, $migratedCourseId, attendance_date, hour_no FROM absences_v1"
            )
            db.execSQL("DROP TABLE absences_v1")
        }
        if (oldVersion < 3) {
            createCourseStudentsTable(db)
            // Transfer the old shared student roster to each existing course so no existing roster is lost.
            db.execSQL(
                "INSERT OR IGNORE INTO course_students(course_id, student_no, first_name, last_name) " +
                    "SELECT c.id, s.student_no, s.first_name, s.last_name FROM courses c CROSS JOIN students s"
            )
        }
        if (oldVersion < 4 && !hasColumn(db, "courses", "absence_limit_percent")) {
            db.execSQL("ALTER TABLE courses ADD COLUMN absence_limit_percent INTEGER NOT NULL DEFAULT 20")
        }
        if (oldVersion < 5) {
            createAttendanceSessionsTable(db)
            // Older versions stored absences immediately but did not track whether an entire class session was saved.
            // Existing dates with absence rows are known attendance dates; infer those without discarding history.
            db.execSQL("INSERT OR IGNORE INTO attendance_sessions(course_id, attendance_date, saved_at) SELECT DISTINCT course_id, attendance_date, 0 FROM absences")
        }
        if (oldVersion < 7) {
            if (!hasColumn(db, "courses", "application_note_percent")) {
                db.execSQL("ALTER TABLE courses ADD COLUMN application_note_percent INTEGER NOT NULL DEFAULT 0")
            }
            createApplicationNotesTable(db)
        }
    }

    fun loadCourses(): List<AppConfig> {
        val result = mutableListOf<AppConfig>()
        readableDatabase.rawQuery(
            "SELECT id, course_name, unit, start_epoch_day, midterm_week, hours_1, hours_2, hours_3, hours_4, hours_5, absence_limit_percent, application_note_percent FROM courses ORDER BY course_name COLLATE NOCASE, id",
            null
        ).use { c ->
            while (c.moveToNext()) {
                val defaultStart = LocalDate.now().with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
                val start = runCatching { LocalDate.ofEpochDay(c.getLong(3)) }.getOrDefault(defaultStart)
                val hours = (1..5).associateWith { day -> c.getInt(4 + day).coerceIn(0, 8) }
                result += AppConfig(
                    id = c.getLong(0), courseName = c.getString(1), unit = c.getString(2), startDate = start,
                    midtermWeek = c.getInt(4).coerceIn(1, 15), hoursByDay = hours,
                    absenceLimitPercent = c.getInt(10).coerceIn(1, 100),
                    applicationNotePercent = c.getInt(11).coerceIn(0, 100)
                )
            }
        }
        return result
    }

    fun saveCourse(config: AppConfig): AppConfig {
        val values = ContentValues().apply {
            put("course_name", config.courseName.trim()); put("unit", config.unit)
            put("start_epoch_day", config.startDate.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY)).toEpochDay())
            put("midterm_week", config.midtermWeek.coerceIn(1, 15))
            (1..5).forEach { day -> put("hours_$day", (config.hoursByDay[day] ?: 0).coerceIn(0, 8)) }
            put("absence_limit_percent", config.absenceLimitPercent.coerceIn(1, 100))
            put("application_note_percent", config.applicationNotePercent.coerceIn(0, 100))
        }
        val db = writableDatabase
        val id = if (config.id > 0L) {
            val updated = db.update("courses", values, "id=?", arrayOf(config.id.toString()))
            if (updated > 0) config.id else db.insertOrThrow("courses", null, values)
        } else db.insertOrThrow("courses", null, values)
        val weeklyHours = config.hoursByDay.values.sum().coerceAtLeast(0)
        writableDatabase.delete("application_notes", "course_id=? AND hour_no>?", arrayOf(id.toString(), weeklyHours.toString()))
        return config.copy(id = id, startDate = config.startDate.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY)))
    }

    fun deleteCourse(courseId: Long) {
        val db = writableDatabase
        db.beginTransaction()
        try {
            db.delete("absences", "course_id=?", arrayOf(courseId.toString()))
            db.delete("course_students", "course_id=?", arrayOf(courseId.toString()))
            db.delete("attendance_sessions", "course_id=?", arrayOf(courseId.toString()))
            db.delete("application_notes", "course_id=?", arrayOf(courseId.toString()))
            db.delete("courses", "id=?", arrayOf(courseId.toString()))
            db.setTransactionSuccessful()
        } finally { db.endTransaction() }
    }

    fun loadStudents(courseId: Long): List<Student> {
        if (courseId <= 0L) return emptyList()
        val result = mutableListOf<Student>()
        readableDatabase.rawQuery(
            "SELECT student_no, first_name, last_name FROM course_students WHERE course_id=? ORDER BY student_no COLLATE NOCASE",
            arrayOf(courseId.toString())
        ).use { c -> while (c.moveToNext()) result += Student(c.getString(0), c.getString(1), c.getString(2)) }
        return result
    }

    fun replaceCourseStudents(courseId: Long, students: List<Student>) {
        require(courseId > 0L) { "Önce dersi kaydetmelisiniz." }
        val oldRoster = loadStudents(courseId)
        val newRoster = students.distinctBy { it.studentNo.trim() }.filter { it.studentNo.isNotBlank() }
        val newByNumber = newRoster.associateBy { it.studentNo.trim() }
        // Attendance is kept only when BOTH the student number and normalized full name match.
        // A reused student number with a different name is treated as a different student.
        val retainedNumbers = oldRoster.filter { old ->
            val incoming = newByNumber[old.studentNo.trim()]
            incoming != null && sameStudentIdentity(old, incoming)
        }.map { it.studentNo.trim() }.toSet()

        val db = writableDatabase
        db.beginTransaction()
        try {
            // Delete absence rows for removed or identity-changed students, including any stale rows
            // left by earlier versions of the app. Matched students' historical attendance remains.
            val existingAbsenceStudents = mutableSetOf<String>()
            db.rawQuery("SELECT DISTINCT student_no FROM absences WHERE course_id=?", arrayOf(courseId.toString())).use { cursor ->
                while (cursor.moveToNext()) existingAbsenceStudents += cursor.getString(0)
            }
            (existingAbsenceStudents - retainedNumbers).forEach { obsoleteStudentNo ->
                db.delete("absences", "course_id=? AND student_no=?", arrayOf(courseId.toString(), obsoleteStudentNo))
            }
            val existingNoteStudents = mutableSetOf<String>()
            db.rawQuery("SELECT DISTINCT student_no FROM application_notes WHERE course_id=?", arrayOf(courseId.toString())).use { cursor ->
                while (cursor.moveToNext()) existingNoteStudents += cursor.getString(0)
            }
            (existingNoteStudents - retainedNumbers).forEach { obsoleteStudentNo ->
                db.delete("application_notes", "course_id=? AND student_no=?", arrayOf(courseId.toString(), obsoleteStudentNo))
            }
            db.delete("course_students", "course_id=?", arrayOf(courseId.toString()))
            newRoster.forEach { s ->
                val values = ContentValues().apply {
                    put("course_id", courseId); put("student_no", s.studentNo.trim())
                    put("first_name", s.firstName.trim()); put("last_name", s.lastName.trim())
                }
                db.insertWithOnConflict("course_students", null, values, SQLiteDatabase.CONFLICT_REPLACE)
                // Retain the legacy table for old installations; current screens read from course_students.
                val legacy = ContentValues().apply {
                    put("student_no", s.studentNo.trim()); put("first_name", s.firstName.trim()); put("last_name", s.lastName.trim())
                }
                db.insertWithOnConflict("students", null, legacy, SQLiteDatabase.CONFLICT_REPLACE)
            }
            db.setTransactionSuccessful()
        } finally { db.endTransaction() }
    }

    fun allCourseStudents(): Map<Long, List<Student>> = loadCourses().associate { it.id to loadStudents(it.id) }

    fun getAbsences(courseId: Long? = null, studentNo: String? = null): List<AbsenceRecord> {
        val conditions = mutableListOf<String>(); val args = mutableListOf<String>()
        if (courseId != null) { conditions += "course_id=?"; args += courseId.toString() }
        if (studentNo != null) { conditions += "student_no=?"; args += studentNo }
        val where = if (conditions.isEmpty()) "" else " WHERE " + conditions.joinToString(" AND ")
        val result = mutableListOf<AbsenceRecord>()
        readableDatabase.rawQuery(
            "SELECT course_id, student_no, attendance_date, hour_no FROM absences$where ORDER BY attendance_date, hour_no, student_no",
            if (args.isEmpty()) null else args.toTypedArray()
        ).use { c ->
            while (c.moveToNext()) {
                val date = runCatching { LocalDate.parse(c.getString(2)) }.getOrNull() ?: continue
                result += AbsenceRecord(c.getLong(0), c.getString(1), date, c.getInt(3))
            }
        }
        return result
    }

    fun absentHours(courseId: Long, studentNo: String, date: LocalDate): Set<Int> =
        getAbsences(courseId, studentNo).filter { it.date == date }.map { it.hour }.toSet()

    fun saveAttendanceForDate(courseId: Long, date: LocalDate, totalHours: Int, absentByStudent: Map<String, Set<Int>>) {
        val db = writableDatabase
        db.beginTransaction()
        try {
            absentByStudent.forEach { (studentNo, hours) ->
                // Update only students in the current course roster; do not erase historical records of students removed from a roster.
                db.delete("absences", "course_id=? AND attendance_date=? AND student_no=?", arrayOf(courseId.toString(), date.toString(), studentNo))
                hours.filter { it in 1..totalHours }.forEach { hour ->
                    val values = ContentValues().apply {
                        put("course_id", courseId); put("student_no", studentNo); put("attendance_date", date.toString()); put("hour_no", hour)
                    }
                    db.insertWithOnConflict("absences", null, values, SQLiteDatabase.CONFLICT_REPLACE)
                }
            }
            val session = ContentValues().apply { put("course_id", courseId); put("attendance_date", date.toString()); put("saved_at", System.currentTimeMillis()) }
            db.insertWithOnConflict("attendance_sessions", null, session, SQLiteDatabase.CONFLICT_REPLACE)
            db.setTransactionSuccessful()
        } finally { db.endTransaction() }
    }

    fun isAttendanceSaved(courseId: Long, date: LocalDate): Boolean = readableDatabase.rawQuery(
        "SELECT 1 FROM attendance_sessions WHERE course_id=? AND attendance_date=? LIMIT 1",
        arrayOf(courseId.toString(), date.toString())
    ).use { it.moveToFirst() }

    fun savedAttendanceDates(courseId: Long): Set<LocalDate> {
        val result = mutableSetOf<LocalDate>()
        readableDatabase.rawQuery("SELECT attendance_date FROM attendance_sessions WHERE course_id=?", arrayOf(courseId.toString())).use { c ->
            while (c.moveToNext()) runCatching { LocalDate.parse(c.getString(0)) }.getOrNull()?.let { result += it }
        }
        return result
    }

    fun getApplicationNotes(courseId: Long? = null, studentNo: String? = null): List<ApplicationNoteRecord> {
        val conditions = mutableListOf<String>()
        val args = mutableListOf<String>()
        if (courseId != null) { conditions += "course_id=?"; args += courseId.toString() }
        if (studentNo != null) { conditions += "student_no=?"; args += studentNo }
        val where = if (conditions.isEmpty()) "" else " WHERE " + conditions.joinToString(" AND ")
        val result = mutableListOf<ApplicationNoteRecord>()
        readableDatabase.rawQuery(
            "SELECT course_id, student_no, week_no, hour_no, score FROM application_notes$where ORDER BY course_id, week_no, student_no, hour_no",
            if (args.isEmpty()) null else args.toTypedArray()
        ).use { c ->
            while (c.moveToNext()) result += ApplicationNoteRecord(c.getLong(0), c.getString(1), c.getInt(2), c.getInt(3), c.getInt(4).coerceIn(0, 4))
        }
        return result
    }

    fun loadApplicationNotes(courseId: Long, week: Int): Map<String, Map<Int, Int>> {
        val result = mutableMapOf<String, MutableMap<Int, Int>>()
        readableDatabase.rawQuery(
            "SELECT student_no, hour_no, score FROM application_notes WHERE course_id=? AND week_no=?",
            arrayOf(courseId.toString(), week.toString())
        ).use { c ->
            while (c.moveToNext()) result.getOrPut(c.getString(0)) { mutableMapOf() }[c.getInt(1)] = c.getInt(2).coerceIn(0, 4)
        }
        return result.mapValues { it.value.toMap() }
    }

    fun saveApplicationNotes(courseId: Long, week: Int, valuesByStudent: Map<String, Map<Int, Int>>) {
        require(week in 1..15) { "Hafta 1–15 arasında olmalı." }
        val db = writableDatabase
        db.beginTransaction()
        try {
            valuesByStudent.forEach { (studentNo, hourValues) ->
                // Replace the selected student's full set of weekly slots so stale slots from a changed timetable do not survive.
                db.delete("application_notes", "course_id=? AND week_no=? AND student_no=?", arrayOf(courseId.toString(), week.toString(), studentNo))
                hourValues.forEach { (hour, score) ->
                    val values = ContentValues().apply {
                        put("course_id", courseId); put("student_no", studentNo); put("week_no", week)
                        put("hour_no", hour.coerceAtLeast(1)); put("score", score.coerceIn(0, 4))
                    }
                    db.insertWithOnConflict("application_notes", null, values, SQLiteDatabase.CONFLICT_REPLACE)
                }
            }
            db.setTransactionSuccessful()
        } finally { db.endTransaction() }
    }

    fun clearCourseStudents(courseId: Long) = replaceCourseStudents(courseId, emptyList())

    fun exportBackupJson(): ByteArray {
        val root = JSONObject().put("formatVersion", 1).put("createdAt", LocalDate.now().toString())
        val courseArray = JSONArray()
        loadCourses().forEach { course ->
            val hours = JSONObject(); (1..5).forEach { day -> hours.put(day.toString(), course.hoursByDay[day] ?: 0) }
            courseArray.put(JSONObject().put("id", course.id).put("courseName", course.courseName).put("unit", course.unit)
                .put("startDate", course.startDate.toString()).put("midtermWeek", course.midtermWeek)
                .put("absenceLimitPercent", course.absenceLimitPercent).put("applicationNotePercent", course.applicationNotePercent)
                .put("hoursByDay", hours))
        }
        root.put("courses", courseArray)
        val studentArray = JSONArray()
        allCourseStudents().forEach { (courseId, roster) -> roster.forEach { student ->
            studentArray.put(JSONObject().put("courseId", courseId).put("studentNo", student.studentNo)
                .put("firstName", student.firstName).put("lastName", student.lastName))
        } }
        root.put("students", studentArray)
        val absenceArray = JSONArray()
        getAbsences().forEach { a -> absenceArray.put(JSONObject().put("courseId", a.courseId).put("studentNo", a.studentNo)
            .put("date", a.date.toString()).put("hour", a.hour)) }
        root.put("absences", absenceArray)
        val sessionArray = JSONArray()
        loadCourses().forEach { c -> savedAttendanceDates(c.id).forEach { d ->
            sessionArray.put(JSONObject().put("courseId", c.id).put("date", d.toString()))
        } }
        root.put("sessions", sessionArray)
        val notesArray = JSONArray()
        getApplicationNotes().forEach { note ->
            notesArray.put(JSONObject().put("courseId", note.courseId).put("studentNo", note.studentNo)
                .put("week", note.week).put("hour", note.hour).put("score", note.score))
        }
        root.put("applicationNotes", notesArray)
        return root.toString(2).toByteArray(Charsets.UTF_8)
    }

    fun restoreBackupJson(bytes: ByteArray) {
        val root = JSONObject(bytes.toString(Charsets.UTF_8))
        require(root.optInt("formatVersion", 0) == 1) { "Yedek dosyasının sürümü desteklenmiyor." }
        val courseArray = root.getJSONArray("courses")
        val studentArray = root.getJSONArray("students")
        val absenceArray = root.getJSONArray("absences")
        val sessionArray = root.optJSONArray("sessions") ?: JSONArray()
        val notesArray = root.optJSONArray("applicationNotes") ?: JSONArray()
        require(courseArray.length() > 0) { "Yedek dosyasında ders bulunamadı." }
        val db = writableDatabase
        db.beginTransaction()
        try {
            db.delete("absences", null, null); db.delete("attendance_sessions", null, null)
            db.delete("application_notes", null, null)
            db.delete("course_students", null, null); db.delete("courses", null, null)
            db.delete("students", null, null)
            for (i in 0 until courseArray.length()) {
                val c = courseArray.getJSONObject(i)
                val hours = c.getJSONObject("hoursByDay")
                val values = ContentValues().apply {
                    put("id", c.getLong("id")); put("course_name", c.getString("courseName")); put("unit", c.optString("unit", "Bolvadin MYO"))
                    put("start_epoch_day", LocalDate.parse(c.getString("startDate")).toEpochDay())
                    put("midterm_week", c.optInt("midtermWeek", 7).coerceIn(1, 15))
                    (1..5).forEach { day -> put("hours_$day", hours.optInt(day.toString(), 0).coerceIn(0, 8)) }
                    put("absence_limit_percent", c.optInt("absenceLimitPercent", 20).coerceIn(1, 100))
                    put("application_note_percent", c.optInt("applicationNotePercent", 0).coerceIn(0, 100))
                }
                db.insertOrThrow("courses", null, values)
            }
            for (i in 0 until studentArray.length()) {
                val s = studentArray.getJSONObject(i)
                val values = ContentValues().apply {
                    put("course_id", s.getLong("courseId")); put("student_no", s.getString("studentNo"))
                    put("first_name", s.optString("firstName", "")); put("last_name", s.optString("lastName", ""))
                }
                db.insertWithOnConflict("course_students", null, values, SQLiteDatabase.CONFLICT_REPLACE)
                val legacy = ContentValues().apply {
                    put("student_no", s.getString("studentNo")); put("first_name", s.optString("firstName", "")); put("last_name", s.optString("lastName", ""))
                }
                db.insertWithOnConflict("students", null, legacy, SQLiteDatabase.CONFLICT_REPLACE)
            }
            for (i in 0 until absenceArray.length()) {
                val a = absenceArray.getJSONObject(i)
                val values = ContentValues().apply {
                    put("course_id", a.getLong("courseId")); put("student_no", a.getString("studentNo"))
                    put("attendance_date", LocalDate.parse(a.getString("date")).toString()); put("hour_no", a.getInt("hour"))
                }
                db.insertWithOnConflict("absences", null, values, SQLiteDatabase.CONFLICT_REPLACE)
            }
            for (i in 0 until sessionArray.length()) {
                val session = sessionArray.getJSONObject(i)
                val values = ContentValues().apply {
                    put("course_id", session.getLong("courseId")); put("attendance_date", LocalDate.parse(session.getString("date")).toString())
                    put("saved_at", System.currentTimeMillis())
                }
                db.insertWithOnConflict("attendance_sessions", null, values, SQLiteDatabase.CONFLICT_REPLACE)
            }
            for (i in 0 until notesArray.length()) {
                val n = notesArray.getJSONObject(i)
                val values = ContentValues().apply {
                    put("course_id", n.getLong("courseId")); put("student_no", n.getString("studentNo"))
                    put("week_no", n.getInt("week").coerceIn(1, 15)); put("hour_no", n.getInt("hour").coerceAtLeast(1))
                    put("score", n.getInt("score").coerceIn(0, 4))
                }
                db.insertWithOnConflict("application_notes", null, values, SQLiteDatabase.CONFLICT_REPLACE)
            }
            db.setTransactionSuccessful()
        } finally { db.endTransaction() }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AttendanceApp(db: AttendanceDb) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var courses by remember { mutableStateOf(db.loadCourses()) }
    var selectedCourseId by remember { mutableStateOf(courses.firstOrNull()?.id) }
    var editingCourse by remember { mutableStateOf<AppConfig?>(null) }
    var students by remember { mutableStateOf(selectedCourseId?.let { db.loadStudents(it) } ?: emptyList()) }
    // Screens: 0 home, 1 setup, 2 courses, 3 attendance, 4 attendance reports, 5 calendar, 6 backup, 7 application notes, 8 application-note reports.
    var screen by remember { mutableIntStateOf(0) }
    var coursePickerTarget by remember { mutableStateOf<Int?>(null) }
    var attendanceRevision by remember { mutableIntStateOf(0) }
    var applicationNoteRevision by remember { mutableIntStateOf(0) }
    var attendanceDirty by remember { mutableStateOf(false) }
    var notice by remember { mutableStateOf("") }
    var selectedReportStudent by remember { mutableStateOf<String?>(null) }
    var pendingPdfBytes by remember { mutableStateOf<ByteArray?>(null) }
    var pendingExcelBytes by remember { mutableStateOf<ByteArray?>(null) }
    var pendingBackupBytes by remember { mutableStateOf<ByteArray?>(null) }
    var pendingImportedStudents by remember { mutableStateOf<List<Student>?>(null) }
    var showStudentListReplaceConfirmation by remember { mutableStateOf(false) }
    var showRestoreConfirmation by remember { mutableStateOf(false) }
    var showExitConfirmation by remember { mutableStateOf(false) }
    var showUnsavedLeaveDialog by remember { mutableStateOf(false) }
    var pendingDestination by remember { mutableIntStateOf(-2) }

    val selectedCourse = courses.firstOrNull { it.id == selectedCourseId } ?: courses.firstOrNull()

    fun reloadCourseRoster(courseId: Long?) {
        students = if (courseId == null) emptyList() else db.loadStudents(courseId)
    }

    fun navigate(destination: Int) {
        if (screen == 3 && attendanceDirty) {
            pendingDestination = destination
            showUnsavedLeaveDialog = true
            return
        }
        screen = destination
        notice = ""
    }

    fun requestExit() {
        if (screen == 3 && attendanceDirty) {
            pendingDestination = -1
            showUnsavedLeaveDialog = true
        } else showExitConfirmation = true
    }

    BackHandler {
        if (screen == 0) requestExit() else navigate(0)
    }

    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        if (uri != null) {
            scope.launch {
                runCatching {
                    val imported = withContext(Dispatchers.IO) {
                        val filename = queryDisplayName(context.contentResolver, uri).lowercase(Locale.ROOT)
                        val input = context.contentResolver.openInputStream(uri) ?: error("Dosya açılamadı.")
                        input.use { parseStudentFile(filename, it) }
                    }
                    if (imported.isEmpty()) error("Dosyada geçerli öğrenci bulunamadı. Öğrenci No ve adı-soyadı alanlarını kontrol edin.")
                    if (students.isNotEmpty()) {
                        // Do not replace the current staged roster until the user confirms.
                        pendingImportedStudents = imported
                        showStudentListReplaceConfirmation = true
                        notice = ""
                    } else {
                        // First roster for this course: stage it until course settings are saved.
                        students = imported
                        notice = "${imported.size} öğrenci içe aktarıldı. Liste, ders ayarlarını kaydettiğinizde yalnızca bu derse kaydedilecek."
                    }
                }.onFailure { e -> notice = "Aktarım yapılamadı: ${e.message ?: "Dosya biçimini kontrol edin."}" }
            }
        }
    }

    val pdfExportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/pdf")) { uri: Uri? ->
        if (uri != null) runCatching {
            val bytes = pendingPdfBytes ?: error("PDF raporu oluşturulamadı.")
            context.contentResolver.openOutputStream(uri)?.use { it.write(bytes) } ?: error("Dosya kaydedilemedi.")
            notice = "PDF raporu başarıyla kaydedildi."
        }.onFailure { notice = "PDF raporu kaydedilemedi: ${it.message ?: "Bilinmeyen hata"}" }
    }

    val excelExportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet")
    ) { uri: Uri? ->
        if (uri != null) runCatching {
            val bytes = pendingExcelBytes ?: error("Excel raporu oluşturulamadı.")
            context.contentResolver.openOutputStream(uri)?.use { it.write(bytes) } ?: error("Dosya kaydedilemedi.")
            notice = "Excel (.xlsx) raporu başarıyla kaydedildi."
        }.onFailure { notice = "Excel raporu kaydedilemedi: ${it.message ?: "Bilinmeyen hata"}" }
    }

    val backupExportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri: Uri? ->
        if (uri != null) runCatching {
            val bytes = pendingBackupBytes ?: error("Yedek hazırlanamadı.")
            context.contentResolver.openOutputStream(uri)?.use { it.write(bytes) } ?: error("Yedek dosyası kaydedilemedi.")
            notice = "Yedek dosyası kaydedildi. Dersler, ders bazlı öğrenci listeleri ve yoklama kayıtları dahil edildi."
        }.onFailure { notice = "Yedek kaydedilemedi: ${it.message ?: "Bilinmeyen hata"}" }
    }

    val backupImportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        if (uri != null) scope.launch {
            runCatching {
                val bytes = withContext(Dispatchers.IO) {
                    context.contentResolver.openInputStream(uri)?.use { it.readBytes() } ?: error("Yedek dosyası açılamadı.")
                }
                // Parse now to reject invalid files before asking for destructive restore confirmation.
                val json = JSONObject(bytes.toString(Charsets.UTF_8))
                require(json.optInt("formatVersion", 0) == 1 && json.has("courses")) { "Geçerli Devam Takip yedeği seçilmedi." }
                pendingBackupBytes = bytes
                showRestoreConfirmation = true
            }.onFailure { notice = "Yedek okunamadı: ${it.message ?: "Dosya geçersiz."}" }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent),
                title = {
                    Column {
                        Text(
                            when (screen) {
                                0 -> "Devam Takip"
                                1 -> if (editingCourse == null) "Ders Ekle" else "Ders Ayarları"
                                2 -> "Derslerim"
                                3 -> "Yoklama Al"
                                4 -> "Devam Raporları"
                                5 -> "Ders Programı Takvimi"
                                6 -> "Yedekleme ve Geri Yükleme"
                                7 -> "Uygulama Notu"
                                8 -> "Uygulama Notu Raporları"
                                else -> "Devam Takip"
                            }, fontWeight = FontWeight.SemiBold
                        )
                        if ((screen in 2..5 || screen in 7..8) && selectedCourse != null) {
                            Text("${selectedCourse.unit} · ${students.size} öğrenci", style = MaterialTheme.typography.labelSmall)
                        }
                    }
                },
                navigationIcon = {
                    if (screen != 0) IconButton(onClick = { navigate(0) }) {
                        androidx.compose.material3.Icon(Icons.Default.Home, contentDescription = "Ana menü")
                    }
                },
            )
        },
        containerColor = Color.Transparent
    ) { insets ->
        Column(Modifier.fillMaxSize().padding(insets)) {
            if (notice.isNotBlank()) {
                Card(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp)) {
                    Text(notice, Modifier.padding(12.dp), style = MaterialTheme.typography.bodySmall)
                }
            }
            when (screen) {
                0 -> HomeMenuScreen(
                    onAddCourse = { editingCourse = null; students = emptyList(); navigate(1) },
                    onCourses = { navigate(2) },
                    onAttendance = {
                        if (courses.isEmpty()) { notice = "Önce Ders Ekle bölümünden bir ders tanımlayın."; navigate(1) }
                        else coursePickerTarget = 3
                    },
                    onReports = { selectedReportStudent = null; coursePickerTarget = 4 },
                    onCalendar = { navigate(5) },
                    onBackup = { navigate(6) },
                    onApplicationNotes = { coursePickerTarget = 7 },
                    onExit = { requestExit() }
                )
                1 -> SetupScreen(
                    config = editingCourse,
                    students = students,
                    onSave = { next ->
                        runCatching {
                            val saved = db.saveCourse(next)
                            db.replaceCourseStudents(saved.id, students)
                            courses = db.loadCourses()
                            selectedCourseId = saved.id
                            reloadCourseRoster(saved.id)
                            editingCourse = null
                            screen = 2
                            notice = "${saved.courseName} dersi ve ${students.size} öğrenci kaydedildi. Öğrenci listesi bu derse özeldir."
                        }.onFailure { notice = "Ders kaydedilemedi: ${it.message ?: "Bilinmeyen hata"}" }
                    },
                    onImport = { importLauncher.launch(arrayOf("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", "text/csv", "text/comma-separated-values", "*/*")) },
                    onRemoveStudents = {
                        val courseId = editingCourse?.id ?: 0L
                        if (courseId > 0L) {
                            db.clearCourseStudents(courseId)
                            courses = db.loadCourses()
                        }
                        students = emptyList()
                        notice = "Öğrenci listesi temizlendi. Bu öğrencilere ait yoklama ve uygulama notu kayıtları da kaldırıldı."
                    },
                    onBackup = {
                        runCatching {
                            pendingBackupBytes = db.exportBackupJson()
                            backupExportLauncher.launch("DevamTakip_Yedek_${LocalDate.now()}.json")
                        }.onFailure { notice = "Yedek oluşturulamadı: ${it.message ?: "Bilinmeyen hata"}" }
                    }
                )
                2 -> CoursesScreen(
                    courses = courses,
                    selectedCourseId = selectedCourse?.id,
                    studentCount = courses.sumOf { db.loadStudents(it.id).size },
                    onSelect = { id -> selectedCourseId = id; reloadCourseRoster(id); selectedReportStudent = null },
                    onAdd = { editingCourse = null; students = emptyList(); navigate(1) },
                    onEdit = { course -> editingCourse = course; selectedCourseId = course.id; reloadCourseRoster(course.id); navigate(1) },
                    onTakeAttendance = { id -> selectedCourseId = id; reloadCourseRoster(id); selectedReportStudent = null; attendanceDirty = false; navigate(3) },
                    onDelete = { course ->
                        db.deleteCourse(course.id)
                        courses = db.loadCourses()
                        selectedCourseId = courses.firstOrNull()?.id
                        reloadCourseRoster(selectedCourseId)
                        notice = "${course.courseName} dersi, bu derse ait öğrenciler ve yoklama kayıtları silindi."
                    }
                )
                3 -> if (selectedCourse != null) {
                    AttendanceScreen(
                        config = selectedCourse,
                        students = students,
                        db = db,
                        revision = attendanceRevision,
                        onDirtyChange = { attendanceDirty = it },
                        onSaved = { attendanceRevision++; attendanceDirty = false; notice = "Yoklama başarıyla kaydedildi." }
                    )
                } else EmptyState("Yoklama almak için önce bir ders ekleyin.")
                4 -> if (selectedCourse != null) {
                    ReportScreen(
                        config = selectedCourse,
                        courses = courses,
                        students = students,
                        db = db,
                        selectedStudentNo = selectedReportStudent,
                        revision = attendanceRevision,
                        onSelectStudent = { selectedReportStudent = it },
                        onExport = { studentNo, format, detailed ->
                            val reportCourse = selectedCourse
                            val reportStudents = students.toList()
                            scope.launch {
                                notice = if (detailed) "Ayrıntılı rapor hazırlanıyor…" else "Özet rapor hazırlanıyor…"
                                runCatching {
                                    withContext(Dispatchers.IO) {
                                        when (format) {
                                            ReportFormat.PDF -> buildReportPdf(reportCourse, reportStudents, db, studentNo, detailed)
                                            ReportFormat.EXCEL -> buildReportXlsx(reportCourse, reportStudents, db, studentNo, detailed)
                                        }
                                    }
                                }.onSuccess { bytes ->
                                    val suffix = if (detailed) "Ayrintili" else "Ozet"
                                    when (format) {
                                        ReportFormat.PDF -> { pendingPdfBytes = bytes; pdfExportLauncher.launch(reportFilename(reportCourse, studentNo, "pdf").replace(".pdf", "_$suffix.pdf")) }
                                        ReportFormat.EXCEL -> { pendingExcelBytes = bytes; excelExportLauncher.launch(reportFilename(reportCourse, studentNo, "xlsx").replace(".xlsx", "_$suffix.xlsx")) }
                                    }
                                }.onFailure { error -> notice = "Rapor oluşturulamadı: ${error.message ?: "Bilinmeyen hata"}" }
                            }
                        }
                    )
                } else EmptyState("Rapor görüntülemek için önce ders ekleyin.")
                5 -> if (selectedCourse != null) CourseCalendarScreen(config = selectedCourse, db = db) else EmptyState("Takvimi görmek için bir ders ekleyin.")
                7 -> if (selectedCourse != null) {
                    ApplicationNoteScreen(
                        config = selectedCourse,
                        students = students,
                        db = db,
                        revision = applicationNoteRevision,
                        onSaved = { applicationNoteRevision++; notice = "Uygulama notları kaydedildi." },
                        onOpenReports = { screen = 8 }
                    )
                } else EmptyState("Uygulama notu için önce bir ders ekleyin.")
                8 -> if (selectedCourse != null) {
                    ApplicationNoteReportScreen(
                        config = selectedCourse,
                        students = students,
                        db = db,
                        onExport = { studentNo, format, detailed ->
                            val reportCourse = selectedCourse
                            val reportStudents = students.toList()
                            scope.launch {
                                notice = if (detailed) "Ayrıntılı uygulama notu raporu hazırlanıyor…" else "Uygulama notu özet raporu hazırlanıyor…"
                                runCatching {
                                    withContext(Dispatchers.IO) {
                                        when (format) {
                                            ReportFormat.PDF -> buildApplicationNoteReportPdf(reportCourse, reportStudents, db, studentNo, detailed)
                                            ReportFormat.EXCEL -> buildApplicationNoteReportXlsx(reportCourse, reportStudents, db, studentNo, detailed)
                                        }
                                    }
                                }.onSuccess { bytes ->
                                    val scopeName = if (studentNo == null) "Toplu" else "Ogrenci"
                                    val detailName = if (detailed) "Ayrintili" else "Ozet"
                                    val base = safeFilename(reportCourse.courseName)
                                    when (format) {
                                        ReportFormat.PDF -> { pendingPdfBytes = bytes; pdfExportLauncher.launch("${base}_UygulamaNotu_${scopeName}_${detailName}.pdf") }
                                        ReportFormat.EXCEL -> { pendingExcelBytes = bytes; excelExportLauncher.launch("${base}_UygulamaNotu_${scopeName}_${detailName}.xlsx") }
                                    }
                                }.onFailure { error -> notice = "Uygulama notu raporu oluşturulamadı: ${error.message ?: "Bilinmeyen hata"}" }
                            }
                        }
                    )
                } else EmptyState("Uygulama notu raporu için önce bir ders ekleyin.")
                else -> BackupScreen(
                    onBackup = {
                        runCatching {
                            pendingBackupBytes = db.exportBackupJson()
                            backupExportLauncher.launch("DevamTakip_Yedek_${LocalDate.now()}.json")
                        }.onFailure { notice = "Yedek oluşturulamadı: ${it.message ?: "Bilinmeyen hata"}" }
                    },
                    onRestore = { backupImportLauncher.launch(arrayOf("application/json", "text/json", "text/plain", "*/*")) }
                )
            }
        }
    }

    val pickerTarget = coursePickerTarget
    if (pickerTarget != null) {
        AlertDialog(
            onDismissRequest = { coursePickerTarget = null },
            title = { Text(when (pickerTarget) { 3 -> "Yoklama için ders seçin"; 4 -> "Devam raporu için ders seçin"; 7 -> "Uygulama notu için ders seçin"; else -> "Ders seçin" }) },
            text = {
                if (courses.isEmpty()) Text("Henüz kayıtlı ders yok. Önce bir ders ekleyin.")
                else Column(Modifier.heightIn(max = 420.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    courses.forEach { course ->
                        OutlinedButton(
                            onClick = {
                                selectedCourseId = course.id
                                reloadCourseRoster(course.id)
                                selectedReportStudent = null
                                screen = pickerTarget
                                coursePickerTarget = null
                                notice = "${course.courseName} seçildi."
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                Text(course.courseName, fontWeight = FontWeight.SemiBold)
                                Text("${course.unit} · ${course.hoursByDay.values.sum()} saat/hafta", style = MaterialTheme.typography.bodySmall)
                            }
                        }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { coursePickerTarget = null }) { Text("İptal") } }
        )
    }

    if (showUnsavedLeaveDialog) {
        AlertDialog(
            onDismissRequest = { showUnsavedLeaveDialog = false; pendingDestination = -2 },
            title = { Text("Kaydedilmemiş yoklama") },
            text = { Text("Yoklama için yaptığınız değişiklikler henüz kaydedilmedi. Kaydetmeden ayrılırsanız bu değişiklikler kaybolacak. Yine de ayrılmak istiyor musunuz?") },
            confirmButton = {
                Button(onClick = {
                    val destination = pendingDestination
                    showUnsavedLeaveDialog = false
                    attendanceDirty = false
                    pendingDestination = -2
                    if (destination == -1) {
                        // The discard confirmation already serves as the exit confirmation when leaving from an unsaved roll call.
                        context.findActivity()?.finish()
                    } else if (destination >= 0) { screen = destination; notice = "" }
                }, colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)) { Text("Kaydetmeden ayrıl") }
            },
            dismissButton = { TextButton(onClick = { showUnsavedLeaveDialog = false; pendingDestination = -2 }) { Text("İptal") } }
        )
    }

    if (showStudentListReplaceConfirmation) {
        val incoming = pendingImportedStudents.orEmpty()
        val retainedCount = students.count { old -> incoming.any { new -> sameStudentIdentity(old, new) } }
        val removedCount = students.count { old -> incoming.none { new -> sameStudentIdentity(old, new) } }
        val addedCount = incoming.count { new -> students.none { old -> sameStudentIdentity(old, new) } }
        AlertDialog(
            onDismissRequest = { showStudentListReplaceConfirmation = false; pendingImportedStudents = null },
            title = { Text("Öğrenci listesi değiştirilsin mi?") },
            text = {
                Text(
                    "Bu derste zaten ${students.size} öğrenci kayıtlı. Yeni dosyada ${incoming.size} öğrenci var.\n\n" +
                        "• Öğrenci numarası ve adı-soyadı aynı olan $retainedCount öğrencinin mevcut yoklama kayıtları korunacak.\n" +
                        "• Eşleşmeyen $removedCount eski öğrenci listeden kaldırılacak ve bu öğrencilere ait devamsızlık kayıtları silinecek.\n" +
                        "• Yeni/eşleşmeyen $addedCount öğrenci yeni listeye eklenecek.\n\n" +
                        "Devam etmek istiyor musunuz? İşlem, ders ayarlarını kaydettiğinizde veritabanına uygulanır."
                )
            },
            confirmButton = {
                Button(onClick = {
                    val incomingRoster = pendingImportedStudents.orEmpty()
                    val retained = students.count { old -> incomingRoster.any { new -> sameStudentIdentity(old, new) } }
                    val removed = students.count { old -> incomingRoster.none { new -> sameStudentIdentity(old, new) } }
                    val added = incomingRoster.count { new -> students.none { old -> sameStudentIdentity(old, new) } }
                    students = incomingRoster
                    notice = "Yeni liste kabul edildi (${incomingRoster.size} öğrenci). Numara ve adı-soyadı eşleşen $retained öğrencinin yoklama kayıtları korunacak; $removed eski öğrenci kaldırılacak, $added yeni/eşleşmeyen öğrenci eklenecek. Değişiklikler ders ayarları kaydedilince uygulanır."
                    showStudentListReplaceConfirmation = false
                    pendingImportedStudents = null
                }) { Text("Evet, listeyi karşılaştır") }
            },
            dismissButton = {
                TextButton(onClick = {
                    showStudentListReplaceConfirmation = false
                    pendingImportedStudents = null
                    notice = "Öğrenci listesi değişikliği iptal edildi; mevcut liste korundu."
                }) { Text("İptal") }
            }
        )
    }

    if (showExitConfirmation) {
        AlertDialog(
            onDismissRequest = { showExitConfirmation = false },
            title = { Text("Uygulamadan çıkılsın mı?") },
            text = { Text("Devam Takip uygulamasından çıkmak istiyor musunuz?") },
            confirmButton = {
                Button(onClick = { showExitConfirmation = false; context.findActivity()?.finish() },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)) { Text("Çıkış yap") }
            },
            dismissButton = { TextButton(onClick = { showExitConfirmation = false }) { Text("Vazgeç") } }
        )
    }

    if (showRestoreConfirmation) {
        AlertDialog(
            onDismissRequest = { showRestoreConfirmation = false; pendingBackupBytes = null },
            title = { Text("Yedek geri yüklensin mi?") },
            text = { Text("Geri yükleme bu telefondaki dersleri, ders bazlı öğrenci listelerini ve yoklama kayıtlarını yedekteki verilerle değiştirir. Önce mevcut verilerin yedeğini almanız önerilir.") },
            confirmButton = {
                Button(onClick = {
                    runCatching {
                        db.restoreBackupJson(pendingBackupBytes ?: error("Yedek dosyası bellekte bulunamadı."))
                        courses = db.loadCourses()
                        selectedCourseId = courses.firstOrNull()?.id
                        reloadCourseRoster(selectedCourseId)
                        selectedReportStudent = null
                        attendanceDirty = false
                        screen = 0
                        notice = "Yedek başarıyla geri yüklendi."
                    }.onFailure { notice = "Yedek geri yüklenemedi: ${it.message ?: "Dosya geçersiz."}" }
                    showRestoreConfirmation = false
                    pendingBackupBytes = null
                }) { Text("Geri yükle") }
            },
            dismissButton = { TextButton(onClick = { showRestoreConfirmation = false; pendingBackupBytes = null }) { Text("İptal") } }
        )
    }
}

private fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}

@Composable
private fun HomeMenuScreen(
    onAddCourse: () -> Unit,
    onCourses: () -> Unit,
    onAttendance: () -> Unit,
    onReports: () -> Unit,
    onCalendar: () -> Unit,
    onBackup: () -> Unit,
    onApplicationNotes: () -> Unit,
    onExit: () -> Unit
) {
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 18.dp, vertical = 14.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Text("Ana menü", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        Text("İşlem yapmak istediğiniz bölümü seçin.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        MenuItemButton("Ders ekle", "Yeni ders ve program tanımla", Icons.Default.MenuBook, Color(0xFF1D4ED8), onAddCourse)
        MenuItemButton("Derslerim", "Kayıtlı dersleri görüntüle ve düzenle", Icons.Default.People, Color(0xFF16865C), onCourses)
        MenuItemButton("Yoklama al", "Hafta seç ve öğrenci yoklaması gir", Icons.Default.CheckCircle, Color(0xFFD97706), onAttendance)
        MenuItemButton("Devam raporları", "Özet ve ayrıntılı raporları gör", Icons.Default.Assessment, Color(0xFF7048C9), onReports)
        MenuItemButton("Ders programı takvimi", "Ders günlerini ve haftaları incele", Icons.Default.CalendarMonth, Color(0xFF0F8B8D), onCalendar)
        MenuItemButton("Uygulama notu", "Haftalık 0–4 not girişi", Icons.Default.Star, Color(0xFFD94876), onApplicationNotes)
        MenuItemButton("Veri yedekleme / geri yükleme", "Verilerini dışa aktar veya geri yükle", Icons.Default.Backup, Color(0xFF496783), onBackup)
        MenuItemButton("ÇIKIŞ", "Uygulamadan çık", Icons.Default.ExitToApp, Color(0xFFD71920), onExit, destructive = true)
    }
}

@Composable
private fun MenuItemButton(
    title: String,
    subtitle: String,
    icon: ImageVector,
    color: Color,
    onClick: () -> Unit,
    destructive: Boolean = false
) {
    Button(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth().heightIn(min = 66.dp),
        shape = RoundedCornerShape(7.dp),
        colors = ButtonDefaults.buttonColors(containerColor = color, contentColor = Color.White),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 16.dp, vertical = 10.dp)
    ) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            androidx.compose.material3.Icon(icon, contentDescription = null, modifier = Modifier.size(28.dp))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Text(subtitle, style = MaterialTheme.typography.bodySmall, maxLines = 2)
            }
            if (!destructive) androidx.compose.material3.Icon(Icons.Default.ChevronRight, contentDescription = null)
        }
    }
}

@Composable
private fun BackupScreen(onBackup: () -> Unit, onRestore: () -> Unit) {
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("Veri yedekleme ve geri yükleme", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        Text("Yedek; ders ayarlarını, her derse özel öğrenci listesini, devamsızlık saatlerini ve kaydedilmiş yoklama tarihlerini içerir. Yedek JSON dosyasıdır.")
        Button(onClick = onBackup, modifier = Modifier.fillMaxWidth().heightIn(min = 58.dp)) { Text("Yedek oluştur ve dışa aktar", style = MaterialTheme.typography.titleMedium) }
        OutlinedButton(onClick = onRestore, modifier = Modifier.fillMaxWidth().heightIn(min = 58.dp)) { Text("Yedekten geri yükle", style = MaterialTheme.typography.titleMedium) }
        Text("Geri yükleme mevcut kayıtları yedek içeriğiyle değiştirir. Geri yüklemeden önce yeni bir yedek almanız önerilir.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
    }
}

@Composable
private fun CourseCalendarScreen(config: AppConfig, db: AttendanceDb) {
    val savedDates = remember(config.id) { db.savedAttendanceDates(config.id) }
    val regularWeeks = (1..15).toList()
    Column(Modifier.fillMaxSize().padding(horizontal = 12.dp, vertical = 8.dp)) {
        Text(config.courseName, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        Text("Ders programı · ${config.unit}", style = MaterialTheme.typography.bodyMedium)
        Text("Vize haftasında yoklama yapılmaz ve bu hafta devam hesabına katılmaz.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(8.dp))
        LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(regularWeeks, key = { it }) { week ->
                val start = config.startDate.plusWeeks((week - 1).toLong())
                val midterm = week == config.midtermWeek
                Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = if (midterm) MaterialTheme.colorScheme.tertiaryContainer else MaterialTheme.colorScheme.surfaceVariant)) {
                    Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(if (midterm) "Hafta $week · VİZE HAFTASI — YOKLAMA YOK" else "Hafta $week", fontWeight = FontWeight.Bold)
                        (1..5).filter { (config.hoursByDay[it] ?: 0) > 0 }.forEach { day ->
                            val date = start.plusDays((day - 1).toLong())
                            val saved = savedDates.contains(date)
                            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                                Column(Modifier.weight(1f)) {
                                    Text("${dayNames[day]} · ${date.format(dateFormatter)}")
                                    Text("${config.hoursByDay[day]} ders saati", style = MaterialTheme.typography.bodySmall)
                                }
                                if (!midterm) Text(if (saved) "Kaydedildi ✓" else "Bekliyor", color = if (saved) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.labelMedium)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SetupScreen(
    config: AppConfig?,
    students: List<Student>,
    onSave: (AppConfig) -> Unit,
    onImport: () -> Unit,
    onRemoveStudents: () -> Unit,
    onBackup: () -> Unit
) {
    var courseName by remember(config) { mutableStateOf(config?.courseName.orEmpty()) }
    var unit by remember(config) { mutableStateOf(config?.unit.orEmpty()) }
    var startDate by remember(config) { mutableStateOf(config?.startDate ?: LocalDate.now().with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))) }
    var midtermWeek by remember(config) { mutableIntStateOf(config?.midtermWeek ?: 7) }
    var absenceLimitPercent by remember(config) { mutableIntStateOf(config?.absenceLimitPercent ?: 20) }
    var applicationNotePercent by remember(config) { mutableIntStateOf(config?.applicationNotePercent ?: 0) }
    var hoursByDay by remember(config) { mutableStateOf(config?.hoursByDay?.toMap() ?: (1..5).associateWith { 0 }) }
    var attemptedSubmit by remember(config) { mutableStateOf(false) }
    var showNoRosterDialog by remember(config) { mutableStateOf(false) }
    var showClearRosterDialog by remember(config) { mutableStateOf(false) }
    val context = LocalContext.current
    val weeklyHours = hoursByDay.values.sum()
    val validFields = courseName.isNotBlank() && unit.isNotBlank() && weeklyHours > 0
    val makeConfig = {
        AppConfig(
            id = config?.id ?: 0L,
            courseName = courseName.trim(),
            unit = unit.trim(),
            startDate = startDate,
            midtermWeek = midtermWeek,
            hoursByDay = (1..5).associateWith { (hoursByDay[it] ?: 0).coerceIn(0, 8) },
            absenceLimitPercent = absenceLimitPercent,
            applicationNotePercent = applicationNotePercent
        )
    }
    fun trySave() {
        attemptedSubmit = true
        if (!validFields) return
        if (students.isEmpty()) showNoRosterDialog = true else onSave(makeConfig())
    }

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(if (config == null) "Yeni ders ekle" else "Ders ayarlarını düzenle", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        OutlinedTextField(
            value = courseName,
            onValueChange = { courseName = it },
            modifier = Modifier.fillMaxWidth(),
            label = { Text("Ders adı *") },
            singleLine = true,
            isError = attemptedSubmit && courseName.isBlank()
        )
        if (attemptedSubmit && courseName.isBlank()) Text("Ders adı zorunludur.", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
        OutlinedTextField(
            value = unit,
            onValueChange = { unit = it },
            modifier = Modifier.fillMaxWidth(),
            label = { Text("Birim adı *") },
            supportingText = { Text("Birim adını serbestçe yazabilirsiniz.") },
            singleLine = true,
            isError = attemptedSubmit && unit.isBlank()
        )
        if (attemptedSubmit && unit.isBlank()) Text("Birim adı zorunludur.", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)

        Card(
            Modifier.fillMaxWidth(),
            border = if (attemptedSubmit && weeklyHours == 0) androidx.compose.foundation.BorderStroke(1.5.dp, MaterialTheme.colorScheme.error) else null
        ) {
            Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("1. haftanın başlangıcı", fontWeight = FontWeight.SemiBold)
                Text("Seçilen tarih ilgili haftanın pazartesi gününe göre ayarlanır.", style = MaterialTheme.typography.bodySmall)
                OutlinedButton(onClick = {
                    DatePickerDialog(
                        context,
                        { _, year, month, day ->
                            startDate = LocalDate.of(year, month + 1, day).with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
                        },
                        startDate.year, startDate.monthValue - 1, startDate.dayOfMonth
                    ).show()
                }, shape = RoundedCornerShape(7.dp)) {
                    Text("Başlangıç: ${startDate.format(dateFormatter)} (Pazartesi)")
                }
            }
        }

        Card(
            Modifier.fillMaxWidth(),
            border = if (attemptedSubmit && weeklyHours == 0) androidx.compose.foundation.BorderStroke(1.5.dp, MaterialTheme.colorScheme.error) else null
        ) {
            Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Haftalık ders programı *", fontWeight = FontWeight.SemiBold)
                Text("Ders yapılan günleri seçin, her gün için saat sayısını belirleyin.", style = MaterialTheme.typography.bodySmall)
                (1..5).forEach { day ->
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                        FilterChip(
                            selected = (hoursByDay[day] ?: 0) > 0,
                            onClick = {
                                val updated = hoursByDay.toMutableMap()
                                updated[day] = if ((updated[day] ?: 0) > 0) 0 else 1
                                hoursByDay = updated
                            },
                            label = { Text(dayNames[day] ?: "") }
                        )
                        Spacer(Modifier.width(10.dp))
                        if ((hoursByDay[day] ?: 0) > 0) {
                            Text("Saat:")
                            Spacer(Modifier.width(6.dp))
                            IntDropdown(
                                value = hoursByDay[day] ?: 1,
                                values = (1..8).toList(),
                                display = { "$it" },
                                onSelected = { value -> hoursByDay = hoursByDay.toMutableMap().apply { put(day, value) } }
                            )
                        } else {
                            Text("Ders yok", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
                Divider()
                Text("Haftalık toplam: $weeklyHours saat", fontWeight = FontWeight.Medium)
                if (attemptedSubmit && weeklyHours == 0) Text("En az bir ders günü ve saat sayısı seçilmelidir.", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
            }
        }

        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Vize haftası", fontWeight = FontWeight.SemiBold)
                Text("14 normal ders haftasına ek olarak 15 takvim haftasından vize haftasını seçin. Vize haftasında yoklama alınmaz.", style = MaterialTheme.typography.bodySmall)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Takvim haftası:")
                    Spacer(Modifier.width(10.dp))
                    IntDropdown(
                        value = midtermWeek,
                        values = (1..15).toList(),
                        display = { week -> if (week == midtermWeek) "Hafta $week · Vize" else "Hafta $week" },
                        onSelected = { midtermWeek = it }
                    )
                }
            }
        }

        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Devamsızlık sınırı uyarısı", fontWeight = FontWeight.SemiBold)
                Text("14 normal ders haftası üzerinden hesaplanır; vize haftası dahil edilmez.", style = MaterialTheme.typography.bodySmall)
                val normalHours = weeklyHours * 14
                val limitHours = ceil(normalHours * absenceLimitPercent / 100.0).toInt()
                val warningHours = ceil(limitHours * 0.8).toInt()
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Sınır:")
                    Spacer(Modifier.width(8.dp))
                    IntDropdown(
                        value = absenceLimitPercent,
                        values = (5..100 step 5).toList(),
                        display = { "%$it (${ceil(normalHours * it / 100.0).toInt()} saat)" },
                        onSelected = { absenceLimitPercent = it }
                    )
                }
                Text("Toplam dönem dersi: $normalHours saat · Yaklaşma uyarısı yaklaşık $warningHours devamsız saatte başlar.", style = MaterialTheme.typography.bodySmall)
                Text("Tüm kesirli saat hesapları yukarı yuvarlanır.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }

        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Uygulama notu ağırlığı", fontWeight = FontWeight.SemiBold)
                Text("Uygulama notunun ders başarı puanına katkı ağırlığını seçin.", style = MaterialTheme.typography.bodySmall)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Ağırlık:")
                    Spacer(Modifier.width(8.dp))
                    IntDropdown(value = applicationNotePercent, values = (0..100 step 5).toList(), display = { "%$it" }, onSelected = { applicationNotePercent = it })
                }
            }
        }

        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Öğrenci listesi", fontWeight = FontWeight.SemiBold)
                Text("Kayıtlı öğrenci: ${students.size}", style = MaterialTheme.typography.bodyMedium)
                Text("Excel sütunları Öğrenci No + Adı Soyadı veya Öğrenci No + Ad + Soyad olmalıdır.", style = MaterialTheme.typography.bodySmall)
                Button(onClick = onImport, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(7.dp)) { Text("Excel / CSV listesinden öğrenci aktar") }
                OutlinedButton(
                    onClick = { showClearRosterDialog = true },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = students.isNotEmpty(),
                    shape = RoundedCornerShape(7.dp),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error)
                ) { Text("Öğrenci listesini kaldır / temizle") }
                if (students.isNotEmpty()) {
                    Divider()
                    Text("Aktarılan öğrenci kayıtları", fontWeight = FontWeight.SemiBold)
                    students.take(8).forEach { student ->
                        Column(Modifier.fillMaxWidth().padding(vertical = 3.dp), verticalArrangement = Arrangement.spacedBy(1.dp)) {
                            Text(student.studentNo, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold)
                            Text(student.fullName, style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                    if (students.size > 8) Text("İlk 8 kayıt gösteriliyor. Diğer ${students.size - 8} öğrenci de Yoklama ekranında listelenir.", style = MaterialTheme.typography.bodySmall)
                }
            }
        }
        Button(onClick = { trySave() }, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp), shape = RoundedCornerShape(7.dp)) {
            Text(if (config == null) "Ders ayarlarını kaydet" else "Ders değişikliklerini kaydet")
        }
        Text("Not: Veriler bu telefonda saklanır. Her dersin öğrenci listesi ve kayıtları ayrıdır.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }

    if (showNoRosterDialog) {
        AlertDialog(
            onDismissRequest = { showNoRosterDialog = false },
            title = { Text("Öğrenci listesi eklenmedi") },
            text = { Text("Ders ayarlarını öğrenci listesi olmadan kaydetmek istiyor musunuz? Listeyi daha sonra Derslerim üzerinden ekleyebilirsiniz.") },
            confirmButton = { Button(onClick = { showNoRosterDialog = false; onImport() }) { Text("Öğrenci ekle") } },
            dismissButton = { TextButton(onClick = { showNoRosterDialog = false; if (validFields) onSave(makeConfig()) }) { Text("Daha sonra ekle") } }
        )
    }

    if (showClearRosterDialog) {
        AlertDialog(
            onDismissRequest = { showClearRosterDialog = false },
            title = { Text("Öğrenci listesi temizlensin mi?") },
            text = { Text("Bu işlem bu dersteki öğrenci listesini ve bu öğrencilere ait yoklama/uygulama notu kayıtlarını kaldırır. Devam etmeden önce yedek almanız önerilir.") },
            confirmButton = {
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                    TextButton(onClick = onBackup) { Text("Önce yedek al") }
                    Button(onClick = { showClearRosterDialog = false; onRemoveStudents() }, colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)) { Text("Temizle") }
                }
            },
            dismissButton = { TextButton(onClick = { showClearRosterDialog = false }) { Text("İptal") } }
        )
    }
}

@Composable
private fun UnitDropdown(value: String, onSelected: (String) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        OutlinedButton(onClick = { expanded = true }, modifier = Modifier.fillMaxWidth()) {
            Text(value, modifier = Modifier.fillMaxWidth())
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            listOf("Bolvadin MYO", "BUB Fakültesi").forEach { option ->
                DropdownMenuItem(text = { Text(option) }, onClick = { onSelected(option); expanded = false })
            }
        }
    }
}

@Composable
private fun CoursesScreen(
    courses: List<AppConfig>,
    selectedCourseId: Long?,
    studentCount: Int,
    onSelect: (Long) -> Unit,
    onAdd: () -> Unit,
    onEdit: (AppConfig) -> Unit,
    onTakeAttendance: (Long) -> Unit,
    onDelete: (AppConfig) -> Unit
) {
    var courseToDelete by remember { mutableStateOf<AppConfig?>(null) }
    Column(Modifier.fillMaxSize().padding(horizontal = 12.dp, vertical = 8.dp)) {
        Text("Derslerim", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        Text("Kayıtlı ders sayısı: ${courses.size} · Öğrenci sayısı: $studentCount", style = MaterialTheme.typography.bodySmall)
        Spacer(Modifier.height(8.dp))
        Button(onClick = onAdd, modifier = Modifier.fillMaxWidth()) { Text("+ Yeni ders ekle") }
        if (courses.isEmpty()) {
            EmptyState("Henüz ders eklenmedi. Yeni ders ekle düğmesini kullanarak ders adı, birim ve haftalık programı kaydedin.")
        } else {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.weight(1f)) {
                items(courses, key = { it.id }) { course ->
                    val weekHours = course.hoursByDay.values.sum()
                    Card(
                        Modifier.fillMaxWidth().clickable { onSelect(course.id) },
                        colors = CardDefaults.cardColors(
                            containerColor = if (course.id == selectedCourseId) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surfaceVariant
                        )
                    ) {
                        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                            Text(course.courseName, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                            Text("Birim: ${course.unit}", style = MaterialTheme.typography.bodyMedium)
                            Text("Haftalık $weekHours saat · Vize haftası: ${course.midtermWeek}", style = MaterialTheme.typography.bodySmall)
                            Text(
                                (1..5).filter { (course.hoursByDay[it] ?: 0) > 0 }
                                    .joinToString(" · ") { "${dayShortNames[it]}: ${course.hoursByDay[it]} saat" }
                                    .ifBlank { "Haftalık program tanımlanmamış" },
                                style = MaterialTheme.typography.bodySmall
                            )
                            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth()) {
                                Button(onClick = { onTakeAttendance(course.id) }, modifier = Modifier.weight(1f)) { Text("Yoklama al") }
                                OutlinedButton(onClick = { onEdit(course) }, modifier = Modifier.weight(1f)) { Text("Düzenle") }
                                OutlinedButton(onClick = { courseToDelete = course }) { Text("Sil") }
                            }
                        }
                    }
                }
            }
        }
    }
    val deleteCandidate = courseToDelete
    if (deleteCandidate != null) {
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { courseToDelete = null },
            title = { Text("Ders silinsin mi?") },
            text = { Text("${deleteCandidate.courseName} ve bu derse ait yoklama kayıtları silinecek. Bu işlem geri alınamaz.") },
            confirmButton = {
                Button(onClick = { onDelete(deleteCandidate); courseToDelete = null }) { Text("Sil") }
            },
            dismissButton = { OutlinedButton(onClick = { courseToDelete = null }) { Text("Vazgeç") } }
        )
    }
}

@Composable
private fun IntDropdown(
    value: Int,
    values: List<Int>,
    display: (Int) -> String,
    onSelected: (Int) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        OutlinedButton(onClick = { expanded = true }) { Text(display(value)) }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            values.forEach { option ->
                DropdownMenuItem(
                    text = { Text(display(option)) },
                    onClick = { onSelected(option); expanded = false }
                )
            }
        }
    }
}

private fun weekNumberForDate(config: AppConfig, date: LocalDate): Int? {
    val start = config.startDate.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
    val distance = ChronoUnit.DAYS.between(start, date)
    if (distance < 0) return null
    val week = (distance / 7L).toInt() + 1
    return week.takeIf { it in 1..15 }
}

private fun isMidtermDate(config: AppConfig, date: LocalDate): Boolean = weekNumberForDate(config, date) == config.midtermWeek

private fun normalTermHours(config: AppConfig): Int = config.hoursByDay.values.sum() * 14

private fun absenceWarning(absentHours: Int, scheduledHours: Int, limitPercent: Int): String? {
    if (scheduledHours <= 0) return null
    val percent = ceil(absentHours * 100.0 / scheduledHours).toInt()
    val limitHours = ceil(scheduledHours * limitPercent / 100.0).toInt()
    val approachHours = ceil(limitHours * 0.8).toInt()
    return when {
        absentHours > limitHours -> "SINIR AŞILDI · %$percent ($absentHours saat; sınır $limitHours saat)"
        absentHours >= limitHours -> "SINIRA ULAŞTI · %$percent ($absentHours / $scheduledHours saat)"
        absentHours >= approachHours -> "SINIRA YAKLAŞTI · %$percent ($absentHours / $scheduledHours saat; eşik $approachHours saat)"
        else -> null
    }
}

@Composable
private fun AttendanceScreen(
    config: AppConfig,
    students: List<Student>,
    db: AttendanceDb,
    revision: Int,
    onDirtyChange: (Boolean) -> Unit,
    onSaved: () -> Unit
) {
    var selectedWeek by remember(config.id) { mutableIntStateOf(0) }
    var selectedDayState by remember(config.id) { mutableIntStateOf(config.hoursByDay.entries.firstOrNull { it.value > 0 }?.key ?: 1) }
    var query by remember(config.id) { mutableStateOf("") }
    var showDiscardDialog by remember { mutableStateOf(false) }
    var targetDay by remember { mutableIntStateOf(-1) } // -1 means return to week list
    var studentForReport by remember { mutableStateOf<Student?>(null) }
    val availableDays = config.hoursByDay.filterValues { it > 0 }.keys.sorted()
    val selectedDay = selectedDayState.takeIf { it in availableDays } ?: (availableDays.firstOrNull() ?: 1)
    val normalizedStart = config.startDate.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
    val weekStart = if (selectedWeek in 1..15) normalizedStart.plusWeeks((selectedWeek - 1).toLong()) else normalizedStart
    val date = weekStart.plusDays((selectedDay - 1).toLong())
    val hours = config.hoursByDay[selectedDay] ?: 0
    @Suppress("UNUSED_VARIABLE") val forceRefresh = revision
    val rosterKey = students.map { it.studentNo }
    var savedState by remember(config.id, selectedWeek, selectedDay, rosterKey) {
        mutableStateOf(students.associate { it.studentNo to db.absentHours(config.id, it.studentNo, date) })
    }
    var draftState by remember(config.id, selectedWeek, selectedDay, rosterKey) { mutableStateOf(savedState) }
    val activeNormalWeek = selectedWeek in 1..15 && selectedWeek != config.midtermWeek
    val sessionSaved = if (activeNormalWeek && selectedDay in availableDays) db.isAttendanceSaved(config.id, date) else true
    // A newly opened session is pending even when no button has been changed; explicit Save is required.
    val dirty = activeNormalWeek && (!sessionSaved || draftState != savedState)
    LaunchedEffect(dirty) { onDirtyChange(dirty) }

    fun setDraft(next: Map<String, Set<Int>>) {
        draftState = next
        onDirtyChange(next != savedState)
    }

    fun requestDayOrWeek(day: Int) {
        if (dirty) {
            targetDay = day
            showDiscardDialog = true
        } else if (day == -1) {
            selectedWeek = 0
            onDirtyChange(false)
        } else selectedDayState = day
    }

    fun saveCurrentDay() {
        if (selectedWeek !in 1..15 || selectedWeek == config.midtermWeek || hours <= 0) return
        db.saveAttendanceForDate(config.id, date, hours, draftState)
        savedState = draftState.toMap()
        onSaved()
    }

    if (selectedWeek == 0) {
        Column(Modifier.fillMaxSize().padding(horizontal = 12.dp, vertical = 8.dp)) {
            Text("Yoklama al", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            Text("${config.courseName} · ${config.unit}", style = MaterialTheme.typography.bodySmall)
            Spacer(Modifier.height(8.dp))
            Text("Hafta seçin", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            LazyColumn(modifier = Modifier.fillMaxWidth().weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                items((1..15).toList(), key = { it }) { week ->
                    val start = normalizedStart.plusWeeks((week - 1).toLong())
                    val end = start.plusDays(6)
                    val isMidterm = week == config.midtermWeek
                    val dateLine = availableDays.joinToString(" · ") { day -> "${dayShortNames[day]} ${start.plusDays((day - 1).toLong()).format(dateFormatter)}" }
                    Card(
                        Modifier.fillMaxWidth().clickable { selectedWeek = week },
                        colors = CardDefaults.cardColors(containerColor = if (isMidterm) MaterialTheme.colorScheme.tertiaryContainer else MaterialTheme.colorScheme.surfaceVariant)
                    ) {
                        Row(Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                                Text(if (isMidterm) "Hafta $week · VİZE (yoklama yok)" else "Hafta $week", fontWeight = FontWeight.SemiBold)
                                Text("${start.format(dateFormatter)} – ${end.format(dateFormatter)}", style = MaterialTheme.typography.bodySmall)
                                if (!isMidterm && dateLine.isNotBlank()) Text(dateLine, style = MaterialTheme.typography.bodySmall)
                            }
                            Text("Aç ›", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.SemiBold)
                        }
                    }
                }
            }
        }
        return
    }

    if (selectedWeek == config.midtermWeek) {
        Column(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            OutlinedButton(onClick = { selectedWeek = 0 }, modifier = Modifier.fillMaxWidth()) { Text("← Haftalara dön") }
            Text("Hafta $selectedWeek · VİZE", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.tertiaryContainer)) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Vize haftası", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Text("Bu hafta yoklama girişi kapalıdır. Vize haftası devamsızlık hesaplamasına dahil edilmez. Ders ayarlarından vize haftasını değiştirirseniz o hafta normal yoklama haftası olarak açılır.")
                }
            }
        }
        return
    }

    Column(Modifier.fillMaxSize().padding(horizontal = 10.dp, vertical = 5.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            OutlinedButton(onClick = { requestDayOrWeek(-1) }, contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 8.dp, vertical = 6.dp)) { Text("← Haftalar") }
            Spacer(Modifier.width(8.dp))
            Text(
                "Hafta $selectedWeek · ${config.courseName} · ${dayNames[selectedDay]} · ${date.format(dateFormatter)} · $hours saat",
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                maxLines = 2
            )
        }
        if (availableDays.size > 1) {
            Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(top = 3.dp, bottom = 2.dp), horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                availableDays.forEach { day ->
                    FilterChip(
                        selected = selectedDay == day,
                        onClick = { requestDayOrWeek(day) },
                        label = { Text(dayShortNames[day] ?: (dayNames[day] ?: "Gün")) }
                    )
                }
            }
        }
        if (dirty) {
            Text("Kaydedilmemiş değişiklikler var.", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.labelMedium)
        }
        Button(onClick = { saveCurrentDay() }, modifier = Modifier.fillMaxWidth().heightIn(min = 44.dp), enabled = students.isNotEmpty()) {
            androidx.compose.material3.Icon(Icons.Default.Save, contentDescription = null)
            Spacer(Modifier.width(6.dp))
            Text("YOKLAMAYI KAYDET")
        }
        Spacer(Modifier.height(3.dp))

        if (students.isEmpty()) {
            EmptyState("Bu derse ait öğrenci listesi yok. Ders ayarlarından bu ders için Excel/CSV dosyası aktarın.")
        } else if (hours > 0) {
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                leadingIcon = { androidx.compose.material3.Icon(Icons.Default.Search, contentDescription = null) },
                label = { Text("Öğrenci no veya ad soyada göre ara") }
            )
            Text("Gösterilen öğrenci: ${students.count { query.isBlank() || it.studentNo.contains(query, true) || it.fullName.contains(query, true) }} / ${students.size}", style = MaterialTheme.typography.labelSmall)
            LazyColumn(verticalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.weight(1f)) {
                items(students.filter { query.isBlank() || it.studentNo.contains(query.trim(), true) || it.fullName.contains(query.trim(), true) }, key = { it.studentNo }) { student ->
                    val absentHours = draftState[student.studentNo].orEmpty().count { it in 1..hours }
                    val priorAbsences = db.getAbsences(config.id, student.studentNo).filter { record ->
                        val week = weekNumberForDate(config, record.date)
                        week != null && week <= selectedWeek && week != config.midtermWeek
                    }
                    val priorAbsenceCount = priorAbsences.count { it.date != date } + draftState[student.studentNo].orEmpty().count { it in 1..hours }
                    val status = absenceWarning(priorAbsenceCount, normalTermHours(config), config.absenceLimitPercent)
                    Card(Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(horizontal = 9.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                    Text(student.studentNo, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleSmall)
                                    Text(student.fullName.ifBlank { "Adı Soyadı bilgisi bulunamadı" }, style = MaterialTheme.typography.bodyMedium)
                                }
                                OutlinedButton(onClick = { studentForReport = student }, contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 7.dp, vertical = 5.dp)) {
                                    Text("Öğrenci raporu", style = MaterialTheme.typography.labelSmall)
                                }
                            }
                            if (status != null) {
                                Text(status, color = if (status.startsWith("SINIR AŞILDI")) MaterialTheme.colorScheme.error else Color(0xFFB36B00), style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                            }
                            (1..hours).toList().chunked(4).forEach { hourRow ->
                                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                                    hourRow.forEach { hour ->
                                        val absent = hour in draftState[student.studentNo].orEmpty()
                                        Button(
                                            onClick = {
                                                val update = draftState.toMutableMap()
                                                val set = draftState[student.studentNo].orEmpty().toMutableSet()
                                                if (absent) set.remove(hour) else set.add(hour)
                                                update[student.studentNo] = set
                                                setDraft(update)
                                            },
                                            modifier = Modifier.weight(1f).height(42.dp),
                                            shape = RoundedCornerShape(8.dp),
                                            colors = ButtonDefaults.buttonColors(
                                                containerColor = if (absent) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.secondaryContainer,
                                                contentColor = if (absent) MaterialTheme.colorScheme.onError else MaterialTheme.colorScheme.onSecondaryContainer
                                            ),
                                            contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 1.dp, vertical = 3.dp)
                                        ) { Text(if (absent) "$hour · Yok" else "$hour · Var", style = MaterialTheme.typography.labelSmall, maxLines = 1) }
                                    }
                                    repeat(4 - hourRow.size) { Spacer(Modifier.weight(1f)) }
                                }
                            }
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                OutlinedButton(
                                    onClick = { val update = draftState.toMutableMap(); update[student.studentNo] = (1..hours).toSet(); setDraft(update) },
                                    modifier = Modifier.weight(1f).heightIn(min = 34.dp),
                                    contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 3.dp, vertical = 3.dp)
                                ) { Text("Tüm saatleri seç", style = MaterialTheme.typography.labelSmall, maxLines = 1) }
                                OutlinedButton(
                                    onClick = { val update = draftState.toMutableMap(); update[student.studentNo] = emptySet(); setDraft(update) },
                                    modifier = Modifier.weight(1f).heightIn(min = 34.dp),
                                    contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 3.dp, vertical = 3.dp)
                                ) { Text("Tüm saatleri kaldır", style = MaterialTheme.typography.labelSmall, maxLines = 1) }
                            }
                            Text("Bu gün için işaretlenen: $absentHours / $hours saat devamsız", style = MaterialTheme.typography.labelSmall, color = if (absentHours > 0) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary)
                        }
                    }
                }
            }
            Button(onClick = { saveCurrentDay() }, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp), enabled = students.isNotEmpty()) {
                androidx.compose.material3.Icon(Icons.Default.Save, contentDescription = null)
                Spacer(Modifier.width(6.dp))
                Text("YOKLAMAYI KAYDET")
            }
        } else EmptyState("Seçili gün için ders saati tanımlanmamış.")
    }

    if (showDiscardDialog) {
        AlertDialog(
            onDismissRequest = { showDiscardDialog = false; targetDay = -1 },
            title = { Text("Kaydedilmemiş yoklama") },
            text = { Text("Değişiklikleri kaydetmeden bu ekrandan ayrılmak istiyor musunuz?") },
            confirmButton = { Button(onClick = {
                showDiscardDialog = false
                onDirtyChange(false)
                val destination = targetDay; targetDay = -1
                if (destination == -1) selectedWeek = 0 else if (destination > 0) selectedDayState = destination
            }, colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)) { Text("Kaydetmeden ayrıl") } },
            dismissButton = { TextButton(onClick = { showDiscardDialog = false; targetDay = -1 }) { Text("İptal") } }
        )
    }

    val reportStudent = studentForReport
    if (reportStudent != null) {
        val reportAbsences = db.getAbsences(config.id, reportStudent.studentNo).filter { record ->
            val week = weekNumberForDate(config, record.date)
            week != null && week <= selectedWeek && week != config.midtermWeek
        }
        AlertDialog(
            onDismissRequest = { studentForReport = null },
            title = { Text("Hafta $selectedWeek sonuna kadar devamsızlık") },
            text = {
                Column(Modifier.heightIn(max = 420.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                    Text("${reportStudent.fullName} · ${reportStudent.studentNo}", fontWeight = FontWeight.Bold)
                    Text("Toplam devamsız: ${reportAbsences.size} saat")
                    if (reportAbsences.isEmpty()) Text("Bu haftaya kadar kayıtlı devamsızlık yok.")
                    reportAbsences.sortedWith(compareBy<AbsenceRecord> { it.date }.thenBy { it.hour }).forEach { r ->
                        Text("${r.date.format(dateFormatter)} · ${dayNames[r.date.dayOfWeek.value]} · ${r.hour}. ders saati")
                    }
                }
            },
            confirmButton = { TextButton(onClick = { studentForReport = null }) { Text("Kapat") } }
        )
    }
}

@Composable
private fun ApplicationNoteScreen(
    config: AppConfig,
    students: List<Student>,
    db: AttendanceDb,
    revision: Int,
    onSaved: () -> Unit,
    onOpenReports: () -> Unit
) {
    var selectedWeek by remember(config.id) { mutableIntStateOf(1) }
    var searchQuery by remember(config.id) { mutableStateOf("") }
    val weeklyHours = config.hoursByDay.values.sum()
    val rosterKey = students.map { it.studentNo }
    var draft by remember(config.id, selectedWeek, rosterKey, revision) {
        mutableStateOf(db.loadApplicationNotes(config.id, selectedWeek))
    }

    Column(Modifier.fillMaxSize().padding(horizontal = 10.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Column(Modifier.weight(1f)) {
                Text(config.courseName, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Text("Uygulama notu ağırlığı: %${config.applicationNotePercent}", style = MaterialTheme.typography.bodySmall)
            }
            OutlinedButton(onClick = onOpenReports, shape = RoundedCornerShape(7.dp)) {
                androidx.compose.material3.Icon(Icons.Default.Assessment, contentDescription = null)
                Spacer(Modifier.width(5.dp))
                Text("Raporlar")
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Hafta:", fontWeight = FontWeight.SemiBold)
            IntDropdown(value = selectedWeek, values = (1..15).toList(), display = { "Hafta $it${if (it == config.midtermWeek) " · Vize" else ""}" }, onSelected = { selectedWeek = it })
            Spacer(Modifier.weight(1f))
            Text("$weeklyHours puan/hafta", style = MaterialTheme.typography.labelSmall)
        }
        Text("Her öğrencinin haftalık programındaki her ders saati için 0–4 arasında puan seçin. Değişiklikler otomatik kaydedilir.", style = MaterialTheme.typography.bodySmall)
        OutlinedTextField(
            value = searchQuery,
            onValueChange = { searchQuery = it },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            leadingIcon = { androidx.compose.material3.Icon(Icons.Default.Search, contentDescription = null) },
            label = { Text("Öğrenci no veya ad soyada göre ara") }
        )
        if (config.applicationNotePercent == 0) {
            Text("Not ağırlığı şu anda %0. Ders ayarlarından ağırlık yüzdesini değiştirebilirsiniz.", color = Color(0xFFAD5D00), style = MaterialTheme.typography.labelMedium)
        }
        if (students.isEmpty()) {
            EmptyState("Bu derse ait öğrenci listesi bulunmuyor. Ders ayarlarından Excel/CSV listesi ekleyin.")
        } else if (weeklyHours == 0) {
            EmptyState("Dersin haftalık saat sayısı tanımlanmamış.")
        } else {
            Text("Her öğrenci için $weeklyHours saatlik puan alanı", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold)
            LazyColumn(Modifier.fillMaxWidth().weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                items(students.filter { searchQuery.isBlank() || it.studentNo.contains(searchQuery.trim(), true) || it.fullName.contains(searchQuery.trim(), true) }, key = { it.studentNo }) { student ->
                    Card(Modifier.fillMaxWidth()) {
                        Column(Modifier.fillMaxWidth().padding(8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text(student.studentNo, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
                            Text(student.fullName, style = MaterialTheme.typography.bodyMedium)
                            Row(
                                Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                (1..weeklyHours).forEach { hour ->
                                    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                        Text("${hour}. saat", style = MaterialTheme.typography.labelSmall)
                                        NoteScoreDropdown(
                                            value = draft[student.studentNo]?.get(hour) ?: 0,
                                            onSelected = { score ->
                                                val current = (1..weeklyHours).associateWith { slot -> draft[student.studentNo]?.get(slot) ?: 0 }.toMutableMap()
                                                current[hour] = score
                                                val updated = draft.toMutableMap().apply { put(student.studentNo, current.toMap()) }
                                                draft = updated
                                                db.saveApplicationNotes(config.id, selectedWeek, mapOf(student.studentNo to current.toMap()))
                                                onSaved()
                                            }
                                        )
                                    }
                                }
                            }
                            val values = draft[student.studentNo].orEmpty().values
                            Text(
                                "Hafta ortalaması: ${if (values.isEmpty()) "0,00" else String.format(Locale("tr", "TR"), "%.2f", values.average())} / 4",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun NoteScoreDropdown(value: Int, onSelected: (Int) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        OutlinedButton(onClick = { expanded = true }, shape = RoundedCornerShape(6.dp), contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 10.dp, vertical = 4.dp)) {
            Text(value.toString(), fontWeight = FontWeight.Bold)
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            (0..4).forEach { score ->
                DropdownMenuItem(text = { Text(score.toString()) }, onClick = { onSelected(score); expanded = false })
            }
        }
    }
}

private data class ApplicationNoteSummary(
    val student: Student,
    val count: Int,
    val average: Double,
    val weightedContribution: Double
)

private fun applicationNoteSummaries(
    config: AppConfig,
    students: List<Student>,
    db: AttendanceDb,
    studentNo: String?
): List<ApplicationNoteSummary> {
    val allowedStudents = students.filter { studentNo == null || it.studentNo == studentNo }
    val notes = db.getApplicationNotes(config.id, studentNo).groupBy { it.studentNo }
    return allowedStudents.map { student ->
        val values = notes[student.studentNo].orEmpty().map { it.score.coerceIn(0, 4) }
        val average = if (values.isEmpty()) 0.0 else values.average()
        ApplicationNoteSummary(
            student = student,
            count = values.size,
            average = average,
            weightedContribution = average / 4.0 * config.applicationNotePercent
        )
    }
}

@Composable
private fun ApplicationNoteReportScreen(
    config: AppConfig,
    students: List<Student>,
    db: AttendanceDb,
    onExport: (String?, ReportFormat, Boolean) -> Unit
) {
    var individual by remember(config.id) { mutableStateOf(false) }
    var selectedStudentNo by remember(config.id) { mutableStateOf(students.firstOrNull()?.studentNo) }
    var detailed by remember(config.id) { mutableStateOf(false) }
    var studentMenuExpanded by remember { mutableStateOf(false) }
    val reportStudentNo = if (individual) selectedStudentNo else null
    val summaries = applicationNoteSummaries(config, students, db, reportStudentNo)
    val records = db.getApplicationNotes(config.id, reportStudentNo)
    Column(Modifier.fillMaxSize().padding(horizontal = 12.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(config.courseName, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        Text("Uygulama notu ağırlığı: %${config.applicationNotePercent} · 0–4 puan ölçeği", style = MaterialTheme.typography.bodySmall)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(selected = !individual, onClick = { individual = false }, label = { Text("Toplu") })
            FilterChip(selected = individual, onClick = { individual = true; if (selectedStudentNo == null) selectedStudentNo = students.firstOrNull()?.studentNo }, label = { Text("Öğrenci bazlı") })
        }
        if (individual) {
            Box {
                OutlinedButton(onClick = { studentMenuExpanded = true }, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(7.dp)) {
                    Text(students.firstOrNull { it.studentNo == selectedStudentNo }?.let { "${it.studentNo} · ${it.fullName}" } ?: "Öğrenci seçin", modifier = Modifier.fillMaxWidth())
                }
                DropdownMenu(expanded = studentMenuExpanded, onDismissRequest = { studentMenuExpanded = false }) {
                    students.forEach { student ->
                        DropdownMenuItem(text = { Text("${student.studentNo} · ${student.fullName}") }, onClick = { selectedStudentNo = student.studentNo; studentMenuExpanded = false })
                    }
                }
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(selected = !detailed, onClick = { detailed = false }, label = { Text("Özet rapor") })
            FilterChip(selected = detailed, onClick = { detailed = true }, label = { Text("Ayrıntılı rapor") })
        }
        if (!detailed) {
            Text("Ortalama puan / 4 ve uygulama notunun ders içindeki ağırlıklı katkısı gösterilir.", style = MaterialTheme.typography.bodySmall)
            LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                items(summaries, key = { it.student.studentNo }) { summary ->
                    Card(Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                            Text(summary.student.studentNo, fontWeight = FontWeight.Bold)
                            Text(summary.student.fullName)
                            Text("Kaydedilmiş puan: ${summary.count} · Ortalama: ${String.format(Locale("tr", "TR"), "%.2f", summary.average)} / 4")
                            Text("Ağırlıklı katkı: ${String.format(Locale("tr", "TR"), "%.2f", summary.weightedContribution)} puan", fontWeight = FontWeight.SemiBold)
                        }
                    }
                }
            }
        } else {
            if (records.isEmpty()) EmptyState("Seçim için kaydedilmiş uygulama notu bulunmuyor.")
            else LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                items(records.sortedWith(compareBy<ApplicationNoteRecord> { it.week }.thenBy { it.studentNo }.thenBy { it.hour })) { record ->
                    val student = students.firstOrNull { it.studentNo == record.studentNo }
                    Card(Modifier.fillMaxWidth()) {
                        Row(Modifier.fillMaxWidth().padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text("Hafta ${record.week} · ${record.hour}. saat", fontWeight = FontWeight.SemiBold)
                                Text("${record.studentNo} · ${student?.fullName.orEmpty()}", style = MaterialTheme.typography.bodySmall)
                            }
                            Text("${record.score} / 4", fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = { onExport(reportStudentNo, ReportFormat.PDF, detailed) }, modifier = Modifier.weight(1f), shape = RoundedCornerShape(7.dp)) { Text("PDF dışa aktar") }
            Button(onClick = { onExport(reportStudentNo, ReportFormat.EXCEL, detailed) }, modifier = Modifier.weight(1f), shape = RoundedCornerShape(7.dp)) { Text("Excel dışa aktar") }
        }
    }
}

private fun buildApplicationNoteReportPdf(
    config: AppConfig,
    students: List<Student>,
    db: AttendanceDb,
    studentNo: String?,
    detailed: Boolean
): ByteArray {
    val summaries = applicationNoteSummaries(config, students, db, studentNo)
    val notes = db.getApplicationNotes(config.id, studentNo).sortedWith(compareBy<ApplicationNoteRecord> { it.week }.thenBy { it.studentNo }.thenBy { it.hour })
    val writer = PdfTextWriter()
    writer.line(if (detailed) "AYRINTILI UYGULAMA NOTU RAPORU" else "UYGULAMA NOTU ÖZET RAPORU", bold = true, size = 16f, spacing = 26f)
    writer.line("Ders: ${config.courseName}", bold = true, size = 12f)
    writer.line("Birim: ${config.unit}")
    writer.line("Rapor tarihi: ${LocalDate.now().format(dateFormatter)}")
    writer.line("Uygulama notu ağırlığı: %${config.applicationNotePercent} · Puan aralığı: 0–4")
    writer.blank()
    writer.line("ÖĞRENCİ ÖZETİ", bold = true, size = 12f, spacing = 20f)
    summaries.forEach { summary ->
        writer.line("${summary.student.studentNo} — ${summary.student.fullName}", bold = true)
        writer.line("Kaydedilmiş puan: ${summary.count} · Ortalama: ${String.format(Locale("tr", "TR"), "%.2f", summary.average)} / 4 · Ağırlıklı katkı: ${String.format(Locale("tr", "TR"), "%.2f", summary.weightedContribution)}")
        writer.blank(3f)
    }
    if (detailed) {
        writer.blank()
        writer.line("AYRINTILI SAATLİK PUANLAR", bold = true, size = 12f, spacing = 20f)
        if (notes.isEmpty()) writer.line("Kaydedilmiş puan bulunmuyor.")
        notes.forEach { note ->
            val student = students.firstOrNull { it.studentNo == note.studentNo }
            writer.line("Hafta ${note.week} · ${note.hour}. saat · ${note.studentNo} — ${student?.fullName.orEmpty()} · Puan: ${note.score}/4")
        }
    } else {
        writer.blank()
        writer.line("HAFTALIK ÖZET", bold = true, size = 12f, spacing = 20f)
        val grouped = notes.groupBy { it.week }.toSortedMap()
        (1..15).forEach { week ->
            val current = grouped[week].orEmpty()
            val average = if (current.isEmpty()) "—" else String.format(Locale("tr", "TR"), "%.2f", current.map { it.score }.average())
            writer.line("Hafta $week · ${current.size} puan kaydı · Ortalama: $average / 4")
        }
    }
    return writer.finish()
}

private fun buildApplicationNoteReportXlsx(
    config: AppConfig,
    students: List<Student>,
    db: AttendanceDb,
    studentNo: String?,
    detailed: Boolean
): ByteArray {
    val summaries = applicationNoteSummaries(config, students, db, studentNo)
    val notes = db.getApplicationNotes(config.id, studentNo).sortedWith(compareBy<ApplicationNoteRecord> { it.week }.thenBy { it.studentNo }.thenBy { it.hour })
    val summaryRows = mutableListOf<List<String>>()
    summaryRows += listOf("Ders", "Birim", "Öğrenci No", "Adı Soyadı", "Kaydedilmiş Puan", "Ortalama (0–4)", "Not Ağırlığı (%)", "Ağırlıklı Katkı")
    summaries.forEach { row ->
        summaryRows += listOf(config.courseName, config.unit, row.student.studentNo, row.student.fullName, row.count.toString(),
            String.format(Locale.US, "%.2f", row.average), config.applicationNotePercent.toString(), String.format(Locale.US, "%.2f", row.weightedContribution))
    }
    val detailRows = mutableListOf<List<String>>()
    if (detailed) {
        detailRows += listOf("Ders", "Birim", "Öğrenci No", "Adı Soyadı", "Hafta", "Saat", "Puan (0–4)")
        notes.forEach { note ->
            detailRows += listOf(config.courseName, config.unit, note.studentNo, students.firstOrNull { it.studentNo == note.studentNo }?.fullName.orEmpty(),
                note.week.toString(), note.hour.toString(), note.score.toString())
        }
    } else {
        detailRows += listOf("Hafta", "Puan Kaydı Sayısı", "Ortalama Puan")
        val groups = notes.groupBy { it.week }
        (1..15).forEach { week ->
            val current = groups[week].orEmpty()
            detailRows += listOf("Hafta $week", current.size.toString(), if (current.isEmpty()) "" else String.format(Locale.US, "%.2f", current.map { it.score }.average()))
        }
    }
    val output = ByteArrayOutputStream()
    ZipOutputStream(output).use { zip ->
        zip.addTextEntry("[Content_Types].xml", """
            <?xml version="1.0" encoding="UTF-8" standalone="yes"?>
            <Types xmlns="http://schemas.openxmlformats.org/package/2006/content-types">
              <Default Extension="rels" ContentType="application/vnd.openxmlformats-package.relationships+xml"/>
              <Default Extension="xml" ContentType="application/xml"/>
              <Override PartName="/xl/workbook.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.sheet.main+xml"/>
              <Override PartName="/xl/worksheets/sheet1.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.worksheet+xml"/>
              <Override PartName="/xl/worksheets/sheet2.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.worksheet+xml"/>
            </Types>
        """.trimIndent())
        zip.addTextEntry("_rels/.rels", """
            <?xml version="1.0" encoding="UTF-8" standalone="yes"?>
            <Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">
              <Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument" Target="xl/workbook.xml"/>
            </Relationships>
        """.trimIndent())
        zip.addTextEntry("xl/workbook.xml", """
            <?xml version="1.0" encoding="UTF-8" standalone="yes"?>
            <workbook xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main" xmlns:r="http://schemas.openxmlformats.org/officeDocument/2006/relationships">
              <sheets><sheet name="Özet" sheetId="1" r:id="rId1"/><sheet name="${if (detailed) "Saatlik Puanlar" else "Haftalık Özet"}" sheetId="2" r:id="rId2"/></sheets>
            </workbook>
        """.trimIndent())
        zip.addTextEntry("xl/_rels/workbook.xml.rels", """
            <?xml version="1.0" encoding="UTF-8" standalone="yes"?>
            <Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">
              <Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/worksheet" Target="worksheets/sheet1.xml"/>
              <Relationship Id="rId2" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/worksheet" Target="worksheets/sheet2.xml"/>
            </Relationships>
        """.trimIndent())
        zip.addTextEntry("xl/worksheets/sheet1.xml", buildWorksheetXml(summaryRows))
        zip.addTextEntry("xl/worksheets/sheet2.xml", buildWorksheetXml(detailRows))
    }
    return output.toByteArray()
}

@Composable
private fun EmptyState(message: String) {
    Card(Modifier.fillMaxWidth().padding(vertical = 12.dp)) {
        Text(message, Modifier.padding(16.dp), style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
private fun ReportScreen(
    config: AppConfig,
    courses: List<AppConfig>,
    students: List<Student>,
    db: AttendanceDb,
    selectedStudentNo: String?,
    revision: Int,
    onSelectStudent: (String?) -> Unit,
    onExport: (String?, ReportFormat, Boolean) -> Unit
) {
    val reportData = remember(config, students, selectedStudentNo, revision) { loadReportData(config, students, db, selectedStudentNo) }
    val selected = reportData.summaries.firstOrNull { it.student.studentNo == selectedStudentNo }
    val allReportData = remember(config, students, revision) { loadReportData(config, students, db, null) }
    var pendingFormat by remember(selectedStudentNo) { mutableStateOf<ReportFormat?>(null) }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 12.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("Devam raporları", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        Text("Ders: ${config.courseName} · ${config.unit}", style = MaterialTheme.typography.bodySmall)
        if (selected != null) {
            OutlinedButton(onClick = { onSelectStudent(null) }) { Text("← Toplu rapora dön") }
            StudentSummaryCard(selected, config.absenceLimitPercent)
            ReportExportButtons(onPdf = { pendingFormat = ReportFormat.PDF }, onExcel = { pendingFormat = ReportFormat.EXCEL })
            Text("Devamsızlık yapılan ders saatleri", fontWeight = FontWeight.SemiBold)
            val detail = reportData.absences.filter { it.studentNo == selected.student.studentNo }
            if (detail.isEmpty()) Text("Bu öğrenci için kayıtlı devamsızlık bulunmuyor.", style = MaterialTheme.typography.bodyMedium)
            detail.sortedWith(compareBy<AbsenceRecord> { it.date }.thenBy { it.hour }).forEach { absence ->
                Card(Modifier.fillMaxWidth()) {
                    Row(Modifier.fillMaxWidth().padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(absence.date.format(dateFormatter), fontWeight = FontWeight.Medium)
                            Text(dayNames[absence.date.dayOfWeek.value] ?: "", style = MaterialTheme.typography.bodySmall)
                        }
                        Text("${absence.hour}. ders saati devamsız", color = MaterialTheme.colorScheme.error)
                    }
                }
            }
        } else {
            val totalScheduled = allReportData.summaries.sumOf { it.recordedHours }
            val totalAbsent = allReportData.summaries.sumOf { it.absentHours }
            val overallAttendance = if (totalScheduled == 0) 0 else (((totalScheduled - totalAbsent).coerceAtLeast(0) * 100.0) / totalScheduled).toInt()
            val warningCount = allReportData.summaries.count { absenceWarning(it.absentHours, it.scheduledHours, config.absenceLimitPercent) != null }
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("Ders özeti", fontWeight = FontWeight.SemiBold)
                    Text("Birim: ${config.unit}")
                    Text("Öğrenci sayısı: ${students.size}")
                    Text("Öğrenci başına planlanan saat: ${allReportData.expectedHoursPerStudent} (14 normal hafta)")
                    Text("Toplam devamsız saat: $totalAbsent")
                    Text("Toplam devam oranı: %$overallAttendance")
                    Text("Devamsızlık sınırı: %${config.absenceLimitPercent} · Uyarı alan öğrenci: $warningCount", color = if (warningCount > 0) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary)
                    Text("Vize haftası hesaplamaya dahil edilmez. Kaydedilmeyen yoklama günleri henüz gerçekleşmemiş kabul edilir.", style = MaterialTheme.typography.bodySmall)
                }
            }
            ReportExportButtons(onPdf = { pendingFormat = ReportFormat.PDF }, onExcel = { pendingFormat = ReportFormat.EXCEL }, enabled = students.isNotEmpty())

            Text("Devamsızlığı en yüksek öğrenciler", fontWeight = FontWeight.SemiBold)
            val highest = allReportData.summaries.sortedWith(compareByDescending<StudentSummary> { it.absentHours }.thenBy { it.student.fullName }).take(5)
            if (highest.isEmpty()) Text("Öğrenci bulunamadı.", style = MaterialTheme.typography.bodySmall)
            highest.forEachIndexed { index, summary ->
                Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
                    Row(Modifier.fillMaxWidth().padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text("${index + 1}.", fontWeight = FontWeight.Bold)
                        Spacer(Modifier.width(8.dp))
                        Column(Modifier.weight(1f)) {
                            Text(summary.student.fullName.ifBlank { summary.student.studentNo }, fontWeight = FontWeight.Medium)
                            Text("${summary.student.studentNo} · ${summary.absentHours} devamsız saat", style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
            }

            Text("Haftalık özet", fontWeight = FontWeight.SemiBold)
            val courseRosterNumbers = students.map { it.studentNo }.toSet()
            val allAbsences = db.getAbsences(config.id).filter { it.studentNo in courseRosterNumbers }
            val savedDates = db.savedAttendanceDates(config.id)
            (1..15).forEach { week ->
                val isMidterm = week == config.midtermWeek
                val dates = config.hoursByDay.filterValues { it > 0 }.keys.map { day -> config.startDate.plusWeeks((week - 1).toLong()).plusDays((day - 1).toLong()) }
                val absent = allAbsences.count { record -> weekNumberForDate(config, record.date) == week }
                val saved = dates.count { it in savedDates }
                Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = if (isMidterm) MaterialTheme.colorScheme.tertiaryContainer else MaterialTheme.colorScheme.surface)) {
                    Row(Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(if (isMidterm) "Hafta $week · VİZE" else "Hafta $week", fontWeight = FontWeight.Medium)
                            Text(if (isMidterm) "Hesaplamaya dahil değil" else "$saved / ${dates.size} gün yoklaması kaydedildi", style = MaterialTheme.typography.bodySmall)
                        }
                        Text(if (isMidterm) "—" else "$absent saat", color = if (absent > 0 && !isMidterm) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface)
                    }
                }
            }

            Text("Birim bazında rapor", fontWeight = FontWeight.SemiBold)
            val unitGroups = courses.groupBy { it.unit }
            unitGroups.toSortedMap().forEach { (unit, unitCourses) ->
                val unitRoster = unitCourses.sumOf { db.loadStudents(it.id).size }
                val unitAbsent = unitCourses.sumOf { course ->
                    val start = course.startDate.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
                    val end = start.plusWeeks(15).minusDays(1)
                    val enrolled = db.loadStudents(course.id).map { it.studentNo }.toSet()
                    db.getAbsences(course.id).count { record ->
                        val week = weekNumberForDate(course, record.date)
                        record.studentNo in enrolled && record.date >= start && record.date <= end &&
                            week != null && week != course.midtermWeek
                    }
                }
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(unit, fontWeight = FontWeight.Bold)
                        Text("Ders sayısı: ${unitCourses.size} · Öğrenci kayıtları (ders bazında): $unitRoster")
                        Text("Toplam kayıtlı devamsız saat: $unitAbsent", color = if (unitAbsent > 0) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary)
                        unitCourses.forEach { course -> Text("• ${course.courseName}", style = MaterialTheme.typography.bodySmall) }
                    }
                }
            }

            Text("Öğrenci listesi · ayrıntı için bir öğrenciye dokunun", fontWeight = FontWeight.SemiBold)
            if (allReportData.summaries.isEmpty()) Text("Öğrenci listesi yok. Ders ayarlarından bu dersin Excel/CSV listesini aktarın.")
            allReportData.summaries.sortedBy { it.student.studentNo }.forEach { row ->
                Card(Modifier.fillMaxWidth().clickable { onSelectStudent(row.student.studentNo) }, colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
                    Row(Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                            Text(row.student.studentNo, fontWeight = FontWeight.SemiBold)
                            Text(row.student.fullName.ifBlank { "Adı Soyadı bilgisi bulunamadı" }, style = MaterialTheme.typography.bodyMedium)
                            Text("Devamsız: ${row.absentHours} / ${row.scheduledHours} saat", style = MaterialTheme.typography.bodySmall)
                            absenceWarning(row.absentHours, row.scheduledHours, config.absenceLimitPercent)?.let {
                                Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                            }
                        }
                        Text("%${row.attendancePercent}", fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }

    val requestedFormat = pendingFormat
    if (requestedFormat != null) {
        AlertDialog(
            onDismissRequest = { pendingFormat = null },
            title = { Text(if (requestedFormat == ReportFormat.PDF) "PDF rapor türü" else "Excel rapor türü") },
            text = { Text("Özet rapor sayısal devam ve devamsızlık toplamlarını içerir. Ayrıntılı rapor, normal ders haftalarının her tarihi ve ders saatini gösterir; kaydedilen yoklamalarda katılım yeşil, devamsızlık kırmızı X ile işaretlenir.") },
            confirmButton = {
                Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Button(onClick = { pendingFormat = null; onExport(selected?.student?.studentNo, requestedFormat, false) }) { Text("Özet raporu dışa aktar") }
                    Button(onClick = { pendingFormat = null; onExport(selected?.student?.studentNo, requestedFormat, true) }) { Text("Ayrıntılı raporu dışa aktar") }
                }
            },
            dismissButton = { TextButton(onClick = { pendingFormat = null }) { Text("İptal") } }
        )
    }
}

@Composable
private fun ReportExportButtons(
    onPdf: () -> Unit,
    onExcel: () -> Unit,
    enabled: Boolean = true
) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedButton(onClick = onPdf, modifier = Modifier.weight(1f), enabled = enabled) {
            Text("PDF dışa aktar")
        }
        Button(onClick = onExcel, modifier = Modifier.weight(1f), enabled = enabled) {
            Text("Excel (.xlsx)")
        }
    }
}

@Composable
private fun StudentSummaryCard(summary: StudentSummary, absenceLimitPercent: Int) {
    Card(Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
            Text(summary.student.fullName, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Text("Öğrenci No: ${summary.student.studentNo}")
            Divider()
            Text("Dönem planlanan ders saati: ${summary.scheduledHours}")
            Text("Kaydedilmiş ders saati: ${summary.recordedHours}")
            Text("Devamsız saat: ${summary.absentHours}", color = MaterialTheme.colorScheme.error)
            Text("Kaydedilen oturumlarda devam edilen saat: ${summary.attendedHours}")
            Text("Devam oranı: %${summary.attendancePercent}", fontWeight = FontWeight.SemiBold)
            absenceWarning(summary.absentHours, summary.scheduledHours, absenceLimitPercent)?.let {
                Text(it, color = MaterialTheme.colorScheme.error, fontWeight = FontWeight.Bold)
            }
        }
    }
}

private fun loadReportData(
    config: AppConfig,
    students: List<Student>,
    db: AttendanceDb,
    studentNo: String?
): ReportData {
    val start = config.startDate.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
    val end = start.plusWeeks(15).minusDays(1)
    val reportStudents = students.filter { studentNo == null || it.studentNo == studentNo }
    val rosterNumbers = reportStudents.map { it.studentNo }.toSet()
    val absences = db.getAbsences(courseId = config.id, studentNo = studentNo)
        .filter { record ->
            val week = weekNumberForDate(config, record.date)
            record.studentNo in rosterNumbers && record.date >= start && record.date <= end && week != null && week != config.midtermWeek
        }
    val expected = normalTermHours(config)
    val savedDates = db.savedAttendanceDates(config.id)
    val recordedHours = plannedClassDays(config).filter { it.date in savedDates }.sumOf { it.hours }
    val summaries = reportStudents.map { student ->
        StudentSummary(student, expected, absences.count { it.studentNo == student.studentNo }, recordedHours)
    }
    return ReportData(summaries, absences, expected)
}

private data class PlannedClassDay(val week: Int, val date: LocalDate, val hours: Int)

private fun plannedClassDays(config: AppConfig): List<PlannedClassDay> {
    val start = config.startDate.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
    return (1..15).filter { it != config.midtermWeek }.flatMap { week ->
        (1..5).filter { (config.hoursByDay[it] ?: 0) > 0 }.map { day ->
            PlannedClassDay(week, start.plusWeeks((week - 1).toLong()).plusDays((day - 1).toLong()), config.hoursByDay[day] ?: 0)
        }
    }.sortedBy { it.date }
}

private fun buildReportPdf(
    config: AppConfig,
    students: List<Student>,
    db: AttendanceDb,
    studentNo: String?,
    detailed: Boolean
): ByteArray {
    val data = loadReportData(config, students, db, studentNo)
    val writer = PdfTextWriter()
    writer.line(if (detailed) "AYRINTILI DEVAM ÇİZELGESİ" else "DEVAM TAKİP ÖZET RAPORU", bold = true, size = 17f, spacing = 28f)
    writer.line("Ders: ${config.courseName}", bold = true, size = 12f, spacing = 19f)
    writer.line("Birim: ${config.unit}")
    writer.line("Rapor tarihi: ${LocalDate.now().format(dateFormatter)}")
    writer.line("Normal ders haftaları: 14 · Vize haftası: ${config.midtermWeek} (hesaplamaya dahil değil)")
    writer.line("Devamsızlık sınırı: %${config.absenceLimitPercent}")
    writer.blank()
    writer.line("ÖĞRENCİ ÖZETİ", bold = true, size = 12f, spacing = 22f)
    writer.line("Planlanan ders saati (öğrenci başına): ${data.expectedHoursPerStudent}")
    if (data.summaries.isEmpty()) writer.line("Gösterilecek öğrenci kaydı bulunamadı.")
    else data.summaries.forEach { summary ->
        writer.line("${summary.student.studentNo} — ${summary.student.fullName.ifBlank { "Adı Soyadı bilgisi bulunamadı" }}", bold = true)
        writer.line("Planlanan: ${summary.scheduledHours} saat | Devamsız: ${summary.absentHours} saat | Devam: ${summary.attendedHours} saat | Oran: %${summary.attendancePercent}")
        absenceWarning(summary.absentHours, summary.scheduledHours, config.absenceLimitPercent)?.let { writer.line(it, bold = true) }
        writer.blank(3f)
    }

    if (detailed) {
        writer.blank(8f)
        writer.line("AYRINTILI SAAT ÇİZELGESİ", bold = true, size = 12f, spacing = 22f)
        writer.line("Yeşil hücre: Var · Kırmızı X: Yok · Gri hücre: yoklama henüz kaydedilmemiş")
        val days = plannedClassDays(config)
        val maxHours = (config.hoursByDay.values.maxOrNull() ?: 0).coerceAtLeast(1)
        val savedDates = db.savedAttendanceDates(config.id)
        val absenceKeys = data.absences.map { Triple(it.studentNo, it.date, it.hour) }.toSet()
        data.summaries.forEach { summary ->
            writer.blank(5f)
            writer.line("${summary.student.studentNo} — ${summary.student.fullName.ifBlank { "Adı Soyadı bilgisi bulunamadı" }}", bold = true, size = 11f)
            writer.attendanceHeader("Tarih / hafta", maxHours)
            days.forEach { day ->
                val statuses = (1..maxHours).map { hour ->
                    if (hour > day.hours) null
                    else if (day.date !in savedDates) null
                    else Triple(summary.student.studentNo, day.date, hour) !in absenceKeys
                }
                writer.attendanceRow("H${day.week} · ${day.date.format(dateFormatter)}", statuses)
            }
        }
    } else {
        writer.blank(6f)
        writer.line("HAFTALIK ÖZET", bold = true, size = 12f, spacing = 22f)
        val allCourseAbsences = data.absences
        val savedDates = db.savedAttendanceDates(config.id)
        (1..15).forEach { week ->
            val weekDates = config.hoursByDay.filterValues { it > 0 }.keys.map { day ->
                config.startDate.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY)).plusWeeks((week - 1).toLong()).plusDays((day - 1).toLong())
            }
            val isMidterm = week == config.midtermWeek
            val savedCount = weekDates.count { it in savedDates }
            val absentCount = allCourseAbsences.count { weekNumberForDate(config, it.date) == week }
            writer.line(if (isMidterm) "Hafta $week · VİZE — hesaplamaya dahil değil" else "Hafta $week · $savedCount/${weekDates.size} gün kaydedildi · $absentCount devamsız saat")
        }
    }
    return writer.finish()
}

private fun buildReportXlsx(
    config: AppConfig,
    students: List<Student>,
    db: AttendanceDb,
    studentNo: String?,
    detailed: Boolean
): ByteArray {
    val data = loadReportData(config, students, db, studentNo)
    val summaryRows = mutableListOf<List<String>>()
    val summaryStyles = mutableListOf<List<Int>>()
    summaryRows += listOf("Ders", "Birim", "Öğrenci No", "Adı Soyadı", "Dönem Planlanan Saat", "Kaydedilen Saat", "Devamsız Saat", "Devam Edilen Saat", "Devam Oranı (%)", "Devamsızlık Uyarısı")
    summaryStyles += List(summaryRows.last().size) { 1 }
    data.summaries.forEach { summary ->
        summaryRows += listOf(
            config.courseName, config.unit, summary.student.studentNo,
            summary.student.fullName.ifBlank { "Adı Soyadı bilgisi bulunamadı" },
            summary.scheduledHours.toString(), summary.recordedHours.toString(), summary.absentHours.toString(),
            summary.attendedHours.toString(), summary.attendancePercent.toString(),
            absenceWarning(summary.absentHours, summary.scheduledHours, config.absenceLimitPercent).orEmpty()
        )
        summaryStyles += List(summaryRows.last().size) { 0 }
    }

    val detailRows = mutableListOf<List<String>>()
    val detailStyles = mutableListOf<List<Int>>()
    if (detailed) {
        val maxHours = (config.hoursByDay.values.maxOrNull() ?: 0).coerceAtLeast(1)
        detailRows += listOf("Ders", "Birim", "Öğrenci No", "Adı Soyadı", "Hafta", "Tarih", "Gün") + (1..maxHours).map { "$it. saat" }
        detailStyles += List(detailRows.last().size) { 1 }
        val savedDates = db.savedAttendanceDates(config.id)
        val absenceKeys = data.absences.map { Triple(it.studentNo, it.date, it.hour) }.toSet()
        val planned = plannedClassDays(config)
        data.summaries.forEach { summary ->
            planned.forEach { day ->
                val cells = (1..maxHours).map { hour ->
                    when {
                        hour > day.hours -> ""
                        day.date !in savedDates -> "—"
                        Triple(summary.student.studentNo, day.date, hour) in absenceKeys -> "X"
                        else -> "✓"
                    }
                }
                detailRows += listOf(config.courseName, config.unit, summary.student.studentNo,
                    summary.student.fullName.ifBlank { "Adı Soyadı bilgisi bulunamadı" }, "Hafta ${day.week}",
                    day.date.format(dateFormatter), dayNames[day.date.dayOfWeek.value].orEmpty()) + cells
                detailStyles += List(7) { 0 } + cells.mapIndexed { index, cell ->
                    when {
                        cell.isBlank() -> 0
                        cell == "—" -> 4
                        cell == "X" -> 3
                        else -> 2
                    }
                }
            }
        }
    } else {
        detailRows += listOf("Ders", "Birim", "Hafta", "Haftanın tarihi", "Kaydedilen yoklama günü", "Devamsız saat", "Not")
        detailStyles += List(7) { 1 }
        val savedDates = db.savedAttendanceDates(config.id)
        (1..15).forEach { week ->
            val weekStart = config.startDate.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY)).plusWeeks((week - 1).toLong())
            val weekDates = config.hoursByDay.filterValues { it > 0 }.keys.map { day -> weekStart.plusDays((day - 1).toLong()) }
            val isMidterm = week == config.midtermWeek
            val savedCount = weekDates.count { it in savedDates }
            val absentCount = data.absences.count { weekNumberForDate(config, it.date) == week }
            detailRows += listOf(config.courseName, config.unit, "Hafta $week", "${weekStart.format(dateFormatter)} – ${weekStart.plusDays(6).format(dateFormatter)}",
                if (isMidterm) "—" else "$savedCount / ${weekDates.size}", if (isMidterm) "—" else absentCount.toString(),
                if (isMidterm) "Vize haftası; hesaplamaya dahil değil" else "Normal ders haftası")
            detailStyles += List(7) { 0 }
        }
    }

    val output = ByteArrayOutputStream()
    ZipOutputStream(output).use { zip ->
        zip.addTextEntry("[Content_Types].xml", """
            <?xml version="1.0" encoding="UTF-8" standalone="yes"?>
            <Types xmlns="http://schemas.openxmlformats.org/package/2006/content-types">
              <Default Extension="rels" ContentType="application/vnd.openxmlformats-package.relationships+xml"/>
              <Default Extension="xml" ContentType="application/xml"/>
              <Override PartName="/xl/workbook.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.sheet.main+xml"/>
              <Override PartName="/xl/worksheets/sheet1.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.worksheet+xml"/>
              <Override PartName="/xl/worksheets/sheet2.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.worksheet+xml"/>
              <Override PartName="/xl/styles.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.styles+xml"/>
            </Types>
        """.trimIndent())
        zip.addTextEntry("_rels/.rels", """
            <?xml version="1.0" encoding="UTF-8" standalone="yes"?>
            <Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">
              <Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument" Target="xl/workbook.xml"/>
            </Relationships>
        """.trimIndent())
        zip.addTextEntry("xl/workbook.xml", """
            <?xml version="1.0" encoding="UTF-8" standalone="yes"?>
            <workbook xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main" xmlns:r="http://schemas.openxmlformats.org/officeDocument/2006/relationships">
              <sheets>
                <sheet name="Özet" sheetId="1" r:id="rId1"/>
                <sheet name="${if (detailed) "Devam Çizelgesi" else "Haftalık Özet"}" sheetId="2" r:id="rId2"/>
              </sheets>
            </workbook>
        """.trimIndent())
        zip.addTextEntry("xl/_rels/workbook.xml.rels", """
            <?xml version="1.0" encoding="UTF-8" standalone="yes"?>
            <Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">
              <Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/worksheet" Target="worksheets/sheet1.xml"/>
              <Relationship Id="rId2" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/worksheet" Target="worksheets/sheet2.xml"/>
              <Relationship Id="rId3" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/styles" Target="styles.xml"/>
            </Relationships>
        """.trimIndent())
        zip.addTextEntry("xl/styles.xml", """
            <?xml version="1.0" encoding="UTF-8" standalone="yes"?>
            <styleSheet xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main">
              <fonts count="2"><font><sz val="11"/><name val="Calibri"/></font><font><b/><color rgb="FFFFFFFF"/><sz val="11"/><name val="Calibri"/></font></fonts>
              <fills count="6">
                <fill><patternFill patternType="none"/></fill><fill><patternFill patternType="gray125"/></fill>
                <fill><patternFill patternType="solid"><fgColor rgb="FF24527A"/><bgColor indexed="64"/></patternFill></fill>
                <fill><patternFill patternType="solid"><fgColor rgb="FFC6EFCE"/><bgColor indexed="64"/></patternFill></fill>
                <fill><patternFill patternType="solid"><fgColor rgb="FFFFC7CE"/><bgColor indexed="64"/></patternFill></fill>
                <fill><patternFill patternType="solid"><fgColor rgb="FFE7E6E6"/><bgColor indexed="64"/></patternFill></fill>
              </fills>
              <borders count="1"><border><left/><right/><top/><bottom/><diagonal/></border></borders>
              <cellStyleXfs count="1"><xf numFmtId="0" fontId="0" fillId="0" borderId="0"/></cellStyleXfs>
              <cellXfs count="5">
                <xf numFmtId="0" fontId="0" fillId="0" borderId="0" xfId="0"/>
                <xf numFmtId="0" fontId="1" fillId="2" borderId="0" xfId="0" applyFont="1" applyFill="1"/>
                <xf numFmtId="0" fontId="0" fillId="3" borderId="0" xfId="0" applyFill="1"/>
                <xf numFmtId="0" fontId="1" fillId="4" borderId="0" xfId="0" applyFont="1" applyFill="1"/>
                <xf numFmtId="0" fontId="0" fillId="5" borderId="0" xfId="0" applyFill="1"/>
              </cellXfs>
              <cellStyles count="1"><cellStyle name="Normal" xfId="0" builtinId="0"/></cellStyles>
            </styleSheet>
        """.trimIndent())
        zip.addTextEntry("xl/worksheets/sheet1.xml", buildWorksheetXml(summaryRows, summaryStyles))
        zip.addTextEntry("xl/worksheets/sheet2.xml", buildWorksheetXml(detailRows, detailStyles))
    }
    return output.toByteArray()
}

private fun ZipOutputStream.addTextEntry(name: String, content: String) {
    putNextEntry(ZipEntry(name))
    write(content.toByteArray(Charsets.UTF_8))
    closeEntry()
}

private fun buildWorksheetXml(rows: List<List<String>>, styleRows: List<List<Int>> = emptyList()): String {
    val xml = StringBuilder()
    xml.append("<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>")
    xml.append("<worksheet xmlns=\"http://schemas.openxmlformats.org/spreadsheetml/2006/main\"><sheetViews><sheetView workbookViewId=\"0\"/></sheetViews><sheetData>")
    rows.forEachIndexed { rowIndex, row ->
        val rowNumber = rowIndex + 1
        xml.append("<row r=\"").append(rowNumber).append("\">")
        row.forEachIndexed { columnIndex, value ->
            val cellReference = excelColumnName(columnIndex + 1) + rowNumber
            val style = styleRows.getOrNull(rowIndex)?.getOrNull(columnIndex) ?: 0
            xml.append("<c r=\"").append(cellReference).append("\"")
            if (style > 0) xml.append(" s=\"").append(style).append("\"")
            xml.append(" t=\"inlineStr\"><is><t xml:space=\"preserve\">")
            xml.append(xmlEscape(value))
            xml.append("</t></is></c>")
        }
        xml.append("</row>")
    }
    xml.append("</sheetData></worksheet>")
    return xml.toString()
}

private fun excelColumnName(number: Int): String {
    var value = number
    val result = StringBuilder()
    while (value > 0) {
        val remainder = (value - 1) % 26
        result.insert(0, ('A'.code + remainder).toChar())
        value = (value - 1) / 26
    }
    return result.toString()
}

private fun xmlEscape(value: String): String = value
    .filter { it == '\t' || it == '\n' || it == '\r' || it.code >= 0x20 }
    .replace("&", "&amp;")
    .replace("<", "&lt;")
    .replace(">", "&gt;")
    .replace("\"", "&quot;")
    .replace("'", "&apos;")

private class PdfTextWriter {
    private val document = PdfDocument()
    private val pageWidth = 595
    private val pageHeight = 842
    private val left = 38f
    private val contentWidth = 519f
    private val footerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = android.graphics.Color.DKGRAY
        textSize = 8f
        typeface = Typeface.DEFAULT
    }
    private val bodyPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = android.graphics.Color.BLACK
        textSize = 10f
        typeface = Typeface.DEFAULT
    }
    private val boldPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = android.graphics.Color.BLACK
        textSize = 10f
        typeface = Typeface.DEFAULT_BOLD
    }
    private var pageNumber = 1
    private var page: PdfDocument.Page? = null
    private var canvas: android.graphics.Canvas? = null
    private var y = 44f

    init { startPage() }

    private fun startPage() {
        page = document.startPage(PdfDocument.PageInfo.Builder(pageWidth, pageHeight, pageNumber).create())
        canvas = page?.canvas
        y = 42f
        if (pageNumber > 1) {
            canvas?.drawText("Devam Takip Raporu · devam", left, y, footerPaint)
            y += 22f
        }
    }

    private fun finishPage() {
        val currentCanvas = canvas ?: return
        currentCanvas.drawText("Sayfa $pageNumber", pageWidth - 72f, pageHeight - 20f, footerPaint)
        page?.let { document.finishPage(it) }
        page = null
        canvas = null
        pageNumber++
    }

    private fun ensureSpace(lineSpacing: Float) {
        if (y + lineSpacing > pageHeight - 42f) {
            finishPage()
            startPage()
        }
    }

    fun line(text: String, bold: Boolean = false, size: Float = 10f, spacing: Float = size + 7f) {
        val paint = (if (bold) boldPaint else bodyPaint).apply { textSize = size }
        if (text.isEmpty()) {
            ensureSpace(spacing)
            y += spacing
            return
        }
        var remaining = text
        while (remaining.isNotEmpty()) {
            val count = paint.breakText(remaining, true, contentWidth, null).coerceAtLeast(1)
            val chunk = remaining.substring(0, count).trimEnd()
            ensureSpace(spacing)
            canvas?.drawText(chunk, left, y, paint)
            y += spacing
            remaining = remaining.substring(count).trimStart()
        }
    }

    fun blank(spacing: Float = 10f) {
        ensureSpace(spacing)
        y += spacing
    }

    fun attendanceHeader(label: String, hourCount: Int) {
        val count = hourCount.coerceAtLeast(1)
        val rowHeight = 22f
        ensureSpace(rowHeight + 8f)
        val dateWidth = 116f
        val cellWidth = (contentWidth - dateWidth) / count
        val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = android.graphics.Color.rgb(36, 82, 122); style = Paint.Style.FILL }
        val text = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = android.graphics.Color.WHITE; textSize = 8f; typeface = Typeface.DEFAULT_BOLD }
        val border = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = android.graphics.Color.GRAY; style = Paint.Style.STROKE; strokeWidth = 0.5f }
        canvas?.drawRect(left, y - 13f, left + dateWidth, y + 8f, fill)
        canvas?.drawText(label.take(22), left + 3f, y + 1f, text)
        for (hour in 1..count) {
            val x = left + dateWidth + (hour - 1) * cellWidth
            canvas?.drawRect(x, y - 13f, x + cellWidth, y + 8f, fill)
            val t = hour.toString()
            canvas?.drawText(t, x + cellWidth / 2f - text.measureText(t) / 2f, y + 1f, text)
            canvas?.drawRect(x, y - 13f, x + cellWidth, y + 8f, border)
        }
        canvas?.drawRect(left, y - 13f, left + dateWidth, y + 8f, border)
        y += rowHeight
    }

    /** status true = attended (green), false = absent (red X), null = not yet saved (grey). */
    fun attendanceRow(label: String, statuses: List<Boolean?>) {
        val count = statuses.size.coerceAtLeast(1)
        val rowHeight = 22f
        ensureSpace(rowHeight + 8f)
        val dateWidth = 116f
        val cellWidth = (contentWidth - dateWidth) / count
        val labelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = android.graphics.Color.BLACK; textSize = 7.5f }
        val border = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = android.graphics.Color.GRAY; style = Paint.Style.STROKE; strokeWidth = 0.5f }
        canvas?.drawText(label.take(25), left + 2f, y + 1f, labelPaint)
        canvas?.drawRect(left, y - 13f, left + dateWidth, y + 8f, border)
        statuses.forEachIndexed { index, status ->
            val x = left + dateWidth + index * cellWidth
            val fillColor = when (status) {
                true -> android.graphics.Color.rgb(198, 239, 206)
                false -> android.graphics.Color.rgb(255, 199, 206)
                null -> android.graphics.Color.rgb(231, 230, 230)
            }
            val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = fillColor; style = Paint.Style.FILL }
            canvas?.drawRect(x, y - 13f, x + cellWidth, y + 8f, fill)
            val mark = when (status) { true -> "✓"; false -> "X"; null -> "—" }
            val markPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = if (status == false) android.graphics.Color.rgb(156, 0, 6) else android.graphics.Color.BLACK
                textSize = 9f; typeface = if (status == false) Typeface.DEFAULT_BOLD else Typeface.DEFAULT
            }
            canvas?.drawText(mark, x + cellWidth / 2f - markPaint.measureText(mark) / 2f, y + 1f, markPaint)
            canvas?.drawRect(x, y - 13f, x + cellWidth, y + 8f, border)
        }
        y += rowHeight
    }

    fun finish(): ByteArray {
        finishPage()
        val out = ByteArrayOutputStream()
        document.writeTo(out)
        document.close()
        return out.toByteArray()
    }
}

private fun reportFilename(config: AppConfig, studentNo: String?, extension: String): String {
    val base = if (studentNo == null) {
        "${safeFilename(config.courseName)}_Toplu_Devam_Raporu"
    } else {
        "${safeFilename(config.courseName)}_${safeFilename(studentNo)}_Raporu"
    }
    return "$base.$extension"
}

private fun safeFilename(value: String): String = value.replace(Regex("[^A-Za-z0-9_-]"), "_").take(40).ifBlank { "ogrenci" }

private fun queryDisplayName(resolver: ContentResolver, uri: Uri): String {
    resolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
        if (cursor.moveToFirst()) {
            val index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
            if (index >= 0) return cursor.getString(index) ?: "students.xlsx"
        }
    }
    return "students.xlsx"
}

private fun parseStudentFile(filename: String, input: InputStream): List<Student> {
    val rows = when {
        filename.endsWith(".xlsx", ignoreCase = true) -> parseXlsxRows(input)
        filename.endsWith(".csv", ignoreCase = true) -> parseCsvRows(input.readBytes().toString(Charsets.UTF_8).removePrefix("\uFEFF"))
        filename.endsWith(".xls", ignoreCase = true) -> error("Eski .xls biçimi desteklenmiyor. Dosyayı Excel'de .xlsx veya .csv olarak kaydedip yeniden seçin.")
        else -> error("Desteklenen dosyalar .xlsx ve .csv biçimleridir.")
    }
    if (rows.isEmpty()) error("Dosyada satır bulunamadı.")

    data class Header(val row: Int, val no: Int, val combined: Int, val first: Int, val last: Int)
    fun findNoIndex(headers: List<String>): Int = headers.indexOfFirst {
        it.contains("ogrenci no") || it.contains("ogrenci numara") || it == "no" ||
            it.contains("numara") || it == "student id" || it == "student no" || it == "student number"
    }
    fun findCombinedIndex(headers: List<String>): Int = headers.indexOfFirst {
        it.contains("adi soyadi") || it.contains("ad soyad") || it == "ad soyadi" ||
            it == "adi ve soyadi" || it == "isim soyisim" || it == "ad soyisim"
    }
    fun findFirstIndex(headers: List<String>): Int = headers.indexOfFirst {
        it == "ad" || it == "adi" || it == "isim" || it == "name" || it == "first name" ||
            it == "ogrenci adi" || it == "ogrencinin adi" || it == "first_name"
    }
    fun findLastIndex(headers: List<String>): Int = headers.indexOfFirst {
        it == "soyad" || it == "soyadi" || it == "ogrenci soyadi" || it == "ogrencinin soyadi" ||
            it == "surname" || it == "last name" || it == "last_name"
    }
    fun detectHeader(row: List<String>, index: Int): Header? {
        val headers = row.map { normalizeHeader(it) }
        val noIndex = findNoIndex(headers)
        val combinedIndex = findCombinedIndex(headers)
        val firstIndex = findFirstIndex(headers)
        val lastIndex = findLastIndex(headers)
        return if (noIndex >= 0 && (combinedIndex >= 0 || (firstIndex >= 0 && lastIndex >= 0))) {
            Header(index, noIndex, combinedIndex, firstIndex, lastIndex)
        } else null
    }

    val headerRows = rows.take(25).map { row -> row.map { normalizeHeader(it) } }
    val foundHeader = rows.take(25).mapIndexedNotNull { index, row -> detectHeader(row, index) }.firstOrNull()
    if (foundHeader == null) {
        val hasStudentNoColumn = headerRows.any { findNoIndex(it) >= 0 }
        val hasFullNameColumn = headerRows.any { findCombinedIndex(it) >= 0 }
        val hasSeparateNameColumns = headerRows.any { findFirstIndex(it) >= 0 && findLastIndex(it) >= 0 }
        if (!hasFullNameColumn && !hasSeparateNameColumns) {
            error("Ad-soyad sütunları bulunamadı. Excel/CSV dosyasında 'Adı Soyadı' sütunu veya ayrı ayrı 'Ad' ve 'Soyad' sütunları bulunmalıdır. Dosya yüklenmedi.")
        }
        if (!hasStudentNoColumn) {
            error("Öğrenci numarası sütunu bulunamadı. Dosyada 'Öğrenci No' sütunu bulunmalıdır. Dosya yüklenmedi.")
        }
        error("Öğrenci No ve ad-soyad başlıkları aynı başlık satırında bulunamadı. Dosya yüklenmedi.")
    }

    val result = linkedMapOf<String, Student>()
    rows.drop(foundHeader.row + 1).forEachIndexed { offset, row ->
        val number = row.getOrNull(foundHeader.no)?.trim().orEmpty()
        if (number.isBlank()) return@forEachIndexed
        val student = if (foundHeader.combined >= 0) {
            val fullName = row.getOrNull(foundHeader.combined)?.trim()?.replace(Regex("\\s+"), " ").orEmpty()
            if (fullName.isBlank()) {
                error("Excel/CSV dosyasında ${foundHeader.row + offset + 2}. satırda öğrenci numarası '$number' var ancak adı-soyadı boş. Hiçbir kayıt yüklenmedi.")
            }
            Student(number, fullName, "")
        } else {
            val firstName = row.getOrNull(foundHeader.first)?.trim()?.replace(Regex("\\s+"), " ").orEmpty()
            val lastName = row.getOrNull(foundHeader.last)?.trim()?.replace(Regex("\\s+"), " ").orEmpty()
            if (firstName.isBlank() && lastName.isBlank()) {
                error("Excel/CSV dosyasında ${foundHeader.row + offset + 2}. satırda öğrenci numarası '$number' var ancak adı ve soyadı boş. Hiçbir kayıt yüklenmedi.")
            }
            Student(number, firstName, lastName)
        }
        result[number] = student
    }
    if (result.isEmpty()) error("Başlıklar bulundu ancak geçerli öğrenci kaydı bulunamadı. Dosya yüklenmedi.")
    return result.values.toList()
}

private fun normalizeHeader(input: String): String {
    val lower = input.trim().lowercase(Locale("tr", "TR"))
    val mapped = lower
        .replace('ı', 'i').replace('ğ', 'g').replace('ü', 'u').replace('ş', 's').replace('ö', 'o').replace('ç', 'c')
    return mapped.replace(Regex("[^a-z0-9 ]"), " ").replace(Regex("\\s+"), " ").trim()
}

private fun parseXlsxRows(input: InputStream): List<List<String>> {
    val entries = mutableMapOf<String, ByteArray>()
    ZipInputStream(input).use { zip ->
        var entry = zip.nextEntry
        while (entry != null) {
            if (!entry.isDirectory) entries[entry.name] = zip.readBytes()
            zip.closeEntry()
            entry = zip.nextEntry
        }
    }
    val sharedStrings = entries["xl/sharedStrings.xml"]?.let { parseSharedStrings(it) } ?: emptyList()
    val workbookBytes = entries["xl/workbook.xml"] ?: error("Excel çalışma kitabı okunamadı.")
    val workbookDoc = parseXml(workbookBytes)
    val sheetNode = workbookDoc.getElementsByTagName("sheet").item(0) as? Element
        ?: error("Excel dosyasında sayfa bulunamadı.")
    val relId = sheetNode.getAttributeNS("http://schemas.openxmlformats.org/officeDocument/2006/relationships", "id")
        .ifBlank { sheetNode.getAttribute("r:id") }
    val relsBytes = entries["xl/_rels/workbook.xml.rels"] ?: error("Excel sayfa bilgileri okunamadı.")
    val relsDoc = parseXml(relsBytes)
    var target = "worksheets/sheet1.xml"
    val relationships = relsDoc.getElementsByTagName("Relationship")
    for (i in 0 until relationships.length) {
        val relation = relationships.item(i) as? Element ?: continue
        if (relation.getAttribute("Id") == relId) {
            target = relation.getAttribute("Target").ifBlank { target }
            break
        }
    }
    val sheetPath = when {
        target.startsWith("/") -> target.removePrefix("/")
        target.startsWith("xl/") -> target
        else -> "xl/$target"
    }
    val sheetBytes = entries[sheetPath] ?: error("İlk çalışma sayfası bulunamadı ($sheetPath).")
    val sheetDoc = parseXml(sheetBytes)
    val rowNodes = sheetDoc.getElementsByTagName("row")
    val result = mutableListOf<List<String>>()
    for (r in 0 until rowNodes.length) {
        val rowElement = rowNodes.item(r) as? Element ?: continue
        val cells = rowElement.getElementsByTagName("c")
        val rowMap = sortedMapOf<Int, String>()
        var maxColumn = -1
        for (c in 0 until cells.length) {
            val cell = cells.item(c) as? Element ?: continue
            val index = cellReferenceToIndex(cell.getAttribute("r"))
            if (index < 0) continue
            val type = cell.getAttribute("t")
            val value = when (type) {
                "s" -> {
                    val raw = cell.getElementsByTagName("v").item(0)?.textContent.orEmpty()
                    sharedStrings.getOrNull(raw.toIntOrNull() ?: -1).orEmpty()
                }
                "inlineStr" -> cell.getElementsByTagName("is").item(0)?.textContent.orEmpty()
                "b" -> if (cell.getElementsByTagName("v").item(0)?.textContent == "1") "TRUE" else "FALSE"
                else -> {
                    val raw = cell.getElementsByTagName("v").item(0)?.textContent.orEmpty()
                    if (raw.matches(Regex("-?\\d+\\.0+"))) runCatching { BigDecimal(raw).toBigInteger().toString() }.getOrDefault(raw) else raw
                }
            }
            rowMap[index] = value.trim()
            maxColumn = maxOf(maxColumn, index)
        }
        if (maxColumn >= 0) result += (0..maxColumn).map { rowMap[it].orEmpty() }
    }
    return result
}

private fun parseSharedStrings(bytes: ByteArray): List<String> {
    val doc = parseXml(bytes)
    val nodes = doc.getElementsByTagName("si")
    return (0 until nodes.length).map { nodes.item(it).textContent.orEmpty() }
}

private fun parseXml(bytes: ByteArray) = DocumentBuilderFactory.newInstance().apply {
    isNamespaceAware = true
    runCatching { setFeature("http://apache.org/xml/features/disallow-doctype-decl", true) }
    runCatching { setFeature("http://xml.org/sax/features/external-general-entities", false) }
    runCatching { setFeature("http://xml.org/sax/features/external-parameter-entities", false) }
}.newDocumentBuilder().parse(ByteArrayInputStream(bytes))

private fun cellReferenceToIndex(reference: String): Int {
    val letters = reference.takeWhile { it.isLetter() }.uppercase(Locale.ROOT)
    if (letters.isEmpty()) return -1
    var number = 0
    letters.forEach { ch -> number = number * 26 + (ch - 'A' + 1) }
    return number - 1
}

private fun parseCsvRows(text: String): List<List<String>> {
    val rows = mutableListOf<List<String>>()
    val row = mutableListOf<String>()
    val field = StringBuilder()
    var quoted = false
    var i = 0
    while (i < text.length) {
        val ch = text[i]
        when {
            ch == '"' && quoted && i + 1 < text.length && text[i + 1] == '"' -> { field.append('"'); i++ }
            ch == '"' -> quoted = !quoted
            (ch == ',' || ch == ';' || ch == '\t') && !quoted -> { row += field.toString(); field.setLength(0) }
            (ch == '\n' || ch == '\r') && !quoted -> {
                if (ch == '\r' && i + 1 < text.length && text[i + 1] == '\n') i++
                row += field.toString()
                if (row.any { it.isNotBlank() }) rows += row.toList()
                row.clear(); field.setLength(0)
            }
            else -> field.append(ch)
        }
        i++
    }
    row += field.toString()
    if (row.any { it.isNotBlank() }) rows += row.toList()
    return rows
}
