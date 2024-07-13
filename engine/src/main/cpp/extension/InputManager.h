//
// Created by lizhi on 2024/7/3.
//

#ifndef AUTOLUA2_INPUTMANAGER_H
#define AUTOLUA2_INPUTMANAGER_H

#include "JavaInputManager.h"
#include "NativeInputManager.h"
#include"NormalRandomNumber.h"
#include <lua.hpp>

namespace autolua {
    class InputManager {
    public:
        explicit InputManager(Display* display, jobject obj,bool isRoot =false);
        ~InputManager();
        void injectToLua(struct lua_State*L);
        int syncPointer(Input::PointerState* pointerState);
        int releasePointer(int id);
    private:
        JavaInputManager javaInputManager_;
        NativeInputManager nativeInputManager_;

        NormalRandomNumber majorDown_;
        NormalRandomNumber minorDown_;
        NormalRandomNumber majorMove_;
        NormalRandomNumber minorMove_;
        NormalRandomNumber majorUp_;
        NormalRandomNumber minorUp_;
        NormalRandomNumber pressKey_;
        NormalRandomNumber tapTime_;

        std::map<int,int> keyDownMap_;
        bool isRoot_ = false;
        bool isUseHardware_ = true;
        int keyDown(int key);
        int keyUp(int key);

        bool shouldUseHardware() const{
            return isRoot_ && isUseHardware_;
        }

        static int createPointer(lua_State*L);

        static int keyDown(lua_State*L);
        static int keyUp(lua_State*L);
        static int keyPress(lua_State*L);
        static int releaseAllDown(lua_State*L);

        static int swipe(lua_State*L);
        static int tap(lua_State*L);
        static int useHardwareInput(lua_State*L);

    };

} // autolua

#endif //AUTOLUA2_INPUTMANAGER_H
