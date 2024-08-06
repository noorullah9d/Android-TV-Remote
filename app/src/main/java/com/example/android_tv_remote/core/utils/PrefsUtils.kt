package com.example.android_tv_remote.core.utils

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit
import com.example.android_tv_remote.core.Constants.APP_PREFS
import com.example.android_tv_remote.core.Constants.IS_FIRST_TIME
import com.example.android_tv_remote.core.Constants.IS_NIGHT_MODE
import com.example.android_tv_remote.core.Constants.IS_SIMPLE_REMOTE
import com.example.android_tv_remote.core.Constants.IS_TOUCHPAD_ENABLE
import com.example.android_tv_remote.core.Constants.IS_VIBRATION_ENABLE

object PrefsUtils {
    private lateinit var sharedPreferences: SharedPreferences
    fun init(context: Context) {
        sharedPreferences = context.getSharedPreferences(APP_PREFS, Context.MODE_PRIVATE)
    }

    var isVibrationEnable
        get() = sharedPreferences.getBoolean(IS_VIBRATION_ENABLE, false)
        set(value) = sharedPreferences.edit { putBoolean(IS_VIBRATION_ENABLE, value) }

    var isSimpleRemote
        get() = sharedPreferences.getBoolean(IS_SIMPLE_REMOTE, false)
        set(value) = sharedPreferences.edit { putBoolean(IS_SIMPLE_REMOTE, value) }

    var isTouchPadEnable
        get() = sharedPreferences.getBoolean(IS_TOUCHPAD_ENABLE, false)
        set(value) = sharedPreferences.edit { putBoolean(IS_TOUCHPAD_ENABLE, value) }

    var isFirstTime
        get() = sharedPreferences.getBoolean(IS_FIRST_TIME, true)
        set(value) = sharedPreferences.edit { putBoolean(IS_FIRST_TIME, value) }

    var isNightMode
        get() = sharedPreferences.getBoolean(IS_NIGHT_MODE, false)
        set(value) = sharedPreferences.edit { putBoolean(IS_NIGHT_MODE, value) }
}