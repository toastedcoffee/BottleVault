package com.toastedcoffee.bottlevault.data.local.database

import androidx.room.TypeConverter
import com.toastedcoffee.bottlevault.data.model.AlcoholType
import com.toastedcoffee.bottlevault.data.model.BottleStatus
import com.toastedcoffee.bottlevault.data.model.SyncStatus
import java.util.Date

class Converters {
    @TypeConverter
    fun fromTimestamp(value: Long?): Date? = value?.let { Date(it) }

    @TypeConverter
    fun dateToTimestamp(date: Date?): Long? = date?.time

    @TypeConverter
    fun fromBottleStatus(status: BottleStatus): String = status.name

    @TypeConverter
    fun toBottleStatus(status: String): BottleStatus = BottleStatus.valueOf(status)

    @TypeConverter
    fun fromSyncStatus(status: SyncStatus): String = status.name

    @TypeConverter
    fun toSyncStatus(status: String): SyncStatus = SyncStatus.valueOf(status)

    @TypeConverter
    fun fromAlcoholType(type: AlcoholType): String = type.name

    @TypeConverter
    fun toAlcoholType(type: String): AlcoholType = AlcoholType.valueOf(type)
}