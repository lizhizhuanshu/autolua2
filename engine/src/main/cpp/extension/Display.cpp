//
// Created by lizhi on 2022/4/22.
//

#include "Display.h"

#include <lua.hpp>
#include <memory.h>
#include <mutex>
#include <csetjmp>
#include "util.h"
#include "lua_vision.h"
#include <lodepng.h>
#include "lua_context.h"
#include "mlua.h"
#include "my_log.h"
#define DISPLAY_CLASS_NAME "com/autolua/engine/extension/display/Display"
#define BYTEBUFFER_CLASS_NAME "java/nio/ByteBuffer"
#define CLASS_ARG(className) "L" className ";"
#define ARGS(args) "(" args ")"

static jmp_buf panic_jump;


Display::Display(jobject obj)
    : Bitmap(), keepDisplay_(false){
    JNIEnv*env = GetJNIEnv();
    obj_ = env->NewWeakGlobalRef(obj);

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
    std::shared_lock<std::shared_mutex> lock(mutex_);
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
    env->DeleteWeakGlobalRef(displayClassID_);
    env->DeleteWeakGlobalRef(obj_);
}


void Display::update() {
    std::unique_lock<std::shared_mutex> lock(mutex_);
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
    auto * display = (Display*) lua_touserdata(L, lua_upvalueindex(1));
    lua_pushinteger(L,display->baseWidth_);
    lua_pushinteger(L,display->baseHeight_);
    return 2;
}


int Display::getBaseDensity(lua_State *L) {
    auto * display = (Display*) lua_touserdata(L, lua_upvalueindex(1));
    lua_pushinteger(L,display->baseDensity_);
    return 1;
}

int Display::getBaseDirection(lua_State *L) {
    auto * display = (Display*) lua_touserdata(L, lua_upvalueindex(1));
    lua_pushinteger(L,display->baseDirection_);
    return 1;
}

int Display::getRotation(lua_State *L) {
    auto * display = (Display*) lua_touserdata(L, lua_upvalueindex(1));
    auto r = display->getRotation();
    lua_pushinteger(L,r);
    return 1;
}

int Display::isChangeDirection(lua_State *L) {
    auto * display = (Display*) lua_touserdata(L, lua_upvalueindex(1));
    lua_pushboolean(L, display->isChangeDirection());
    return 1;
}

int Display::reset(lua_State*L)
{
    auto * display = (Display*) lua_touserdata(L, lua_upvalueindex(1));
    jint width = luaL_checkinteger(L,1);
    jint height = luaL_checkinteger(L,2);
    bool result= display->localReset(width,height);
    lua_pushboolean(L,result);
    return 1;
};

int Display::update(lua_State *L) {
    auto * display = (Display*) lua_touserdata(L, lua_upvalueindex(1));
    display->update();
    return 0;
}

int Display::keepDisplay(lua_State *L) {
    auto * display = (Display*) lua_touserdata(L, lua_upvalueindex(1));
    if(lua_isboolean(L,1)){
        display->keepDisplay_ = lua_toboolean(L,1);
        return 0;
    }
    lua_pushboolean(L,display->keepDisplay_);
    return 1;
}

int Display::updateAndKeepDisplay(lua_State *L) {
    auto * display = (Display*) lua_touserdata(L, lua_upvalueindex(1));
    display->keepDisplay_= true;
    display->update();
    return 0;
}

int Display::save(lua_State*L){
    auto * display = (Display*) lua_touserdata(L, lua_upvalueindex(1));
    int x = luaL_checkinteger(L,1);
    int y = luaL_checkinteger(L,2);
    int x1 = luaL_checkinteger(L,3);
    int y1 = luaL_checkinteger(L,4);
    const char* path = luaL_checkstring(L,5);
    Display::SCREEN_SHOT_FORMAT format = Display::kPng;
    if(lua_isstring(L,6)){
        const char* formatStr = lua_tostring(L,6);
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


struct Text{
    ~Text(){
        LOGE("delete text %p",this);
    }
};

int Display::transitMethod(lua_State *L) {
    auto *display = (Display*) lua_touserdata(L, lua_upvalueindex(1));
    if(display->isChangeDirection()){
        luaL_error(L,"display rotate");
    }
    if (!display->isKeepDisplay()) {
        display->update();
    }
    std::shared_lock<std::shared_mutex> lock(display->mutex_);
    auto method = (lua_CFunction) lua_touserdata(L,lua_upvalueindex(2));
    auto r= method(L);
    return r;
}

struct WrapCompareColorMethodContext{
    int tableIndex;
    lua_State *L;
    Display *display;
};

void Display::compareColorWrapper(const char* name, lua_CFunction method, void *data){
    auto context = (WrapCompareColorMethodContext*) data;
    lua_pushlightuserdata(context->L, context->display);
    lua_pushlightuserdata(context->L, (void*)method);
    lua_pushcclosure(context->L, transitMethod, 2);
    lua_setfield(context->L, context->tableIndex, name);
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

void Display::injectToLua(lua_State *L) {
    luaL_Reg  method[] = {
            {"getBaseSize",getBaseSize},
            {"getBaseDensity",getBaseDensity},
            {"getBaseDirection",getBaseDirection},
            {"getRotation",getRotation},
            {"isChangeDirection",isChangeDirection},
            {"resetScreen",reset},
            {"updateScreen",update},
            {"keepScreen",keepDisplay},
            {"updateAndKeepScreen",updateAndKeepDisplay},
            {"saveScreen",save},
            {nullptr,nullptr}
    };
    lua_geti(L,LUA_REGISTRYINDEX,LUA_RIDX_GLOBALS);
    lua_pushlightuserdata(L,this);
    luaL_setfuncs(L,method,1);
    WrapCompareColorMethodContext s{};
    s.L = L;
    s.tableIndex = lua_absindex(L,-1);
    s.display = this;
    eachCompareColorMethodByUpData(compareColorWrapper, &s);
    lua_pop(L,1);
}


