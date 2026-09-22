package com.oble.ideacapture.whatsapp

import com.oble.core.WhatsAppFormatter
import com.oble.ideacapture.data.SettingsRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.addJsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException

/**
 * Official WhatsApp Business Platform Cloud API client (Meta Graph API).
 *
 * Requirements (one-time, done by you in Meta Business Manager):
 *  1. A WhatsApp Business Account with a registered sender phone number
 *     -> its "Phone number ID".
 *  2. A permanent System User access token with `whatsapp_business_messaging`.
 *  3. A message template approved by Meta with ONE body variable, e.g.
 *       name: oble_note   body: "New note from OBLE: {{1}}"
 *     Business-initiated messages outside a 24h customer-service window MUST use
 *     an approved template. Plain "text" messages only work within 24 hours of the
 *     recipient last messaging your business number.
 *  4. The destination number must be able to receive messages from that business
 *     number (on test numbers it must be added as an allowed recipient).
 */
class CloudApiClient(
    private val http: OkHttpClient,
    private val settings: SettingsRepository,
) {
    class Result(val success: Boolean, val retryable: Boolean, val error: String? = null)

    suspend fun send(message: String): Result = withContext(Dispatchers.IO) {
        val s = settings.current
        val token = settings.waCloudToken()
        val to = WhatsAppFormatter.normalizePhone(s.destinationPhone)
        if (token.isNullOrBlank() || s.waPhoneNumberId.isBlank() || to == null) {
            return@withContext Result(false, retryable = false, error = "WhatsApp Cloud API is not fully configured")
        }
        val body = buildJsonObject {
            put("messaging_product", "whatsapp")
            put("recipient_type", "individual")
            put("to", to)
            if (s.waUseTemplate) {
                put("type", "template")
                putJsonObject("template") {
                    put("name", s.waTemplateName.trim())
                    putJsonObject("language") { put("code", s.waTemplateLanguage.trim().ifBlank { "en" }) }
                    putJsonArray("components") {
                        addJsonObject {
                            put("type", "body")
                            putJsonArray("parameters") {
                                addJsonObject {
                                    put("type", "text")
                                    put("text", WhatsAppFormatter.flattenForTemplate(message))
                                }
                            }
                        }
                    }
                }
            } else {
                put("type", "text")
                putJsonObject("text") {
                    put("preview_url", false)
                    put("body", message.take(4096))
                }
            }
        }
        val request = Request.Builder()
            .url("https://graph.facebook.com/$GRAPH_VERSION/${s.waPhoneNumberId.trim()}/messages")
            .header("Authorization", "Bearer $token")
            .post(body.toString().toRequestBody("application/json".toMediaType()))
            .build()
        try {
            http.newCall(request).execute().use { r ->
                if (r.isSuccessful) Result(true, false)
                else Result(
                    false,
                    retryable = r.code == 429 || r.code >= 500,
                    error = "WhatsApp API ${r.code}: ${r.body?.string().orEmpty().take(200)}",
                )
            }
        } catch (e: IOException) {
            Result(false, retryable = true, error = "Network unavailable")
        }
    }

    companion object {
        const val GRAPH_VERSION = "v21.0"
    }
}
