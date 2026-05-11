package com.ohmyclipping.service

import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.string.shouldNotContain
import org.junit.jupiter.api.Test
import java.nio.file.Files
import java.nio.file.Paths

class RssCollectionPortBoundaryTest {

    @Test
    fun `services should depend on rss collection port instead of concrete feed collector`() {
        val serviceSources = listOf(
            Paths.get("modules/collection/src/main/kotlin/com/ohmyclipping/service/collection/RssSourceCollectionService.kt"),
            Paths.get("src/main/kotlin/com/ohmyclipping/service/competitor/CompetitorCollectionService.kt"),
            Paths.get("src/main/kotlin/com/ohmyclipping/service/competitor/CompetitorWatchlistService.kt"),
            Paths.get("modules/source/src/main/kotlin/com/ohmyclipping/service/source/SourceHealthScheduler.kt")
        )

        serviceSources.forEach { sourcePath ->
            val source = Files.readString(sourcePath)

            source shouldContain "RssCollectionPort"
            source shouldNotContain "import com.ohmyclipping.rss.RssFeedCollector"
        }
    }

    @Test
    fun `rss collection port should not expose app models`() {
        val source = Files.readString(
            Paths.get("modules/digest-policy/src/main/kotlin/com/ohmyclipping/service/port/RssCollectionPort.kt")
        )

        source shouldContain "RssCollectionSource"
        source shouldContain "RssCollectedItem"
        source shouldNotContain "com.ohmyclipping.model"
        source shouldNotContain "RssItem"
        source shouldNotContain "RssSource"
    }

    @Test
    fun `rss feed collector should be adapted instead of implementing the port directly`() {
        val collectorSource = Files.readString(
            Paths.get("src/main/kotlin/com/ohmyclipping/rss/RssFeedCollector.kt")
        )
        val adapterSource = Files.readString(
            Paths.get("src/main/kotlin/com/ohmyclipping/rss/RssCollectionAdapter.kt")
        )

        collectorSource shouldNotContain ": RssCollectionPort"
        adapterSource shouldContain ": RssCollectionPort"
    }
}
