//
// Created by lizhi on 2022/4/22.
//

#ifndef AUTOLUA2_DISPLAY_H
#define AUTOLUA2_DISPLAY_H
#include "jni.h"
#include "functional"

#include "Bitmap.h"

struct lua_State;
class Display : public autolua::Bitmap{
    jobject obj_;
    bool keepDisplay_;
public:
    static void initializeJavaDisplayClass(JNIEnv*env);
    static void releaseJavaDisplayClass(JNIEnv*env);
    static void pushObjectToLua(lua_State*L,jobject obj);
    Display(jobject obj);
    ~Display();
    void update();

    bool isKeepDisplay(){
        return keepDisplay_;
    }
    void keepDisplay(){
        keepDisplay_ = true;
    }
    bool isChangeDirection();
    int getRotation();
    bool getBaseSize(int&w,int&h){
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

    static jclass displayClassID_;


    static jmethodID getRotationMethodID;


    static jmethodID initializeMethodID;
    static jmethodID isChangeDirectionMethodID;
    static jmethodID getDisplayBufferMethodID ;
    static jmethodID getHeightMethodID;
    static jmethodID getRowStrideMethodID ;
    static jmethodID getPixelStrideMethodID;
    static jmethodID getWidthMethodID;
    static jmethodID updateMethodID;
};


#endif //AUTOLUA2_DISPLAY_H
