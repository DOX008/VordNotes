package com.mhss.app.util

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import javax.mail.internet.MimeBodyPart
import javax.mail.internet.MimeMultipart
import java.util.Locale

const val MAX_EMAIL_IMAGE_COUNT = 10
internal const val EMAIL_IMAGE_SEPARATOR = "----------------------------------------"
private val attachmentFileNameUnsafeCharacters = Regex("[^\\p{L}\\p{N}._ -]+")
private val leadingDots = Regex("^\\.+")

enum class EmailAttachmentLimit {
    TOO_MANY_IMAGES,
    UNSUPPORTED_IMAGE
}

class EmailAttachmentException(val reason: EmailAttachmentLimit) : IllegalArgumentException(
    when (reason) {
        EmailAttachmentLimit.TOO_MANY_IMAGES -> "Too many images selected."
        EmailAttachmentLimit.UNSUPPORTED_IMAGE -> "A selected file is not an image."
    }
)

fun getUriDisplayName(context: Context, uri: Uri): String? =
    context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
        ?.use { cursor ->
            val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
            if (nameIndex >= 0 && cursor.moveToFirst()) cursor.getString(nameIndex) else null
        }

internal data class EmailImageMetadata(
    val displayName: String?
)

internal fun validateEmailImageUris(context: Context, uris: List<Uri>): List<EmailImageMetadata> {
    validateImageCount(uris.size)
    return uris.map { uri -> EmailImageMetadata(getUriDisplayName(context, uri)) }
}

internal fun validateImageCount(count: Int) {
    if (count > MAX_EMAIL_IMAGE_COUNT) {
        throw EmailAttachmentException(EmailAttachmentLimit.TOO_MANY_IMAGES)
    }
}

internal fun requireImageContentType(contentType: String?, displayName: String): String {
    val normalizedType = contentType?.substringBefore(';')?.trim()?.lowercase(Locale.ROOT)
    if (normalizedType != null && normalizedType.startsWith("image/") && normalizedType.length > 6) {
        return normalizedType
    }
    val isGenericType = normalizedType == null ||
        normalizedType == "application/octet-stream" ||
        normalizedType == "binary/octet-stream"
    if (!isGenericType) {
        throw EmailAttachmentException(EmailAttachmentLimit.UNSUPPORTED_IMAGE)
    }
    val extension = displayName.substringAfterLast('.', "").lowercase(Locale.ROOT)
    return when (extension) {
        "jpg", "jpeg" -> "image/jpeg"
        "png" -> "image/png"
        "gif" -> "image/gif"
        "webp" -> "image/webp"
        "bmp" -> "image/bmp"
        "heic" -> "image/heic"
        "heif" -> "image/heif"
        "avif" -> "image/avif"
        "tif", "tiff" -> "image/tiff"
        else -> throw EmailAttachmentException(EmailAttachmentLimit.UNSUPPORTED_IMAGE)
    }
}

internal fun sanitizeEmailAttachmentFileName(displayName: String?, index: Int): String =
    displayName.orEmpty()
        .replace(attachmentFileNameUnsafeCharacters, "_")
        .trim()
        .replace(leadingDots, "_")
        .take(255)
        .takeIf { it.isNotBlank() }
        ?: "image-${index + 1}"

internal fun emailImageContentId(index: Int): String = "image-$index"

internal fun buildEmailTextBody(messageBody: String, imageSectionLabel: String): String =
    "${messageBody.trimEnd()}\n\n$EMAIL_IMAGE_SEPARATOR\n$imageSectionLabel"

internal fun buildRelatedEmailMultipart(
    messageBody: String,
    imageSectionLabel: String,
    imageAltTextPrefix: String,
    imageParts: List<MimeBodyPart>
): MimeMultipart {
    val related = MimeMultipart("related")
    val alternative = MimeMultipart("alternative")
    alternative.addBodyPart(MimeBodyPart().apply {
        setText(buildEmailTextBody(messageBody, imageSectionLabel), "UTF-8")
    })
    alternative.addBodyPart(MimeBodyPart().apply {
        setContent(
            buildEmailHtmlBody(messageBody, imageParts.size, imageSectionLabel, imageAltTextPrefix),
            "text/html; charset=UTF-8"
        )
    })
    related.addBodyPart(MimeBodyPart().apply { setContent(alternative) })
    imageParts.forEach(related::addBodyPart)
    return related
}

internal fun buildEmailHtmlBody(
    messageBody: String,
    imageCount: Int,
    imageSectionLabel: String,
    imageAltTextPrefix: String
): String {
    val imageTags = (0 until imageCount).joinToString("\n") { index ->
        """<img src="cid:${emailImageContentId(index)}" alt="${imageAltTextPrefix.htmlEscape()} ${index + 1}" style="display:inline-block;vertical-align:top;width:160px;max-width:100%;height:auto;margin:4px;">"""
    }
    return """
        <!doctype html>
        <html>
        <body style="font-family:sans-serif;">
          <div style="white-space:pre-wrap;">${messageBody.htmlEscape()}</div>
          <hr style="border:0;border-top:2px solid #777;margin:20px 0;">
          <p>${imageSectionLabel.htmlEscape()}</p>
          <div>$imageTags</div>
        </body>
        </html>
    """.trimIndent()
}

private fun String.htmlEscape(): String = replace("&", "&amp;")
    .replace("<", "&lt;")
    .replace(">", "&gt;")
    .replace("\"", "&quot;")
    .replace("'", "&#39;")
