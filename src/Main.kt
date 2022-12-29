import com.google.gson.Gson
import com.google.gson.JsonObject
import java.io.BufferedReader
import java.io.File
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL
import java.nio.file.Files
import java.nio.file.Paths


fun main() {
    val file = File("out.txt")
    println("Hello")

    val json = getPageJson("https://api.scryfall.com/cards/search?q=t%3Abasic+unique%3Aprints+-is%3Afullart&unique=cards&as=grid&order=name")

    val cards = json["data"].asJsonArray

    cards.forEach { card ->
        card as JsonObject
        val name = card["name"].toString().filter { it != '\"' }
        val set = card["set"].toString().filter { it != '\"' }
        val collectorNumber = card["collector_number"].toString().filter { it != '\"' }
        if (collectorNumber.find { !it.isDigit() && !it.isLetter() } != null) {
            throw Exception("Invalid card collector number: $collectorNumber")
        }
        val imageDownloadUrl = (card["image_uris"] as JsonObject)["png"].toString().trim('\"')

        val imageName = "$name-$collectorNumber-$set"
//        URL(imageDownloadUrl).openStream().use { inputStream ->
//            Files.copy(inputStream, Paths.get("C:\\AndroidProjects\\ScryfallMTGDownloader\\out\\$imageName.png"))
//        }
        println("Stored image. Filename: $imageName, url: $imageDownloadUrl")
    }

    if (json.has("next_page")) {
        // Finished
    } else {
        // Finished
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