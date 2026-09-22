package com.oble.ideacapture.data

import androidx.room.TypeConverter
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json

class Converters {
    private val listSerializer = ListSerializer(String.serializer())

    @TypeConverter
    fun fromList(value: List<String>): String = Json.encodeToString(listSerializer, value)

    @TypeConverter
    fun toList(value: String?): List<String> =
        if (value.isNullOrBlank()) emptyList()
        else runCatching { Json.decodeFromString(listSerializer, value) }.getOrDefault(emptyList())

    @TypeConverter
    fun fromStatus(value: WhatsAppStatus): String = value.name

    @TypeConverter
    fun toStatus(value: String?): WhatsAppStatus = WhatsAppStatus.parse(value)
}
