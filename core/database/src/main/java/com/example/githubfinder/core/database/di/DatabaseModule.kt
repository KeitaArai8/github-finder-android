/*
 * Abstract:
 * Room Database・DAOのHilt DI定義。
 */

package com.example.githubfinder.core.database.di

import android.content.Context
import androidx.room.Room
import com.example.githubfinder.core.database.AppDatabase
import com.example.githubfinder.core.database.FavoriteDao
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): AppDatabase =
        Room.databaseBuilder(context, AppDatabase::class.java, "github_finder.db").build()

    @Provides
    fun provideFavoriteDao(database: AppDatabase): FavoriteDao = database.favoriteDao()
}
