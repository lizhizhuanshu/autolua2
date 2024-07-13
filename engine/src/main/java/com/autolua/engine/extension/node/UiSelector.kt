package com.autolua.engine.extension.node


import android.annotation.SuppressLint
import com.autolua.engine.base.LuaContext
import com.autolua.engine.base.LuaObjectAdapter
import java.util.regex.Pattern
import androidx.test.uiautomator.util.Patterns;

class UiSelector:LuaObjectAdapter {
  var index:Int? = null
  var maxDepth:Int? = null
  var minDepth:Int? = null
  var clickable:Boolean? = null
  var checkable:Boolean? = null
  var checked:Boolean? = null
  var enabled:Boolean? = null
  var focusable:Boolean? = null
  var focused:Boolean? = null
  var hasChild: UiSelector? = null
  var longClickable:Boolean? = null
  var scrollable:Boolean? = null
  var selected:Boolean? = null


  var desc:Pattern? = null
  var clazz:Pattern? = null
  var pkg:Pattern? = null
  var res:Pattern? = null
  var text:Pattern? = null
  var hint :Pattern? = null

  override fun index(L: LuaContext): Int {
    return 2
  }

  override fun newIndex(L: LuaContext): Int {
    TODO("Not yet implemented")
  }

  private fun optTrue(L: LuaContext,index:Int):Boolean{
    val t = L.type(index)
    if(t == LuaContext.ValueType.BOOLEAN){
      return L.toBoolean(index)
    }
    return true
  }

  @SuppressLint("RestrictedApi")
  override fun call(L: LuaContext, name: String): Int {
    when(name){
      "checkable" ->{
        checkable = optTrue(L,2)
      }
      "checked" ->{
        checked = optTrue(L,2)
      }
      "clickable" ->{
        clickable = optTrue(L,2)
      }
      "depth" ->{
        minDepth = L.toLong(2).toInt()
        if(L.isInteger(3)){
          maxDepth = L.toLong(3).toInt()
        }else{
          maxDepth = minDepth
        }
      }
      "desc" ->{
        desc = Pattern.compile(Pattern.quote(L.toString(2)!!))
      }
      "descContains" ->{
        desc = Patterns.contains(L.toString(2)!!)
      }
      "descStartsWith" ->{
        desc = Patterns.startsWith(L.toString(2)!!)
      }
      "descEndsWith" ->{
        desc = Patterns.endsWith(L.toString(2)!!)
      }
      "descMatches" ->{
        desc = Pattern.compile(L.toString(2)!!)
      }
      "enabled" ->{
        enabled = optTrue(L,2)
      }
      "focusable" ->{
        focusable = optTrue(L,2)
      }
      "focused" ->{
        focused = optTrue(L,2)
      }
      "hasChild" ->{
        hasChild = L.toLuaObjectAdapter(2) as UiSelector
      }
      "longClickable" ->{
        longClickable = optTrue(L,2)
      }
      "maxDepth" ->{
        maxDepth = L.toLong(2).toInt()
      }
      "minDepth" ->{
        minDepth = L.toLong(2).toInt()
      }
      "pkg" ->{
        pkg = Pattern.compile(Pattern.quote(L.toString(2)!!))
      }
      "pkgMatches" ->{
        pkg = Pattern.compile(L.toString(2)!!)
      }
      "res" ->{
        res = Pattern.compile(Pattern.quote(L.toString(2)!!))
      }
      "resMatches" ->{
        res = Pattern.compile(L.toString(2)!!)
      }
      "scrollable" ->{
        scrollable = optTrue(L,2)
      }
      "selected" ->{
        selected = optTrue(L,2)
      }

      "text" ->{
        text = Pattern.compile(Pattern.quote(L.toString(2)!!))
      }
      "textContains" ->{
        text = Patterns.contains(L.toString(2)!!)
      }
      "textMatches" ->{
        text = Pattern.compile(L.toString(2)!!)
      }
      "textStartsWith" ->{
        text = Patterns.startsWith(L.toString(2)!!)
      }
      "textEndsWith" ->{
        text = Patterns.endsWith(L.toString(2)!!)
      }
      "clazz" ->{
        clazz = Pattern.compile(Pattern.quote(L.toString(2)!!))
      }
      "clazzMatches" ->{
        clazz = Pattern.compile(L.toString(2)!!)
      }
      "clazzContains" ->{
        clazz = Patterns.contains(L.toString(2)!!)
      }
      "clazzStartsWith" ->{
        clazz = Patterns.startsWith(L.toString(2)!!)
      }
      "clazzEndsWith" ->{
        clazz = Patterns.endsWith(L.toString(2)!!)
      }
      "hint"->{
        hint = Pattern.compile(Pattern.quote(L.toString(2)!!))
      }
      "hintContains" ->{
        hint = Patterns.contains(L.toString(2)!!)
      }
      "hintMatches" ->{
        hint = Pattern.compile(L.toString(2)!!)
      }
      "hintStartsWith" ->{
        hint = Patterns.startsWith(L.toString(2)!!)
      }
      "hintEndsWith" ->{
        hint = Patterns.endsWith(L.toString(2)!!)
      }
      "index" ->{
        index = L.toLong(2).toInt()
      }
      else ->{
        throw IllegalArgumentException("Unknown method: $name")
      }
    }
    L.pushValue(1)
    return 1
  }

  override fun invoke(L: LuaContext): Int {
    TODO("Not yet implemented")
  }

  override fun release(L: LuaContext) {
  }


}