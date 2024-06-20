package com.autolua.autolua2.activity.ui.my

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel

class MyViewModel: ViewModel(){
  private val _text = MutableLiveData<String>().apply {
    value = "this is my fragment"
  }
  val text: LiveData<String> = _text
}