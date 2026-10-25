package com.example.data

import android.content.Context
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import kotlinx.coroutines.flow.Flow

@Dao
interface MuallimunDao {

    // --- PACKAGES ---
    @Query("SELECT * FROM paketler WHERE mode = :mode ORDER BY id")
    fun observePackagesByMode(mode: String): Flow<List<PackageEntity>>

    @Query("SELECT * FROM paketler WHERE mode = :mode ORDER BY id")
    suspend fun getPackagesByMode(mode: String): List<PackageEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPackages(packages: List<PackageEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPackage(pkg: PackageEntity)

    @Query("DELETE FROM paketler WHERE id = :id")
    suspend fun deletePackageById(id: String)

    // --- WORDS ---
    @Query("SELECT * FROM kelimeler ORDER BY id")
    fun observeAllWords(): Flow<List<WordEntity>>

    @Query("SELECT * FROM kelimeler ORDER BY id")
    suspend fun getAllWords(): List<WordEntity>

    @Query("SELECT * FROM kelimeler WHERE paketId = :paketId ORDER BY id")
    suspend fun getWordsByPackage(paketId: String): List<WordEntity>

    @Query("SELECT * FROM kelimeler WHERE id = :id LIMIT 1")
    suspend fun getWordById(id: String): WordEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertWords(words: List<WordEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertWord(word: WordEntity)

    @Query("DELETE FROM kelimeler WHERE id = :id")
    suspend fun deleteWordById(id: String)

    @Query("DELETE FROM kelimeler WHERE paketId = :paketId")
    suspend fun deleteWordsByPackage(paketId: String)

    @Query("DELETE FROM kelimeler")
    suspend fun deleteAllWords()

    @Query("UPDATE kelimeler SET dogru = 0, yanlis = 0, isLearned = 0, intervalDays = 0, easeFactor = 2.5, nextReview = ''")
    suspend fun resetWordStats()

    // --- SOZLUK (SARF) ---
    @Query("SELECT * FROM sozluk ORDER BY id")
    fun observeAllSozluk(): Flow<List<SozlukEntity>>

    @Query("SELECT * FROM sozluk ORDER BY id")
    suspend fun getAllSozluk(): List<SozlukEntity>

    @Query("SELECT * FROM sozluk WHERE wordId = :wordId OR id = :cleanId LIMIT 1")
    suspend fun getSozlukByWordId(wordId: String, cleanId: String): SozlukEntity?

    @Query("SELECT * FROM sozluk WHERE kok = :kok AND id != :excludeId LIMIT 8")
    suspend fun getSozlukFamilyByRoot(kok: String, excludeId: String): List<SozlukEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSozlukList(items: List<SozlukEntity>)

    @Query("DELETE FROM sozluk")
    suspend fun deleteAllSozluk()

    // --- DIALOGUES ---
    @Query("SELECT * FROM diyaloglar ORDER BY id")
    fun observeAllDialogues(): Flow<List<DialogueEntity>>

    @Query("SELECT * FROM diyaloglar ORDER BY id")
    suspend fun getAllDialogues(): List<DialogueEntity>

    @Query("SELECT * FROM diyalog_satirlari WHERE diyalogId = :dialogueId ORDER BY sira, id")
    suspend fun getDialogueLines(dialogueId: String): List<DialogueLineEntity>

    @Query("SELECT * FROM diyalog_satirlari ORDER BY diyalogId, sira, id")
    fun observeAllDialogueLines(): Flow<List<DialogueLineEntity>>

    @Query("SELECT * FROM diyalog_satirlari ORDER BY diyalogId, sira, id")
    suspend fun getAllDialogueLines(): List<DialogueLineEntity>

    @Query("SELECT * FROM diyalog_satirlari WHERE id = :lineId LIMIT 1")
    suspend fun getDialogueLineById(lineId: Int): DialogueLineEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertDialogues(dialogues: List<DialogueEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertDialogue(dialogue: DialogueEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertDialogueLines(lines: List<DialogueLineEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertDialogueLine(line: DialogueLineEntity)

    @Query("DELETE FROM diyalog_satirlari WHERE id = :lineId")
    suspend fun deleteDialogueLineById(lineId: Int)

    @Query("DELETE FROM diyalog_satirlari WHERE diyalogId = :dialogueId")
    suspend fun deleteLinesByDialogue(dialogueId: String)

    @Query("DELETE FROM diyaloglar WHERE id = :dialogueId")
    suspend fun deleteDialogueById(dialogueId: String)

    @Query("DELETE FROM diyalog_satirlari")
    suspend fun deleteAllDialogueLines()

    @Query("DELETE FROM diyaloglar")
    suspend fun deleteAllDialogues()

    @Query("UPDATE diyalog_satirlari SET dogru = 0, yanlis = 0")
    suspend fun resetDialogueStats()

    // --- SENTENCES ---
    @Query("SELECT * FROM cumleler ORDER BY id")
    fun observeAllSentences(): Flow<List<SentenceEntity>>

    @Query("SELECT * FROM cumleler ORDER BY id")
    suspend fun getAllSentences(): List<SentenceEntity>

    @Query("SELECT * FROM cumleler WHERE id = :id LIMIT 1")
    suspend fun getSentenceById(id: String): SentenceEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSentences(sentences: List<SentenceEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSentence(sentence: SentenceEntity)

    @Query("DELETE FROM cumleler WHERE id = :id")
    suspend fun deleteSentenceById(id: String)

    @Query("DELETE FROM cumleler WHERE paketId = :paketId")
    suspend fun deleteSentencesByPackage(paketId: String)

    @Query("DELETE FROM cumleler")
    suspend fun deleteAllSentences()

    @Query("UPDATE cumleler SET dogru = 0, yanlis = 0, isLearned = 0, intervalDays = 0, easeFactor = 2.5, nextReview = ''")
    suspend fun resetSentenceStats()

    // --- CONFIG ---
    @Query("SELECT * FROM config")
    fun observeAllConfig(): Flow<List<ConfigEntity>>

    @Query("SELECT value FROM config WHERE `key` = :key LIMIT 1")
    suspend fun getConfigVal(key: String): String?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun setConfig(config: ConfigEntity)
}

@Database(
    entities = [
        PackageEntity::class,
        WordEntity::class,
        SozlukEntity::class,
        DialogueEntity::class,
        DialogueLineEntity::class,
        SentenceEntity::class,
        ConfigEntity::class
    ],
    version = 1,
    exportSchema = false
)
abstract class MuallimunDatabase : RoomDatabase() {
    abstract fun dao(): MuallimunDao

    companion object {
        @Volatile
        private var INSTANCE: MuallimunDatabase? = null

        fun getInstance(context: Context): MuallimunDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    MuallimunDatabase::class.java,
                    "muallimun_arapca_v40.db"
                ).build()
                INSTANCE = instance
                instance
            }
        }
    }
}
