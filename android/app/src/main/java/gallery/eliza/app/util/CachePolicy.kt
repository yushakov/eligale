package gallery.eliza.app.util

/**
 * Returns true when a loading spinner should be shown.
 * Suppressed when existing data is already available to display.
 */
fun shouldShowSpinner(hasCachedData: Boolean): Boolean = !hasCachedData

/**
 * Returns the error message to surface after a failed network load,
 * or null when existing data is available and the failure should be silent.
 */
fun errorMessageForDisplay(hasCachedData: Boolean, exception: Exception): String? =
    if (hasCachedData) null
    else exception.message ?: "Ошибка загрузки"
