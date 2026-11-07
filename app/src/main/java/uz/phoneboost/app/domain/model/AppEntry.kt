package uz.phoneboost.app.domain.model

data class AppEntry(
    val packageName: String,
    val label: String,
    val isSystem: Boolean,
    val apkSizeBytes: Long?,
    val versionName: String?,
    val installedAtMillis: Long?,
    /** Foydalanuvchi allaqachon bergan (granted) ruxsatlar. */
    val grantedPermissions: Set<String>
)
