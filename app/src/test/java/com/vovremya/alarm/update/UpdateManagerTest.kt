package com.vovremya.alarm.update

import com.vovremya.alarm.notifications.NotificationHelper
import com.vovremya.alarm.data.UpdateChannel
import org.json.JSONArray
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
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
    fun `stable beta and alpha channels only select their own releases`() {
        val releases = JSONArray(
            """
            [
              {"tag_name":"v1.5.0-beta.1","draft":false,"prerelease":true},
              {"tag_name":"v1.7.0-alpha.2","draft":false,"prerelease":true},
              {"tag_name":"v1.4.0","draft":false,"prerelease":false},
              {"tag_name":"v9.0.0","draft":true,"prerelease":false}
            ]
            """.trimIndent(),
        )

        assertEquals("v1.4.0", manager.selectRelease(releases, UpdateChannel.STABLE)?.getString("tag_name"))
        assertEquals("v1.5.0-beta.1", manager.selectRelease(releases, UpdateChannel.BETA)?.getString("tag_name"))
        assertEquals("v1.7.0-alpha.2", manager.selectRelease(releases, UpdateChannel.ALPHA)?.getString("tag_name"))
    }

    @Test
    fun `semantic version comparison keeps stable above beta of the same version`() {
        assertEquals(1, manager.compareVersions("1.5.0", "1.5.0-beta.9"))
        assertEquals(1, manager.compareVersions("1.5.0-beta.2", "1.5.0-beta.1"))
        assertEquals(-1, manager.compareVersions("1.5.0-beta.1", "1.5.0"))
    }

    @Test
    fun `release catalog classifies stable beta and alpha apk releases but not drafts`() {
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
                "tag_name":"v1.7.0-alpha.1",
                "name":"Alpha 1.7.0",
                "draft":false,
                "prerelease":true,
                "assets":[{"name":"app-release.apk","browser_download_url":"https://example.test/alpha.apk"}]
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

        assertEquals(listOf("1.7.0-alpha.1", "1.6.0-beta.1", "1.4.0"), catalog.map { it.version })
        assertEquals(listOf(UpdateChannel.ALPHA, UpdateChannel.BETA, UpdateChannel.STABLE), catalog.map { it.channel })
        assertEquals(listOf(ReleaseRelation.NEWER, ReleaseRelation.NEWER, ReleaseRelation.OLDER), catalog.map { it.relation })
    }

    @Test
    fun `downloaded state is restored for stable and beta releases`() {
        val stable = release(tag = "v1.4.0", version = "1.4.0", prerelease = false)
        val beta = release(tag = "v1.5.0-beta.4", version = "1.5.0-beta.4", prerelease = true)
        manager.releaseFile(stable.version).apply {
            parentFile?.mkdirs()
            writeBytes(byteArrayOf(1, 2, 3))
        }
        manager.releaseFile(beta.version).apply {
            parentFile?.mkdirs()
            writeBytes(byteArrayOf(4, 5, 6))
        }

        val downloaded = manager.downloadedReleaseTags(listOf(stable, beta))

        assertEquals(setOf(stable.tag, beta.tag), downloaded)
        assertTrue(manager.releaseFile("1.5.0/beta 4").name.endsWith("1.5.0-beta-4.apk"))
        manager.releaseFile(stable.version).delete()
        manager.releaseFile(beta.version).delete()
    }

    private fun release(tag: String, version: String, prerelease: Boolean) = AvailableRelease(
        tag = tag,
        version = version,
        name = tag,
        prerelease = prerelease,
        channel = if (prerelease) UpdateChannel.BETA else UpdateChannel.STABLE,
        publishedAt = "",
        apkUrl = "https://example.test/app.apk",
        relation = ReleaseRelation.OLDER,
    )
}
