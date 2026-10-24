package com.mhss.app.util

import android.content.Context
import android.net.Uri
import android.util.Log
import java.io.File
import javax.activation.DataSource
import javax.activation.DataHandler
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.withContext
import java.io.FileNotFoundException
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.Properties
import java.util.TimeZone
import java.util.UUID
import javax.mail.*
import javax.mail.internet.InternetAddress
import javax.mail.internet.MimeBodyPart
import javax.mail.internet.MimeMessage

object MailSender {
    private const val TAG = "MailSender"

    // 🔐 FYLL I dina riktiga värden (lägg helst i BuildConfig/secure storage)
    private const val FROM_EMAIL = "u5362781704@gmail.com"
    private const val APP_PASSWORD = "itkvpojzplpkksku" // App-lösenordet

    // 📬 Gmail SMTP
    private const val SMTP_HOST = "smtp.gmail.com"
    private const val SMTP_PORT = "587"

    suspend fun sendMail(
        context: Context,
        toEmail: String,
        subject: String,
        messageBody: String,
        imageSectionLabel: String,
        imageAltTextPrefix: String,
        imageUris: List<Uri> = emptyList()
    ): Result<Unit> = withContext(Dispatchers.IO) {
        val compressedImageFiles = mutableListOf<File>()
        try {
            val imageMetadata = if (imageUris.isNotEmpty()) {
                validateEmailImageUris(context, imageUris)
            } else {
                emptyList()
            }
            val session = smtpSession()

            val message = anonymousMessage(session).apply {
                setFrom(InternetAddress(FROM_EMAIL, "Vård & Omsorg", "UTF-8"))
                setRecipients(Message.RecipientType.TO, InternetAddress.parse(toEmail))
                setSubject(subject, "UTF-8")
                if (imageUris.isEmpty()) {
                    setText(messageBody, "UTF-8")
                } else {
                    val imageParts = imageUris.mapIndexed { index, uri ->
                        val metadata = imageMetadata[index]
                        val displayName = metadata.displayName
                        requireImageContentType(
                            context.contentResolver.getType(uri),
                            displayName ?: uri.lastPathSegment.orEmpty()
                        )
                        val clean = stripImageMetadata(
                            { context.contentResolver.openInputStream(uri) },
                            context.cacheDir
                        )
                        compressedImageFiles.add(clean.file)
                        // Generiskt filnamn så originalnamnet (t.ex. IMG_datum) inte avslöjas
                        val attachmentName = "image-${index + 1}.${clean.extension}"
                        val contentId = emailImageContentId(index)
                        MimeBodyPart().apply {
                            dataHandler = DataHandler(
                                FileEmailImageDataSource(clean.file, clean.contentType, attachmentName)
                            )
                            fileName = attachmentName
                            disposition = MimeBodyPart.INLINE
                            setHeader("Content-ID", "<$contentId>")
                        }
                    }
                    setContent(
                        buildRelatedEmailMultipart(
                            messageBody,
                            imageSectionLabel,
                            imageAltTextPrefix,
                            imageParts
                        )
                    )
                }
            }

            Transport.send(message)
            Result.success(Unit)

        } catch (e: AuthenticationFailedException) {
            e.printStackTrace()
            Result.failure(IllegalStateException("Autentisering misslyckades mot SMTP (kontrollera app-lösenordet).", e))

        } catch (e: NoSuchProviderException) {
            e.printStackTrace()
            Result.failure(IllegalStateException("SMTP-provider saknas (lägg in ProGuard/R8 keep-reglerna).", e))

        } catch (e: MessagingException) {
            Log.e(TAG, "Email messaging failed (${e.javaClass.simpleName})")
            Result.failure(e.findAttachmentLimitException() ?: e)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "Email sending failed (${e.javaClass.simpleName})")
            Result.failure(e.findAttachmentLimitException() ?: e)
        } finally {
            compressedImageFiles.forEach { it.delete() }
        }
    }

    /** Meddelande utan X-Mailer, enhetens värdnamn i Message-ID eller lokal tidszon i Date. */
    private fun anonymousMessage(session: Session): MimeMessage = object : MimeMessage(session) {
        override fun updateMessageID() {
            setHeader("Message-ID", "<${UUID.randomUUID()}@${FROM_EMAIL.substringAfter('@')}>")
        }

        override fun updateHeaders() {
            super.updateHeaders()
            removeHeader("X-Mailer")
            removeHeader("User-Agent")
            val format = SimpleDateFormat("EEE, d MMM yyyy HH:mm:ss '+0000'", Locale.US)
            format.timeZone = TimeZone.getTimeZone("UTC")
            setHeader("Date", format.format(Date()))
        }
    }

    private fun smtpSession(): Session {
        // TLS/STARTTLS och TLS1.2 tvingas
        val props = Properties().apply {
            put("mail.smtp.auth", "true")
            put("mail.smtp.starttls.enable", "true")
            put("mail.smtp.starttls.required", "true")
            put("mail.smtp.ssl.protocols", "TLSv1.2")
            put("mail.smtp.host", SMTP_HOST)
            put("mail.smtp.port", SMTP_PORT)
        }
        return Session.getInstance(props, object : Authenticator() {
            override fun getPasswordAuthentication(): PasswordAuthentication =
                PasswordAuthentication(FROM_EMAIL, APP_PASSWORD)
        })
    }

    /** Skickar flera dagboksinlägg i ett mejl, ett block per inlägg med egna bilder. */
    suspend fun sendDiaryMail(
        context: Context,
        toEmail: String,
        subject: String,
        entries: List<DiaryMailEntry>,
        imageAltTextPrefix: String
    ): Result<Unit> = withContext(Dispatchers.IO) {
        val cleanFiles = mutableListOf<File>()
        try {
            val imageFiles = entries.map { entry ->
                entry.imagePaths.map(::File).filter { it.isFile }
            }
            if (imageFiles.sumOf { it.size } > MAX_DIARY_EMAIL_IMAGE_COUNT) {
                throw EmailAttachmentException(EmailAttachmentLimit.TOO_MANY_IMAGES)
            }
            val message = anonymousMessage(smtpSession()).apply {
                setFrom(InternetAddress(FROM_EMAIL, "Vård & Omsorg", "UTF-8"))
                setRecipients(Message.RecipientType.TO, InternetAddress.parse(toEmail))
                setSubject(subject, "UTF-8")
                val imageParts = imageFiles.flatMapIndexed { entryIndex, files ->
                    files.mapIndexed { imageIndex, file ->
                        // Skicka alltid en omkodad kopia utan metadata, även för äldre sparade bilder
                        val clean = stripImageMetadata({ file.inputStream() }, context.cacheDir)
                        cleanFiles.add(clean.file)
                        val name = "image-${entryIndex + 1}-${imageIndex + 1}.${clean.extension}"
                        MimeBodyPart().apply {
                            dataHandler = DataHandler(
                                FileEmailImageDataSource(clean.file, clean.contentType, name)
                            )
                            fileName = name
                            disposition = MimeBodyPart.INLINE
                            setHeader("Content-ID", "<${diaryImageContentId(entryIndex, imageIndex)}>")
                        }
                    }
                }
                setContent(buildDiaryEmailMultipart(entries, imageFiles.map { it.size }, imageAltTextPrefix, imageParts))
            }
            Transport.send(message)
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: AuthenticationFailedException) {
            Result.failure(IllegalStateException("Autentisering misslyckades mot SMTP (kontrollera app-lösenordet).", e))
        } catch (e: Exception) {
            Log.e(TAG, "Diary email sending failed (${e.javaClass.simpleName})")
            Result.failure(e.findAttachmentLimitException() ?: e)
        } finally {
            cleanFiles.forEach { it.delete() }
        }
    }

    private class UriDataSource(
        private val context: Context,
        private val uri: Uri,
        private val contentType: String,
        private val attachmentName: String
    ) : DataSource {
        override fun getInputStream(): InputStream =
            context.contentResolver.openInputStream(uri)
                ?: throw FileNotFoundException("Unable to open image: $attachmentName")

        override fun getOutputStream(): OutputStream =
            throw IOException("Writing to an image attachment is not supported.")

        override fun getContentType(): String = contentType

        override fun getName(): String = attachmentName
    }

    private class FileEmailImageDataSource(
        private val file: File,
        private val contentType: String,
        private val attachmentName: String
    ) : DataSource {
        override fun getInputStream(): InputStream = file.inputStream()

        override fun getOutputStream(): OutputStream =
            throw IOException("Writing to an image attachment is not supported.")

        override fun getContentType(): String = contentType

        override fun getName(): String = attachmentName
    }

    private fun Throwable.findAttachmentLimitException(): EmailAttachmentException? {
        var current: Throwable? = this
        while (current != null) {
            if (current is EmailAttachmentException) return current
            if (current.cause === current) break
            current = current.cause
        }
        return null
    }
}
