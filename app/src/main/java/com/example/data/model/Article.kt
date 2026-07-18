package com.example.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.io.Serializable

@Entity(tableName = "articles")
data class Article(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val title: String,
    val summary: String,
    val category: String,
    val whyItMatters: String,
    val analogy: String,
    val videoQuery: String,
    val videoUrl: String,
    val timestamp: Long = System.currentTimeMillis(),
    val isSaved: Boolean = false,
    val isUserCreated: Boolean = false // to distinguish user chatbot generated reports from official seeds
) : Serializable
