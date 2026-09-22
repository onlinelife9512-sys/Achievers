package com.oble.ideacapture.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters

@Database(
    entities = [IdeaEntity::class, SessionEntity::class, SegmentEntity::class],
    version = 1,
    exportSchema = false,
)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun ideaDao(): IdeaDao
    abstract fun sessionDao(): SessionDao
    abstract fun segmentDao(): SegmentDao

    companion object {
        fun build(context: Context): AppDatabase =
            Room.databaseBuilder(context, AppDatabase::class.java, "oble.db").build()
    }
}
