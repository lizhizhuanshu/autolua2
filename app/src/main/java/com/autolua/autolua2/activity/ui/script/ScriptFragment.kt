package com.autolua.autolua2.activity.ui.script

import android.content.Context
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.widget.AppCompatSpinner
import androidx.fragment.app.Fragment
import com.autolua.autolua2.R
import com.autolua.autolua2.databinding.FragmentScriptBinding
import com.autolua.autolua2.project.ProjectInfo
import com.autolua.autolua2.project.ProjectManager
import com.autolua.autolua2.project.ProjectManagerImp

class ScriptFragment: Fragment(), ProjectManager.ProjectObserver{
  private var _binding : FragmentScriptBinding? = null
  private val binding get() = _binding!!
  private val projectManager = ProjectManagerImp.instance
  private lateinit var mainHandler:Handler

  private val getContent = registerForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
    // 处理返回的文件 URI
    uri?.let {
      Log.e("ScriptFragment", "onActivityResult: $it")
      projectManager.addProject(it)
    }
  }


  class CustomSpinnerAdapter(context: Context, resource: Int, objects: List<String>) :
    ArrayAdapter<String>(context, resource, objects) {

    override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
      return initView(position, convertView, parent)
    }

    override fun getDropDownView(position: Int, convertView: View?, parent: ViewGroup): View {
      return initView(position, convertView, parent)
    }

    private fun initView(position: Int, convertView: View?, parent: ViewGroup): View {
      val view = convertView ?: LayoutInflater.from(context).inflate(android.R.layout.simple_spinner_item, parent, false)
      val textView = view.findViewById<TextView>(android.R.id.text1)
      textView.text = getItem(position)
      textView.textSize = 18f // 设置文本大小
      return view
    }
  }


  private fun onUpdateScriptList(projectInfo: List<ProjectInfo>){
    binding.scriptList.removeAllViews()
    for(oneProjectInfo in projectInfo){
      val view = LayoutInflater.from(context).inflate(R.layout.item_script,null)
      view.layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT,LinearLayout.LayoutParams.WRAP_CONTENT).also {
        val dpToPx = { dp: Int -> (dp * resources.displayMetrics.density).toInt() }
        it.setMargins(dpToPx(8),dpToPx(4),dpToPx(8),dpToPx(4))
      }
      view.findViewById<TextView>(R.id.script_name).text = oneProjectInfo.name
      val newestVersion = oneProjectInfo.getLastActionVersion()
      var description:String? = null
      for(version in oneProjectInfo.versions){
        if(version.name == newestVersion){
          description = version.description
          break
        }
      }
      val versionList = view.findViewById<AppCompatSpinner>(R.id.script_version)
      val names = oneProjectInfo.versions.map { it.name }
      versionList.adapter = CustomSpinnerAdapter(requireContext(),
        android.R.layout.simple_spinner_item,
        names)
      versionList.setSelection(oneProjectInfo.versions.indexOfFirst { it.name == newestVersion })
      val descriptionView = view.findViewById<TextView>(R.id.script_description)
      descriptionView.text = description
      versionList.onItemSelectedListener = object : AdapterView.OnItemSelectedListener{
        override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
          descriptionView.text = oneProjectInfo.versions.toList()[position].description
        }
        override fun onNothingSelected(parent: AdapterView<*>?) {
          descriptionView.text = ""
        }
      }
      view.findViewById<ImageButton>(R.id.script_run).setOnClickListener {
        val version = versionList.selectedItem as String
        projectManager.startProjectActivity(requireContext(), oneProjectInfo.name, version)
      }
      
      view.findViewById<ImageButton>(R.id.delete_script).setOnClickListener {
        val version = versionList.selectedItem as String
        projectManager.removeProject(oneProjectInfo.name, version)
      }

      binding.scriptList.addView(view)
    }
  }

  override fun onCreateView(
    inflater: LayoutInflater,
    container: ViewGroup?,
    savedInstanceState: Bundle?
  ): View {
    _binding = FragmentScriptBinding.inflate(inflater, container, false)
    val root: View = binding.root
    mainHandler = Handler(Looper.getMainLooper())
    val scriptList = projectManager.attach(this)
    onUpdateScriptList(scriptList)
    binding.addScript.setOnClickListener {
      getContent.launch("application/zip")
    }
    return root
  }

  override fun onDestroyView() {
    projectManager.detach(this)
    super.onDestroyView()
    _binding = null
  }

  override fun onProjectChanged() {
    val projectInfo = projectManager.getAllProjects()
    mainHandler.post {
      onUpdateScriptList(projectInfo)
    }
  }
}