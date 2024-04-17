import kotlinx.serialization.json.Json

interface Platform {
    val name: String
}

expect fun getPlatform(): Platform

val json = Json { ignoreUnknownKeys = true }
