import com.google.gson.Gson
import com.google.gson.JsonArray
import com.google.gson.JsonObject
import java.io.BufferedReader
import java.io.File
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL
import java.nio.file.Files
import java.nio.file.Paths
import kotlin.time.ExperimentalTime
import kotlin.time.measureTimedValue

private const val LANDS_FOLDER_PATH = "out\\Lands"
private const val NEW_LANDS_FOLDER_PATH = "out\\Lands\\new"

// Search queries to find lands:
// t:basic unique:prints
//
// To find the card in scryfall.com from the image name, you can use this search syntax: "set:abc cn:123"
//
@OptIn(ExperimentalTime::class)
fun main() {
    println("Start")
    val timedResult = measureTimedValue {
        val downloadLocation = createLandsFolder()
        downloadCards(
            downloadLocation = downloadLocation,
            url = "https://api.scryfall.com/cards/search?q=t%3Abasic+unique%3Aprints&unique=cards&as=grid&order=set",
            page = 0,
            result = DownloadResult.Empty
        )
    }
    val result = timedResult.value
    println("---------------------------------------")
    println("Elapsed: ${timedResult.duration}")
    println("Downloaded card count: ${result.downloadedCardList.size}")
    println("Downloaded card list:")
    println(result.downloadedCardList)
}

private fun downloadCards(
    downloadLocation: DownloadLocation,
    url: String,
    page: Int,
    result: DownloadResult
): DownloadResult {
    println("Downloading cards from page $page, via url: $url")
    val json = getPageJson(url)

    val cards = json["data"].asJsonArray

    val downloadedCardList = mutableListOf<String>()

    cards.forEach { card ->
        card as JsonObject
        val name = card["name"].toString().filter { it != '\"' }
        val set = card["set"].toString().filter { it != '\"' }
        val collectorNumber = card["collector_number"].toString().filter { it != '\"' }
        val imageStatus = card["image_status"].toString().filter { it != '\"' }
        if (imageStatus == "placeholder") {
            // Example: https://scryfall.com/card/ody/312/ja/%E8%A6%8B%E6%8D%A8%E3%81%A6%E3%82%89%E3%82%8C%E3%81%9F%E5%89%8D%E5%93%A8%E5%9C%B0
            System.err.println("Image is a placeholder: $url")
        } else {
            // '-' Adds support for cards like this https://scryfall.com/card/plst/JMP-50/island
            if (collectorNumber.find { !it.isDigit() && !it.isLetter() && it != '★' && it != '-' } != null) {
                error("Invalid card collector number: $collectorNumber")
            }
            val imageName = "$name-$collectorNumber-$set"

            val imageUris = card["image_uris"] as? JsonObject
            if (imageUris == null) {
                downloadMultipleFaceCards(downloadLocation, card, imageName, collectorNumber, set, downloadedCardList)
            } else {
                downloadImage(downloadLocation, imageUris, imageName, downloadedCardList)
            }
        }
    }

    println("---------------------------------------")
    return if (json.has("next_page")) {
        downloadCards(
            downloadLocation = downloadLocation,
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

private fun downloadMultipleFaceCards(
    downloadLocation: DownloadLocation,
    card: JsonObject,
    imageName: String,
    collectorNumber: String,
    set: String,
    downloadedCardList: MutableList<String>
) {
    // Some cards have 2 faces, back and front, thus `image_uris` exists only in those inner elements. Example: "Plains // Plains-21-rex".
    val cardFaces = card["card_faces"] as? JsonArray
    if (cardFaces == null) {
        error("Card does not contain \"image_uris\" object AND does not contain \"card_faces\". Investigate! imageName: $imageName")
    } else {
        if (cardFaces.size() != 2) {
            error("Card does not contain \"image_uris\" object but contains ${cardFaces.size()} \"card_faces\". Very unusual. Investigate! imageName: $imageName")
        }
        cardFaces.forEachIndexed { index, cardFace ->
            cardFace as JsonObject
            val cardFaceImageUris = cardFace["image_uris"] as? JsonObject
            if (cardFaceImageUris == null) {
                error("Card does not contain \"image_uris\" object and contains \"card_faces\" BUT does not contains inner cardFace \"image_uris\". Investigate! imageName: $imageName")
            } else {
                // The "upper" card name is not great since it looks like "Plains // Plains",
                //  so we use the face name.
                val cardFaceName = cardFace["name"].toString().filter { it != '\"' }
                val cardFaceImageName = "$cardFaceName-$collectorNumber-$set-face-${index + 1}"
                downloadImage(downloadLocation, cardFaceImageUris, cardFaceImageName, downloadedCardList)
            }
        }
    }
}

private fun downloadImage(
    downloadLocation: DownloadLocation,
    imageUris: JsonObject,
    imageName: String,
    downloadedCardList: MutableList<String>
) {
    val imageDownloadUrl = imageUris["png"].toString().trim('\"')

    val folder = when (downloadLocation) {
        DownloadLocation.Initial -> LANDS_FOLDER_PATH
        DownloadLocation.Update -> NEW_LANDS_FOLDER_PATH
    }
    if (Files.exists(Paths.get("$LANDS_FOLDER_PATH\\$imageName.png")) ||
        Files.exists(Paths.get("$NEW_LANDS_FOLDER_PATH\\$imageName.png"))
    ) {
        System.err.println("Image already exists: $imageName")
    } else {
        URL(imageDownloadUrl).openStream().use { inputStream ->
            Files.copy(inputStream, Paths.get("$folder\\$imageName.png"))
            downloadedCardList.add("$imageName.png")
            println("Stored image. Filename: $imageName, url: $imageDownloadUrl")
        }
    }
}

private fun createLandsFolder(): DownloadLocation {
    val landsFolder = File(LANDS_FOLDER_PATH)
    return if (landsFolder.exists()) {
        if (landsFolder.list()?.isNotEmpty() == true) {
            val newLandsFolder = File(NEW_LANDS_FOLDER_PATH)
            if (!newLandsFolder.exists()) {
                newLandsFolder.mkdirs()
            }
            DownloadLocation.Update
        } else {
            DownloadLocation.Initial
        }
    } else {
        landsFolder.mkdirs()
        DownloadLocation.Initial
    }
}

enum class DownloadLocation {
    Initial,
    Update
}

data class DownloadResult(
    val downloadedCardList: List<String>
) {
    companion object {
        val Empty = DownloadResult(emptyList())
    }
}