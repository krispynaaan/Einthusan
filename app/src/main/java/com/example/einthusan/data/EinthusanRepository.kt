package com.example.einthusan.data

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.jsoup.Jsoup
import java.io.IOException
import java.net.URLEncoder
import java.util.Locale
import java.util.concurrent.TimeUnit
import kotlin.math.max

class EinthusanRepository {

    // 1. TIMEOUTS: Increased to 30s to handle slow emulator networks
    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .retryOnConnectionFailure(true)
        .build()

    // 2. RATE LIMITER: Reduced to 3 to prevent choking the network
    private val requestSemaphore = Semaphore(3)

    private val TAG = "EinthusanRepo"
    private val LANGUAGES = listOf("tamil", "telugu", "hindi")

    data class HomeData(
        val featuredMovies: List<Movie>,
        val categories: Map<String, List<Movie>>
    )

    // --- 1. SEARCH FUNCTION ---
    suspend fun searchMovies(query: String): List<Movie> = withContext(Dispatchers.IO) {
        val encodedQuery = URLEncoder.encode(query, "UTF-8")

        val rawResults = LANGUAGES.map { lang ->
            async {
                requestSemaphore.withPermit {
                    scrapeSearchForLang(lang, encodedQuery)
                }
            }
        }.awaitAll()

        return@withContext mergeAndSort(rawResults.flatten())
    }

    private fun scrapeSearchForLang(lang: String, query: String): List<Movie> {
        val url = "https://einthusan.tv/movie/results/?lang=$lang&query=$query"
        val movies = mutableListOf<Movie>()
        try {
            val doc = fetchDocument(url) // Uses Retry Logic
            val elements = doc.select("#UIMovieSummary ul li")
            for (element in elements) {
                try {
                    var title = element.select(".block2 .title h3").text()
                    if (title.isEmpty()) title = element.select("h3").text()

                    var link = element.select("a.title").attr("href")
                    if (link.isEmpty()) link = element.select(".block2 a").attr("href")

                    var thumb = element.select(".block1 img").attr("src")
                    var synopsis = element.select(".block2 .synopsis").text()

                    val infoText = element.select(".block2 .info").text()
                    val year = """(19|20)\d{2}""".toRegex().find(infoText)?.value ?: ""

                    if (link.startsWith("/")) link = "${Constants.BASE_URL}$link"
                    if (thumb.startsWith("//")) thumb = "https:$thumb"

                    if (title.isNotEmpty() && link.isNotEmpty()) {
                        val displayLang = lang.replaceFirstChar { if (it.isLowerCase()) it.titlecase(Locale.getDefault()) else it.toString() }
                        movies.add(Movie(
                            title = title,
                            imageUrl = thumb,
                            videoPageUrl = link,
                            synopsis = synopsis,
                            year = year,
                            languages = listOf(displayLang),
                            videoUrls = mapOf(displayLang to link)
                        ))
                    }
                } catch (e: Exception) { }
            }
        } catch (e: Exception) { Log.e(TAG, "Search fail for $lang: ${e.message}") }
        return movies
    }

    // --- 2. HOME PAGE FUNCTION ---
    suspend fun fetchHomeData(): HomeData = withContext(Dispatchers.IO) {
        // 1. Scrape all 3 Home Pages (With Retries & Throttle)
        val deferredResults = LANGUAGES.map { lang ->
            async {
                try {
                    requestSemaphore.withPermit { scrapeHomeForLang(lang) }
                } catch (e: Exception) {
                    Log.e(TAG, "CRITICAL: Failed to scrape $lang Home: ${e.message}")
                    HomeData(emptyList(), emptyMap()) // Return empty if all retries fail
                }
            }
        }
        val results = deferredResults.awaitAll()

        val allFeatured = results.flatMap { it.featuredMovies }
        val allCategoriesMap = mutableMapOf<String, MutableList<Movie>>()

        results.forEach { result ->
            result.categories.forEach { (catTitle, movies) ->
                if (!allCategoriesMap.containsKey(catTitle)) {
                    allCategoriesMap[catTitle] = mutableListOf()
                }
                allCategoriesMap[catTitle]?.addAll(movies)
            }
        }

        // 2. MERGE & DEDUPLICATE (Consolidated from T/Te/Hi home pages)
        val uniqueFeatured = mergeAndSort(allFeatured)

        // 3. Enrich with TMDB (Images Only, NO Deep Search for performance)
        val finalFeatured = uniqueFeatured.map { movie ->
            async { enrichMovieWithTmdb(movie) }
        }.awaitAll()

        // 4. Final Sort by Rating
        val sortedFeatured = finalFeatured.sortedByDescending { parseRating(it.rating) }

        // 5. Process Categories
        val finalCategories = mutableMapOf<String, List<Movie>>()
        allCategoriesMap.forEach { (catTitle, movies) ->
            val merged = mergeAndSort(movies)
            finalCategories[catTitle] = merged.sortedByDescending { parseRating(it.rating) }
        }

        return@withContext HomeData(sortedFeatured, finalCategories)
    }

    private fun scrapeHomeForLang(lang: String): HomeData {
        val url = "${Constants.BASE_URL}/movie/browse/?lang=$lang"
        val featuredList = mutableListOf<Movie>()
        val categoryMap = mutableMapOf<String, List<Movie>>()

        try {
            val doc = fetchDocument(url) // Uses Retry Logic
            val displayLang = lang.replaceFirstChar { if (it.isLowerCase()) it.titlecase(Locale.getDefault()) else it.toString() }

            // Featured
            val featuredElements = doc.select("#UIFeaturedFilms .tabview")
            for (element in featuredElements) {
                try {
                    val title = element.select(".block2 h2").text()
                    var link = element.select(".block2 .title").attr("href")
                    var thumb = element.select(".block1 img").attr("src")

                    val infoBlock = element.selectFirst(".block2 .info p")
                    val rawInfoText = infoBlock?.text() ?: ""
                    val year = """(19|20)\d{2}""".toRegex().find(rawInfoText)?.value ?: ""
                    val synopsis = element.select(".block2 .desc").text()

                    val ratingsMap = mutableMapOf<String, Double>()
                    element.select(".average-rating li").forEach { el ->
                        val label = el.selectFirst("label")?.text() ?: ""
                        val valueStr = el.selectFirst("p")?.attr("data-value") ?: el.selectFirst("p")?.text()
                        val value = valueStr?.toDoubleOrNull() ?: 0.0
                        ratingsMap[label] = value
                    }
                    val calculatedRating = calculateWeightedRating(ratingsMap)

                    if (link.startsWith("/")) link = "${Constants.BASE_URL}$link"
                    if (thumb.startsWith("//")) thumb = "https:$thumb"

                    if (title.isNotEmpty()) {
                        featuredList.add(Movie(
                            title = title,
                            imageUrl = thumb,
                            videoPageUrl = link,
                            synopsis = synopsis,
                            year = year,
                            rating = calculatedRating,
                            languages = listOf(displayLang),
                            videoUrls = mapOf(displayLang to link)
                        ))
                    }
                } catch (e: Exception) { }
            }

            // Categories
            val categoryTitles = doc.select("#UIShowcasedFilms .tabbing ul li label p").map { it.text() }
            val categoryContents = doc.select("#UIShowcasedFilms > .tabbing > .tabview")

            categoryTitles.forEachIndexed { index, title ->
                if (index < categoryContents.size) {
                    val categoryElement = categoryContents[index]
                    val movies = mutableListOf<Movie>()
                    val items = categoryElement.select("ul li")
                    for (item in items) {
                        try {
                            val t = item.select("a.title").text()
                            var l = item.select("a.title").attr("href")
                            var th = item.select("img").attr("src")
                            if (l.startsWith("/")) l = "${Constants.BASE_URL}$l"
                            if (th.startsWith("//")) th = "https:$th"
                            if (t.isNotEmpty()) {
                                movies.add(Movie(
                                    title = t,
                                    imageUrl = th,
                                    videoPageUrl = l,
                                    synopsis = "",
                                    year = "",
                                    languages = listOf(displayLang),
                                    videoUrls = mapOf(displayLang to l)
                                ))
                            }
                        } catch (e: Exception) { }
                    }
                    if (movies.isNotEmpty()) categoryMap[title] = movies
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Scrape logic failed inside $lang: ${e.message}")
            throw e // Re-throw to trigger retry logic in fetchDocument if needed, or logging in fetchHomeData
        }

        return HomeData(featuredList, categoryMap)
    }

    // --- CORE LOGIC: MERGE & SORT ---
    private fun mergeAndSort(movies: List<Movie>): List<Movie> {
        val mergedList = mutableListOf<Movie>()

        for (incoming in movies) {
            val normTitle = incoming.title.lowercase().trim()

            // Fuzzy match: Title Match + (Year Match OR one is missing)
            val matchIndex = mergedList.indexOfFirst { existing ->
                val existingTitle = existing.title.lowercase().trim()
                val titleMatch = existingTitle == normTitle
                val yearMatch = (existing.year.isEmpty() || incoming.year.isEmpty() || existing.year == incoming.year)
                titleMatch && yearMatch
            }

            if (matchIndex != -1) {
                // MERGE
                val existing = mergedList[matchIndex]

                val newLanguages = (existing.languages + incoming.languages).distinct()
                val newUrls = existing.videoUrls + incoming.videoUrls

                // Keep best metadata
                val bestYear = if (existing.year.isNotEmpty()) existing.year else incoming.year
                val bestRating = if (existing.rating.isNotEmpty() && existing.rating != "N/A") existing.rating else incoming.rating
                val bestBackdrop = if (existing.backdropUrl != existing.imageUrl) existing.backdropUrl else incoming.backdropUrl
                val bestSynopsis = if (existing.synopsis.length > incoming.synopsis.length) existing.synopsis else incoming.synopsis

                mergedList[matchIndex] = existing.copy(
                    year = bestYear,
                    rating = bestRating,
                    backdropUrl = bestBackdrop,
                    synopsis = bestSynopsis,
                    languages = newLanguages,
                    videoUrls = newUrls
                )
            } else {
                mergedList.add(incoming)
            }
        }

        // Sort by Rating (High -> Low)
        return mergedList.sortedByDescending { parseRating(it.rating) }
    }

    private fun parseRating(ratingStr: String): Double {
        if (ratingStr.isEmpty() || ratingStr == "N/A") return 0.0
        return ratingStr.replace("★", "").trim().toDoubleOrNull() ?: 0.0
    }

    // --- 3. MOVIE DETAILS ---
    suspend fun getMovieDetails(videoPageUrl: String): MovieDetails = withContext(Dispatchers.IO) {
        val scrapedData = try {
            requestSemaphore.withPermit { scrapeEinthusanDetails(videoPageUrl) }
        } catch (e: Exception) { throw e }

        val currentLang = scrapedData.languages.firstOrNull() ?: "Tamil"

        val siblings = searchMovies(scrapedData.title)
        val matches = siblings.filter {
            it.title.trim().equals(scrapedData.title.trim(), ignoreCase = true) &&
                    (it.year.isEmpty() || scrapedData.year.isEmpty() || it.year == scrapedData.year)
        }

        var finalLanguages = scrapedData.languages
        var finalUrls = scrapedData.videoUrls.toMutableMap()
        finalUrls[currentLang] = videoPageUrl

        matches.forEach { match ->
            finalLanguages = (finalLanguages + match.languages).distinct()
            finalUrls.putAll(match.videoUrls)
        }

        var finalRating = scrapedData.rating
        var finalCover = scrapedData.coverUrl
        var finalBackdrop = scrapedData.backdropUrl
        var finalSynopsis = scrapedData.synopsis

        if (Constants.TMDB_API_KEY != "YOUR_TMDB_API_KEY_HERE") {
            try {
                val searchResponse = TmdbClient.api.searchMovie(Constants.TMDB_API_KEY, scrapedData.title, scrapedData.year)
                var tmdbMatch = searchResponse.results.find { it.original_language == "ta" }
                if (tmdbMatch == null) tmdbMatch = searchResponse.results.firstOrNull()

                if (tmdbMatch != null) {
                    val details = TmdbClient.api.getMovieDetails(tmdbMatch.id, Constants.TMDB_API_KEY)
                    if (finalRating.isEmpty() || finalRating == "N/A") {
                        if (details.vote_average > 0.0) finalRating = String.format("★ %.1f", details.vote_average)
                    }
                    if (details.poster_path != null) finalCover = "${Constants.TMDB_IMAGE_BASE}${details.poster_path}"
                    if (details.backdrop_path != null) finalBackdrop = "${Constants.TMDB_IMAGE_BASE}${details.backdrop_path}"
                    if (!details.overview.isNullOrEmpty()) finalSynopsis = details.overview
                }
            } catch (e: Exception) { Log.e(TAG, "TMDB Detail Failed: ${e.message}") }
        }

        return@withContext scrapedData.copy(
            rating = finalRating,
            coverUrl = finalCover,
            backdropUrl = finalBackdrop,
            synopsis = finalSynopsis,
            languages = finalLanguages,
            videoUrls = finalUrls
        )
    }

    private suspend fun enrichMovieWithTmdb(movie: Movie): Movie {
        var enriched = movie
        if (Constants.TMDB_API_KEY == "YOUR_TMDB_API_KEY_HERE") return enriched

        try {
            val searchRes = TmdbClient.api.searchMovie(Constants.TMDB_API_KEY, movie.title, movie.year)
            var match = searchRes.results.find { it.original_language == "ta" }
            if (match == null) match = searchRes.results.firstOrNull()

            if (match != null) {
                val details = TmdbClient.api.getMovieDetails(match.id, Constants.TMDB_API_KEY, "")
                val tmdbRating = if (details.vote_average > 0.0) String.format("★ %.1f", details.vote_average) else ""
                val tmdbYear = details.release_date?.take(4) ?: ""

                val finalRating = if (enriched.rating.isNotEmpty() && enriched.rating != "N/A") enriched.rating else tmdbRating

                enriched = enriched.copy(
                    imageUrl = if (details.poster_path != null) "${Constants.TMDB_IMAGE_BASE}${details.poster_path}" else movie.imageUrl,
                    backdropUrl = if (details.backdrop_path != null) "${Constants.TMDB_IMAGE_BASE}${details.backdrop_path}" else movie.imageUrl,
                    synopsis = if (!details.overview.isNullOrEmpty()) details.overview else movie.synopsis,
                    rating = finalRating,
                    year = if (movie.year.isEmpty()) tmdbYear else movie.year
                )
            }
        } catch (e: Exception) { }

        if (enriched.rating.isEmpty()) {
            try {
                requestSemaphore.withPermit {
                    val fallbackUrl = enriched.videoUrls.values.firstOrNull()
                    if (fallbackUrl != null) {
                        val internal = scrapeEinthusanDetails(fallbackUrl)
                        if (internal.rating.isNotEmpty() && internal.rating != "N/A") {
                            enriched = enriched.copy(rating = internal.rating.replace("★", "").trim())
                        }
                    }
                }
            } catch (e: Exception) {}
        }
        return enriched
    }

    private fun scrapeEinthusanDetails(videoPageUrl: String): MovieDetails {
        val doc = fetchDocument(videoPageUrl)
        val summary = doc.selectFirst("#UIMovieSummary") ?: throw IOException("Parse Error")

        val title = summary.selectFirst(".block2 a.title h3")?.text() ?: summary.selectFirst("h3")?.text() ?: "Unknown"
        var coverUrl = doc.select("meta[property=og:image]").attr("content")
        if (coverUrl.isEmpty()) coverUrl = summary.selectFirst(".block1 img")?.attr("src") ?: ""
        if (coverUrl.startsWith("//")) coverUrl = "https:$coverUrl"
        else if (coverUrl.startsWith("/")) coverUrl = "${Constants.BASE_URL}$coverUrl"

        val synopsis = summary.selectFirst("p.synopsis")?.text() ?: ""

        val ratingsMap = mutableMapOf<String, Double>()
        summary.select(".average-rating li").forEach { el ->
            val label = el.selectFirst("label")?.text() ?: ""
            val valueStr = el.selectFirst("p")?.attr("data-value") ?: el.selectFirst("p")?.text()
            val value = valueStr?.toDoubleOrNull() ?: 0.0
            ratingsMap[label] = value
        }
        val rating = calculateWeightedRating(ratingsMap)

        val infoBlock = summary.selectFirst(".info p")
        val rawInfoText = infoBlock?.text() ?: ""
        val year = """(19|20)\d{2}""".toRegex().find(rawInfoText)?.value ?: ""

        val langRaw = infoBlock?.select("span")?.text() ?: "Tamil"
        val langText = langRaw.replaceFirstChar { if (it.isLowerCase()) it.titlecase(Locale.getDefault()) else it.toString() }

        val castList = mutableListOf<CastMember>()
        val profs = summary.select(".professionals .prof")
        for (prof in profs) {
            val name = prof.select("p").text()
            val role = prof.select("label").text()
            var img = prof.parent()?.select("img")?.attr("src") ?: ""
            if (img.startsWith("//")) img = "https:$img"
            if (name.isNotEmpty()) castList.add(CastMember(name, img, role))
        }

        return MovieDetails(
            title = title, coverUrl = coverUrl, backdropUrl = coverUrl, synopsis = synopsis,
            year = year, rating = rating, genres = emptyList(), cast = castList,
            languages = listOf(langText), videoUrls = mapOf(langText to videoPageUrl)
        )
    }

    private fun calculateWeightedRating(ratingsMap: Map<String, Double>): String {
        val sStory = ratingsMap["Storyline"] ?: 0.0
        val sPerf = ratingsMap["Performance"] ?: 0.0
        val sAction = ratingsMap["Action"] ?: 0.0
        val sComedy = ratingsMap["Comedy"] ?: 0.0
        val sRomance = ratingsMap["Romance"] ?: 0.0

        fun calcActivation(score: Double) = max(0.0, (score - 2.5) / 2.5)
        val wActionRaw = 0.10 * calcActivation(sAction)
        val wComedyRaw = 0.10 * calcActivation(sComedy)
        val wRomanceRaw = 0.10 * calcActivation(sRomance)
        val totalWeight = 0.35 + 0.35 + wActionRaw + wComedyRaw + wRomanceRaw

        return if (totalWeight > 0.0) {
            val weightedSum = (sStory * 0.35 + sPerf * 0.35 + sAction * wActionRaw + sComedy * wComedyRaw + sRomance * wRomanceRaw) / totalWeight
            String.format("★ %.1f", weightedSum * 2)
        } else "N/A"
    }

    // --- ROBUST FETCH WITH RETRY ---
    private fun fetchDocument(url: String): org.jsoup.nodes.Document {
        var attempt = 0
        var lastException: Exception? = null

        while (attempt < 3) {
            try {
                val request = Request.Builder()
                    .url(url)
                    .header("User-Agent", Constants.USER_AGENT)
                    .build()

                val response = client.newCall(request).execute()
                if (response.isSuccessful) {
                    val body = response.body?.string() ?: ""
                    return Jsoup.parse(body)
                } else {
                    response.close()
                }
            } catch (e: Exception) {
                lastException = e
                Log.w(TAG, "Attempt ${attempt+1} failed for $url: ${e.message}")
            }
            attempt++
            Thread.sleep(1000) // Backoff
        }
        throw lastException ?: IOException("Failed to fetch $url after 3 attempts")
    }
}