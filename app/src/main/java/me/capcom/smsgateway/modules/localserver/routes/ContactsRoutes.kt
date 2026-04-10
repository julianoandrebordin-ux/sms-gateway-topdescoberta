package me.capcom.smsgateway.modules.localserver.routes

import android.content.Context
import android.provider.ContactsContract
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.call
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import me.capcom.smsgateway.modules.localserver.auth.AuthScopes
import me.capcom.smsgateway.modules.localserver.auth.requireScope

data class Contact(
    val name: String,
    val phone: String,
    val email: String? = null
)

class ContactsRoutes(
    private val context: Context,
) {
    fun register(routing: Route) {
        routing.apply {
            contactsRoutes()
        }
    }

    private fun Route.contactsRoutes() {
        // GET /contacts - Returns all contacts from the device
        get {
            if (!requireScope(AuthScopes.MessagesRead)) return@get

            val limit = call.request.queryParameters["limit"]?.toIntOrNull() ?: 5000
            val offset = call.request.queryParameters["offset"]?.toIntOrNull() ?: 0
            val search = call.request.queryParameters["search"]

            try {
                val contacts = getContacts(limit, offset, search)
                call.response.headers.append("X-Total-Count", getTotalContacts(search).toString())
                call.respond(contacts)
            } catch (e: SecurityException) {
                call.respond(
                    HttpStatusCode.Forbidden,
                    mapOf("message" to "READ_CONTACTS permission not granted")
                )
            } catch (e: Throwable) {
                call.respond(
                    HttpStatusCode.InternalServerError,
                    mapOf("message" to "Failed to read contacts: ${e.message}")
                )
            }
        }

        // GET /contacts/count - Returns total number of contacts
        get("count") {
            if (!requireScope(AuthScopes.MessagesRead)) return@get

            try {
                val total = getTotalContacts(null)
                call.respond(mapOf("total" to total))
            } catch (e: SecurityException) {
                call.respond(
                    HttpStatusCode.Forbidden,
                    mapOf("message" to "READ_CONTACTS permission not granted")
                )
            }
        }
    }

    private fun getContacts(limit: Int, offset: Int, search: String?): List<Contact> {
        val contacts = mutableListOf<Contact>()

        val projection = arrayOf(
            ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME,
            ContactsContract.CommonDataKinds.Phone.NUMBER,
        )

        val selection = if (search != null) {
            "${ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME} LIKE ? OR ${ContactsContract.CommonDataKinds.Phone.NUMBER} LIKE ?"
        } else null

        val selectionArgs = if (search != null) {
            arrayOf("%$search%", "%$search%")
        } else null

        val sortOrder = "${ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME} ASC LIMIT $limit OFFSET $offset"

        val cursor = context.contentResolver.query(
            ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
            projection,
            selection,
            selectionArgs,
            sortOrder
        )

        cursor?.use {
            val nameIndex = it.getColumnIndex(ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME)
            val phoneIndex = it.getColumnIndex(ContactsContract.CommonDataKinds.Phone.NUMBER)

            while (it.moveToNext()) {
                val name = it.getString(nameIndex) ?: ""
                val phone = it.getString(phoneIndex) ?: ""

                if (phone.isNotBlank()) {
                    // Clean phone number: remove spaces, dashes, parentheses
                    val cleanPhone = phone.replace(Regex("[^+0-9]"), "")
                    if (cleanPhone.length >= 8) {
                        contacts.add(Contact(name = name.trim(), phone = cleanPhone))
                    }
                }
            }
        }

        // Remove duplicates by phone number, keep first occurrence (with name)
        return contacts.distinctBy { it.phone }
    }

    private fun getTotalContacts(search: String?): Int {
        val selection = if (search != null) {
            "${ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME} LIKE ? OR ${ContactsContract.CommonDataKinds.Phone.NUMBER} LIKE ?"
        } else null

        val selectionArgs = if (search != null) {
            arrayOf("%$search%", "%$search%")
        } else null

        val cursor = context.contentResolver.query(
            ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
            arrayOf(ContactsContract.CommonDataKinds.Phone._ID),
            selection,
            selectionArgs,
            null
        )

        val count = cursor?.count ?: 0
        cursor?.close()
        return count
    }
}
