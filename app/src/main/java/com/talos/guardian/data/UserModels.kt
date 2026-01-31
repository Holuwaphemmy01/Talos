package com.talos.guardian.data

import com.google.firebase.firestore.PropertyName

enum class UserRole {
    PARENT,
    CHILD
}

enum class ChildStatus {
    ACTIVE,
    ALERT,
    OFFLINE
}

data class TalosUser(
    @get:PropertyName("uid") val uid: String = "",
    @get:PropertyName("email") val email: String = "",
    @get:PropertyName("role") val role: UserRole = UserRole.PARENT,
    @get:PropertyName("createdAt") val createdAt: Long = System.currentTimeMillis()
)

data class ParentUser(
    @get:PropertyName("uid") val uid: String = "",
    @get:PropertyName("email") val email: String = "",
    @get:PropertyName("linkedChildIDs") val linkedChildIDs: List<String> = emptyList(),
    @get:PropertyName("createdAt") val createdAt: Long = System.currentTimeMillis()
) {
    val role = UserRole.PARENT
}

data class ChildUser(
    @get:PropertyName("uid") val uid: String = "",
    @get:PropertyName("deviceID") val deviceID: String = "",
    @get:PropertyName("pairedParentID") val pairedParentID: String = "",
    @get:PropertyName("deviceName") val deviceName: String = "Unknown Device",
    @get:PropertyName("currentStatus") val currentStatus: ChildStatus = ChildStatus.ACTIVE,
    @get:PropertyName("createdAt") val createdAt: Long = System.currentTimeMillis()
) {
    val role = UserRole.CHILD
}

data class ActivityLog(
    @get:PropertyName("id") val id: String = "",
    @get:PropertyName("timestamp") val timestamp: Long = System.currentTimeMillis(),
    @get:PropertyName("appName") val appName: String = "",
    @get:PropertyName("riskCategory") val riskCategory: String = "",
    @get:PropertyName("aiReasoning") val aiReasoning: String = ""
)
