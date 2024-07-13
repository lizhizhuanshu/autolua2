package com.autolua.engine.extension.node

import android.graphics.Rect
import android.os.Bundle
import android.view.accessibility.AccessibilityNodeInfo
import androidx.core.view.accessibility.AccessibilityNodeInfoCompat
import com.autolua.engine.base.LuaContext
import com.autolua.engine.base.LuaObjectAdapter

class UiObject(val node: AccessibilityNodeInfo):LuaObjectAdapter   {
  private var isRecycled = false
  override fun index(L: LuaContext): Int {
    val key = L.toString(2);
    when(key!!){
      "clazz" ->{
        L.push(node.className.toString())
      }
      "text" ->{
        L.push(node.text.toString())
      }
      "desc" ->{
        L.push(node.contentDescription.toString())
      }
      "pkg" ->{
        L.push(node.packageName.toString())
      }
      "res" ->{
        L.push(node.viewIdResourceName.toString())
      }
      "bounds" ->{
        val rect = Rect()
        node.getBoundsInScreen(rect)
        L.createTable(4,0)
        L.push(1L)
        L.push(rect.left.toLong())
        L.setTable(-3)
        L.push(2L)
        L.push(rect.top.toLong())
        L.setTable(-3)
        L.push(3L)
        L.push(rect.right.toLong())
        L.setTable(-3)
        L.push(4L)
        L.push(rect.bottom.toLong())
        L.setTable(-3)
      }
      "childCount" ->{
        L.push(node.childCount.toLong())
      }
      "child" ->{
        return 2
      }
      "parent" ->{
        val parent = node.parent
        if(parent != null){
          L.push(UiObject(parent))
        }else{
          L.pushNil()
        }
      }
      "clickable" ->{
        L.push(node.isClickable)
      }
      "checkable" ->{
        L.push(node.isCheckable)
      }
      "checked" ->{
        L.push(node.isChecked)
      }
      "focusable" ->{
        L.push(node.isFocusable)
      }
      "focused" ->{
        L.push(node.isFocused)
      }
      "scrollable" ->{
        L.push(node.isScrollable)
      }
      "selected" ->{
        L.push(node.isSelected)
      }
      "enabled" ->{
        L.push(node.isEnabled)
      }
      "editable" ->{
        L.push(node.isEditable)
      }
      "visibleToUser" ->{
        L.push(node.isVisibleToUser)
      }
      "longClickable" ->{
        L.push(node.isLongClickable)
      }
      "findFocus" ->{
        return 2
      }
      "setText" ->{
        return 2
      }
      "recycle" ->{
        return 2
      }
      "find" ->{
        return 2
      }
      "findOne" ->{
        return 2
      }

      else ->{
        L.pushNil()
      }
    }
    return 1
  }

  override fun newIndex(L: LuaContext): Int {
    TODO("Not yet implemented")
  }

  override fun call(L: LuaContext, name: String): Int {
    when(name){
      "child" ->{
        val index = L.toLong(2).toInt()
        val child = child(index)
        if(child == null) {
          L.pushNil()
        }else{
          L.push(child)
        }
      }
      "findFocus" ->{
        val focus = L.toLong(2).toInt()
        val child = findFocus(focus)
        if(child == null){
          L.pushNil()
        }else{
          L.push(child)
        }
      }
      "setText" ->{
        val text = L.toString(2)
        setText(text!!)
      }
      "recycle" ->{
        recycle()
      }
      "find"->{
        val selector = L.toLuaObjectAdapter(2) as UiSelector
        val one =false
        val result = find(selector, one)
        L.pushValue(result.toTypedArray(),Array<UiObject>::class.java)
      }
      "findOne"->{
        val selector = L.toLuaObjectAdapter(2) as UiSelector
        val one =true
        val result = find(selector, one)
        if(result.isNotEmpty()){
          L.push(result[0])
        }else{
          L.pushNil()
        }
      }
      else ->{
        L.pushNil()
      }
    }
    return 1
  }

  private fun recycle(){
    if(!isRecycled){
      node.recycle()
      isRecycled = true
    }
  }

  override fun invoke(L: LuaContext): Int {
    TODO("Not yet implemented")
  }

  override fun release(L: LuaContext) {
    recycle()
  }


  fun child(index:Int):UiObject?{
    val child = node.getChild(index)
    return if(child != null){
      UiObject(child)
    }else{
      null
    }
  }

  fun findFocus(focus:Int):UiObject?{
    val child = node.findFocus(focus)
    return if(child != null){
      UiObject(child)
    }else{
      null
    }
  }

  fun setText(text:String):Boolean{
    val b = Bundle()
    b.putCharSequence(AccessibilityNodeInfoCompat.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, text)
    return node.performAction(AccessibilityNodeInfoCompat.ACTION_SET_TEXT, b)
  }

  fun find(selector: UiSelector, one:Boolean):List<UiObject>{
    val matcher = ByMatcher(node,selector, one)
    return matcher.find()
  }

}