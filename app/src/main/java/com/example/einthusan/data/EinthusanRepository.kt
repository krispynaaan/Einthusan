package com.example.einthusan.data

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.delay
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

    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .retryOnConnectionFailure(true)
        .build()

    // Semaphore set to 5 for parallel loading (improves speed significantly)
    private val requestSemaphore = Semaphore(5)

    private val TAG = "EinthusanRepo"

    private val LANGUAGES = listOf("tamil", "telugu", "hindi", "kannada", "bengali", "marathi", "punjabi")
    private val CORE_LANGS = listOf("tamil", "telugu", "hindi")

    data class HomeData(
        val featuredMovies: List<Movie>,
        val categories: Map<String, List<Movie>>
    )
    
    companion object {
        // In-memory application session cache shared across all repository instances
        var cachedHomeData: HomeData? = null
    }

    // --- 1. SEARCH FUNCTION ---
    suspend fun searchMovies(query: String): List<Movie> = withContext(Dispatchers.IO) {
        val encodedQuery = URLEncoder.encode(query, "UTF-8")

        val rawResults = LANGUAGES.map { lang ->
            async {
                retryWithBackoff(3) {
                    requestSemaphore.withPermit {
                        scrapeSearchForLang(lang, encodedQuery)
                    }
                } ?: emptyList()
            }
        }.awaitAll()

        return@withContext mergeAndSort(rawResults.flatten())
    }

    private fun scrapeSearchForLang(lang: String, query: String): List<Movie> {
        val url = "https://einthusan.tv/movie/results/?lang=$lang&query=$query"
        val movies = mutableListOf<Movie>()
        try {
            val doc = fetchDocument(url)
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
        // Return memory cached data if available rather than re-fetching network/TMDB details
        cachedHomeData?.let { return@withContext it }

        val deferredResults = LANGUAGES.map { lang ->
            async {
                val result = retryWithBackoff(times = 3, initialDelay = 1000) {
                    requestSemaphore.withPermit {
                        if (lang != LANGUAGES.first()) delay(500)
                        scrapeHomeForLang(lang)
                    }
                }

                if (result == null) {
                    Log.e(TAG, "CRITICAL: $lang failed to load after 3 attempts.")
                }
                result ?: HomeData(emptyList(), emptyMap())
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

        val uniqueFeatured = mergeAndSort(allFeatured)

        val finalFeatured = uniqueFeatured.map { movie ->
            async { enrichMovieWithTmdb(movie) }
        }.awaitAll()

        val sortedFeatured = finalFeatured.sortedByDescending { parseRating(it.rating) }

        val finalCategories = mutableMapOf<String, List<Movie>>()
        allCategoriesMap.forEach { (catTitle, movies) ->
            val merged = mergeAndSort(movies)
            finalCategories[catTitle] = merged.sortedByDescending { parseRating(it.rating) }
        }

        val resultData = HomeData(sortedFeatured, finalCategories)
        cachedHomeData = resultData // Save to cache array
        return@withContext resultData
    }

    private suspend fun <T> retryWithBackoff(
        times: Int,
        initialDelay: Long = 500,
        factor: Double = 2.0,
        block: suspend () -> T
    ): T? {
        var currentDelay = initialDelay
        repeat(times - 1) { attempt ->
            try {
                return block()
            } catch (e: Exception) {
                Log.w(TAG, "Attempt ${attempt + 1} failed: ${e.message}. Retrying in ${currentDelay}ms...")
                delay(currentDelay)
                currentDelay = (currentDelay * factor).toLong()
            }
        }
        return try {
            block()
        } catch (e: Exception) {
            Log.e(TAG, "Final attempt failed: ${e.message}")
            null
        }
    }

    private fun scrapeHomeForLang(lang: String): HomeData {
        val url = "${Constants.BASE_URL}/movie/browse/?lang=$lang"
        val featuredList = mutableListOf<Movie>()
        val categoryMap = mutableMapOf<String, List<Movie>>()

        val doc = fetchDocument(url)
        val displayLang = lang.replaceFirstChar { if (it.isLowerCase()) it.titlecase(Locale.getDefault()) else it.toString() }

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

        return HomeData(featuredList, categoryMap)
    }

    private fun mergeAndSort(movies: List<Movie>): List<Movie> {
        val mergedList = mutableListOf<Movie>()

        for (incoming in movies) {
            val cleanTarget = incoming.title.replace("[^a-zA-Z0-9]".toRegex(), "").lowercase()

            val matchIndex = mergedList.indexOfFirst { existing ->
                val cleanExisting = existing.title.replace("[^a-zA-Z0-9]".toRegex(), "").lowercase()
                val titleMatch = cleanExisting == cleanTarget
                val yearMatch = (existing.year.isEmpty() || incoming.year.isEmpty() || existing.year == incoming.year)
                titleMatch && yearMatch
            }

            if (matchIndex != -1) {
                val existing = mergedList[matchIndex]
                
                // Merge cleanly
                val combinedLangs = (existing.languages + incoming.languages).distinct()
                
                // Sort Languages: Tamil, Telugu, Hindi, then alphabetical
                val newLanguages = combinedLangs.sortedWith(compareBy<String> {
                    when (it.lowercase()) {
                        "tamil" -> 0
                        "telugu" -> 1
                        "hindi" -> 2
                        else -> 99
                    }
                }.thenBy { it })
                
                val newUrls = existing.videoUrls + incoming.videoUrls
                val bestYear = if (existing.year.isNotEmpty()) existing.year else incoming.year
                val bestRating = if (existing.rating.isNotEmpty() && existing.rating != "N/A") existing.rating else incoming.rating
                val bestBackdrop = if (existing.backdropUrl != existing.imageUrl) existing.backdropUrl else incoming.backdropUrl
                val bestSynopsis = if (existing.synopsis.length > incoming.synopsis.length) existing.synopsis else incoming.synopsis
                // NEW: Merge genres
                val bestGenres = (existing.genres + incoming.genres).distinct()

                mergedList[matchIndex] = existing.copy(
                    year = bestYear,
                    rating = bestRating,
                    backdropUrl = bestBackdrop,
                    synopsis = bestSynopsis,
                    languages = newLanguages,
                    videoUrls = newUrls,
                    genres = bestGenres // Added
                )
            } else {
                mergedList.add(incoming)
            }
        }
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

        val urlLang = videoPageUrl.substringAfter("lang=", "tamil").substringBefore("&")
        val currentLangFromUrl = urlLang.replaceFirstChar { if (it.isLowerCase()) it.titlecase(Locale.getDefault()) else it.toString() }

        val currentLang = if (scrapedData.languages.isNotEmpty() && scrapedData.languages.first().isNotBlank()) {
            scrapedData.languages.first()
        } else {
            currentLangFromUrl
        }
        
        // Absolute Best Approach: Aggressive fuzzy-matching against the global Memory Cache first.
        // Einthusan's native search endpoint is extremely flaky and transliterates titles incorrectly.
        // By checking our aggressively deduplicated Home Cache, we guarantee 1:1 consistency with the UI.
        val siblings = mutableListOf<Movie>()
        
        cachedHomeData?.let { homeCache ->
            val allMovies = homeCache.featuredMovies + homeCache.categories.values.flatten()
            val cleanTargetTitle = scrapedData.title.replace("[^a-zA-Z0-9]".toRegex(), "").lowercase()

            val matchesFromCache = allMovies.filter { candidate ->
                val cleanCandidate = candidate.title.replace("[^a-zA-Z0-9]".toRegex(), "").lowercase()
                cleanCandidate == cleanTargetTitle
            }
            siblings.addAll(matchesFromCache)
        }
        
        // Only if the cache yields absolutely nothing (e.g. searching from SearchScreen for an old movie)
        // do we fallback to the flaky Einthusan web scraper search endpoint.
        if (siblings.isEmpty()) {
            siblings.addAll(searchMovies(scrapedData.title))
        }

        val cleanTarget = scrapedData.title.replace("[^a-zA-Z0-9]".toRegex(), "").lowercase()
        val matches = siblings.filter { candidate ->
            val cleanCandidate = candidate.title.replace("[^a-zA-Z0-9]".toRegex(), "").lowercase()
            val titleMatch = cleanCandidate == cleanTarget
            val yearMatch = (candidate.year.isEmpty() || scrapedData.year.isEmpty() || candidate.year == scrapedData.year)
            titleMatch && yearMatch
        }

        var finalLanguages = (scrapedData.languages + listOf(currentLang)).filter { it.isNotBlank() }.distinct()
        var finalUrls = scrapedData.videoUrls.toMutableMap()
        finalUrls[currentLang] = videoPageUrl

        matches.forEach { match ->
            finalLanguages = (finalLanguages + match.languages).distinct()
            finalUrls.putAll(match.videoUrls)
        }

        finalLanguages = finalLanguages.sortedWith(compareBy<String> {
            when (it.lowercase()) {
                "tamil" -> 0
                "telugu" -> 1
                "hindi" -> 2
                else -> 99
            }
        }.thenBy { it })

        var finalRating = scrapedData.rating
        var finalCover = scrapedData.coverUrl
        var finalBackdrop = scrapedData.backdropUrl
        var finalSynopsis = scrapedData.synopsis
        var finalGenres = scrapedData.genres
        var finalCast = scrapedData.cast

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
                    if (finalGenres.isEmpty() && details.genres != null) finalGenres = details.genres.map { it.name }
                    if (details.credits?.cast != null) {
                        val tmdbCast = details.credits.cast.take(10).map {
                            CastMember(it.name, if(it.profile_path != null) "${Constants.TMDB_IMAGE_BASE}${it.profile_path}" else "", it.character ?: "Actor")
                        }
                        if (tmdbCast.isNotEmpty()) finalCast = tmdbCast
                    }
                }
            } catch (e: Exception) { Log.e(TAG, "TMDB Detail Failed: ${e.message}") }
        }

        return@withContext scrapedData.copy(
            rating = finalRating,
            coverUrl = finalCover,
            backdropUrl = finalBackdrop,
            synopsis = finalSynopsis,
            genres = finalGenres,
            cast = finalCast,
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

                // NEW: Extract genres from TMDB
                val tmdbGenres = details.genres?.map { it.name } ?: emptyList()

                enriched = enriched.copy(
                    imageUrl = if (details.poster_path != null) "${Constants.TMDB_IMAGE_BASE}${details.poster_path}" else movie.imageUrl,
                    backdropUrl = if (details.backdrop_path != null) "${Constants.TMDB_IMAGE_BASE}${details.backdrop_path}" else movie.imageUrl,
                    synopsis = if (!details.overview.isNullOrEmpty()) details.overview else movie.synopsis,
                    rating = finalRating,
                    year = if (movie.year.isEmpty()) tmdbYear else movie.year,
                    genres = tmdbGenres // Populate genres
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

        val urlLang = videoPageUrl.substringAfter("lang=", "tamil").substringBefore("&")
        val fallbackLang = urlLang.replaceFirstChar { if (it.isLowerCase()) it.titlecase(Locale.getDefault()) else it.toString() }

        val langRaw = infoBlock?.select("span")?.text()
        val langText = if (!langRaw.isNullOrBlank()) {
            langRaw.replaceFirstChar { if (it.isLowerCase()) it.titlecase(Locale.getDefault()) else it.toString() }
        } else fallbackLang

        val genres = mutableListOf<String>()
        try {
            val infoText = summary.select(".block2 .info").text()
            if (infoText.contains("|")) {
                val potentialGenres = infoText.split("|").map { it.trim() }
                val clean = potentialGenres.filter { !it.matches(Regex("\\d{4}")) && it != "Tamil" && it != "Telugu" && it != "Hindi" }
                if (clean.isNotEmpty()) genres.addAll(clean)
            }
            val genreElements = summary.select(".genres li a, .genre-tag")
            genreElements.forEach { genres.add(it.text()) }
            if (genres.isEmpty()) {
                val commonGenres = listOf("Action", "Comedy", "Romance", "Drama", "Thriller", "Horror", "Sci-Fi", "Adventure", "Crime", "Family", "Fantasy")
                commonGenres.forEach { g -> if (infoText.contains(g, ignoreCase = true)) genres.add(g) }
            }
        } catch(e: Exception) { Log.e(TAG, "Genre parsing failed for $title", e) }

        val castList = mutableListOf<CastMember>()
        try {
            var profs = summary.select(".professionals .prof")
            if (profs.isEmpty()) profs = doc.select(".professionals .prof")
            if (profs.isEmpty()) profs = doc.select("#UICast .prof, .cast-list .prof")

            for (prof in profs) {
                val name = prof.select("p").text()
                val role = prof.select("label").text()
                var img = prof.select("img").attr("src")
                if (img.isEmpty()) img = prof.select(".img-box img").attr("src")
                if (img.startsWith("//")) img = "https:$img"
                if (name.isNotEmpty()) castList.add(CastMember(name, img, role))
            }
        } catch(e: Exception) { Log.e(TAG, "Cast parsing failed for $title", e) }

        return MovieDetails(
            title = title, coverUrl = coverUrl, backdropUrl = coverUrl, synopsis = synopsis,
            year = year, rating = rating, genres = genres.distinct(), cast = castList,
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

    private fun fetchDocument(url: String): org.jsoup.nodes.Document {
        val request = Request.Builder().url(url).header("User-Agent", Constants.USER_AGENT).build()
        val response = client.newCall(request).execute()

        if (response.isSuccessful) {
            return Jsoup.parse(response.body?.string() ?: "")
        } else {
            response.close()
            throw IOException("HTTP Error: ${response.code}")
        }
    }
}