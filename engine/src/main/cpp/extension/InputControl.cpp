//
// Created by lizhi on 2022/5/19.
//

#include "InputControl.h"


#include "util.h"
#include "mlua.h"


InputControl::InputControl(jobject obj) {
    JNIEnv*env = GetJNIEnv();
    object_ = env->NewWeakGlobalRef(obj);
    jclass jc = env->FindClass("com/autolua/engine/extension/input/InputManager");
    touchDownMethodID = env->GetMethodID(jc,"touchDown", "(FFFFFF)I");
    touchMoveMethodID = env->GetMethodID(jc,"touchMove", "(IFFFFFF)Z");
    touchUpMethodID = env->GetMethodID(jc,"touchUp", "(I)Z");
    injectKeyEventMethodID = env->GetMethodID(jc, "injectKeyEvent", "(IIII)Z");
    releaseAllPointerMethodID = env->GetMethodID(jc,"releaseAllPointer","()V");
    class_ = (jclass)env->NewWeakGlobalRef(jc);
    env->DeleteLocalRef(jc);
}

InputControl::~InputControl() {
    JNIEnv*env = GetJNIEnv();
    env->DeleteWeakGlobalRef(object_);
    env->DeleteWeakGlobalRef(class_);
}

int InputControl::touchDown(lua_State *L) {
    auto input = lua::toObjectPointer<InputControl>(L,1);
    jfloat x = luaL_checknumber(L,2);
    jfloat y = luaL_checknumber(L,3);
    jfloat major = luaL_optnumber(L,4,0);
    jfloat minor = luaL_optnumber(L,5,0);
    jfloat pressure = luaL_optnumber(L,6,0);
    jfloat size = luaL_optnumber(L,7,0);
    JNIEnv *env = GetJNIEnv();
    jint result = env->CallIntMethod(input->object_,input->touchDownMethodID,x,y,major,minor,pressure,size);
    lua_pushinteger(L,result);
    return 1;
}

int InputControl::touchMove(lua_State *L) {
    auto input = lua::toObjectPointer<InputControl>(L,1);
    jint id = luaL_checkinteger(L,2);
    jfloat x = luaL_checknumber(L,3);
    jfloat y = luaL_checknumber(L,4);
    jfloat major = luaL_optnumber(L,5,0);
    jfloat minor = luaL_optnumber(L,6,0);
    jfloat pressure = luaL_optnumber(L,7,0);
    jfloat size = luaL_optnumber(L,8,0);
    JNIEnv *env = GetJNIEnv();
    jboolean result = env->CallBooleanMethod(input->object_,input->touchMoveMethodID,id,x,y,major,minor,pressure,size);
    lua_pushboolean(L,result);
    return 1;
}

int InputControl::touchUp(lua_State *L) {
    auto input = lua::toObjectPointer<InputControl>(L,1);
    jint id = luaL_checkinteger(L,2);
    JNIEnv *env = GetJNIEnv();
    jboolean  result = env->CallBooleanMethod(input->object_,input->touchUpMethodID,id);
    lua_pushboolean(L,result);
    return 1;
}

int InputControl::injectKeyEvent(lua_State *L) {
    auto input = lua::toObjectPointer<InputControl>(L,1);
    jint action = luaL_checkinteger(L,2);
    jint code = luaL_checkinteger(L,3);
    jint repeat = luaL_optinteger(L,4,0);
    jint metaState = luaL_optinteger(L,5,0);
    JNIEnv *env = GetJNIEnv();
    jboolean  result = env->CallBooleanMethod(input->object_,input->injectKeyEventMethodID,
                                              action,code,repeat,metaState);
    lua_pushboolean(L,result);
    return 1;
}

int InputControl::releaseAllPointer(lua_State *L) {
    JNIEnv *env = GetJNIEnv();
    auto input = lua::toObjectPointer<InputControl>(L,1);
    env->CallVoidMethod(input->object_,input->releaseAllPointerMethodID);
    return 0;
}

void InputControl::pushObjectToLua(struct lua_State *L) {
#define ONE_METHOD(name) {#name,name}
    luaL_Reg  method[] = {
            ONE_METHOD(touchUp),
            ONE_METHOD(touchMove),
            ONE_METHOD(touchDown),
            ONE_METHOD(injectKeyEvent),
            {"__gc", releaseAllPointer},
            {nullptr,nullptr}
    };
#undef ONE_METHOD
    lua::pushObjectPointer(L,this);
    luaL_newlib(L,method);
    lua_pushvalue(L,-1);
    lua_setfield(L,-2,"__index");
    lua_setmetatable(L,-2);
}