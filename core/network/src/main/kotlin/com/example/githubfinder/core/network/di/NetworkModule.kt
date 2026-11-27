/*
 * Abstract:
 * Retrofit・OkHttp・JSONパーサーのHilt DI定義。
 */

package com.example.githubfinder.core.network.di

import com.example.githubfinder.core.network.BuildConfig
import com.example.githubfinder.core.network.GitHubApiService
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import java.util.concurrent.TimeUnit
import javax.inject.Singleton
import kotlinx.serialization.json.Json
import okhttp3.Call
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory

@Module
@InstallIn(SingletonComponent::class)
object NetworkModule {

    private const val TIMEOUT_SECONDS = 15L

    @Provides
    @Singleton
    fun provideJson(): Json = Json {
        ignoreUnknownKeys = true
        coerceInputValues = true
    }

    /**
     * 通信の実体（OkHttp）。ここでは Call.Factory として公開し、Retrofit 側は [dagger.Lazy] 越しに受ける。
     * OkHttpClient の構築（コネクションプール・ディスパッチャ等の初期化）が初回リクエスト時まで遅延され、
     * 起動時にメインスレッドで走らないようにするため（NowInAndroid と同じ構成）。
     *
     * ログは BASIC（メソッド・URL・ステータス・所要時間のみ）にとどめ、ヘッダやボディは出さない。
     */
    @Provides
    @Singleton
    fun provideCallFactory(): Call.Factory = OkHttpClient.Builder()
        .connectTimeout(TIMEOUT_SECONDS, TimeUnit.SECONDS)
        .readTimeout(TIMEOUT_SECONDS, TimeUnit.SECONDS)
        .apply {
            if (BuildConfig.DEBUG) {
                addInterceptor(
                    HttpLoggingInterceptor().setLevel(HttpLoggingInterceptor.Level.BASIC),
                )
            }
        }
        .build()

    @Provides
    @Singleton
    fun provideRetrofit(json: Json, callFactory: dagger.Lazy<Call.Factory>): Retrofit = Retrofit.Builder()
        .baseUrl("https://api.github.com/")
        .callFactory { request -> callFactory.get().newCall(request) }
        .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
        .build()

    @Provides
    @Singleton
    fun provideGitHubApiService(retrofit: Retrofit): GitHubApiService = retrofit.create(GitHubApiService::class.java)
}
