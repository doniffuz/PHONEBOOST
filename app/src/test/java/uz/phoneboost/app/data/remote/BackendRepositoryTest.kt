package uz.phoneboost.app.data.remote

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import uz.phoneboost.app.data.update.UpdateRepository
import uz.phoneboost.app.data.update.UpdateResult

class BackendRepositoryTest {
    @Test fun anonymousDonorNeverShowsName() {
        val j = JSONObject("""{"items":[
            {"rank":1,"displayName":"Ali","anonymous":false,"amount":60000,"currency":"UZS","consent":true},
            {"rank":2,"displayName":"Leak","anonymous":true,"amount":50000,"currency":"UZS","consent":false},
            {"rank":3,"displayName":null,"anonymous":false,"amount":10,"currency":"UZS","consent":false}]}""")
        val r = BackendRepository.parseTopDonors(j)
        assertEquals("Ali", r[0].displayName); assertFalse(r[0].anonymous)
        assertNull(r[1].displayName); assertTrue(r[1].anonymous)
        assertNull(r[2].displayName); assertTrue(r[2].anonymous)
    }

    @Test fun emptyOrBrokenResponsesGiveEmptyLists() {
        assertTrue(BackendRepository.parseTopDonors(null).isEmpty())
        assertTrue(BackendRepository.parseTopDonors(JSONObject("{}")).isEmpty())
        assertTrue(BackendRepository.parseAnnouncements(JSONObject("""{"items":[{"id":1,"message":"  "}]}""")).isEmpty())
    }

    @Test fun announcementImageMustBeHttps() {
        val j = JSONObject("""{"items":[{"id":1,"title":"T","message":"M","imageUrl":"http://x/y.png"},{"id":2,"title":"","message":"M2","imageUrl":"https://x/y.png"}]}""")
        val r = BackendRepository.parseAnnouncements(j)
        assertNull(r[0].imageUrl); assertEquals("https://x/y.png", r[1].imageUrl)
    }

    @Test fun updateBelowMinimumIsForced() {
        val j = JSONObject("""{"versionCode":6,"versionName":"0.6.0","minSupportedVersionCode":5,"forceUpdate":false,"downloadUrl":"https://x/app.apk","releaseNotes":"n"}""")
        val r = UpdateRepository("https://x/update").parse(j, current = 4) as UpdateResult.Available
        assertTrue(r.force); assertEquals("n", r.changelog)
        val ok = UpdateRepository("https://x/update").parse(j, current = 5) as UpdateResult.Available
        assertFalse(ok.force)
        assertTrue(UpdateRepository("https://x/update").parse(j, current = 6) is UpdateResult.UpToDate)
    }
}
