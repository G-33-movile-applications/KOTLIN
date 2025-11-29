package com.mobile.mymeds.models

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "user_interactions")
data class UserInteraction(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val fieldId: String,
    val selectedValue: String,
    val timestamp: Long = System.currentTimeMillis()
)