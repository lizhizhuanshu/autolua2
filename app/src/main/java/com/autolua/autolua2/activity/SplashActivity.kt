package com.autolua.autolua2.activity

import android.annotation.SuppressLint
import android.os.Bundle
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.autolua.autolua2.R
import com.autolua.autolua2.MainService

@SuppressLint("CustomSplashScreen")
class SplashActivity : AppCompatActivity() {
  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    enableEdgeToEdge()
    setContentView(R.layout.activity_splash)
    ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
      val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
      v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
      insets
    }
    checkPermission()
  }


  private fun start(){
    isSkip = true
    startActivity(android.content.Intent(this, MainActivity::class.java))
  }


  private var isSkip = false
  override fun onRequestPermissionsResult(
    requestCode: Int,
    permissions: Array<out String>,
    grantResults: IntArray
  ) {
    super.onRequestPermissionsResult(requestCode, permissions, grantResults)
    if (requestCode == REQUEST_CODE_PERMISSION) {
      if (grantResults.isNotEmpty() && grantResults[0] == android.content.pm.PackageManager.PERMISSION_GRANTED) {
        start()
      } else {
        android.util.Log.d(TAG, "onRequestPermissionsResult: permission denied")
      }
    }
  }

  override fun onPause() {
    super.onPause()
    finish()
  }

  private fun checkPermission() {
    try {
      val permission = ActivityCompat.checkSelfPermission(this, android.Manifest.permission.WRITE_EXTERNAL_STORAGE)
      if (permission != android.content.pm.PackageManager.PERMISSION_GRANTED) {
        ActivityCompat.requestPermissions(this, PERMISSION_STORAGE, REQUEST_CODE_PERMISSION)
      }else{
        start()
      }
    }catch (e: Exception) {
      android.util.Log.e(TAG, "checkPermission: ", e)
    }
  }

  companion object {
    private const val TAG = "SplashActivity"
    private const val REQUEST_CODE_PERMISSION = 100
    private val PERMISSION_STORAGE = arrayOf(android.Manifest.permission.WRITE_EXTERNAL_STORAGE
    , android.Manifest.permission.READ_EXTERNAL_STORAGE,
      android.Manifest.permission.SYSTEM_ALERT_WINDOW)
  }
}