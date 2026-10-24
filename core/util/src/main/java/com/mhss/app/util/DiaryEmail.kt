package com.mhss.app.util

import javax.mail.internet.MimeBodyPart
import javax.mail.internet.MimeMultipart

const val MAX_DIARY_EMAIL_IMAGE_COUNT = 30

/** Ett dagboksinlägg exakt som det visas på dagbokens framsida, men med hela innehållet. */
data class DiaryMailEntry(
    val title: String,
    val content: String,
    val dateTimeText: String,
    val moodLabel: String,
    val moodColorHex: String,
    val imagePaths: List<String>
)

internal fun diaryImageContentId(entryIndex: Int, imageIndex: Int): String =
    "diary-$entryIndex-$imageIndex"

internal fun buildDiaryEmailTextBody(entries: List<DiaryMailEntry>, imageCounts: List<Int>): String =
    entries.mapIndexed { index, entry ->
        buildString {
            append(entry.title.trim()).append("\n\n")
            if (entry.content.isNotBlank()) append(entry.content.trimEnd()).append("\n\n")
            if (imageCounts.getOrElse(index) { 0 } > 0) {
                append("[Images: ${imageCounts[index]}]\n")
            }
            append("\t\t\t").append(entry.dateTimeText).append("\n")
            append(EMAIL_IMAGE_SEPARATOR)
        }
    }.joinToString("\n\n")

internal fun buildDiaryEmailHtmlBody(
    entries: List<DiaryMailEntry>,
    imageCounts: List<Int>,
    imageAltTextPrefix: String
): String {
    val blocks = entries.mapIndexed { entryIndex, entry ->
        val images = (0 until imageCounts.getOrElse(entryIndex) { 0 }).joinToString("\n") { imageIndex ->
            """<a href="cid:${diaryImageContentId(entryIndex, imageIndex)}"><img src="cid:${diaryImageContentId(entryIndex, imageIndex)}" alt="${imageAltTextPrefix.htmlEscape()} ${imageIndex + 1}" style="display:inline-block;vertical-align:top;width:160px;max-width:100%;height:auto;margin:4px 4px 4px 0;border-radius:8px;"></a>"""
        }
        val color = entry.moodColorHex.takeIf { it.matches(Regex("#[0-9A-Fa-f]{6}")) } ?: "#777777"
        val content = if (entry.content.isBlank()) "" else
            """<div style="white-space:pre-wrap;margin-top:12px;">${entry.content.htmlEscape()}</div>"""
        val imageBlock = if (images.isEmpty()) "" else """<div style="margin-top:12px;">$images</div>"""
        """
        <div style="background:#EEEBF1;border-radius:20px;padding:12px;">
          <div style="font-size:16px;font-weight:bold;"><span style="color:$color;">&#9679; ${entry.moodLabel.htmlEscape()}</span> &nbsp; ${entry.title.htmlEscape()}</div>
          $content
          $imageBlock
          <div style="text-align:right;font-weight:bold;margin-top:16px;">${entry.dateTimeText.htmlEscape()}</div>
        </div>
        <hr style="border:0;border-top:2px solid #777;margin:20px 0 16px 0;">
        """.trimIndent()
    }.joinToString("\n")
    return """
        <!doctype html>
        <html>
        <body style="font-family:sans-serif;">
        $blocks
        </body>
        </html>
    """.trimIndent()
}

internal fun buildDiaryEmailMultipart(
    entries: List<DiaryMailEntry>,
    imageCounts: List<Int>,
    imageAltTextPrefix: String,
    imageParts: List<MimeBodyPart>
): MimeMultipart {
    val related = MimeMultipart("related")
    val alternative = MimeMultipart("alternative")
    alternative.addBodyPart(MimeBodyPart().apply {
        setText(buildDiaryEmailTextBody(entries, imageCounts), "UTF-8")
    })
    alternative.addBodyPart(MimeBodyPart().apply {
        setContent(
            buildDiaryEmailHtmlBody(entries, imageCounts, imageAltTextPrefix),
            "text/html; charset=UTF-8"
        )
    })
    related.addBodyPart(MimeBodyPart().apply { setContent(alternative) })
    imageParts.forEach(related::addBodyPart)
    return related
}
