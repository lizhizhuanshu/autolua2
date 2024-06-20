package com.autolua.autolua2.activity.ui.my

import android.content.ComponentName
import android.content.Intent
import android.content.ServiceConnection
import android.os.Bundle
import android.os.IBinder
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import com.autolua.autolua2.databinding.FragmentMyBinding
import com.autolua.autolua2.engine.AutoLuaEngineService
import com.autolua.autolua2.engine.AutoLuaEngineServiceImp

class MyFragment:Fragment() {
  private var _binding: FragmentMyBinding? = null
  private val binding get() = _binding!!

  private var engineService:AutoLuaEngineService? = null

  private val engineObserver = {state:AutoLuaEngineService.State ->
    Log.d("MyFragment","engine state state:$state")
    changeViewWithState(state)
  }

  private fun changeViewWithState(state:AutoLuaEngineService.State){
    when(state){
      AutoLuaEngineService.State.IDLE -> {
        binding.engineSwitch.isChecked = false
        binding.engineSwitch.isEnabled = true
      }
      AutoLuaEngineService.State.STARTING -> {
        binding.engineSwitch.isChecked = true
        binding.engineSwitch.isEnabled = false
      }
      AutoLuaEngineService.State.RUNNING ->{
        binding.engineSwitch.isChecked = true
        binding.engineSwitch.isEnabled = true
      }
      AutoLuaEngineService.State.STOPPING -> {
        binding.engineSwitch.isChecked = false
        binding.engineSwitch.isEnabled = false
      }
    }
  }

  private val serviceConnection = object : ServiceConnection {
    override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
      val engine = service as AutoLuaEngineService
      engineService = engine
      engine.addObserver(0,engineObserver)
      changeViewWithState(engine.getState())
    }

    override fun onServiceDisconnected(name: ComponentName?) {
      engineService = null
    }
  }

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    Intent(requireContext(),AutoLuaEngineServiceImp::class.java).also { intent ->
      requireContext().bindService(intent,serviceConnection,0)
    }
  }



  override fun onCreateView(
    inflater: LayoutInflater,
    container: ViewGroup?,
    savedInstanceState: Bundle?
  ): View {
    _binding = FragmentMyBinding.inflate(inflater, container, false)
    val root: View = binding.root
    changeViewWithState(engineService?.getState()?:AutoLuaEngineService.State.IDLE)
    binding.engineSwitch.setOnClickListener {
      if(binding.engineSwitch.isChecked){
        engineService?.start()
      }else{
        engineService?.stop()
      }
//      changeViewWithState(engineService?.getState()?:AutoLuaEngineService.State.IDLE)
    }
    return root
  }

  override fun onViewStateRestored(savedInstanceState: Bundle?) {
    super.onViewStateRestored(savedInstanceState)
    changeViewWithState(engineService?.getState()?:AutoLuaEngineService.State.IDLE)
  }
}