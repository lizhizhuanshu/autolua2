package com.autolua.autolua2.activity.ui.my


import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import com.autolua.autolua2.databinding.FragmentMyBinding
import com.autolua.engine.core.AutoLuaEngine
import com.autolua.engine.core.AutoLuaEngineProxy

class MyFragment:Fragment() {
  private var _binding: FragmentMyBinding? = null
  private val binding get() = _binding!!

  private var engineService: AutoLuaEngine = AutoLuaEngineProxy.instance

  private val engineObserver = {state:AutoLuaEngine.State ->
    Log.d("MyFragment","engine state state:$state")
    changeViewWithState(state)
  }

  private fun changeViewWithState(state:AutoLuaEngine.State){
    when(state){
      AutoLuaEngine.State.IDLE -> {
        binding.engineSwitch.isChecked = false
        binding.engineSwitch.isEnabled = true
      }
      AutoLuaEngine.State.STARTING -> {
        binding.engineSwitch.isChecked = true
        binding.engineSwitch.isEnabled = false
      }
      AutoLuaEngine.State.RUNNING ->{
        binding.engineSwitch.isChecked = true
        binding.engineSwitch.isEnabled = true
      }
      AutoLuaEngine.State.STOPPING -> {
        binding.engineSwitch.isChecked = false
        binding.engineSwitch.isEnabled = false
      }
    }
  }


  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    engineService.addObserver(engineObserver)
  }

  override fun onDestroy() {
    super.onDestroy()
    engineService.removeObserver(engineObserver)
  }



  override fun onCreateView(
    inflater: LayoutInflater,
    container: ViewGroup?,
    savedInstanceState: Bundle?
  ): View {
    _binding = FragmentMyBinding.inflate(inflater, container, false)
    val root: View = binding.root
    changeViewWithState(engineService.getState())
    binding.engineSwitch.setOnClickListener {
      if(binding.engineSwitch.isChecked){
        engineService.start()
      }else{
        engineService.stop()
      }
//      changeViewWithState(engineService?.getState()?:AutoLuaEngineService.State.IDLE)
    }
    return root
  }

  override fun onViewStateRestored(savedInstanceState: Bundle?) {
    super.onViewStateRestored(savedInstanceState)
    changeViewWithState(engineService.getState())
  }
}