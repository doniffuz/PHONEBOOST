package uz.phoneboost.app.data.remote

import org.json.JSONObject

data class Announcement(val id: Long, val title: String, val message: String, val imageUrl: String?)
data class TopDonor(val rank: Int, val displayName: String?, val anonymous: Boolean, val amount: Long, val currency: String)
data class DonationRegistration(val reference: String, val status: String)

/**
 * Backend'dan ommaviy ma'lumotlar. Tarmoq/backend xatosi = bo'sh ro'yxat/null: soxta ma'lumot HECH QACHON ko'rsatilmaydi.
 * Haqiqiy ism faqat server donorning roziligi bilan beradi; anonim bo'lsa displayName = null.
 */
object BackendRepository {
    suspend fun announcements(): List<Announcement> {
        val r = ApiClient.get("/announcements")?.takeIf { it.ok } ?: return emptyList()
        return parseAnnouncements(r.json)
    }

    suspend fun topDonors(): List<TopDonor> {
        val r = ApiClient.get("/top-donors")?.takeIf { it.ok } ?: return emptyList()
        return parseTopDonors(r.json)
    }

    /** To'lov niyatini 'pending' deb yozadi. Success FAQAT admin tasdiqlagandan keyin serverda o'rnatiladi. */
    suspend fun registerDonation(amount: Long, provider: String, donorName: String?, showName: Boolean): DonationRegistration? {
        val body = JSONObject()
            .put("amount", amount)
            .put("provider", provider)
            .put("anonymous", !showName)
            .put("consent", showName)
        if (showName && !donorName.isNullOrBlank()) body.put("donorName", donorName.trim())
        val r = ApiClient.post("/donation-event", body)?.takeIf { it.ok } ?: return null
        val ref = r.json?.optString("reference").orEmpty()
        return if (ref.isNotEmpty()) DonationRegistration(ref, r.json?.optString("status").orEmpty().ifEmpty { "pending" }) else null
    }

    /** null = noma'lum (tarmoq xatosi yoki topilmadi). */
    suspend fun donationStatus(reference: String): String? {
        val r = ApiClient.get("/donation-status?ref=" + java.net.URLEncoder.encode(reference, "UTF-8"))?.takeIf { it.ok } ?: return null
        return r.json?.optString("status")?.takeIf { it in KNOWN_STATUSES }
    }

    private val KNOWN_STATUSES = setOf("pending", "success", "failed", "cancelled")

    internal fun parseAnnouncements(j: JSONObject?): List<Announcement> {
        val arr = j?.optJSONArray("items") ?: return emptyList()
        val out = ArrayList<Announcement>()
        for (i in 0 until arr.length()) {
            val o = arr.optJSONObject(i) ?: continue
            val msg = o.optString("message").trim()
            if (msg.isEmpty()) continue
            out += Announcement(o.optLong("id"), o.optString("title").trim(), msg,
                o.optString("imageUrl").takeIf { it.startsWith("https://") })
        }
        return out
    }

    internal fun parseTopDonors(j: JSONObject?): List<TopDonor> {
        val arr = j?.optJSONArray("items") ?: return emptyList()
        val out = ArrayList<TopDonor>()
        for (i in 0 until arr.length()) {
            val o = arr.optJSONObject(i) ?: continue
            val name = if (o.isNull("displayName")) null else o.optString("displayName").trim().ifEmpty { null }
            // Ism faqat server anonymous=false bersa ko'rinadi (rozilik serverda tekshirilgan).
            val anonymous = o.optBoolean("anonymous", true) || name == null
            out += TopDonor(o.optInt("rank", i + 1), if (anonymous) null else name, anonymous,
                o.optLong("amount"), o.optString("currency", "UZS"))
        }
        return out.sortedBy { it.rank }.take(10)
    }
}
