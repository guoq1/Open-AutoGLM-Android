package com.example.dbgt.data.database

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.util.UUID

/**
 * 单个对话
 */
@Entity(tableName = "conversations")
data class Conversation(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    val title: String = "新对话",
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)