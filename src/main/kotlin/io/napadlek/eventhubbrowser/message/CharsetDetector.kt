package io.napadlek.eventhubbrowser.message

import org.mozilla.universalchardet.UniversalDetector
import java.nio.charset.Charset

fun detectBodyCharset(body: ByteArray): Charset? {
    val bodyStream = body.inputStream()
    val charsetDetector = UniversalDetector(null)
    var nread: Int
    val buf = ByteArray(4096)

    while (bodyStream.available() > 0 && !charsetDetector.isDone) {
        nread = bodyStream.read(buf)
        charsetDetector.handleData(buf, 0, nread)
    }
    charsetDetector.dataEnd()
    return charsetDetector.detectedCharset?.let { Charset.forName(it) }
}
