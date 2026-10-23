package com.mhss.app.util

import javax.activation.DataHandler
import javax.mail.internet.MimeBodyPart
import javax.mail.util.ByteArrayDataSource
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.assertThrows
import org.junit.Test

class EmailImageSupportTest {
    @Test
    fun htmlEmailEscapesTextAndPlacesImagesAfterSeparator() {
        val body = buildEmailHtmlBody("<note & text>", 2, "Images follow", "Image")

        assertTrue(body.contains("&lt;note &amp; text&gt;"))
        assertTrue(body.indexOf("<hr") < body.indexOf("cid:image-0"))
        assertTrue(body.indexOf("cid:image-0") < body.indexOf("cid:image-1"))
    }

    @Test
    fun plainTextEmailKeepsNoteAndAddsImageSeparator() {
        val body = buildEmailTextBody("Note text", "Images are shown below.")

        assertTrue(body.startsWith("Note text"))
        assertTrue(body.contains(EMAIL_IMAGE_SEPARATOR))
        assertTrue(body.endsWith("Images are shown below."))
    }

    @Test
    fun imageContentTypeRejectsNonImageFiles() {
        val error = assertThrows(EmailAttachmentException::class.java) {
            requireImageContentType("text/plain", "notes.txt")
        }
        assertEquals(EmailAttachmentLimit.UNSUPPORTED_IMAGE, error.reason)
    }

    @Test
    fun imageContentTypeAcceptsImageFiles() {
        assertEquals("image/jpeg", requireImageContentType("image/jpeg", "photo.jpg"))
        assertEquals("image/png", requireImageContentType("Image/PNG", "photo.png"))
        assertEquals("image/jpeg", requireImageContentType(null, "photo.jpg"))
        assertEquals("image/png", requireImageContentType("application/octet-stream", "photo.png"))
        assertEquals(EmailAttachmentLimit.UNSUPPORTED_IMAGE, assertThrows(EmailAttachmentException::class.java) {
            requireImageContentType("text/plain", "photo.png")
        }.reason)
    }

    @Test
    fun attachmentFileNamesAreSanitizedAndHaveAFallback() {
        assertEquals("photo_one.png", sanitizeEmailAttachmentFileName("photo\r\none.png", 0))
        assertEquals("image-2", sanitizeEmailAttachmentFileName(null, 1))
        assertEquals("_photo_name.png", sanitizeEmailAttachmentFileName("../photo/name.png", 0))
        assertEquals("_hidden", sanitizeEmailAttachmentFileName(".hidden", 0))
        assertEquals("photo_name.png", sanitizeEmailAttachmentFileName("photo\"name.png", 0))
        assertEquals("photo_name_.png", sanitizeEmailAttachmentFileName("photo:<name>|?.png", 0))
        assertEquals("_", sanitizeEmailAttachmentFileName("..", 0))
        assertTrue(sanitizeEmailAttachmentFileName("a".repeat(300), 0).length <= 255)
    }

    @Test
    fun imageCountIsLimited() {
        assertThrows(EmailAttachmentException::class.java) {
            validateImageCount(MAX_EMAIL_IMAGE_COUNT + 1)
        }.also { assertEquals(EmailAttachmentLimit.TOO_MANY_IMAGES, it.reason) }
    }

    @Test
    fun imagesUseInlineLayoutThatCanWrapAcrossAvailableWidth() {
        val body = buildEmailHtmlBody("Note", 2, "Images follow", "Image")

        assertTrue(body.contains("display:inline-block"))
        assertTrue(body.contains("width:160px;max-width:100%"))
        assertTrue(body.contains("margin:4px"))
    }

    @Test
    fun relatedMultipartPreservesInlineImageOrderAndContentIds() {
        val firstImage = imagePart("image-0")
        val secondImage = imagePart("image-1")

        val multipart = buildRelatedEmailMultipart(
            messageBody = "Note",
            imageSectionLabel = "Images are attached",
            imageAltTextPrefix = "Image",
            imageParts = listOf(firstImage, secondImage)
        )

        assertEquals(3, multipart.count)
        assertEquals("<image-0>", multipart.getBodyPart(1).getHeader("Content-ID").single())
        assertEquals("<image-1>", multipart.getBodyPart(2).getHeader("Content-ID").single())
        val alternative = multipart.getBodyPart(0).content as javax.mail.internet.MimeMultipart
        assertTrue(alternative.getBodyPart(0).content.toString().contains("Note"))
        assertTrue(alternative.getBodyPart(1).content.toString().contains("cid:image-0"))
        assertTrue(alternative.getBodyPart(1).content.toString().contains("alt=\"Image 1\""))
        assertEquals("image-0", emailImageContentId(0))
    }

    private fun imagePart(contentId: String) = MimeBodyPart().apply {
        dataHandler = DataHandler(ByteArrayDataSource(byteArrayOf(1), "image/jpeg"))
        disposition = MimeBodyPart.INLINE
        setHeader("Content-ID", "<$contentId>")
    }
}
