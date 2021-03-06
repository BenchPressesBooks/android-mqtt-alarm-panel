/*
 * <!--
 *   ~ Copyright (c) 2017. ThanksMister LLC
 *   ~
 *   ~ Licensed under the Apache License, Version 2.0 (the "License");
 *   ~ you may not use this file except in compliance with the License. 
 *   ~ You may obtain a copy of the License at
 *   ~
 *   ~ http://www.apache.org/licenses/LICENSE-2.0
 *   ~
 *   ~ Unless required by applicable law or agreed to in writing, software distributed 
 *   ~ under the License is distributed on an "AS IS" BASIS, 
 *   ~ WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. 
 *   ~ See the License for the specific language governing permissions and 
 *   ~ limitations under the License.
 *   -->
 */

package com.thanksmister.iot.mqtt.alarmpanel.ui.fragments

import android.Manifest
import android.content.Context
import android.content.DialogInterface
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.location.Criteria
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.support.v4.app.ActivityCompat
import android.support.v7.app.AlertDialog
import android.support.v7.preference.CheckBoxPreference
import android.support.v7.preference.EditTextPreference
import android.support.v7.preference.PreferenceFragmentCompat
import android.text.TextUtils
import android.view.View
import android.widget.Toast

import com.thanksmister.iot.mqtt.alarmpanel.BaseActivity
import com.thanksmister.iot.mqtt.alarmpanel.R
import com.thanksmister.iot.mqtt.alarmpanel.network.DarkSkyOptions
import com.thanksmister.iot.mqtt.alarmpanel.ui.Configuration
import com.thanksmister.iot.mqtt.alarmpanel.utils.LocationUtils

import timber.log.Timber

import com.thanksmister.iot.mqtt.alarmpanel.R.xml.preferences_weather
import com.thanksmister.iot.mqtt.alarmpanel.ui.Configuration.Companion.PREF_WEATHER_API_KEY
import com.thanksmister.iot.mqtt.alarmpanel.ui.Configuration.Companion.PREF_WEATHER_LATITUDE
import com.thanksmister.iot.mqtt.alarmpanel.ui.Configuration.Companion.PREF_WEATHER_LONGITUDE
import com.thanksmister.iot.mqtt.alarmpanel.ui.Configuration.Companion.PREF_WEATHER_UNITS
import com.thanksmister.iot.mqtt.alarmpanel.ui.Configuration.Companion.PREF_WEATHER_WEATHER
import com.thanksmister.iot.mqtt.alarmpanel.ui.activities.SettingsActivity
import com.thanksmister.iot.mqtt.alarmpanel.utils.DialogUtils
import dagger.android.support.AndroidSupportInjection
import javax.inject.Inject

class WeatherSettingsFragment : PreferenceFragmentCompat(), SharedPreferences.OnSharedPreferenceChangeListener {

    @Inject lateinit var dialogUtils: DialogUtils
    @Inject lateinit var configuration: Configuration
    @Inject lateinit var darkSkyOptions: DarkSkyOptions

    private var weatherModulePreference: CheckBoxPreference? = null
    private var unitsPreference: CheckBoxPreference? = null
    private var weatherApiKeyPreference: EditTextPreference? = null
    private var weatherLatitude: EditTextPreference? = null
    private var weatherLongitude: EditTextPreference? = null
    private var locationManager: LocationManager? = null
    private var locationHandler: Handler? = null

    internal val locationListener: LocationListener = object : LocationListener {
        override fun onLocationChanged(location: Location?) {
            if (location != null) {
                if (isAdded) {
                    val latitude = location.latitude.toString()
                    val longitude = location.longitude.toString()
                    if (LocationUtils.coordinatesValid(latitude, longitude)) {
                        darkSkyOptions.latitude = location.latitude.toString()
                        darkSkyOptions.longitude = location.longitude.toString()
                        weatherLatitude!!.summary = darkSkyOptions.latitude
                        weatherLongitude!!.summary = darkSkyOptions.longitude
                    } else {
                        Toast.makeText(activity, R.string.toast_invalid_coordinates, Toast.LENGTH_SHORT).show()
                    }
                    locationManager!!.removeUpdates(this)
                }
            }
        }

        override fun onStatusChanged(provider: String, status: Int, extras: Bundle) {
            Timber.d("onStatusChanged: " + status)
        }

        override fun onProviderEnabled(provider: String) {
            Timber.d("onProviderEnabled")
        }

        override fun onProviderDisabled(provider: String) {
            Timber.d("onProviderDisabled")
            locationHandler = Handler()
            locationHandler!!.postDelayed(locationRunnable, 500)
        }
    }

    private val locationRunnable = Runnable {
        if (isAdded) { // Without this in certain cases application will show ANR
            //dialogUtils.hideProgressDialog()
            val builder = AlertDialog.Builder(activity!!)
            builder.setMessage(R.string.string_location_services_disabled).setCancelable(false).setPositiveButton(android.R.string.ok) { _, _ ->
                val gpsOptionsIntent = Intent(android.provider.Settings.ACTION_LOCATION_SOURCE_SETTINGS)
                startActivity(gpsOptionsIntent)
            }
            builder.setNegativeButton(android.R.string.cancel) { dialog, _ -> dialog.cancel() }
            val alert = builder.create()
            alert.show()
        }
    }

    override fun onAttach(context: Context) {
        AndroidSupportInjection.inject(this)
        super.onAttach(context)
    }

    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        addPreferencesFromResource(preferences_weather)
    }

    override fun onResume() {
        super.onResume()
        preferenceScreen.sharedPreferences.registerOnSharedPreferenceChangeListener(this)
    }

    override fun onPause() {
        super.onPause()
        preferenceScreen.sharedPreferences.unregisterOnSharedPreferenceChangeListener(this)
    }

    override fun onDetach() {
        super.onDetach()
        if (locationHandler != null) {
            locationHandler!!.removeCallbacks(locationRunnable)
        }
        if (locationManager != null) {
            locationManager!!.removeUpdates(locationListener)
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {

        super.onViewCreated(view, savedInstanceState)

        weatherModulePreference = findPreference(PREF_WEATHER_WEATHER) as CheckBoxPreference
        unitsPreference = findPreference(PREF_WEATHER_UNITS) as CheckBoxPreference
        weatherApiKeyPreference = findPreference(PREF_WEATHER_API_KEY) as EditTextPreference
        weatherLongitude = findPreference(PREF_WEATHER_LONGITUDE) as EditTextPreference
        weatherLatitude = findPreference(PREF_WEATHER_LATITUDE) as EditTextPreference

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            if (ActivityCompat.checkSelfPermission(activity as BaseActivity, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED
                    && ActivityCompat.checkSelfPermission(activity as BaseActivity, Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
                weatherModulePreference!!.isEnabled = false
                unitsPreference!!.isEnabled = false
                weatherApiKeyPreference!!.isEnabled = false
                weatherLongitude!!.isEnabled = false
                weatherLatitude!!.isEnabled = false
                configuration.setShowWeatherModule(false)
                dialogUtils.showAlertDialog(activity as BaseActivity, getString(R.string.dialog_no_location_permissions))
                return
            }
        }
        
        if (!TextUtils.isEmpty(darkSkyOptions.darkSkyKey)) {
            weatherApiKeyPreference!!.text = darkSkyOptions.darkSkyKey.toString()
            weatherApiKeyPreference!!.summary = darkSkyOptions.darkSkyKey.toString()
        }

        if (!TextUtils.isEmpty(darkSkyOptions.latitude)) {
            weatherLatitude!!.text = darkSkyOptions.latitude
            weatherLatitude!!.summary = darkSkyOptions.latitude
        }

        if (!TextUtils.isEmpty(darkSkyOptions.longitude)) {
            weatherLongitude!!.text = darkSkyOptions.longitude
            weatherLongitude!!.summary = darkSkyOptions.longitude
        }

        weatherModulePreference!!.isChecked = configuration.showWeatherModule()

        unitsPreference!!.isChecked = darkSkyOptions.isCelsius()
        unitsPreference!!.isEnabled = configuration.showWeatherModule()
        weatherApiKeyPreference!!.isEnabled = configuration.showWeatherModule()
        weatherLatitude!!.isEnabled = configuration.showWeatherModule()
        weatherLongitude!!.isEnabled = configuration.showWeatherModule()
    }

    override fun onSharedPreferenceChanged(sharedPreferences: SharedPreferences, key: String) {
        when (key) {
            PREF_WEATHER_WEATHER -> {
                val checked = weatherModulePreference!!.isChecked
                configuration.setShowWeatherModule(checked)
                weatherApiKeyPreference!!.isEnabled = checked
                weatherLatitude!!.isEnabled = checked
                weatherLongitude!!.isEnabled = checked
                unitsPreference!!.isEnabled = checked
                if (checked) {
                    setUpLocationMonitoring()
                }
            }
            PREF_WEATHER_UNITS -> {
                val useCelsius = unitsPreference!!.isChecked
                darkSkyOptions.setIsCelsius(useCelsius)
            }
            PREF_WEATHER_API_KEY -> {
                val value = weatherApiKeyPreference!!.text
                darkSkyOptions.darkSkyKey = value
                weatherApiKeyPreference!!.summary = value
            }
            PREF_WEATHER_LONGITUDE -> {
                val value = weatherLongitude!!.text
                if (LocationUtils.longitudeValid(value)) {
                    darkSkyOptions.longitude = value
                    weatherLongitude!!.summary = value
                } else {
                    Toast.makeText(activity, R.string.toast_invalid_latitude, Toast.LENGTH_SHORT).show()
                    darkSkyOptions.longitude = value
                    weatherLongitude!!.summary = ""
                }
            }
            PREF_WEATHER_LATITUDE -> {
                val value = weatherLatitude!!.text
                if (LocationUtils.longitudeValid(value)) {
                    darkSkyOptions.latitude = value
                    weatherLatitude!!.summary = value
                } else {
                    Toast.makeText(activity, R.string.toast_invalid_longitude, Toast.LENGTH_SHORT).show()
                    darkSkyOptions.latitude = value
                    weatherLatitude!!.summary = ""
                }
            }
        }
    }

    private fun setUpLocationMonitoring() {
        Timber.d("setUpLocationMonitoring")
        if (isAdded) {
            Toast.makeText(activity as BaseActivity, getString(R.string.progress_location), Toast.LENGTH_SHORT).show()
            locationManager = activity!!.getSystemService(Context.LOCATION_SERVICE) as LocationManager
            val criteria = Criteria()
            criteria.accuracy = Criteria.ACCURACY_COARSE
            try {
                if (locationManager!!.allProviders.contains(LocationManager.NETWORK_PROVIDER)) {
                    locationManager!!.requestLocationUpdates(LocationManager.NETWORK_PROVIDER, HOUR_MILLIS, METERS_MIN.toFloat(), locationListener)
                }
                if (locationManager!!.allProviders.contains(LocationManager.GPS_PROVIDER)) {
                    locationManager!!.requestLocationUpdates(LocationManager.GPS_PROVIDER, HOUR_MILLIS, METERS_MIN.toFloat(), locationListener)
                }
            } catch (e: SecurityException) {
                Timber.e("Location manager could not use network provider", e)
                Toast.makeText(activity, R.string.toast_invalid_provider, Toast.LENGTH_SHORT).show()
            }
        }
    }

    companion object {
        private val HOUR_MILLIS = (60 * 60 * 1000).toLong()
        private val METERS_MIN = 500
    }
}