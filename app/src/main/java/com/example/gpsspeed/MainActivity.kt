package com.example.gpsspeed

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Build
import android.os.Bundle
import android.os.Looper
import android.provider.Settings
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.google.android.material.snackbar.Snackbar
import java.util.Locale

class MainActivity : AppCompatActivity() {

    private lateinit var locationManager: LocationManager
    private lateinit var tvSpeed: TextView
    private lateinit var tvSignal: TextView
    private lateinit var tvAltitude: TextView   // 新增：海拔显示

    private val locationListener = object : LocationListener {
        override fun onLocationChanged(location: Location) {
            // 速度更新
            val speedMs = location.speed
            val speedKmh = speedMs * 3.6f
            tvSpeed.text = String.format(Locale.getDefault(), "%.1f km/h", speedKmh)

            // 海拔更新
            if (location.hasAltitude()) {
                val altitudeMeters = location.altitude
                tvAltitude.text = String.format(Locale.getDefault(), "海拔: %.1f m", altitudeMeters)
            } else {
                tvAltitude.text = "海拔: 不可用"
            }
        }

        override fun onProviderEnabled(provider: String) {
            // 可空实现
        }

        override fun onProviderDisabled(provider: String) {
            Snackbar.make(tvSpeed, "请开启GPS定位", Snackbar.LENGTH_INDEFINITE)
                .setAction("开启") {
                    startActivity(Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS))
                }.show()
        }
    }

    private val gnssStatusCallback = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
        object : android.location.GnssStatus.Callback() {
            override fun onSatelliteStatusChanged(status: android.location.GnssStatus) {
                val count = status.satelliteCount
                tvSignal.text = "信号强度: $count 颗卫星"
            }
        }
    } else {
        null
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        tvSpeed = findViewById(R.id.tvSpeed)
        tvSignal = findViewById(R.id.tvSignal)
        tvAltitude = findViewById(R.id.tvAltitude)   // 初始化海拔控件
        locationManager = getSystemService(LOCATION_SERVICE) as LocationManager

        if (checkLocationPermission()) {
            startLocationUpdates()
        } else {
            requestLocationPermission()
        }
    }

    private fun checkLocationPermission(): Boolean {
        return ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
    }

    private fun requestLocationPermission() {
        ActivityCompat.requestPermissions(
            this,
            arrayOf(
                Manifest.permission.ACCESS_FINE_LOCATION,
                Manifest.permission.ACCESS_COARSE_LOCATION
            ),
            REQUEST_LOCATION_PERMISSION
        )
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == REQUEST_LOCATION_PERMISSION) {
            if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                startLocationUpdates()
            } else {
                Snackbar.make(tvSpeed, "需要定位权限才能工作", Snackbar.LENGTH_INDEFINITE)
                    .setAction("授予") { requestLocationPermission() }.show()
            }
        }
    }

    private fun startLocationUpdates() {
        if (!checkLocationPermission()) return

        if (!locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER)) {
            Snackbar.make(tvSpeed, "请开启GPS定位", Snackbar.LENGTH_INDEFINITE)
                .setAction("开启") {
                    startActivity(Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS))
                }.show()
        }

        locationManager.requestLocationUpdates(
            LocationManager.GPS_PROVIDER,
            1000L,
            0f,
            locationListener,
            Looper.getMainLooper()
        )

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N && gnssStatusCallback != null) {
            locationManager.registerGnssStatusCallback(gnssStatusCallback, null)
        }

        val lastKnown = locationManager.getLastKnownLocation(LocationManager.GPS_PROVIDER)
        lastKnown?.let { locationListener.onLocationChanged(it) }
    }

    override fun onPause() {
        super.onPause()
        locationManager.removeUpdates(locationListener)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N && gnssStatusCallback != null) {
            locationManager.unregisterGnssStatusCallback(gnssStatusCallback)
        }
    }

    override fun onResume() {
        super.onResume()
        if (checkLocationPermission()) {
            startLocationUpdates()
        }
    }

    companion object {
        private const val REQUEST_LOCATION_PERMISSION = 1001
    }
}
