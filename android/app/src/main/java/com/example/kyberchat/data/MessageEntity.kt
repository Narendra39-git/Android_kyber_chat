package com.example.kyberchat.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "messages")
data class MessageEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val senderId: String,
    val recipientId: String, // To differentiate chats
    val content: String,
    val timestamp: Long,
    val isFromMe: Boolean,
    val isRead: Boolean = false
)
