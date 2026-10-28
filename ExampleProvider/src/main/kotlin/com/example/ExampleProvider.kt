package com.example

import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.*
import org.jsoup.nodes.Element

class ExampleProvider : MainAPI() {
    // The new Animexin domain
    override var mainUrl = "https://animexin.dev"
    override var name = "Animexin"
    override val hasMainPage = true
    override var lang = "en"
    override val supportedTypes = setOf(TvType.Anime, TvType.AnimeMovie)

    // 1. Search Function
    override suspend fun search(query: String): List<SearchResponse>? {
        val document = app.get("$mainUrl/?s=$query").document
        return document.select("article.bs").mapNotNull { it.toSearchResult() }
    }

    private fun Element.toSearchResult(): SearchResponse? {
        val title = this.selectFirst("h2")?.text() ?: this.selectFirst("h3")?.text() ?: return null
        val href = fixUrl(this.selectFirst("a")?.attr("href") ?: return null)
        val posterUrl = fixUrlNull(this.selectFirst("img")?.attr("src"))
        
        return newAnimeSearchResponse(title, href, TvType.Anime) {
            this.posterUrl = posterUrl
        }
    }

    // 2. Load Episodes Function
    override suspend fun load(url: String): LoadResponse? {
        val document = app.get(url).document
        val title = document.selectFirst("h1")?.text() ?: return null
        val poster = fixUrlNull(document.selectFirst("div.thumb img")?.attr("src"))
        val description = document.selectFirst("div.entry-content p")?.text()
        
        val episodes = document.select("div.eplister ul li a").mapNotNull {
            val epHref = fixUrl(it.attr("href"))
            val epName = it.selectFirst(".epl-title")?.text() ?: "Episode"
            val epNum = it.selectFirst(".epl-num")?.text()?.toIntOrNull()
            newEpisode(epHref) {
                this.name = epName
                this.episode = epNum
            }
        }

        return newAnimeLoadResponse(title, url, TvType.Anime) {
            this.posterUrl = poster
            this.plot = description
            // This syntax fixes the build error we had earlier
            this.episodes = mutableMapOf(DubStatus.Subbed to episodes)
        }
    }

    // 3. Stream & Download Links Function
    override suspend fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean {
        val document = app.get(data).document

        // Find the video player iframe
        val embedUrl = document.selectFirst("div.player-embed iframe")?.attr("src")
        
        if (embedUrl != null) {
            try {
                // Fetch the HTML of the video player
                val embedHtml = app.get(embedUrl).text
                
                // Hunt for the hidden .m3u8 link
                val m3u8Regex = Regex("""(https?://[^\s"']+\.m3u8[^\s"']*)""")
                val m3u8Url = m3u8Regex.find(embedHtml)?.value

                if (m3u8Url != null) {
                    // Use CloudStream's built-in helper to split the m3u8 into qualities (360p, 480p, 720p, etc.)
                    val m3u8Helper = M3u8Helper().generateM3u8(
                        source = this.name,
                        streamUrl = m3u8Url,
                        referer = mainUrl,
                        headers = mapOf("Referer" to mainUrl)
                    )

                    // Send every quality to the player/downloader
                    m3u8Helper.forEach { link ->
                        callback(link)
                    }
                }
            } catch (e: Exception) {
                println("M3U8 parsing failed: ${e.message}")
            }
        }
        return true
    }
}
