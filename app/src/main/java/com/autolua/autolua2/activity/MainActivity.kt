package com.autolua.autolua2.activity

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.addCallback
import com.google.android.material.bottomnavigation.BottomNavigationView
import androidx.appcompat.app.AppCompatActivity
import androidx.navigation.findNavController
import androidx.navigation.ui.AppBarConfiguration
import androidx.navigation.ui.setupActionBarWithNavController
import androidx.navigation.ui.setupWithNavController
import com.autolua.autolua2.MainService
import com.autolua.autolua2.R
import com.autolua.autolua2.databinding.ActivityMainBinding

class MainActivity : AppCompatActivity() {

  private lateinit var binding: ActivityMainBinding

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)

    binding = ActivityMainBinding.inflate(layoutInflater)
    setContentView(binding.root)
    val navView: BottomNavigationView = binding.navView

    val navController = findNavController(R.id.nav_host_fragment_activity_main)
    // Passing each menu ID as a set of Ids because each
    // menu should be considered as top level destinations.
    val appBarConfiguration = AppBarConfiguration(
      setOf(
        R.id.navigation_debug, R.id.navigation_script, R.id.navigation_my
      )
    )
    setupActionBarWithNavController(navController, appBarConfiguration)
    navView.setupWithNavController(navController)

    onBackPressedDispatcher.addCallback {
      val nowTime = System.currentTimeMillis()
      if (nowTime - lastBackTime < 3000) {
        stopService(Intent(this@MainActivity, MainService::class.java))
        finish()
      } else {
        lastBackTime = nowTime
        Toast.makeText(this@MainActivity, "再按一次退出", Toast.LENGTH_SHORT).show()
      }
    }
  }
  private var lastBackTime = 0L
}