package ar.com.josemascherpa.controltv.data.local

import android.content.SharedPreferences

/** Almacenamiento de prueba: permite recrear los repositorios sin Android ni disco. */
internal class MemoryPreferences : SharedPreferences {
    private val strings = mutableMapOf<String, String?>()
    private val booleans = mutableMapOf<String, Boolean>()
    private val sets = mutableMapOf<String, Set<String>?>()
    override fun getString(key: String?, defValue: String?): String? = strings[key] ?: defValue
    override fun getBoolean(key: String?, defValue: Boolean): Boolean = booleans[key] ?: defValue
    override fun getStringSet(key: String?, defValues: MutableSet<String>?): MutableSet<String>? = sets[key]?.toMutableSet() ?: defValues
    override fun contains(key: String?): Boolean = strings.containsKey(key) || booleans.containsKey(key) || sets.containsKey(key)
    override fun getAll(): MutableMap<String, *> = (strings + booleans + sets).toMutableMap()
    override fun getInt(key: String?, defValue: Int): Int = defValue
    override fun getLong(key: String?, defValue: Long): Long = defValue
    override fun getFloat(key: String?, defValue: Float): Float = defValue
    override fun registerOnSharedPreferenceChangeListener(listener: SharedPreferences.OnSharedPreferenceChangeListener?) {}
    override fun unregisterOnSharedPreferenceChangeListener(listener: SharedPreferences.OnSharedPreferenceChangeListener?) {}
    override fun edit(): SharedPreferences.Editor = object : SharedPreferences.Editor {
        override fun putString(key: String?, value: String?): SharedPreferences.Editor = apply { strings[requireNotNull(key)] = value }
        override fun putBoolean(key: String?, value: Boolean): SharedPreferences.Editor = apply { booleans[requireNotNull(key)] = value }
        override fun putStringSet(key: String?, values: MutableSet<String>?): SharedPreferences.Editor = apply { sets[requireNotNull(key)] = values?.toSet() }
        override fun remove(key: String?): SharedPreferences.Editor = apply { strings.remove(key); booleans.remove(key); sets.remove(key) }
        override fun clear(): SharedPreferences.Editor = apply { strings.clear(); booleans.clear(); sets.clear() }
        override fun putInt(key: String?, value: Int): SharedPreferences.Editor = error("No se usa")
        override fun putLong(key: String?, value: Long): SharedPreferences.Editor = error("No se usa")
        override fun putFloat(key: String?, value: Float): SharedPreferences.Editor = error("No se usa")
        override fun apply() {}
        override fun commit(): Boolean = true
    }
}
