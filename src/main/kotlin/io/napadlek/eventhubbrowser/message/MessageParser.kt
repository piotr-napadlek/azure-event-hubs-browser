package io.napadlek.eventhubbrowser.message

import com.fasterxml.jackson.core.JsonParseException
import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import com.microsoft.azure.eventhubs.EventData
import org.springframework.stereotype.Component
import java.nio.charset.Charset
import java.nio.charset.StandardCharsets
import java.nio.charset.UnsupportedCharsetException
import java.util.*
import java.util.zip.GZIPInputStream
import java.util.zip.ZipException

@Component
class MessageParser(val mapper: ObjectMapper) {

    internal fun readEventContent(data: EventData, includedBodyFormats: Set<BodyFormat>): BodyRepresentation {
        if (data.bytes == null) {
            return BodyRepresentation(null, null, null, null)
        }
        val bodyBase64 = Base64.getEncoder().encode(data.bytes).toString(StandardCharsets.UTF_8)
        val bodyBytes = if (data.properties["ContentEncoding"] == "gzip") try {
            GZIPInputStream(data.bytes.inputStream()).readAllBytes()
        } catch (e: ZipException) {
            data.bytes
        } else data.bytes
        val detectedCharset: Charset? = try {
            detectBodyCharset(bodyBytes)
        } catch (ex: UnsupportedCharsetException) {
            null
        }
        val bodyString: String? = try {
            detectedCharset?.let { bodyBytes.toString(it) } ?: bodyBytes.toString(StandardCharsets.UTF_8)
        } catch (ex: Exception) {
            null
        }
        val bodyJson = try {
            mapper.readTree(bodyString)
        } catch (ex: JsonParseException) {
            null
        }
        return BodyRepresentation(
                if (includedBodyFormats.contains(BodyFormat.BASE64)) bodyBase64 else null,
                if (includedBodyFormats.contains(BodyFormat.STRING)) bodyString else null,
                if (includedBodyFormats.contains(BodyFormat.JSON)) bodyJson else null,
                detectedCharset)
    }
}

internal data class BodyRepresentation(
        val base64: String? = null,
        val string: String? = null,
        val json: JsonNode? = null,
        val charset: Charset? = null
)

enum class BodyFormat {
    JSON,
    BASE64,
    STRING
}
