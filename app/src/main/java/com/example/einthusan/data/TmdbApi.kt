package com.example.einthusan.data

import com.google.gson.annotations.SerializedName
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.GET
import retrofit2.http.Path
import retrofit2.http.Query
import java.security.SecureRandom
import java.security.cert.X509Certificate
import javax.net.ssl.SSLContext
import javax.net.ssl.TrustManager
import javax.net.ssl.X509TrustManager

// --- API INTERFACE ---
interface TmdbApi {
    @GET("search/movie")
    suspend fun searchMovie(
        @Query("api_key") apiKey: String,
        @Query("query") query: String,
        @Query("year") year: String
    ): TmdbSearchResponse

    @GET("movie/{movie_id}")
    suspend fun getMovieDetails(
        @Path("movie_id") id: Int,
        @Query("api_key") apiKey: String,
        @Query("append_to_response") append: String = "credits"
    ): TmdbMovieFullDetails
}

// --- DATA MODELS (Fixed with SerializedName) ---
data class TmdbSearchResponse(
    @SerializedName("results") val results: List<TmdbSearchResult>
)

data class TmdbSearchResult(
    @SerializedName("id") val id: Int,
    @SerializedName("title") val title: String,
    @SerializedName("release_date") val release_date: String?,
    @SerializedName("original_language") val original_language: String?
)

data class TmdbMovieFullDetails(
    @SerializedName("id") val id: Int,
    @SerializedName("title") val title: String,
    @SerializedName("overview") val overview: String?,
    @SerializedName("poster_path") val poster_path: String?,
    @SerializedName("backdrop_path") val backdrop_path: String?,
    @SerializedName("vote_average") val vote_average: Double,
    @SerializedName("release_date") val release_date: String?,
    @SerializedName("original_language") val original_language: String?,
    @SerializedName("genres") val genres: List<TmdbGenre>?,
    @SerializedName("credits") val credits: TmdbCredits?
)

data class TmdbGenre(
    @SerializedName("name") val name: String
)

data class TmdbCredits(
    @SerializedName("cast") val cast: List<TmdbCastMember>?
)

data class TmdbCastMember(
    @SerializedName("name") val name: String,
    @SerializedName("character") val character: String?,
    @SerializedName("profile_path") val profile_path: String?
)

// --- CLIENT ---
object TmdbClient {
    // Unsafe Client to bypass SSL errors on Emulators
    private fun getUnsafeOkHttpClient(): OkHttpClient {
        try {
            val trustAllCerts = arrayOf<TrustManager>(object : X509TrustManager {
                override fun checkClientTrusted(chain: Array<X509Certificate>, authType: String) {}
                override fun checkServerTrusted(chain: Array<X509Certificate>, authType: String) {}
                override fun getAcceptedIssuers(): Array<X509Certificate> = arrayOf()
            })

            val sslContext = SSLContext.getInstance("SSL")
            sslContext.init(null, trustAllCerts, SecureRandom())

            return OkHttpClient.Builder()
                .sslSocketFactory(sslContext.socketFactory, trustAllCerts[0] as X509TrustManager)
                .hostnameVerifier { _, _ -> true }
                .build()
        } catch (e: Exception) {
            throw RuntimeException(e)
        }
    }

    val api: TmdbApi by lazy {
        Retrofit.Builder()
            .baseUrl(Constants.TMDB_BASE_URL)
            .client(getUnsafeOkHttpClient())
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(TmdbApi::class.java)
    }
}