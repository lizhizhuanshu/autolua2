//
// Created by lizhi on 2024/6/18.
//

#include "Input.h"

#include <cstdio>
#include <cstdlib>
#include <unistd.h>
#include <sys/types.h>
#include <fcntl.h>
#include <cerrno>
#include <ctime>
#include <linux/input.h>
#include<linux/uinput.h>
#include<lua.hpp>
#include<random>
#include<algorithm>
#include <memory.h>
#include "lua_context.h"

#include"mlua.h"

#include "my_log.h"
#include "Display.h"

namespace autolua {

    struct Point{
        short x;
        short y;
    };

    void transportCoordinate(Display*display, int &x, int &y) {
        auto rotation = display->getRotation();
        int baseWidth, baseHeight;
        display->getBaseSize(baseWidth, baseHeight);
        double width = display->width_;
        double height = display->height_;
        if(rotation == 0){
            x = std::round(x * baseWidth/width);
            y = std::round(y * baseHeight/height);
        }else if(rotation == 1) {
            x = std::round(baseWidth - 1- (y * baseWidth / height));
            y = std::round(x * baseHeight / width);
        }else if(rotation == 2){
            x = std::round(baseWidth - 1 - (x * baseWidth / width));
            y = std::round(baseHeight - 1 - (y * baseHeight / height));
        }else{
            y = std::round(baseHeight - 1 - (x * baseHeight / width));
            x = std::round(y * baseWidth / height);
        }
    }

    int Input::touchUp(lua_State *L) {
        auto self = lua::toObjectPointer<Input>(L,1);
        auto slot = luaL_checkint(L,2);
        lua_pushboolean(L,self->touchUp(slot));
        return 1;
    }

    int Input::touchDown(lua_State *L) {
        auto self = lua::toObjectPointer<Input>(L,1);
        auto x = luaL_checkint(L, 2);
        auto y = luaL_checkint(L,3);
        auto major = luaL_optint(L,4,self->majorDown_.next());
        auto minor = luaL_optint(L,5,self->minorDown_.next());
        auto pressure = luaL_optint(L,6,0);
        auto display = (Display*)toLuaContext(L)->display;
        transportCoordinate(display,x,y);
        auto id = self->touchDown(x,y,major,minor,pressure);
        lua_pushinteger(L,id);
        return 1;
    }



    int Input::releaseRunningEvent(lua_State *L) {
        auto self = lua::toObjectPointer<Input>(L,1);
        self->releaseAllTouch();
        self->releaseAllKey();
        return 0;
    }

    int Input::touchChange(lua_State *L) {
        auto self = lua::toObjectPointer<Input>(L,1);
        auto slot = luaL_checkint(L,2);
        auto x = luaL_optint(L,3,-1);
        auto y = luaL_optint(L,4,-1);
        auto major = luaL_optint(L,5,0);
        auto minor = luaL_optint(L,6,0);
        auto pressure = luaL_optint(L,7,0);
        auto display = (Display*)toLuaContext(L)->display;
        transportCoordinate(display,x,y);
        auto r = self->touchChange(slot,x,y,major,minor,pressure);
        lua_pushboolean(L,r);
        return 1;
    }

    int Input::writeInputEvent(int fd, int type, int code, int value) {
        input_event event;
        event.type = type;
        event.code = code;
        event.value = value;
        event.time.tv_sec = 0;
        event.time.tv_usec = 0;
        gettimeofday(&event.time, nullptr);
        write(fd, &event, sizeof(event));
        return 0;
    }

    int Input::touchDown(int x, int y,int major,int minor,int pressure) {
        std::lock_guard<std::mutex> lock(touchLock_);
        auto touchFd = screen_.fd;
        int slot = nextTouchSlot();
        if(slot < 0){
            return -1;
        }
        if(major <minor) std::swap(major,minor);
        int id = newTouchId();
        auto & touch = touch_[slot];
        touch.x = x;
        touch.y = y;
        touch.major = major;
        touch.minor = minor;
        touch.pressure = pressure;
        touch.down = true;
        writeInputEvent(touchFd, EV_ABS, ABS_MT_SLOT, slot);
        writeInputEvent(touchFd, EV_ABS, ABS_MT_TRACKING_ID, id);
        writeInputEvent(touchFd, EV_ABS, ABS_MT_POSITION_X, x);
        writeInputEvent(touchFd, EV_ABS, ABS_MT_POSITION_Y, y);
        writeInputEvent(touchFd, EV_ABS, ABS_MT_TOUCH_MAJOR, major);
        writeInputEvent(touchFd, EV_ABS, ABS_MT_TOUCH_MINOR, minor);
        if(pressure > 0){
            writeInputEvent(touchFd, EV_ABS, ABS_MT_PRESSURE, pressure);
        }
        writeInputEvent(touchFd, EV_KEY, BTN_TOUCH, 1);
        writeInputEvent(touchFd, EV_KEY, BTN_TOOL_FINGER, 1);
        writeInputEvent(touchFd, EV_SYN, SYN_REPORT, 0);
        return slot;
    }


    int Input::newTouchId() {
        int id;
        while (true){
            id = touchId_++;
            if(id <= 0){
                id = 1;
            }
            break;
        }
        return id;
    }

    int Input::nextTouchSlot() {
        for(int i=0;i<touch_.size();i++){
            if(!touch_[i].down)
                return i;
        }
        return -1;
    }

    Input::Input(): majorDown_(5.5,13.5),
                    minorDown_(4.0,11.0),
                    majorMove_(9.0,17.0),
                    minorMove_(7.0,13.0),
                    majorUp_(5.5,13.5),
                    minorUp_(4.5,11.5),
                    pressKey_(140.0,210.0),
                    tapTime_(90,200){
        touchId_ = 1;
    }

    int Input::touchChange(int slot, int x, int y, int major, int minor, int pressure) {
        std::lock_guard<std::mutex> lock(touchLock_);
        Touch & touch = touch_[slot];
        if(!touch.down){
            return 0;
        }
        if(major <minor) std::swap(major,minor);
        auto touchFd = screen_.fd;
        writeInputEvent(touchFd, EV_ABS, ABS_MT_SLOT, slot);

        if(x >=0 && x != touch.x){
            touch.x = x;
            writeInputEvent(touchFd, EV_ABS, ABS_MT_POSITION_X, x);
        }
        if(y >= 0 && y != touch.y){
            touch.y = y;
            writeInputEvent(touchFd, EV_ABS, ABS_MT_POSITION_Y, y);
        }
        if(major > 0 && major != touch.major){
            touch.major = major;
            writeInputEvent(touchFd, EV_ABS, ABS_MT_TOUCH_MAJOR, major);
        }

        if(minor > 0 && minor != touch.minor){
            touch.minor = minor;
            writeInputEvent(touchFd, EV_ABS, ABS_MT_TOUCH_MINOR, minor);
        }

        if(pressure > 0 && pressure != touch.pressure){
            touch.pressure = pressure;
            writeInputEvent(touchFd, EV_ABS, ABS_MT_PRESSURE, pressure);
        }
        writeInputEvent(touchFd, EV_SYN, SYN_REPORT, 0);
        return 1;
    }

    int Input::touchUp(int slot) {
        std::lock_guard<std::mutex> lock(touchLock_);
        auto & touch = touch_[slot];
        if(!touch.down){
            return 0;
        }
        auto touchFd = screen_.fd;
        writeInputEvent(touchFd, EV_ABS, ABS_MT_SLOT, slot);
        writeInputEvent(touchFd, EV_ABS, ABS_MT_TRACKING_ID, -1);
        writeInputEvent(touchFd, EV_KEY, BTN_TOUCH, 0);
        writeInputEvent(touchFd, EV_KEY, BTN_TOOL_FINGER, 0);
        writeInputEvent(touchFd, EV_SYN, SYN_REPORT, 0);
        touch.down = false;
        return 1;
    }

    void Input::releaseAllTouch() {
        for(int i=0;i<touch_.size();i++){
            touchUp(i);
        }
    }

    int Input::keyPress(lua_State *L) {
        auto self = lua::toObjectPointer<Input>(L,1);
        auto code = luaL_checkint(L,2);
        auto time = luaL_optint(L,3,self->pressKey_.next());
        auto r = self->keyDown(code);
        if(r){
            usleep(time*1000);
            self->keyUp(code);
        }
        lua_pushboolean(L,r);
        return 1;
    }
#define test_bit(bit,mask) (mask[(bit)/8] & (1 << ((bit)%8)))

    static bool isTouchDevice(int fd){
        unsigned char mask[ABS_MAX / 8 + 1]={0};
        return ioctl(fd, EVIOCGBIT(EV_ABS,sizeof(mask)), mask) >= 0 &&
               test_bit(ABS_MT_POSITION_X, mask) &&
               test_bit(ABS_MT_POSITION_Y, mask);
    }

    static void readKeys(int fd,std::unordered_set<int> & keys){
        unsigned char mask[KEY_MAX / 8 + 1];
        memset(mask,0,sizeof(mask));
        auto r = ioctl(fd,EVIOCGBIT(EV_KEY,sizeof(mask)),mask);
        if(r < 0) return;
        for(int i=0;i<KEY_MAX;i++){
            if(test_bit(i,mask)){
                keys.insert(i);
            }
        }
        std::string str;
        for (int key : keys) {
            str.append(std::to_string(key)).append(" ");
        }
        LOGI("fd %d keys %s",fd,str.c_str());
    }

    bool Input::init(){
        char path[64];
        unsigned char mask[EV_MAX / 8 + 1]={0};
        for(int id = 0;id<255;id++){
            sprintf(path,"/dev/input/event%d",id);
            int fd = open(path,O_RDWR,0);
            if(fd < 0) continue;
            ioctl(fd,EVIOCGBIT(0,sizeof(mask)),mask);
            if(test_bit(EV_ABS,mask) && isTouchDevice(fd)){
                screen_.fd = fd;
                LOGI("find touch device %s",path);
                readKeys(fd,screen_.keys);
            }else if(test_bit(EV_KEY,mask)) {
                LOGI("find key device %s",path);
                keyDevice_.push_back({fd});
                readKeys(fd, keyDevice_.back().keys);
            }else{
                close(fd);
            }
        }
#undef test_bit
        return true;
    }


    int Input::keyDown(lua_State *L) {
        auto self = lua::toObjectPointer<Input>(L,1);
        auto code = luaL_checkint(L,2);
        lua_pushboolean(L,self->keyDown(code));
        return 1;
    }

    //生成控制点
    void generatePoints(const Point & origin, const Point & target, int count, std::vector<Point> &out){
        float dx = target.x - origin.x;
        float dy = target.y - origin.y;
        float distance = sqrt(dx*dx + dy*dy);
        if(count == 0){
            count = 1;
        }
        float stepX = dx/count;
        float stepY = dy/count;
        for(int i=0;i<count;i++){
            out.push_back({(short)(origin.x + stepX*i),(short)(origin.y + stepY*i)});
        }
        out.push_back({target.x,target.y});
    }


    int Input::swipe(lua_State *L) {
        auto self = lua::toObjectPointer<Input>(L,1);
        auto x1 = luaL_checkint(L,2);
        auto y1 = luaL_checkint(L,3);

        auto x2 = luaL_checkint(L,4);
        auto y2 = luaL_checkint(L,5);
        float time = luaL_checkint(L,6);
        auto display = (Display*)toLuaContext(L)->display;
        transportCoordinate(display,x1,y1);
        transportCoordinate(display,x2,y2);
        std::vector<Point> points;
        auto count = std::round(time/50);
        generatePoints({(short)x1,(short)y1}, {(short)x2,(short)y2}, count, points);
        auto slot = self->touchDown(x1, y1, self->majorDown_.next(), self->minorDown_.next(), 0);
        for(auto & point : points){
            usleep(50*1000);
            self->touchChange(slot,point.x,point.y,self->majorMove_.next(),self->minorMove_.next(),0);
        }
        self->touchUp(slot);
        return 0;
    }

    int Input::tap(lua_State *L) {
        auto self = lua::toObjectPointer<Input>(L,1);
        auto x = luaL_checkint(L,2);
        auto y = luaL_checkint(L,3);
        auto time = luaL_optint(L,4,self->tapTime_.next());
        auto major = self->majorDown_.next();
        auto minor = self->minorDown_.next();
        if(minor > major){
            std::swap(major,minor);
        }
        auto display = (Display*)toLuaContext(L)->display;
        transportCoordinate(display,x,y);
        auto slot = self->touchDown(x, y, self->majorDown_.next(), self->minorDown_.next(), 0);
        usleep(time*1000);
        self->touchUp(slot);
        return 0;
    }

    int Input::keyUp(lua_State *L) {
        auto self = lua::toObjectPointer<Input>(L,1);
        auto code = luaL_checkint(L,2);
        lua_pushboolean(L,self->keyUp(code));
        return 1;
    }

    int Input::keyDown(int code) {
        std::lock_guard<std::mutex> lock(keyLock_);
        if(keyDown_.find(code) != keyDown_.end()){
            return 0;
        }
        int fd = 0;
        if(screen_.keys.find(code) != screen_.keys.end()){
            fd = screen_.fd;
        }else{
            for(auto & device : keyDevice_){
                if(device.keys.find(code) != device.keys.end()){
                    fd = device.fd;
                    break;
                }
            }
        }
        if(fd == 0){
            return 0;
        }

        keyDown_[code] = fd;
        writeInputEvent(fd,EV_KEY,code,1);
        writeInputEvent(fd,EV_SYN,SYN_REPORT,0);
        return 1;
    }

    int Input::keyUp(int code) {
        std::lock_guard<std::mutex> lock(keyLock_);
        auto it = keyDown_.find(code);
        if(it == keyDown_.end()){
            return 0;
        }
        keyDown_.erase(it);
        writeInputEvent(it->second,EV_KEY,code,0);
        writeInputEvent(it->second,EV_SYN,SYN_REPORT,0);
        return 1;
    }

    void Input::releaseAllKey() {
        std::lock_guard<std::mutex> lock(keyLock_);
        for(auto & key : keyDown_){
            writeInputEvent(key.second,EV_KEY,key.first,0);
            writeInputEvent(key.second,EV_SYN,SYN_REPORT,0);
        }
    }

    void Input::pushObjectToLua(struct lua_State *L) {
        luaL_Reg methods[] = {
                {"touchDown",touchDown},
                {"touchChange",touchChange},
                {"touchUp",touchUp},
                {"keyPress",keyPress},
                {"keyDown",keyDown},
                {"keyUp",keyUp},
                {"swipe",swipe},
                {"tap",tap},
                {"__gc",releaseRunningEvent},
                {nullptr,nullptr}
        };
        lua::pushObjectPointer(L,this);
        lua_newtable(L);
        luaL_setfuncs(L,methods,0);
        lua_pushvalue(L,-1);
        lua_setfield(L,-2,"__index");
        lua_setmetatable(L,-2);
    }

    Input::~Input() {
        releaseAllTouch();
        releaseAllKey();
        if(screen_.fd > 0){
            close(screen_.fd);
        }
        for(auto & device : keyDevice_){
            if(device.fd > 0){
                close(device.fd);
            }
        }
    }



} // autolua