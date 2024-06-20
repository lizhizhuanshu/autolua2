package com.autolua.autolua2.activity.ui.debug

import android.content.ComponentName
import android.content.Intent
import android.content.ServiceConnection
import android.os.Bundle
import android.os.IBinder
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import com.autolua.autolua2.databinding.FragmentDebugBinding
import androidx.core.widget.addTextChangedListener
import com.autolua.autolua2.base.Constants
import com.autolua.autolua2.activity.PreferencesHelper
import com.autolua.autolua2.debug.DebugService
import com.autolua.autolua2.debug.DebugServiceImp

class DebugFragment: Fragment(){
  private var _binding : FragmentDebugBinding? = null
  private val binding get() = _binding!!
  private lateinit var debugViewModel: DebugViewModel
  private var bindDebug = false
  private lateinit var helper: PreferencesHelper

  private fun changeConfigureView(active:Boolean){
    binding.listenPort.isEnabled = active
    binding.debugToken.isEnabled = active
    binding.switchBroadcastLocation.isEnabled = active
  }

  private fun changeViewFromState(state:DebugService.State){
    when(state){
      DebugService.State.IDLE ->{
        binding.switchAutoDebug.isChecked=false
        binding.switchAutoDebug.isEnabled=true
        changeConfigureView(true)
      }
      DebugService.State.STARTING -> binding.switchAutoDebug.isEnabled=false
      DebugService.State.RUNNING -> {
        binding.switchAutoDebug.isEnabled=true
        binding.switchAutoDebug.isChecked=true
        changeConfigureView(false)
      }
      DebugService.State.STOPPING -> binding.switchAutoDebug.isEnabled=false
    }
  }

  @Volatile
  private var debugService: DebugService? = null
  private val debugServiceObserver = {state:DebugService.State,flags:Int->
    changeViewFromState(state)
  }
  private val serviceConnection: ServiceConnection = object : ServiceConnection {
    override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
      debugService = (service as DebugService)
      debugService?.addObserver(debugServiceObserver)
      startDebugService(debugService!!)
    }

    override fun onServiceDisconnected(name: ComponentName?) {
      debugService = null
      bindDebug = false
    }
  }

  override fun onDestroy() {
    super.onDestroy()
    debugService?.removeObserver(debugServiceObserver)
    requireContext().unbindService(serviceConnection)
  }

  private fun startDebugService(service:DebugService){
    val port = binding.listenPort.text.toString().toIntOrNull()?: Constants.DEBUG_PORT
    val token = binding.debugToken.text.toString()
    val broadcastLocation = binding.switchBroadcastLocation.isChecked
    startDebugService(service,port,token,broadcastLocation)
  }

  private fun startDebugService(service:DebugService,
                                port:Int,
                                token:String,
                                broadcastLocation:Boolean){
    service.set(port,token,broadcastLocation)
    service.start()
  }

  override fun onCreateView(
    inflater: LayoutInflater,
    container: ViewGroup?,
    savedInstanceState: Bundle?
  ): View {
    helper = PreferencesHelper(requireContext())
    debugViewModel = ViewModelProvider(this)[DebugViewModel::class.java]
    _binding = FragmentDebugBinding.inflate(inflater, container, false)
    val root: View = binding.root
    debugViewModel.myIP.observe(viewLifecycleOwner){
      binding.myIp.text = it
    }

    binding.switchBroadcastLocation.isChecked = helper.getBoolean("broadcastLocation", false)
    binding.switchBroadcastLocation.setOnCheckedChangeListener { _, isChecked ->
      helper.saveBoolean("broadcastLocation", isChecked)
    }

    binding.listenPort.setText(
      helper.getInt("listenPort", Constants.DEBUG_PORT).toString())

    binding.listenPort.addTextChangedListener {
      val value = it.toString()
      helper.saveInt("listenPort", value.toIntOrNull() ?: Constants.DEBUG_PORT)
    }

    binding.debugToken.setText(
      helper.getString("debugToken", Constants.DEBUG_DEFAULT_TOKEN))

    binding.debugToken.addTextChangedListener {
      val value = it.toString()
      helper.saveString("debugToken", value)
    }

    binding.switchAutoDebug.setOnClickListener {
      val service = debugService
      if(service == null){
        if(!bindDebug){
          bindDebug = true
          changeConfigureView(false)
          Intent(requireContext(), DebugServiceImp::class.java).also { intent ->
            requireContext().startService(intent)
            requireContext().bindService(intent, serviceConnection, 0)
          }
        }
        return@setOnClickListener
      }

      val isChecked = binding.switchAutoDebug.isChecked
      binding.switchAutoDebug.isChecked = !isChecked
      if(isChecked){
        startDebugService(service)
      }else{
        service.stop()
      }
    }

    return root
  }

  override fun onViewStateRestored(savedInstanceState: Bundle?) {
    super.onViewStateRestored(savedInstanceState)
    changeViewFromState(debugService?.getState() ?: DebugService.State.IDLE)
  }
}