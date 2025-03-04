package com.example.bamia.activities

import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.widget.ImageButton
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.example.bamia.R
import com.example.bamia.managers.NetworkManager
import com.google.android.material.button.MaterialButton
import com.google.android.material.textfield.TextInputEditText

@Suppress("DEPRECATION")
class SettingsActivity : AppCompatActivity() {

    private lateinit var etIpAddress: TextInputEditText
    private lateinit var etPin: TextInputEditText
    private lateinit var etCameraName: TextInputEditText
    private lateinit var btnSave: MaterialButton
    private lateinit var btnBackSettings: ImageButton

    // callerExtra를 통해 호출한 화면을 구분합니다.
    private var caller: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = Color.BLACK
        window.navigationBarColor = Color.BLACK

        setContentView(R.layout.activity_settings)

        etIpAddress = findViewById(R.id.etIpAddress)
        etPin = findViewById(R.id.etPin)
        etCameraName = findViewById(R.id.etCameraName)
        btnSave = findViewById(R.id.btnSave)
        btnBackSettings = findViewById(R.id.btnBackSettings)

        val networkManager = NetworkManager.getInstance(this)
        etIpAddress.setText(networkManager.ipAddress)
        etPin.setText(networkManager.pin)

        val prefs = getSharedPreferences("app_prefs", Context.MODE_PRIVATE)
        val cameraName = prefs.getString("camera_name", "BABY")
        etCameraName.setText(cameraName)

        // PIN 길이 검증 (4~8자리)
        etPin.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) { }
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) { }
            override fun afterTextChanged(s: Editable?) {
                s?.let {
                    if (it.length < 4 || it.length > 8) {
                        etPin.error = "PIN은 4~8자리여야 합니다."
                    }
                }
            }
        })

        // caller extra값을 확인합니다.
        caller = intent.getStringExtra("caller")

        btnSave.setOnClickListener {
            val newPin = etPin.text.toString()
            if (newPin.length !in 4..8) {
                Toast.makeText(this, "PIN은 4~8자리여야 합니다.", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            val newCameraName = etCameraName.text.toString()
            networkManager.pin = newPin
            prefs.edit().putString("camera_name", newCameraName).apply()
            // 무조건 카메라 모드 메인으로 이동 (저장 후 화면 전환 처리)
            startActivity(Intent(this, CameraModeActivity::class.java))
            finish()
        }

        // 뒤로가기 버튼 클릭 시 onBackPressed() 호출
        btnBackSettings.setOnClickListener {
            onBackPressed()
        }
    }

    @Suppress("MissingSuperCall")
    override fun onBackPressed() {
        // caller 값에 따라 복귀 대상을 다르게 합니다.
        if (caller == "camera") {
            // 케밥 메뉴에서 Settings를 호출한 경우, CameraModeActivity로 복귀
            startActivity(Intent(this, CameraModeActivity::class.java))
        } else {
            // 모드 선택 화면에서 Settings를 호출한 경우, LauncherActivity로 복귀
            startActivity(Intent(this, LauncherActivity::class.java))
        }
        finish()
    }
}
