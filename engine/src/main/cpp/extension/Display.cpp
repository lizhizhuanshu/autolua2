//
// Created by lizhi on 2022/4/22.
//

#include "Display.h"

#include <lua.hpp>
#include <memory.h>
#include "util.h"
#include "lua_vision.h"
#include <lodepng.h>
#include "lua_context.h"
#include "mlua.h"
#define DISPLAY_CLASS_NAME "com/autolua/engine/extension/display/Display"
#define BYTEBUFFER_CLASS_NAME "java/nio/ByteBuffer"
#define CLASS_ARG(className) "L" className ";"
#define ARGS(args) "(" args ")"

jclass Display::displayClassID_ = nullptr;
jmethodID Display::getRotationMethodID = nullptr;


jmethodID Display::initializeMethodID = nullptr;
jmethodID Display::isChangeDirectionMethodID = nullptr;
jmethodID Display::getDisplayBufferMethodID  = nullptr;
jmethodID Display::getHeightMethodID = nullptr;
jmethodID Display::getRowStrideMethodID  = nullptr;
jmethodID Display::getPixelStrideMethodID = nullptr;
jmethodID Display::getWidthMethodID = nullptr;
jmethodID Display::updateMethodID = nullptr;

Display::Display(jobject obj)
    : Bitmap(), keepDisplay_(false){
    JNIEnv*env = GetJNIEnv();
    obj_ = env->NewWeakGlobalRef(obj);
    jmethodID method = env->GetMethodID(displayClassID_,"getBaseWidth", "()I");
    baseWidth_ = env->CallIntMethod(obj_,method);
    method = env->GetMethodID(displayClassID_,"getBaseHeight","()I");
    baseHeight_ = env->CallIntMethod(obj_,method);
    method = env->GetMethodID(displayClassID_,"getBaseDensity","()I");
    baseDensity_ = env->CallIntMethod(obj_,method);
    method = env->GetMethodID(displayClassID_,"getBaseDirection","()I");
    baseDirection_ = env->CallIntMethod(obj_,method);
    update();
}

bool Display::isChangeDirection() {
    JNIEnv*env = GetJNIEnv();
    return env->CallBooleanMethod(obj_,isChangeDirectionMethodID);
}

bool Display::localReset(int w, int h) {
    JNIEnv*env = GetJNIEnv();
    int result = env->CallBooleanMethod(obj_, initializeMethodID, w, h);
    update();
    return  result;
}




int Display::screenshotToPng(int x, int y, int x1, int y1, std::vector<unsigned char> &out) {
    int rowSize = (x1-x)* pixelStride_;
    int size = rowSize*(y1-y);
    std::vector<unsigned char> data(size);
    unsigned char* index = data.data();
    unsigned char* ptr = origin_+x*pixelStride_+y*rowShift_;
    for (int i = y; i < y1; ++i) {
        memcpy(index,ptr,rowSize);
        index += rowSize;
        ptr += rowShift_;
    }
    lodepng::State state;
    state.info_raw.colortype  = pixelStride_ == 4? LodePNGColorType::LCT_RGBA:LodePNGColorType::LCT_RGB;
    auto r = lodepng::encode(out,data,x1-x,y1-y,state);
    if(r)
        return 2;
    return 0;
}


int Display::screenshot(int x,int y,int x1,int y1,SCREEN_SHOT_FORMAT format,std::vector<unsigned  char> &out) {
    if(isChangeDirection())
        localReset(0,0);
    else
        update();
    return _screenshot(x,y,x1,y1,format,out);
}


Display::~Display() {
    JNIEnv*env = GetJNIEnv();
    env->DeleteWeakGlobalRef(obj_);
}


void Display::update() {
#define LocalCallIntMethod(env,method) env->CallIntMethod(obj_,method)
    JNIEnv*env = GetJNIEnv();
    env->CallVoidMethod(obj_,updateMethodID);
    width_ = LocalCallIntMethod(env, getWidthMethodID);
    height_ = LocalCallIntMethod(env, getHeightMethodID);
    pixelStride_ = LocalCallIntMethod(env, getPixelStrideMethodID);
    rowShift_ = LocalCallIntMethod(env, getRowStrideMethodID);
    jobject buffer = env->CallObjectMethod(obj_,getDisplayBufferMethodID);
    origin_ = (unsigned char*)env->GetDirectBufferAddress(buffer);
    env->DeleteLocalRef(buffer);
}

int Display::getBaseSize(lua_State*L)
{
    auto * display = (Display*) lua_touserdata(L,1);
    lua_pushinteger(L,display->baseWidth_);
    lua_pushinteger(L,display->baseHeight_);
    return 2;
}


int Display::getBaseDensity(lua_State *L) {
    auto * display = (Display*) lua_touserdata(L,1);
    lua_pushinteger(L,display->baseDensity_);
    return 1;
}

int Display::getBaseDirection(lua_State *L) {
    auto * display = (Display*) lua_touserdata(L,1);
    lua_pushinteger(L,display->baseDirection_);
    return 1;
}

int Display::getRotation(lua_State *L) {
    auto * display = (Display*) lua_touserdata(L,1);
    auto r = display->getRotation();
    lua_pushinteger(L,r);
    return 1;
}

int Display::isChangeDirection(lua_State *L) {
    auto * display = (Display*) lua_touserdata(L,1);
    lua_pushboolean(L, display->isChangeDirection());
    return 1;
}

int Display::reset(lua_State*L)
{
    auto * display = (Display*) lua_touserdata(L,1);
    jint width = luaL_checkinteger(L,2);
    jint height = luaL_checkinteger(L,3);
    bool result= display->localReset(width,height);
    lua_pushboolean(L,result);
    return 1;
};

int Display::update(lua_State *L) {
    auto * display = (Display*) lua_touserdata(L,1);
    display->update();
    return 0;
}

int Display::keepDisplay(lua_State *L) {
    auto * display = (Display*) lua_touserdata(L,1);
    if(lua_isboolean(L,2)){
        display->keepDisplay_ = lua_toboolean(L,2);
        return 0;
    }
    lua_pushboolean(L,display->keepDisplay_);
    return 1;
}

int Display::updateAndKeepDisplay(lua_State *L) {
    auto * display = (Display*) lua_touserdata(L,1);
    display->keepDisplay_= true;
    display->update();
    return 0;
}

int Display::save(lua_State*L){
    auto * display = (Display*) lua_touserdata(L,1);
    int x = luaL_checkinteger(L,2);
    int y = luaL_checkinteger(L,3);
    int x1 = luaL_checkinteger(L,4);
    int y1 = luaL_checkinteger(L,5);
    const char* path = luaL_checkstring(L,6);
    Display::SCREEN_SHOT_FORMAT format = Display::kPng;
    if(lua_isstring(L,7)){
        const char* formatStr = lua_tostring(L,7);
        if(strcmp(formatStr,"png") == 0)
            format = Display::kPng;
        else if(strcmp(formatStr,"jpeg") == 0)
            format = Display::kJpeg;
        else
            luaL_error(L,"unknown format %s",formatStr);
    }
    std::vector<unsigned char> data;
    int result = display->_screenshot(x,y,x1,y1,format,data);
    if(result != 0){
        lua_pushboolean(L,false);
        return 1;
    }
    FILE* file = fopen(path,"wb");
    if(file == nullptr){
        lua_pushboolean(L,false);
        return 1;
    }
    fwrite(data.data(),1,data.size(),file);
    fclose(file);
    lua_pushboolean(L,true);
    return 1;
}

static int transitMethod(lua_State *L) {
    auto *display = (Display*) lua_touserdata(L,1);
    if(display->isChangeDirection()){
        luaL_error(L,"display_ rotate");
    }
    if (!display->isKeepDisplay()) {
        display->update();
    }
    auto method = (lua_CFunction) lua_touserdata(L,lua_upvalueindex(1));
    return method(L);
}

struct WrapCompareColorMethodContext{
    int tableIndex;
    lua_State *L;
    Display *display;
};

static void compareColorWrapper(const char* name, lua_CFunction method, void *data){
    auto context = (WrapCompareColorMethodContext*) data;
    lua_pushlightuserdata(context->L, (void*)method);
    lua_pushcclosure(context->L, transitMethod, 1);
    lua_setfield(context->L, context->tableIndex, name);
}

void Display::pushObjectToLua(lua_State *L,jobject obj) {
#define ONE_METHOD(name) {#name,name}
    luaL_Reg  method[] = {
            ONE_METHOD(getBaseSize),
            ONE_METHOD(getRotation),
            ONE_METHOD(getBaseDensity),
            ONE_METHOD(getBaseDirection),
            ONE_METHOD(isChangeDirection),
            ONE_METHOD(reset),
            ONE_METHOD(update),
            ONE_METHOD(keepDisplay),
            ONE_METHOD(updateAndKeepDisplay),
            ONE_METHOD(save),
            {"__gc",lua::finish<Display>},
            {nullptr,nullptr}
    };
#undef ONE_METHOD
    auto display = (Display*) lua_newuserdata(L,sizeof(Display));
    auto context = toLuaContext(L);
    context->display = display;
    new(display)Display(obj);
    luaL_newlib(L,method);
    WrapCompareColorMethodContext s{};
    s.L = L;
    s.tableIndex = lua_absindex(L,-1);
    s.display = display;
    eachCompareColorMethod(compareColorWrapper, &s);
    lua_pushvalue(L,-1);
    lua_setfield(L,-2,"__index");
    lua_setmetatable(L,-2);
}

void Display::releaseJavaDisplayClass(JNIEnv *env) {
    if(displayClassID_){
        env->DeleteWeakGlobalRef(displayClassID_);
        displayClassID_ = nullptr;
    }
}

void Display::initializeJavaDisplayClass(JNIEnv *env) {
#define SET_JAVA_METHOD(methodName,classID,result,...) methodName##MethodID = env->GetMethodID(classID,#methodName,ARGS(__VA_ARGS__)result)
    jobject local = env->FindClass(DISPLAY_CLASS_NAME);
    displayClassID_ = (jclass)env->NewWeakGlobalRef(local);
    env->DeleteLocalRef(local);
    SET_JAVA_METHOD(getRotation, displayClassID_, "I");
    SET_JAVA_METHOD(initialize, displayClassID_, "Z", "II");
    SET_JAVA_METHOD(getDisplayBuffer, displayClassID_, CLASS_ARG(BYTEBUFFER_CLASS_NAME));
    SET_JAVA_METHOD(getHeight, displayClassID_, "I");
    SET_JAVA_METHOD(getWidth, displayClassID_, "I");
    SET_JAVA_METHOD(getRowStride, displayClassID_, "I");
    SET_JAVA_METHOD(getPixelStride, displayClassID_, "I");
    SET_JAVA_METHOD(isChangeDirection, displayClassID_, "Z");
    SET_JAVA_METHOD(update, displayClassID_, "V");
#undef SET_JAVA_METHOD
}


int Display::_screenshot(int x, int y, int x1, int y1, Display::SCREEN_SHOT_FORMAT format,
                         std::vector<unsigned char> &out) {
    if(x == -1 && y == -1)
    {
        x = y = 0;
        x1 = width_;
        y1 = height_;
    }
    if (format == kPng)
        return screenshotToPng(x,y,x1,y1,out);
    return 0;
}

int Display::getRotation() {
    JNIEnv*env = GetJNIEnv();
    jint r = env->CallIntMethod(obj_,getRotationMethodID);
    return r;
}

