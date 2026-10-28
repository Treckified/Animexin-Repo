package com.example

import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.*
import org.jsoup.nodes.Element

class ExampleProvider : MainAPI() {
    override var mainUrl = "https://animexin.dev" 
    override var name = "Animexin"
    override val hasMainPage = true
    override var lang = "en"
    override val supportedTypes = setOf(TvType.Anime, TvType.AnimeMovie)

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
            this.addEpisodes(episodes)
        }
    }

    override suspend fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean {
        val document = app.get(data).document

        val embedUrl = document.selectFirst("div.player-embed iframe")?.attr("src")
        
        if (embedUrl != null) {
            loadExtractor(embedUrl, mainUrl, subtitleCallback, callback)
            
            try {
                val embedHtml = app.get(embedUrl).text
                val m3u8Regex = Regex("""(https?://[^\s"']+\.m3u8[^\s"']*)""")
                val m3u8Url = m3u8Regex.find(embedHtml)?.value

                if (m3u8Url != null) {
                    val m3u8Content = app.get(m3u8Url).text
                    val lines = m3u8Content.lines()
                    var currentQuality = Qualities.Unknown.value
                    
                    for (line in lines) {
                        if (line.contains("RESOLUTION=")) {
                            val resolutionRegex = Regex("""RESOLUTION=\d+x(\d+)""")
                            val res = resolutionRegex.find(line)?.groupValues?.get(1)?.toIntOrNull()
                            currentQuality = when (res) {
                                360 -> Qualities.P360.value
                                480 -> Qualities.P480.value
                                720 -> Qualities.P720.value
                                1080 -> Qualities.P1080.value
                                else -> Qualities.Unknown.value
                            }
                        } else if (line.startsWith("http") && currentQuality != Qualities.Unknown.value) {
                            callback(
                                newExtractorLink(
                                    source = this.name,
                                    name = "Animexin Stream - ${currentQuality}p",
                                    url = line,
                                    type = ExtractorLinkType.M3U8
                                ) {
                                    this.referer = mainUrl
                                    this.quality = currentQuality
                                }
                            )
                            currentQuality = Qualities.Unknown.value
                        }
                    }
                }
            } catch (e: Exception) {
                println("M3U8 parsing failed: ${e.message}")
            }
        }
        return true
    }
}
