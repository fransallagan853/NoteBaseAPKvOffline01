package com.notebaseapk.data

import com.google.firebase.Timestamp
import com.google.firebase.firestore.ServerTimestamp

data class User(
    val uid: String = "",
    val name: String = "",
    val email: String = "",
    val phone: String = "",
    val role: String = "user",
    val subscriptionStatus: String = "inactive",
    val activeUntil: Timestamp? = null,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)
