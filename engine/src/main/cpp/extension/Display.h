//
// Created by lizhi on 2022/4/22.
//

#ifndef AUTOLUA2_DISPLAY_H
#define AUTOLUA2_DISPLAY_H
#include "jni.h"
#include "functional"
#include <shared_mutex>
#include "Bitmap.h"
#include <lua.hpp>


class Display : public autolua::Bitmap{
    jobject obj_;
    bool keepDisplay_;
public:
    explicit Display(jobject obj);
    void injectToLua(lua_State*L);
    ~Display();
    void update();

    bool isKeepDisplay() const{
        return keepDisplay_;
    }
    void keepDisplay(){
        keepDisplay_ = true;
    }
    bool isChangeDirection();
    int getRotation();
    bool getScreenBaseSize(int&w, int&h) const{
        w = baseWidth_;
        h = baseHeight_;
        return true;
    }
    enum SCREEN_SHOT_FORMAT{
        kPng= 0,
        kJpeg = 1
    };
    int screenshot(int x,int y,int x1,int y1,SCREEN_SHOT_FORMAT format,std::vector<unsigned  char> &out);
private:
    int _screenshot(int x,int y,int x1,int y1,SCREEN_SHOT_FORMAT format,std::vector<unsigned  char> &out);
    int screenshotToPng(int x,int y,int x1,int y1,std::vector<unsigned  char> &out);
    static int transitMethod(lua_State*L);
    static void compareColorWrapper(const char* name, lua_CFunction method, void *data);
    inline bool localReset(int w,int h);
    static int getBaseSize(lua_State*L);

    static int getRotation(lua_State*L);
    static int getBaseDensity(lua_State*L);
    static int getBaseDirection(lua_State*L);
    static int isChangeDirection(lua_State*L);
    static int reset(lua_State*L);
    static int update(lua_State*L);
    static int keepDisplay(lua_State*L);
    static int updateAndKeepDisplay(lua_State*L);
    static int save(lua_State*L);
    jint baseWidth_;
    jint baseHeight_;
    jint baseDensity_;
    jint baseDirection_;
    std::shared_mutex mutex_;

    jclass displayClassID_;


    jmethodID getRotationMethodID;
    jmethodID initializeMethodID;
    jmethodID isChangeDirectionMethodID;
    jmethodID getDisplayBufferMethodID ;
    jmethodID getHeightMethodID;
    jmethodID getRowStrideMethodID ;
    jmethodID getPixelStrideMethodID;
    jmethodID getWidthMethodID;
    jmethodID updateMethodID;
};


#endif //AUTOLUA2_DISPLAY_H
