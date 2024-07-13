package com.autolua.engine.extension.node

import android.os.Build
import android.view.accessibility.AccessibilityNodeInfo

class ByMatcher(private val node:AccessibilityNodeInfo, private val uiSelector: UiSelector, private val one: Boolean = true) {

  fun find():List<UiObject>{
    val result = mutableListOf<UiObject>()
    find(node, 0,0, result)
    return result
  }

  private fun find(node:AccessibilityNodeInfo, depth:Int,index:Int, result:MutableList<UiObject>){
    if(matchesSelector(uiSelector, node, depth, index)){
      result.add(UiObject(node))
      if(one)
        return
    }
    val childCount = node.childCount
    for(i in 0 until childCount){
      val child = node.getChild(i)
      if(child != null)
        find(child, depth + 1,i, result)
      if(one && result.isNotEmpty())
        return
    }
  }


  companion object{
    private fun matchesSelector(selector: UiSelector, node:AccessibilityNodeInfo, depth:Int, index:Int):Boolean{
      return (selector.minDepth == null || depth >= selector.minDepth!!)
              && (selector.maxDepth == null || depth <= selector.maxDepth!!)
              && (selector.index == null || index == selector.index!!)
              && (selector.clickable == null || node.isClickable == selector.clickable!!)
              && (selector.checkable == null || node.isCheckable == selector.checkable!!)
              && (selector.checked == null || node.isChecked == selector.checked!!)
              && (selector.enabled == null || node.isEnabled == selector.enabled!!)
              && (selector.focusable == null || node.isFocusable == selector.focusable!!)
              && (selector.focused == null || node.isFocused == selector.focused!!)
              && (selector.longClickable == null || node.isLongClickable == selector.longClickable!!)
              && (selector.scrollable == null || node.isScrollable == selector.scrollable!!)
              && (selector.selected == null || node.isSelected == selector.selected!!)
              && (selector.desc == null || selector.desc!!.matcher(node.contentDescription).matches())
              && (selector.clazz == null || selector.clazz!!.matcher(node.className).matches())
              && (selector.pkg == null || selector.pkg!!.matcher(node.packageName).matches())
              && (selector.res == null || selector.res!!.matcher(node.viewIdResourceName).matches())
              && (selector.text == null || selector.text!!.matcher(node.text).matches())
              && matchesHint(selector, node)
              && matchesChild(selector, node, depth)
    }

    private fun matchesHint(selector: UiSelector, node:AccessibilityNodeInfo):Boolean{
      if(Build.VERSION.SDK_INT < Build.VERSION_CODES.O)
        return true
      return (selector.hint == null || selector.hint!!.matcher(node.hintText).matches())
    }

    private fun matchesChild(selector: UiSelector, node:AccessibilityNodeInfo,depth:Int):Boolean{
      if(selector.hasChild == null)
        return true
      val childCount = node.childCount
      for(i in 0 until childCount){
        val child = node.getChild(i)
        if(child != null && matchesSelector(selector.hasChild!!, child, depth, i))
          return true
      }
      return false
    }

  }





}