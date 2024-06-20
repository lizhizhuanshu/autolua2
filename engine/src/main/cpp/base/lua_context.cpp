//
// Created by lizhi on 2024/5/8.
//

#include "lua_context.h"

#include "util.h"
#include "mlua.h"

#define toLuaState(L) ((lua_State*)L)

#define PUSH_THROWABLE_ERROR "push java throwable error"


static void throwTypeError(JNIEnv *env, lua_State*L,int index,int exception)
{
    char buffer[1024];
    const char* exceptionName = lua_typename(L,exception);
    const char* nowName = lua_typename(L,lua_type(L,index));
    sprintf(buffer,"luaState %p  index %d exception type %s now type %s",L,index,exceptionName,nowName);
    LocalReference<jclass> jobj(env,env->FindClass("com/autolua/engine/base/LuaTypeError"));
    env->ThrowNew(jobj.get(),buffer);
}

extern "C"  JNIEXPORT jlong JNICALL
Java_com_autolua_engine_base_LuaContextImplement_00024Companion_toInteger(JNIEnv *env,
                                                                          jobject jobj, jlong native_lua,
                                                           jint index) {
    lua_State *L =toLuaState(native_lua);
    if (lua_isnumber(L,index))
    {
        return (jlong)lua_tointeger(L,index);
    } else{
        throwTypeError(env, L, index,LUA_TNUMBER);
    }
    return 0;
}



extern "C" JNIEXPORT jdouble JNICALL
Java_com_autolua_engine_base_LuaContextImplement_00024Companion_toNumber(JNIEnv *env, jobject jobj, jlong native_lua,
                                                          jint index) {
    lua_State *L =toLuaState(native_lua);
    if (lua_isnumber(L,index))
    {
        return (jdouble)lua_tonumber(L,index);
    }else{
        throwTypeError(env,L,index,LUA_TNUMBER);
    }
    return 0;
}

extern "C" JNIEXPORT jboolean JNICALL
Java_com_autolua_engine_base_LuaContextImplement_00024Companion_toBoolean(JNIEnv *env, jobject jobj, jlong native_lua,
                                                           jint index) {
    lua_State *L =toLuaState(native_lua);
    return lua_toboolean(L,index);
}

extern "C" JNIEXPORT jbyteArray JNICALL
Java_com_autolua_engine_base_LuaContextImplement_00024Companion_toBytes(JNIEnv *env, jobject jobj, jlong native_lua,
                                                         jint index) {
    lua_State *L =toLuaState(native_lua);
    if(lua_isstring(L,index))
    {
        size_t len = 0;
        const char* str = lua_tolstring(L,index,&len);
        jbyteArray result = env->NewByteArray(len);
        if (env->ExceptionCheck())
            return nullptr;
        env->SetByteArrayRegion(result,0,len,(jbyte*)str);
        return result;
    } else{
        throwTypeError(env,L,index,LUA_TSTRING);
    }
    return nullptr;
}

extern "C" JNIEXPORT jstring JNICALL
Java_com_autolua_engine_base_LuaContextImplement_00024Companion_toString(JNIEnv *env, jobject jobj, jlong native_lua,
                                                          jint index) {
    lua_State *L =toLuaState(native_lua);
    if(lua_isstring(L,index))
    {
        size_t len = 0;
        const char* str = lua_tolstring(L,index,&len);
        jstring result = env->NewStringUTF(str);
        return result;
    } else{
        throwTypeError(env,L,index,LUA_TSTRING);
    }
    return nullptr;
}


extern "C" JNIEXPORT void JNICALL
Java_com_autolua_engine_base_LuaContextImplement_00024Companion_pushNil(JNIEnv *env, jobject jobj, jlong native_lua) {
    lua_pushnil(toLuaState(native_lua));
}

extern "C" JNIEXPORT void JNICALL
Java_com_autolua_engine_base_LuaContextImplement_00024Companion_push__JJ(JNIEnv *env, jobject jobj, jlong native_lua,
                                                          jlong v) {
    lua_pushinteger(toLuaState(native_lua),v);
}

extern "C" JNIEXPORT void JNICALL
Java_com_autolua_engine_base_LuaContextImplement_00024Companion_push__JD(JNIEnv *env, jobject jobj, jlong native_lua,
                                                          jdouble v) {
    lua_pushnumber(toLuaState(native_lua),v);
}


extern "C"
JNIEXPORT void JNICALL
Java_com_autolua_engine_base_LuaContextImplement_00024Companion_push__J_3B(JNIEnv *env, jobject jobj,
                                                            jlong native_lua, jbyteArray v) {
    if (v){
        LocalJavaBytes localJavaBytes(env,v);
        lua_pushlstring(toLuaState(native_lua),localJavaBytes.str(),localJavaBytes.size());
    } else{
        lua_pushnil(toLuaState(native_lua));
    }
}

extern "C"
JNIEXPORT void JNICALL
Java_com_autolua_engine_base_LuaContextImplement_00024Companion_push__JZ(JNIEnv *env, jobject jobj,
                                                          jlong native_lua, jboolean v) {
    lua_pushboolean(toLuaState(native_lua),v);
}

extern "C"
JNIEXPORT void JNICALL
Java_com_autolua_engine_base_LuaContextImplement_00024Companion_pushValue(JNIEnv *env, jobject jobj,
                                                           jlong native_lua, jint index) {
    lua_pushvalue(toLuaState(native_lua),index);
}

extern "C" JNIEXPORT jint JNICALL
Java_com_autolua_engine_base_LuaContextImplement_00024Companion_type(JNIEnv *env, jobject jobj, jlong native_lua, jint index) {
    return lua_type(toLuaState(native_lua),index);
}

extern "C" JNIEXPORT jboolean JNICALL
Java_com_autolua_engine_base_LuaContextImplement_00024Companion_isInteger(JNIEnv *env, jobject jobj, jlong native_value,
                                                           jint index) {
    return lua_isinteger(toLuaState(native_value),index);
}

extern "C" JNIEXPORT jint JNICALL
Java_com_autolua_engine_base_LuaContextImplement_00024Companion_getTop(JNIEnv *env, jobject jobj, jlong native_lua) {
    return lua_gettop(toLuaState(native_lua));
}

extern "C" JNIEXPORT void JNICALL
Java_com_autolua_engine_base_LuaContextImplement_00024Companion_setTop(JNIEnv *env, jobject jobj, jlong native_lua,
                                                        jint index) {
    lua_settop(toLuaState(native_lua),index);
}



static const char* getCodeMode(int t)
{
    const char* mode;
    if (t == 0)
        mode = "bt";
    else if(t == 1)
        mode = "t";
    else
        mode = "b";
    return mode;
}

static bool checkLuaLoadOrCallError(lua_State*L,JNIEnv*env,int code)
{
    if (code == LUA_OK)
        return false;
    jclass clazz  = env->FindClass("com/autolua/engine/base/LuaError");
    const char* message;
    if (lua_isstring(L,-1))
        message = lua_tostring(L,-1);
    else
        message = "unknown error";
    env->ThrowNew(clazz,message);
    return true;
}

extern "C" JNIEXPORT void JNICALL
Java_com_autolua_engine_base_LuaContextImplement_00024Companion_loadBuffer(JNIEnv *env, jobject jobj, jlong native_lua,
                                                            jbyteArray code, jstring chunk_name,
                                                            jint code_type) {
    LocalJavaBytes codeWrapper(env,code);
    LocalJavaString chunkName(env,chunk_name);
    lua_State *L = toLuaState(native_lua);
    int result= luaL_loadbufferx(L,
                                 codeWrapper.str(), codeWrapper.size(), chunkName.str(), getCodeMode(code_type));
    checkLuaLoadOrCallError(L,env,result);
}


extern "C" JNIEXPORT void JNICALL
Java_com_autolua_engine_base_LuaContextImplement_00024Companion_loadFile(JNIEnv *env, jobject jobj, jlong native_lua,
                                                          jstring file_name, jint code_type) {
    LocalJavaString fileName(env,file_name);
    lua_State *L = toLuaState(native_lua);
    int result = luaL_loadfilex(L, fileName.str(), getCodeMode(code_type));
    checkLuaLoadOrCallError(L,env,result);
}


extern "C" JNIEXPORT void JNICALL
Java_com_autolua_engine_base_LuaContextImplement_00024Companion_pop(JNIEnv *env, jobject jobj, jlong native_lua, jint n) {
    lua_pop(toLuaState(native_lua), n);
}


extern "C" JNIEXPORT jlong JNICALL
Java_com_autolua_engine_base_LuaContextImplement_00024Companion_toPointer(JNIEnv *env, jobject jobj, jlong native_lua,
                                                           jint index) {
    return (jlong)lua_topointer(toLuaState(native_lua),index);
}

#define NewSetMethod(name) static int SetMethod##name(lua_State*L) \
{\
    int tableIndex = lua_tointeger(L,lua_upvalueindex(1));\
    lua_##name(L,tableIndex);\
    return 0;\
}

NewSetMethod(settable)
NewSetMethod(rawset)

static int luaProtectCall(lua_State*L,jint tableIndex,lua_CFunction method,int resultSum)
{

    int oldTop = lua_gettop(L);
    lua_pushinteger(L,tableIndex);
    lua_pushcclosure(L,method,1);
    for (int i = 1; i <=oldTop; ++i) {
        lua_pushvalue(L,i);
    }
    return lua_pcall(L,oldTop,resultSum,0);
}

static void setTable(JNIEnv*env,jlong native_lua,jint tableIndex,lua_CFunction method)
{
    lua_State *L = toLuaState(native_lua);
    int result = luaProtectCall(L,tableIndex,method,0);
    checkLuaLoadOrCallError(L,env,result);
    lua_pop(L,2);
}

extern "C" JNIEXPORT void JNICALL
Java_com_autolua_engine_base_LuaContextImplement_00024Companion_setTable(JNIEnv *env, jobject jobj, jlong native_lua,
                                                          jint table_index) {
    setTable(env,native_lua,table_index,SetMethodsettable);
}

#define NewGetMethod(name) static int GetMethod##name(lua_State*L)\
{\
    int tableIndex = lua_tointeger(L,lua_upvalueindex(1));\
    int result = lua_##name(L,tableIndex);\
    lua_pushinteger(L,result);\
    return 2;\
}

NewGetMethod(gettable)
NewGetMethod(rawget)

static jint getTable(JNIEnv*env,jlong native_lua,jint table_index,lua_CFunction method)
{
    lua_State *L = toLuaState(native_lua);
    jint result = luaProtectCall(L,table_index,method,2);
    if(!checkLuaLoadOrCallError(L,env,result))
    {
        result = lua_tointeger(L,-1);
        lua_copy(L,-2,-3);
        lua_pop(L,2);
    } else{
        lua_pop(L,1);
    }
    return result;
}

extern "C" JNIEXPORT jint
JNICALL
Java_com_autolua_engine_base_LuaContextImplement_00024Companion_getTable(JNIEnv *env, jobject jobj,
                                                          jlong native_lua, jint table_index) {
    return getTable(env,native_lua,table_index,GetMethodgettable);
}


extern "C"
JNIEXPORT void JNICALL
Java_com_autolua_engine_base_LuaContextImplement_00024Companion_rawSet(JNIEnv *env, jobject jobj,
                                                        jlong native_lua, jint table_index) {
    setTable(env,native_lua,table_index,SetMethodrawset);
}

extern "C"
JNIEXPORT jint JNICALL
Java_com_autolua_engine_base_LuaContextImplement_00024Companion_rawGet(JNIEnv *env, jobject jobj,
                                                        jlong native_lua, jint table_index) {
    return getTable(env,native_lua,table_index,GetMethodrawget);
}

static int lua_setGlobal(lua_State*L)
{
    auto context = toLuaContext(L);
    auto jKey = (jstring)lua_touserdata(L,1);
    LocalJavaString key(context->env,jKey);
    lua_setglobal(L,key.str());
    return 0;
}

extern "C"
JNIEXPORT void JNICALL
Java_com_autolua_engine_base_LuaContextImplement_setGlobal(JNIEnv *env, jclass clazz,
                                                           jlong native_lua, jstring key) {
    lua_State *L = toLuaState(native_lua);
    lua_pushcfunction(L,lua_setGlobal);
    lua_pushlightuserdata(L,key);
    lua_pushvalue(L,-3);
    int result = lua_pcall(L,2,0,0);
    checkLuaLoadOrCallError(L,env,result);
    lua_pop(L,1);
}

static int lua_getGlobal(lua_State*L)
{
    auto context = toLuaContext(L);
    auto jKey = (jstring)lua_touserdata(L,1);
    LocalJavaString key(context->env,jKey);
    int result = lua_getglobal(L,key.str());
    lua_pushinteger(L,result);
    return 2;
}

extern "C"
JNIEXPORT jint JNICALL
Java_com_autolua_engine_base_LuaContextImplement_00024Companion_getGlobal(JNIEnv *env, jobject jobj,
                                                           jlong native_lua, jstring key) {
    lua_State *L = toLuaState(native_lua);
    lua_pushcfunction(L,lua_getGlobal);
    lua_pushlightuserdata(L,key);
    jint result = lua_pcall(L,1,2,0);
    if (!checkLuaLoadOrCallError(L,env,result))
    {
        result = lua_tointeger(L,-1);
        lua_pop(L,1);
    }
    return result;
}


extern "C"
JNIEXPORT void JNICALL
Java_com_autolua_engine_base_LuaContextImplement_00024Companion_createTable(JNIEnv *env, jobject jobj,
                                                             jlong native_lua, jint array_size,
                                                             jint dictionary_size) {
    lua_createtable(toLuaState(native_lua),array_size,dictionary_size);
}




extern "C"
JNIEXPORT jlong JNICALL
Java_com_autolua_engine_base_LuaContextImplement_00024Companion_toLuaObjectAdapter(JNIEnv *env, jobject jobj,
                                                                    jlong native_lua,
                                                                    jint index) {
    auto L = toLuaState(native_lua);
    auto r = luaL_testudata(L,index, CLASS_METATABLE_NAME(LuaObjectAdapter));
    if(r){
        return toLuaObjectAdapterId(r);
    }
    return 0;
}


static int pushJavaThrowable(JNIEnv* env,lua_State*L,jthrowable throwable)
{
    LocalReference<jclass> clazz(env,env->FindClass("java/lang/Throwable"));
    jmethodID method = env->GetMethodID(clazz.get(),"getMessage", "()Ljava/lang/String;");
    auto message = (jstring)env->CallObjectMethod(throwable,method);
    if (!env->ExceptionCheck())
    {
        LocalJavaString cMessage(env, message);
        lua_pushlstring(L, cMessage.str(), cMessage.size());
        return 1;
    }
    env->ExceptionDescribe();
    env->ExceptionClear();
    return 0;
}

static int catchAndPushJavaThrowable(JNIEnv *env, lua_State*L)
{
    LocalReference<jthrowable> throwable(env,env->ExceptionOccurred());
    if (throwable.get() != nullptr)
    {
        env->ExceptionDescribe();
        env->ExceptionClear();
        if (!pushJavaThrowable(env,L,throwable.get()))
            lua_pushstring(L,PUSH_THROWABLE_ERROR);
        return 1;
    }
    return 0;
}


static int callMethod(lua_State*L){
    auto context = toLuaContext(L);
    auto env = context->env;
    auto adapter = luaL_checkObject(LuaObjectAdapter,L,1);
    const char* methodName = lua_tostring(L, lua_upvalueindex(1));
    jstring  name = env->NewStringUTF(methodName);
    auto result = env->CallIntMethod(context->lua_context, context->callMethod, adapter->id, name);
    if(catchAndPushJavaThrowable(env,L)){
        env->DeleteLocalRef(name);
        lua_error(L);
    }
    env->DeleteLocalRef(name);
    return result;
}


static int indexMethod(lua_State*L){
    auto context = toLuaContext(L);
    auto env = context->env;
    auto adapter = (LuaObjectAdapter*)lua_touserdata(L,1);
    const char* methodName = lua_tostring(L,2);
    jstring  name = env->NewStringUTF(methodName);
    auto has = env->CallBooleanMethod(context->lua_context, context->hasMethod, adapter->id, name);
    if(catchAndPushJavaThrowable(env,L)){
        env->DeleteLocalRef(name);
        lua_error(L);
    }
    env->DeleteLocalRef(name);
    if (not has)
        return 0;
    lua_pushvalue(L,2);
    lua_pushcclosure(L,callMethod,1);
    return 1;
}

static int release(lua_State*L){
    auto context = toLuaContext(L);
    auto env = context->env;
    auto adapter = luaL_checkObject(LuaObjectAdapter,L,1);
    env->CallVoidMethod(context->lua_context, context->releaseMethod, adapter->id);
    if(catchAndPushJavaThrowable(env,L)){
        lua_error(L);
    }
    return 0;
}



extern "C"{
JNIEXPORT jlong JNICALL
Java_com_autolua_engine_base_LuaContextImplement_createLuaContext(JNIEnv *env, jobject thiz) {
    auto context = new LuaContext();
    context->env = env;
    context->lua_context = env->NewWeakGlobalRef(thiz);
    context->lua_context_class = (jclass)env->NewWeakGlobalRef(env->GetObjectClass(thiz));
    context->hasMethod = env->GetMethodID(context->lua_context_class,"hasMethod","(JLjava/lang/String;)Z");
    context->callMethod = env->GetMethodID(context->lua_context_class,"callMethod","(JLjava/lang/String;)I");
    context->releaseMethod = env->GetMethodID(context->lua_context_class,"release","(J)V");
    context->ptr = env->GetFieldID(context->lua_context_class,"nativeLua","J");
    auto L = luaL_newstate();
    luaL_openlibs(L);
    void *p = lua_getextraspace(L);
    *(static_cast<LuaContext**>(p)) = context;

    if (luaL_newClassMetatable(LuaObjectAdapter,L)){
        luaL_Reg methods[] = {
                {"__index",indexMethod},
                {"__gc",release},
                {nullptr, nullptr}
        };
        luaL_setfuncs(L,methods,0);
    }

    return (jlong)L;
}

}

extern "C"
JNIEXPORT void JNICALL
Java_com_autolua_engine_base_LuaContextImplement_00024Companion_destroy(JNIEnv *env, jobject thiz,
                                                                        jlong native_lua) {
    auto L = toLuaState(native_lua);
    auto context = toLuaContext(L);
    lua_close(L);
    env->DeleteWeakGlobalRef(context->lua_context);
    env->DeleteWeakGlobalRef(context->lua_context_class);
    delete context;
}


extern "C"
JNIEXPORT void JNICALL
Java_com_autolua_engine_base_LuaContextImplement_pushLuaObjectAdapter(JNIEnv *env, jclass clazz,
                                                                      jlong native_lua,
                                                                      jlong adapter_id) {
    auto L = toLuaState(native_lua);
    auto adapter = luaL_pushNewObject(LuaObjectAdapter,L);
    adapter->id = adapter_id;
}
extern "C"
JNIEXPORT jint JNICALL
Java_com_autolua_engine_base_LuaContextImplement_00024Companion_pcall(JNIEnv *env, jobject thiz,
                                                                      jlong native_lua, jint n_args,
                                                                      jint n_results,
                                                                      jint err_func){
    auto L = toLuaState(native_lua);
    return lua_pcall(L,n_args,n_results,err_func);
}
