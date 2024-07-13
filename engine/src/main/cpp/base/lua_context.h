//
// Created by lizhi on 2024/5/8.
//

#ifndef AUTOLUA2_LUA_CONTEXT_H
#define AUTOLUA2_LUA_CONTEXT_H
#include "jni.h"


struct  LuaContext{
    JNIEnv *env;
    jobject lua_context;
    jclass lua_context_class;
    jmethodID indexMethod;
    jmethodID newIndexMethod;
    jmethodID callMethod;
    jmethodID invokeMethod;
    jmethodID releaseMethod;
    jfieldID ptr;
    void* signalEvent;
    void* interpreter;
    void* display;
} ;

struct LuaObjectAdapter {
    jlong id;
};

#define toLuaObjectAdapterId(p) ((LuaObjectAdapter*)p)->id
#define  toLuaContext(L) (*(static_cast<LuaContext**>(lua_getextraspace(L))))

#endif //AUTOLUA2_LUA_CONTEXT_H
