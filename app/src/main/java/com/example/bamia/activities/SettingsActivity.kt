package com.example.bamia.activities

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.example.bamia.R
import com.example.bamia.managers.NetworkManager
import com.google.android.material.button.MaterialButton
import com.google.android.material.textfield.TextInputEditText

/**
 * 설정 화면
 * - IP, PIN, 그리고 카메라(아기) 이름 설정 기능 제공
 * - 모드 선택 재설정을 위한 버튼 포함
 */
class SettingsActivity : AppCompatActivity() {

    private lateinit var etIpAddress: TextInputEditText
    private lateinit var etPin: TextInputEditText
    private lateinit var etCameraName: TextInputEditText  // 아기 이름 입력란
    private lateinit var btnSave: MaterialButton
    private lateinit var btnModeSelect: MaterialButton

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)

        etIpAddress = findViewById(R.id.etIpAddress)
        etPin = findViewById(R.id.etPin)
        etCameraName = findViewById(R.id.etCameraName)
        btnSave = findViewById(R.id.btnSave)
        btnModeSelect = findViewById(R.id.btnModeSelect)

        val networkManager = NetworkManager.getInstance(this)
        etIpAddress.setText(networkManager.ipAddress)
        etPin.setText(networkManager.pin)

        // 아기 이름은 SharedPreferences에 저장 (키: camera_name)
        val prefs = getSharedPreferences("app_prefs", Context.MODE_PRIVATE)
        val cameraName = prefs.getString("camera_name", "아기")
        etCameraName.setText(cameraName)

        btnSave.setOnClickListener {
            val newIp = etIpAddress.text.toString()
            val newPin = etPin.text.toString()
            val newCameraName = etCameraName.text.toString()
            networkManager.ipAddress = newIp
            networkManager.pin = newPin
            prefs.edit().putString("camera_name", newCameraName).apply()
            finish()
        }

        btnModeSelect.setOnClickListener {
            prefs.edit().remove("last_mode").apply()
            val intent = Intent(this, LauncherActivity::class.java)
            intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_NEW_TASK)
            startActivity(intent)
            finish()
        }
    }
}
