package com.vovremya.alarm.update

import com.vovremya.alarm.notifications.NotificationHelper
import org.json.JSONArray
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = android.app.Application::class)
class UpdateManagerTest {
    private val manager = UpdateManager(
        RuntimeEnvironment.getApplication(),
        NotificationHelper(RuntimeEnvironment.getApplication()),
    )

    @Test
    fun `stable channel ignores prereleases while beta channel accepts them`() {
        val releases = JSONArray(
            """
            [
              {"tag_name":"v1.5.0-beta.1","draft":false,"prerelease":true},
              {"tag_name":"v1.4.0","draft":false,"prerelease":false},
              {"tag_name":"v9.0.0","draft":true,"prerelease":false}
            ]
            """.trimIndent(),
        )

        assertEquals("v1.4.0", manager.selectRelease(releases, allowPrerelease = false)?.getString("tag_name"))
        assertEquals("v1.5.0-beta.1", manager.selectRelease(releases, allowPrerelease = true)?.getString("tag_name"))
    }

    @Test
    fun `semantic version comparison keeps stable above beta of the same version`() {
        assertEquals(1, manager.compareVersions("1.5.0", "1.5.0-beta.9"))
        assertEquals(1, manager.compareVersions("1.5.0-beta.2", "1.5.0-beta.1"))
        assertEquals(-1, manager.compareVersions("1.5.0-beta.1", "1.5.0"))
    }

    @Test
    fun `release catalog contains stable and beta apk releases but not drafts`() {
        val releases = JSONArray(
            """
            [
              {
                "tag_name":"v1.4.0",
                "name":"Stable 1.4.0",
                "draft":false,
                "prerelease":false,
                "assets":[{"name":"app-release.apk","browser_download_url":"https://example.test/stable.apk"}]
              },
              {
                "tag_name":"v1.6.0-beta.1",
                "name":"Beta 1.6.0",
                "draft":false,
                "prerelease":true,
                "assets":[{"name":"app-release.apk","browser_download_url":"https://example.test/beta.apk"}]
              },
              {
                "tag_name":"v9.0.0",
                "draft":true,
                "prerelease":false,
                "assets":[{"name":"app-release.apk","browser_download_url":"https://example.test/draft.apk"}]
              },
              {"tag_name":"v1.3.0","draft":false,"prerelease":false,"assets":[]}
            ]
            """.trimIndent(),
        )

        val catalog = manager.parseAvailableReleases(releases)

        assertEquals(listOf("1.6.0-beta.1", "1.4.0"), catalog.map { it.version })
        assertEquals(listOf(true, false), catalog.map { it.prerelease })
        assertEquals(listOf(ReleaseRelation.NEWER, ReleaseRelation.OLDER), catalog.map { it.relation })
    }
}
