/*
 * Abstract:
 * アプリケーション全体で使用するドメインエラー型。
 */

@file:Suppress("SERIALIZABLE_OBJECT_WITH_NO_READ_RESOLVE")

package com.example.githubfinder.core.model

sealed class AppError : Exception() {
    data object Network : AppError()
    data class Server(val statusCode: Int) : AppError()
    data object RateLimit : AppError()
    data class Unknown(override val message: String) : AppError()
}
