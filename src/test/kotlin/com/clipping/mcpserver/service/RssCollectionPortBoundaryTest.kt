package com.clipping.mcpserver.service

import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.string.shouldNotContain
import org.junit.jupiter.api.Test
import java.nio.file.Files
import java.nio.file.Paths

class RssCollectionPortBoundaryTest {

    @Test
    fun `services should depend on rss collection port instead of concrete feed collector`() {
        val serviceSources = listOf(
            Paths.get("clipping-collection/src/main/kotlin/com/clipping/mcpserver/service/collection/RssSourceCollectionService.kt"),
            Paths.get("src/main/kotlin/com/clipping/mcpserver/service/competitor/CompetitorCollectionService.kt"),
            Paths.get("src/main/kotlin/com/clipping/mcpserver/service/competitor/CompetitorWatchlistService.kt"),
            Paths.get("clipping-source/src/main/kotlin/com/clipping/mcpserver/service/source/SourceHealthScheduler.kt")
        )

        serviceSources.forEach { sourcePath ->
            val source = Files.readString(sourcePath)

            source shouldContain "RssCollectionPort"
            source shouldNotContain "import com.clipping.mcpserver.rss.RssFeedCollector"
        }
    }

    @Test
    fun `rss collection port should not expose app models`() {
        val source = Files.readString(
            Paths.get("clipping-engine/src/main/kotlin/com/clipping/mcpserver/service/port/RssCollectionPort.kt")
        )

        source shouldContain "RssCollectionSource"
        source shouldContain "RssCollectedItem"
        source shouldNotContain "com.clipping.mcpserver.model"
        source shouldNotContain "RssItem"
        source shouldNotContain "RssSource"
    }

    @Test
    fun `rss feed collector should be adapted instead of implementing the port directly`() {
        val collectorSource = Files.readString(
            Paths.get("src/main/kotlin/com/clipping/mcpserver/rss/RssFeedCollector.kt")
        )
        val adapterSource = Files.readString(
            Paths.get("src/main/kotlin/com/clipping/mcpserver/rss/RssCollectionAdapter.kt")
        )

        collectorSource shouldNotContain ": RssCollectionPort"
        adapterSource shouldContain ": RssCollectionPort"
    }
}
