package com.example.gpsspeed

import android.Manifest
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Build
import android.os.Bundle
import android.os.Looper
import android.util.Log
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

    private val locationListener = object : LocationListener {
        override fun onLocationChanged(location: Location) {
            // 使用GPS提供的即时速度（m/s）转换成km/h
            val speedMs = location.speed
            val speedKmh = speedMs * 3.6f
            tvSpeed.text = String.format(Locale.getDefault(), "%.1f km/h", speedKmh)
        }

        override fun onProviderEnabled(provider: String) {
            // GPS重新开启时可在此处理
        }

        override fun onProviderDisabled(provider: String) {
            // 提示用户开启GPS
            Snackbar.make(tvSpeed, "请开启GPS定位", Snackbar.LENGTH_INDEFINITE)
                .setAction("开启") {
                    startActivity(android.provider.Settings.ACTION_LOCATION_SOURCE_SETTINGS)
                }.show()
        }
    }

    // 用于获取卫星数量（Android 7.0+）
    private val gnssStatusCallback = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
        object : android.location.GnssStatus.Callback() {
            override fun onSatelliteStatusChanged(status: android.location.GnssStatus) {
                val count = status.satelliteCount
                tvSignal.text = "信号强度: $count 颗卫星"
            }
        }
    } else {
        // 低版本使用GpsStatus（此处省略，但可简单兼容）
        null
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        tvSpeed = findViewById(R.id.tvSpeed)
        tvSignal = findViewById(R.id.tvSignal)
        locationManager = getSystemService(LOCATION_SERVICE) as LocationManager

        // 检查权限
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

        // 检查GPS是否可用
        if (!locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER)) {
            Snackbar.make(tvSpeed, "请开启GPS定位", Snackbar.LENGTH_INDEFINITE)
                .setAction("开启") {
                    startActivity(android.provider.Settings.ACTION_LOCATION_SOURCE_SETTINGS)
                }.show()
        }

        // 请求GPS位置更新：最小时间1秒，最小距离0米
        locationManager.requestLocationUpdates(
            LocationManager.GPS_PROVIDER,
            1000L,
            0f,
            locationListener,
            Looper.getMainLooper()
        )

        // 注册卫星状态监听（Android 7.0+）
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N && gnssStatusCallback != null) {
            locationManager.registerGnssStatusCallback(gnssStatusCallback, null)
        }

        // 立即显示一次当前已知位置（如果有）
        val lastKnown = locationManager.getLastKnownLocation(LocationManager.GPS_PROVIDER)
        lastKnown?.let { locationListener.onLocationChanged(it) }
    }

    override fun onPause() {
        super.onPause()
        // 节省电量，停止更新
        locationManager.removeUpdates(locationListener)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N && gnssStatusCallback != null) {
            locationManager.unregisterGnssStatusCallback(gnssStatusCallback)
        }
    }

    override fun onResume() {
        super.onResume()
        // 恢复更新（如果权限已授予）
        if (checkLocationPermission()) {
            startLocationUpdates()
        }
    }

    companion object {
        private const val REQUEST_LOCATION_PERMISSION = 1001
    }
}