//
// Created by lizhi on 2024/6/18.
//

#ifndef AUTOLUA2_INPUT_H
#define AUTOLUA2_INPUT_H

#include <unordered_set>
#include <unordered_map>
#include <atomic>
#include <mutex>
#include<array>
#include <functional>
#include"NormalRandomNumber.h"

struct lua_State;
namespace autolua {

class Input {
    struct Touch {
        int x=-1;
        int y=-1;
        int major=0;
        int minor=0;
        int pressure=0;
        bool down = false;
    };
    struct DeviceInfo {
        int fd;
        std::unordered_set<int> keys;
    };
public:
    Input();
    ~Input();
    bool init();
    void pushObjectToLua(struct lua_State*L);
private:
    DeviceInfo screen_;
    std::vector<DeviceInfo> keyDevice_;
    int touchId_;
    std::mutex keyLock_;
    std::mutex touchLock_;
    std::array<Touch,10> touch_;
    NormalRandomNumber majorDown_;
    NormalRandomNumber minorDown_;
    NormalRandomNumber majorMove_;
    NormalRandomNumber minorMove_;
    NormalRandomNumber majorUp_;
    NormalRandomNumber minorUp_;
    NormalRandomNumber pressKey_;
    NormalRandomNumber tapTime_;


    std::unordered_map<int,int> keyDown_;
    int nextTouchSlot();
    int newTouchId();
    int touchDown(int x,int y,int major,int minor,int pressure);
    int touchChange(int slot, int x, int y, int major, int minor, int pressure);
    int touchUp(int slot);
    void releaseAllTouch();
    void releaseAllKey();



    static int writeInputEvent(int fd, int type, int code, int value);
    static int releaseRunningEvent(lua_State*L);

    int keyDown(int code);
    int keyUp(int code);

    static int touchDown(lua_State*L);
    static int touchChange(lua_State*L);
    static int touchUp(lua_State*L);


    static int keyPress(lua_State*L);
    static int keyDown(lua_State*L);
    static int keyUp(lua_State*L);

    static int swipe(lua_State*L);
    static int tap(lua_State*L);
};

} // autolua

#endif //AUTOLUA2_INPUT_H
