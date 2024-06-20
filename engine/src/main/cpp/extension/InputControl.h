//
// Created by lizhi on 2022/5/19.
//

#ifndef AUTOLUA2_INPUTCONTROL_H
#define AUTOLUA2_INPUTCONTROL_H
#include <lua.hpp>
#include <jni.h>

class InputControl {
public:
    InputControl(jobject obj);
    ~InputControl();

    void pushObjectToLua(struct lua_State*L);
private:
    jobject object_;
    jclass class_;
    jmethodID touchDownMethodID;
    jmethodID touchMoveMethodID;
    jmethodID touchUpMethodID;
    jmethodID injectKeyEventMethodID;
    jmethodID releaseAllPointerMethodID;

    static int touchDown(lua_State*L);
    static int touchMove(lua_State*L);
    static int touchUp(lua_State*L);
    static int injectKeyEvent(lua_State*L);
    static int releaseAllPointer(lua_State*L);
};


#endif //AUTOLUA2_INPUTCONTROL_H
