package com.duitku.app.domain.model

/** Hasil use case yang bisa gagal dengan pesan yang layak ditampilkan ke pengguna. */
sealed interface UseCaseResult<out T> {
    data class Success<T>(val value: T) : UseCaseResult<T>
    data class Failure(val message: String) : UseCaseResult<Nothing>
}

inline fun <T> UseCaseResult<T>.onFailure(block: (String) -> Unit): UseCaseResult<T> {
    if (this is UseCaseResult.Failure) block(message)
    return this
}

inline fun <T> UseCaseResult<T>.onSuccess(block: (T) -> Unit): UseCaseResult<T> {
    if (this is UseCaseResult.Success) block(value)
    return this
}
