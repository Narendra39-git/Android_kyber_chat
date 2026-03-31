package com.example.kyberchat.data

import android.content.Context
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import kotlinx.coroutines.flow.Flow

@Dao
interface MessageDao {
    @Query("SELECT * FROM messages WHERE (senderId = :contactId AND recipientId = :myId) OR (senderId = :myId AND recipientId = :contactId) ORDER BY timestamp DESC")
    fun getMessagesForContact(myId: String, contactId: String): Flow<List<MessageEntity>>

    @Query("SELECT * FROM messages ORDER BY timestamp DESC")
    fun getAllMessages(): Flow<List<MessageEntity>>
    
    // Get unique list of people we've chatted with
    // FIX: Only show people who sent TO ME or received FROM ME
    @Query("SELECT DISTINCT senderId FROM messages WHERE recipientId = :myId UNION SELECT DISTINCT recipientId FROM messages WHERE senderId = :myId")
    fun getRecentContacts(myId: String): Flow<List<String>>

    @Insert
    suspend fun insertMessage(message: MessageEntity)

    @Query("DELETE FROM messages WHERE (senderId = :contactId AND recipientId = :myId) OR (senderId = :myId AND recipientId = :contactId)")
    suspend fun deleteChat(myId: String, contactId: String)
}

@Database(entities = [MessageEntity::class], version = 1, exportSchema = false)
abstract class AppDatabase : RoomDatabase() {
    abstract fun messageDao(): MessageDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "kyber_chat_db"
                ).build()
                INSTANCE = instance
                instance
            }
        }
    }
}
