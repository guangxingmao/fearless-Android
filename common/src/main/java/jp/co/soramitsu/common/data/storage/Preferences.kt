package jp.co.soramitsu.common.data.storage

interface Preferences {

    fun putString(field: String, value: String)

    fun getString(field: String, defaultValue: String): String

    fun getString(field: String): String?

    fun putBoolean(field: String, value: Boolean)

    fun getBoolean(field: String, defaultValue: Boolean): Boolean

    fun putInt(field: String, value: Int)

    fun getInt(field: String, defaultValue: Int): Int

    fun putLong(field: String, value: Long)

    fun getLong(field: String, defaultValue: Long): Long
}