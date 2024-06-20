package com.autolua.autolua2.activity

import android.content.Context
import android.content.SharedPreferences

class PreferencesHelper(context: Context) {
  private val preferences: SharedPreferences = context.getSharedPreferences("main", Context.MODE_PRIVATE)

  fun saveString(key: String, value: String) {
    preferences.edit().putString(key, value).apply()
  }

  fun getString(key: String, defaultValue: String): String {
    return preferences.getString(key, defaultValue) ?: defaultValue
  }

  fun saveInt(key: String, value: Int) {
    preferences.edit().putInt(key, value).apply()
  }

  fun getInt(key: String, defaultValue: Int): Int {
    return preferences.getInt(key, defaultValue)
  }

  fun saveBoolean(key: String, value: Boolean) {
    preferences.edit().putBoolean(key, value).apply()
  }

  fun getBoolean(key: String, defaultValue: Boolean): Boolean {
    return preferences.getBoolean(key, defaultValue)
  }
}