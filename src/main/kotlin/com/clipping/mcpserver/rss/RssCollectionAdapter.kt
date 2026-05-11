package com.clipping.mcpserver.rss

import com.clipping.mcpserver.service.collection.toRssCollectedItem
import com.clipping.mcpserver.service.collection.toRssItem
import com.clipping.mcpserver.service.collection.toRssSource
import com.clipping.mcpserver.service.port.RssCollectedItem
import com.clipping.mcpserver.service.port.RssCollectionPort
import com.clipping.mcpserver.service.port.RssCollectionSource
import org.springframework.stereotype.Component

@Component
class RssCollectionAdapter(
    private val rssFeedCollector: RssFeedCollector
) : RssCollectionPort {

    override fun collect(
        source: RssCollectionSource,
        hoursBack: Int,
        enrichShortContent: Boolean
    ): List<RssCollectedItem> =
        rssFeedCollector.collect(source.toRssSource(), hoursBack, enrichShortContent)
            .map { it.toRssCollectedItem() }

    override fun collectByUrl(url: String, hoursBack: Int): List<RssCollectedItem> =
        rssFeedCollector.collectByUrl(url, hoursBack)
            .map { it.toRssCollectedItem() }

    override fun enrichShortContent(item: RssCollectedItem): RssCollectedItem =
        rssFeedCollector.enrichShortContent(item.toRssItem()).toRssCollectedItem()
}
