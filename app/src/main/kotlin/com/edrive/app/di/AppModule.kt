package com.edrive.app.di

import android.content.Context
import androidx.room.Room
import com.edrive.app.data.db.AppDatabase
import com.edrive.app.data.db.Migrations
import com.edrive.app.data.db.dao.FileDao
import com.edrive.app.data.db.dao.FolderDao
import com.edrive.app.data.db.dao.UserDao
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit
import javax.inject.Qualifier
import javax.inject.Singleton

/** Tətbiq ömrü boyu yaşayan coroutine scope-u (arxa fon işləri üçün). */
@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class ApplicationScope

/**
 * Hilt-in özü yarada bilmədiyi obyektlər (kitabxana sinifləri) burada təqdim olunur.
 * Öz siniflərimiz isə konstruktorundakı `@Inject` ilə avtomatik tapılır.
 */
@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    @Provides
    @Singleton
    fun okHttp(): OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(120, TimeUnit.SECONDS)
        .writeTimeout(0, TimeUnit.SECONDS) // böyük yükləmələr kəsilməsin
        .build()

    @Provides
    @Singleton
    fun database(@ApplicationContext context: Context): AppDatabase =
        Room.databaseBuilder(context, AppDatabase::class.java, AppDatabase.NAME)
            .addMigrations(*Migrations.ALL)
            .build()

    @Provides
    fun userDao(db: AppDatabase): UserDao = db.users()

    @Provides
    fun fileDao(db: AppDatabase): FileDao = db.files()

    @Provides
    fun folderDao(db: AppDatabase): FolderDao = db.folders()

    @Provides
    @Singleton
    @ApplicationScope
    fun applicationScope(): CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
}
