package com.superjet.notificationmonitor

import android.content.Context
import android.provider.Settings
import android.net.Uri
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL
import java.util.UUID

data class ApiResult(
    val ok: Boolean,
    val body: JSONObject = JSONObject(),
    val error: String = ""
)

object StaffApi {
    private const val TIMEOUT_CONNECT = 10000
    private const val TIMEOUT_READ = 20000

    fun deviceId(context: Context): String =
        Settings.Secure.getString(context.contentResolver, Settings.Secure.ANDROID_ID)
            ?.takeIf { it.isNotBlank() }
            ?: UUID.nameUUIDFromBytes(
                (context.packageName + android.os.Build.MODEL).toByteArray()
            ).toString()

    fun login(context: Context, username: String, password: String): ApiResult {
        val body = JSONObject()
        body.put("username", username.trim())
        body.put("password", password)
        body.put("device_id", deviceId(context))
        val r = request("POST", "/api/mobile/login", body.toString(), null)
        if (r.ok) {
            val employee = r.body.optJSONObject("employee") ?: JSONObject()
            SecureConfig.setToken(context, r.body.optString("token"))
            SecureConfig.saveEmployee(
                context,
                employee.optString("employee_id"),
                employee.optString("name"),
                employee.optString("role")
            )
        }
        return r
    }

    fun dashboard(context: Context): ApiResult =
        request("GET", "/api/mobile/dashboard", null, auth(context))

    fun approve(context: Context, operationId: String): ApiResult =
        request("POST", "/api/mobile/operations/" + operationId + "/approve", "{}", auth(context))

    fun reject(context: Context, operationId: String, reason: String): ApiResult {
        val body = JSONObject()
        body.put("reason", reason)
        return request("POST", "/api/mobile/operations/" + operationId + "/reject", body.toString(), auth(context))
    }

    fun uploadTicket(
        context: Context,
        operationId: String,
        uris: List<Uri>,
        finalBookingRef: String
    ): ApiResult {
        val token = SecureConfig.getToken(context)
        if (token.isBlank()) return ApiResult(false, error = "LOGIN_REQUIRED")
        if (uris.isEmpty()) return ApiResult(false, error = "TICKET_REQUIRED")

        val boundary = "----SuperJet" + UUID.randomUUID().toString()
        val connection = (URL(SecureConfig.getServerUrl() + "/api/mobile/operations/" + operationId + "/ticket")
            .openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            connectTimeout = TIMEOUT_CONNECT
            readTimeout = TIMEOUT_READ
            doOutput = true
            setRequestProperty("Authorization", "Bearer " + token)
            setRequestProperty("Accept", "application/json")
            setRequestProperty("Content-Type", "multipart/form-data; boundary=" + boundary)
        }

        return try {
            connection.outputStream.use { out ->
                writeField(out, boundary, "final_booking_ref", finalBookingRef)
                for ((index, uri) in uris.take(4).withIndex()) {
                    val mime = context.contentResolver.getType(uri) ?: "image/jpeg"
                    val name = "ticket_" + (index + 1) + "." + extensionForMime(mime)
                    out.write(("--" + boundary + "\r\n").toByteArray())
                    out.write(("Content-Disposition: form-data; name=\"ticket\"; filename=\"" + name + "\"\r\n").toByteArray())
                    out.write(("Content-Type: " + mime + "\r\n\r\n").toByteArray())
                    context.contentResolver.openInputStream(uri)?.use { input ->
                        val buffer = ByteArray(8192)
                        while (true) {
                            val read = input.read(buffer)
                            if (read <= 0) break
                            out.write(buffer, 0, read)
                        }
                    } ?: throw IllegalStateException("FILE_OPEN_FAILED")
                    out.write("\r\n".toByteArray())
                }
                out.write(("--" + boundary + "--\r\n").toByteArray())
            }
            readResponse(connection)
        } catch (e: Exception) {
            ApiResult(false, error = e.javaClass.simpleName + ":" + (e.message ?: "network error"))
        } finally {
            connection.disconnect()
        }
    }

    private fun writeField(out: java.io.OutputStream, boundary: String, name: String, value: String) {
        out.write(("--" + boundary + "\r\n").toByteArray())
        out.write(("Content-Disposition: form-data; name=\"" + name + "\"\r\n\r\n").toByteArray())
        out.write(value.toByteArray(Charsets.UTF_8))
        out.write("\r\n".toByteArray())
    }

    private fun extensionForMime(mime: String): String = when (mime.lowercase()) {
        "image/png" -> "png"
        "image/webp" -> "webp"
        "image/jpeg" -> "jpg"
        else -> "jpg"
    }

    private fun auth(context: Context): Map<String, String> =
        mapOf(
            "Authorization" to ("Bearer " + SecureConfig.getToken(context)),
            "X-SuperJet-Device-Id" to deviceId(context)
        )

    private fun request(
        method: String,
        path: String,
        body: String?,
        headers: Map<String, String>?
    ): ApiResult {
        val connection = (URL(SecureConfig.getServerUrl() + path).openConnection() as HttpURLConnection).apply {
            requestMethod = method
            connectTimeout = TIMEOUT_CONNECT
            readTimeout = TIMEOUT_READ
            headers?.forEach { pair -> setRequestProperty(pair.key, pair.value) }
            if (body != null) {
                doOutput = true
                setRequestProperty("Content-Type", "application/json; charset=UTF-8")
            }
        }
        return try {
            if (body != null) {
                connection.outputStream.use { it.write(body.toByteArray(Charsets.UTF_8)) }
            }
            readResponse(connection)
        } catch (e: Exception) {
            ApiResult(false, error = e.javaClass.simpleName + ":" + (e.message ?: "network error"))
        } finally {
            connection.disconnect()
        }
    }

    private fun readResponse(connection: HttpURLConnection): ApiResult {
        val code = connection.responseCode
        val stream = if (code in 200..299) connection.inputStream else connection.errorStream
        val raw = if (stream != null) {
            BufferedReader(InputStreamReader(stream, Charsets.UTF_8)).use { it.readText() }
        } else ""
        val body = runCatching { JSONObject(raw) }.getOrElse { JSONObject() }
        return if (code in 200..299) ApiResult(true, body)
        else ApiResult(false, body, "HTTP_" + code + ":" + body.optString("error", raw))
    }
}