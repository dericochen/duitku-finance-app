package com.duitku.app.presentation.common

/** Event satu-kali dari ViewModel ke layar (snackbar, navigasi). */
sealed interface UiEvent {
    data class ShowMessage(val text: String) : UiEvent
    data object NavigateBack : UiEvent
}
