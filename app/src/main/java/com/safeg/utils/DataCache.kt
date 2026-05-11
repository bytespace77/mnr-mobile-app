package com.safeg.utils

import android.content.Context
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.safeg.models.GetActiveCountryResponseItem
import com.safeg.models.GetActiveVehicleTypeResponseItem
import com.safeg.models.GetCityByStateResponseItem
import com.safeg.models.GetStateByCountryResponseItem

object DataCache {

    private const val PREF_NAME = "safeg_cache"
    private const val KEY_COUNTRIES = "countries"
    private const val KEY_STATES = "states_"
    private const val KEY_CITIES = "cities_"
    private const val KEY_VEHICLE_TYPES = "vehicle_types"
    private const val CACHE_EXPIRY_MS = 24 * 60 * 60 * 1000L // ✅ 24 hours

    private val gson = Gson()

    // ============ COUNTRIES ============

    fun saveCountries(context: Context, list: ArrayList<GetActiveCountryResponseItem>) {
        val prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        prefs.edit()
            .putString(KEY_COUNTRIES, gson.toJson(list))
            .putLong("${KEY_COUNTRIES}_time", System.currentTimeMillis())
            .apply()
    }

    fun getCountries(context: Context): ArrayList<GetActiveCountryResponseItem>? {
        val prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        if (isExpired(prefs, "${KEY_COUNTRIES}_time")) return null
        val json = prefs.getString(KEY_COUNTRIES, null) ?: return null
        return try {
            val type = object : TypeToken<ArrayList<GetActiveCountryResponseItem>>() {}.type
            gson.fromJson(json, type)
        } catch (t: Throwable) { null }
    }

    // ============ STATES ============

    fun saveStates(context: Context, countryId: Int, list: ArrayList<GetStateByCountryResponseItem>) {
        val prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        prefs.edit()
            .putString("$KEY_STATES$countryId", gson.toJson(list))
            .putLong("${KEY_STATES}${countryId}_time", System.currentTimeMillis())
            .apply()
    }

    fun getStates(context: Context, countryId: Int): ArrayList<GetStateByCountryResponseItem>? {
        val prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        if (isExpired(prefs, "${KEY_STATES}${countryId}_time")) return null
        val json = prefs.getString("$KEY_STATES$countryId", null) ?: return null
        return try {
            val type = object : TypeToken<ArrayList<GetStateByCountryResponseItem>>() {}.type
            gson.fromJson(json, type)
        } catch (t: Throwable) { null }
    }

    // ============ CITIES ============

    fun saveCities(context: Context, stateId: Int, list: ArrayList<GetCityByStateResponseItem>) {
        val prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        prefs.edit()
            .putString("$KEY_CITIES$stateId", gson.toJson(list))
            .putLong("${KEY_CITIES}${stateId}_time", System.currentTimeMillis())
            .apply()
    }

    fun getCities(context: Context, stateId: Int): ArrayList<GetCityByStateResponseItem>? {
        val prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        if (isExpired(prefs, "${KEY_CITIES}${stateId}_time")) return null
        val json = prefs.getString("$KEY_CITIES$stateId", null) ?: return null
        return try {
            val type = object : TypeToken<ArrayList<GetCityByStateResponseItem>>() {}.type
            gson.fromJson(json, type)
        } catch (t: Throwable) { null }
    }

    // ============ VEHICLE TYPES ============

    fun saveVehicleTypes(context: Context, list: ArrayList<GetActiveVehicleTypeResponseItem>) {
        val prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        prefs.edit()
            .putString(KEY_VEHICLE_TYPES, gson.toJson(list))
            .putLong("${KEY_VEHICLE_TYPES}_time", System.currentTimeMillis())
            .apply()
    }

    fun getVehicleTypes(context: Context): ArrayList<GetActiveVehicleTypeResponseItem>? {
        val prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        if (isExpired(prefs, "${KEY_VEHICLE_TYPES}_time")) return null
        val json = prefs.getString(KEY_VEHICLE_TYPES, null) ?: return null
        return try {
            val type = object : TypeToken<ArrayList<GetActiveVehicleTypeResponseItem>>() {}.type
            gson.fromJson(json, type)
        } catch (t: Throwable) { null }
    }

    // ============ UTILS ============

    fun clearAll(context: Context) {
        context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
            .edit().clear().apply()
    }

    private fun isExpired(
        prefs: android.content.SharedPreferences,
        key: String
    ): Boolean {
        val savedTime = prefs.getLong(key, 0L)
        if (savedTime == 0L) return true
        return System.currentTimeMillis() - savedTime > CACHE_EXPIRY_MS
    }
}