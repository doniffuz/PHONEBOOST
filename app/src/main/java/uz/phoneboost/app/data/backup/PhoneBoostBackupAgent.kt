package uz.phoneboost.app.data.backup

import android.app.backup.BackupAgentHelper
import android.app.backup.FullBackupDataOutput

/**
 * Tizimning avtomatik zaxirasi. Foydalanuvchi Settings'da o'chirgan bo'lsa, hech narsa yuklanmaydi.
 * Nimalar kiritilishi res/xml/data_extraction_rules.xml va backup_rules.xml da belgilangan.
 */
class PhoneBoostBackupAgent : BackupAgentHelper() {
    override fun onFullBackup(data: FullBackupDataOutput?) {
        if (!BackupPrefs(this).autoBackupEnabled) return
        super.onFullBackup(data)
    }
}
