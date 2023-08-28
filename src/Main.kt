import com.google.gson.Gson
import com.google.gson.JsonObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL
import java.nio.file.Files
import java.nio.file.Paths
import kotlin.time.ExperimentalTime
import kotlin.time.measureTimedValue


// Search queries to find lands:
// t:basic unique:prints
// t:basic unique:prints is:fullart (Downloads all basic land cards plus fullart cards)
//
// To find the card in scryfall.com from the image name, you can use this search syntax: "set:abc cn:123"
//
@OptIn(ExperimentalTime::class)
fun main() {
    println("Start")
    val timedResult = measureTimedValue {
        downloadCards(
            "https://api.scryfall.com/cards/search?q=t%3Abasic+unique%3Aprints&unique=cards&as=grid&order=set",
            0,
            DownloadResult.Empty
        )
    }
    val result = timedResult.value
    println("---------------------------------------")
    println("Elapsed: ${timedResult.duration}")
    println("Downloaded card count: ${result.downloadedCardList.size}")
    println("Downloaded card list:")
    println(result.downloadedCardList)
}

private fun downloadCards(url: String, page: Int, result: DownloadResult): DownloadResult {
    println("Downloading cards from page $page, via url: $url")
    val json = getPageJson(url)

    val cards = json["data"].asJsonArray

    val downloadedCardList = mutableListOf<String>()

    cards.forEach { card ->
        card as JsonObject
        val name = card["name"].toString().filter { it != '\"' }
        val set = card["set"].toString().filter { it != '\"' }
        val collectorNumber = card["collector_number"].toString().filter { it != '\"' }
        if (collectorNumber.find { !it.isDigit() && !it.isLetter() && it != '★' } != null) {
            throw Exception("Invalid card collector number: $collectorNumber")
        }
        val imageDownloadUrl = (card["image_uris"] as JsonObject)["png"].toString().trim('\"')

        val imageName = "$name-$collectorNumber-$set"
        if (Files.exists(Paths.get("out\\Lands\\$imageName.png"))) {
            System.err.println("Image already exists: $imageName")
        } else {
            URL(imageDownloadUrl).openStream().use { inputStream ->
                Files.copy(inputStream, Paths.get("out\\Lands\\$imageName.png"))
                downloadedCardList.add("$imageName.png")
            }
        }
        println("Stored image. Filename: $imageName, url: $imageDownloadUrl")
    }

    println("---------------------------------------")
    return if (json.has("next_page")) {
        downloadCards(
            url = json["next_page"].toString().trim('\"'),
            page = page + 1,
            result = result.copy(downloadedCardList = result.downloadedCardList + downloadedCardList)
        )
    } else {
        println("Finished")
        result
    }
}

private fun getPageJson(url: String): JsonObject {
    val con: HttpURLConnection = URL(url).openConnection() as HttpURLConnection
    con.requestMethod = "GET"

    con.doOutput = true

    val response = StringBuilder()
    BufferedReader(InputStreamReader(con.inputStream)).use { reader ->
        var line: String?
        while (reader.readLine().also { line = it } != null) {
            response.append(line)
        }
    }

    val gson = Gson()
    return gson.fromJson(response.toString(), JsonObject::class.java)
}

data class DownloadResult(
    val downloadedCardList: List<String>
) {
    companion object {
        val Empty = DownloadResult(emptyList())
    }
}