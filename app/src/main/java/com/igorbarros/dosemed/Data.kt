package com.igorbarros.dosemed

import android.content.Context
import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Entity data class Profile(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val color: Int,
)

@Entity data class Medication(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val profileId: Long,
    val name: String,
    val dose: String = "",
    val form: String = "Comprimido",
    val color: Int = 0xFF12A594.toInt(),
    val photoPath: String? = null,
    val type: String = "DAILY",          // DAILY, WEEKDAYS, INTERVAL, ASNEEDED
    val times: String = "08:00",         // "08:00,20:00" (INTERVAL: primeiro horário)
    val intervalHours: Int = 8,
    val weekdays: Int = 127,             // bit0 = segunda ... bit6 = domingo
    val startDate: Long = 0,             // epochDay
    val endDate: Long? = null,           // epochDay (inclusive); null = uso contínuo
    val stock: Int? = null,              // null = sem controle
    val unitsPerDose: Int = 1,
    val lowStockAt: Int = 5,
    val notes: String = "",
    val active: Boolean = true,
)

@Entity(indices = [Index(value = ["medId", "scheduledAt"], unique = true)])
data class DoseLog(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val medId: Long,
    val scheduledAt: Long,
    val status: String,                  // TAKEN, SKIPPED
    val actedAt: Long,
    val note: String = "",
)

@Entity data class Appointment(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val profileId: Long,
    val title: String,
    val doctor: String = "",
    val location: String = "",
    val dateTime: Long,
    val notes: String = "",
    val remindMinutes: Int = 60,         // 0 = sem aviso
    val done: Boolean = false,
)

@Entity data class Note(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val profileId: Long,
    val text: String,
    val createdAt: Long,
)

@Dao interface AppDao {
    @Query("SELECT * FROM Profile ORDER BY id") fun profiles(): Flow<List<Profile>>
    @Query("SELECT COUNT(*) FROM Profile") suspend fun profileCount(): Int
    @Insert suspend fun insertProfile(p: Profile): Long
    @Update suspend fun updateProfile(p: Profile)
    @Query("DELETE FROM Profile WHERE id=:id") suspend fun deleteProfile(id: Long)

    @Query("SELECT * FROM Medication ORDER BY name COLLATE NOCASE") fun meds(): Flow<List<Medication>>
    @Query("SELECT * FROM Medication") suspend fun medsNow(): List<Medication>
    @Query("SELECT * FROM Medication WHERE id=:id") suspend fun med(id: Long): Medication?
    @Insert suspend fun insertMed(m: Medication): Long
    @Update suspend fun updateMed(m: Medication)
    @Query("DELETE FROM Medication WHERE id=:id") suspend fun deleteMed(id: Long)
    @Query("DELETE FROM DoseLog WHERE medId=:id") suspend fun deleteLogsOfMed(id: Long)
    @Query("UPDATE Medication SET stock=:stock WHERE id=:id") suspend fun setStock(id: Long, stock: Int?)

    @Query("SELECT * FROM DoseLog ORDER BY scheduledAt DESC") fun logs(): Flow<List<DoseLog>>
    @Query("SELECT * FROM DoseLog WHERE medId=:medId AND scheduledAt=:at LIMIT 1") suspend fun log(medId: Long, at: Long): DoseLog?
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun insertLog(l: DoseLog): Long
    @Query("DELETE FROM DoseLog WHERE id=:id") suspend fun deleteLog(id: Long)

    @Query("SELECT * FROM Appointment ORDER BY dateTime") fun appts(): Flow<List<Appointment>>
    @Query("SELECT * FROM Appointment") suspend fun apptsNow(): List<Appointment>
    @Query("SELECT * FROM Appointment WHERE id=:id") suspend fun appt(id: Long): Appointment?
    @Insert suspend fun insertAppt(a: Appointment): Long
    @Update suspend fun updateAppt(a: Appointment)
    @Query("DELETE FROM Appointment WHERE id=:id") suspend fun deleteAppt(id: Long)

    @Query("SELECT * FROM Note ORDER BY createdAt DESC") fun notes(): Flow<List<Note>>
    @Insert suspend fun insertNote(n: Note): Long
    @Update suspend fun updateNote(n: Note)
    @Query("DELETE FROM Note WHERE id=:id") suspend fun deleteNote(id: Long)
}

@Database(
    entities = [Profile::class, Medication::class, DoseLog::class, Appointment::class, Note::class],
    version = 1, exportSchema = false,
)
abstract class AppDb : RoomDatabase() {
    abstract fun dao(): AppDao
    companion object {
        @Volatile private var inst: AppDb? = null
        fun get(ctx: Context): AppDb = inst ?: synchronized(this) {
            inst ?: Room.databaseBuilder(ctx.applicationContext, AppDb::class.java, "dosemed.db")
                .fallbackToDestructiveMigration().build().also { inst = it }
        }
    }
}
