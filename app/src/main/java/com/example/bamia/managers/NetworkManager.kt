package com.example.bamia.managers

import android.content.Context
import android.net.ConnectivityManager
import android.net.wifi.WifiManager
import android.os.Build
import java.net.Inet4Address

/**
 * 네트워크 관련 정보 및 PIN/IP 관리를 담당합니다.
 * PIN과 IP 주소는 SharedPreferences를 통해 관리되며, IP 주소는 기기에서 자동으로 감지합니다.
 */
class NetworkManager private constructor(private val context: Context) {

    private val prefs = context.getSharedPreferences("network_prefs", Context.MODE_PRIVATE)

    var pin: String = prefs.getString("pin", "1234") ?: "1234"
        set(value) {
            field = value
            prefs.edit().putString("pin", value).apply()
        }

    var ipAddress: String = getDeviceIpAddress(context)

    companion object {
        @Volatile private var instance: NetworkManager? = null

        fun getInstance(context: Context): NetworkManager =
            instance ?: synchronized(this) {
                instance ?: NetworkManager(context.applicationContext).also { instance = it }
            }
    }

    /**
     * API 31 이상에서는 ConnectivityManager의 LinkProperties를 이용하여 IPv4 주소를 추출.
     * 이전 버전에서는 WifiManager를 사용합니다.
     */
    fun getDeviceIpAddress(context: Context): String {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val connectivityManager =
                context.getSystemService(ConnectivityManager::class.java)
            val network = connectivityManager.activeNetwork ?: return "0.0.0.0"
            val linkProperties = connectivityManager.getLinkProperties(network)
            val ipv4Address = linkProperties?.linkAddresses
                ?.firstOrNull { it.address is Inet4Address }
                ?.address
                ?.hostAddress
            ipv4Address ?: "0.0.0.0"
        } else {
            val wifiManager =
                context.applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
            val ipInt = wifiManager.connectionInfo.ipAddress
            String.format(
                "%d.%d.%d.%d",
                (ipInt and 0xff),
                (ipInt shr 8 and 0xff),
                (ipInt shr 16 and 0xff),
                (ipInt shr 24 and 0xff)
            )
        }
    }
}
