package com.example.harperandroid

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import uniffi.harper_android.DocumentMode
import uniffi.harper_android.HarperConfig
import uniffi.harper_android.HarperDialect

class HarperSettingsRepository(private val context: Context) {
    private val prefs: SharedPreferences = context.getSharedPreferences("harper_settings", Context.MODE_PRIVATE)

    private val _config = MutableStateFlow(loadConfig())
    val config: StateFlow<HarperConfig> = _config.asStateFlow()

    fun updateDialect(dialect: HarperDialect) {
        prefs.edit().putString("dialect", dialect.name).apply()
        _config.value = _config.value.copy(dialect = dialect)
    }

    fun updateDocumentMode(mode: DocumentMode) {
        prefs.edit().putString("document_mode", mode.name).apply()
        _config.value = _config.value.copy(documentMode = mode)
    }

    fun toggleRule(
        ruleId: String,
        disabled: Boolean,
    ) {
        val currentDisabled = _config.value.disabledRules.toMutableSet()
        if (disabled) {
            currentDisabled.add(ruleId)
        } else {
            currentDisabled.remove(ruleId)
        }
        prefs.edit().putStringSet("disabled_rules", currentDisabled).apply()
        _config.value = _config.value.copy(disabledRules = currentDisabled.toList())
    }

    fun addWordToDictionary(word: String) {
        val currentDict = _config.value.userDictionary.toMutableSet()
        if (currentDict.add(word)) {
            prefs.edit().putStringSet("user_dictionary", currentDict).apply()
            _config.value = _config.value.copy(userDictionary = currentDict.toList())
        }
    }

    fun removeWordFromDictionary(word: String) {
        val currentDict = _config.value.userDictionary.toMutableSet()
        if (currentDict.remove(word)) {
            prefs.edit().putStringSet("user_dictionary", currentDict).apply()
            _config.value = _config.value.copy(userDictionary = currentDict.toList())
        }
    }

    private fun loadConfig(): HarperConfig {
        val dialectName = prefs.getString("dialect", HarperDialect.AMERICAN.name) ?: HarperDialect.AMERICAN.name
        val dialect =
            try {
                HarperDialect.valueOf(dialectName)
            } catch (e: IllegalArgumentException) {
                HarperDialect.AMERICAN
            }

        val documentModeName = prefs.getString("document_mode", DocumentMode.PLAIN_ENGLISH.name) ?: DocumentMode.PLAIN_ENGLISH.name
        val documentMode =
            try {
                DocumentMode.valueOf(documentModeName)
            } catch (e: IllegalArgumentException) {
                DocumentMode.PLAIN_ENGLISH
            }

        val disabledRules = prefs.getStringSet("disabled_rules", emptySet())?.toList() ?: emptyList()
        val userDictionary = prefs.getStringSet("user_dictionary", emptySet())?.toList() ?: emptyList()
        return HarperConfig(
            dialect = dialect,
            documentMode = documentMode,
            disabledRules = disabledRules,
            userDictionary = userDictionary,
        )
    }
}
