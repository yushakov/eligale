package gallery.eliza.app.data

import android.content.Context

object TokenStorage {
    private const val PREFS = "eliza_prefs"
    private const val KEY = "auth_token"

    fun get(context: Context): String? =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY, null)

    fun save(context: Context, token: String) =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString(KEY, token).apply()

    fun clear(context: Context) =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().remove(KEY).apply()
}
