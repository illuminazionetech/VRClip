package com.xrclip.database

import androidx.room.AutoMigration
import androidx.room.Database
import androidx.room.RoomDatabase
import com.xrclip.database.objects.CommandTemplate
import com.xrclip.database.objects.CookieProfile
import com.xrclip.database.objects.DownloadedVideoInfo
import com.xrclip.database.objects.OptionShortcut

@Database(
    entities =
        [
            DownloadedVideoInfo::class,
            CommandTemplate::class,
            CookieProfile::class,
            OptionShortcut::class,
        ],
    version = 5,
    autoMigrations =
        [
            AutoMigration(from = 1, to = 2),
            AutoMigration(from = 2, to = 3),
            AutoMigration(from = 3, to = 4),
            AutoMigration(from = 4, to = 5),
        ],
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun videoInfoDao(): VideoInfoDao
}
