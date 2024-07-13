//
// Created by lizhi on 2024/7/3.
//

#include "NativeInputManager.h"

#include <linux/input.h>
#include <map>
#include <unistd.h>
#include <fcntl.h>
#include <sys/ioctl.h>
#include <cstdlib>
#include <string>
#include <memory.h>
#include <cmath>
#include "my_log.h"

//android key code to linux key code
std::map<int,int> createKeyMap(){
    std::map<int,int> keyMap;
    keyMap[3] = KEY_HOMEPAGE;
    keyMap[4] = KEY_BACK;
    keyMap[5] = KEY_PHONE;
    keyMap[6] = KEY_HANGUP_PHONE;
    keyMap[24] = KEY_VOLUMEUP;
    keyMap[25] = KEY_VOLUMEDOWN;
    keyMap[26] = KEY_POWER;
    keyMap[27] = KEY_CAMERA;
    keyMap[28] = KEY_CLEAR;
    keyMap[66] = KEY_ENTER;
    keyMap[67] = KEY_MENU;
    keyMap[82] = KEY_MENU;
    return keyMap;
}

void transportCoordinate(Display*display, int &x, int &y) {
    auto rotation = display->getRotation();
    int baseWidth, baseHeight;
    display->getScreenBaseSize(baseWidth, baseHeight);
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

namespace autolua {
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

    NativeInputManager::NativeInputManager(Display*display):display_(display) {
        char path[64];
        unsigned char mask[EV_MAX / 8 + 1]={0};
        for(int id = 0;id<255;id++){
            sprintf(path,"/dev/input/event%d",id);
            int fd = open(path,O_RDWR,0);
            if(fd < 0) continue;
            ioctl(fd,EVIOCGBIT(0,sizeof(mask)),mask);
            if(test_bit(EV_ABS,mask) && isTouchDevice(fd)){
                screenDevice_.fd = fd;
                LOGI("find touch device %s",path);
                readKeys(fd, screenDevice_.keys);
            }else if(test_bit(EV_KEY,mask)) {
                LOGI("find key device %s",path);
                keyDevice_.push_back({fd});
                readKeys(fd, keyDevice_.back().keys);
            }else{
                close(fd);
            }
        }
    }


#undef test_bit

    NativeInputManager::~NativeInputManager() {
        for(auto& device:keyDevice_){
            close(device.fd);
        }
        if(screenDevice_.fd > 0){
            close(screenDevice_.fd);
        }
    }
    int writeInputEvent(int fd,int type,int code ,int value){
        input_event event{};
        event.type = type;
        event.code = code;
        event.value = value;
        event.time.tv_sec = 0;
        event.time.tv_usec = 0;
        gettimeofday(&event.time, nullptr);
        write(fd,&event,sizeof(event));
        return 0;
    }


    int NativeInputManager::syncPointer(autolua::Input::PointerState *pointerState) {
        auto fd = screenDevice_.fd;
        if(pointerState->id == -1){
            int id = newPointerId();
            if(id == -1){
                return 0;
            }
            pointerState->id = id;
        }
        auto touchDown = touchDown_[pointerState->id];
        LOGD("touch down %d",touchDown);
        writeInputEvent(fd, EV_ABS, ABS_MT_SLOT, pointerState->id);
        if(!touchDown){
            int id = newTouchId();
            writeInputEvent(fd, EV_ABS, ABS_MT_TRACKING_ID, id);
        }
        int x = static_cast<int> (pointerState->x);
        int y = static_cast<int> (pointerState->y);
        transportCoordinate(display_,x,y);
        if (pointerState->hasFlag(PointerState::Target::kX)){
            writeInputEvent(fd, EV_ABS, ABS_MT_POSITION_X, static_cast<int>(pointerState->x));
        }
        if(pointerState->hasFlag(PointerState::Target::kY)){
            writeInputEvent(fd, EV_ABS, ABS_MT_POSITION_Y, static_cast<int>(pointerState->y));
        }
        if(pointerState->hasFlag(PointerState::Target::kMajor)){
            writeInputEvent(fd, EV_ABS, ABS_MT_TOUCH_MAJOR, static_cast<int>(pointerState->major));
        }
        if(pointerState->hasFlag(PointerState::Target::kMinor)){
            writeInputEvent(fd, EV_ABS, ABS_MT_TOUCH_MINOR, static_cast<int>(pointerState->minor));
        }
        if(pointerState->hasFlag(PointerState::Target::kPressure)){
            writeInputEvent(fd, EV_ABS, ABS_MT_PRESSURE, static_cast<int>(pointerState->pressure));
        }
        if(!touchDown){
            writeInputEvent(fd, EV_KEY, BTN_TOUCH, 1);
            writeInputEvent(fd, EV_KEY, BTN_TOOL_FINGER, 1);
        }
        writeInputEvent(fd, EV_SYN, SYN_REPORT, 0);
        pointerState->clearFlag();
        touchDown_[pointerState->id] = true;
        return 1;
    }


    int NativeInputManager::releasePointer(int id) {
        auto deviceId = screenDevice_.fd;
        if(touchDown_[id]){
            writeInputEvent(deviceId,EV_ABS,ABS_MT_SLOT,id);
            writeInputEvent(deviceId,EV_ABS,ABS_MT_TRACKING_ID,-1);
            writeInputEvent(deviceId,EV_KEY,BTN_TOUCH,0);
            writeInputEvent(deviceId,EV_KEY,BTN_TOOL_FINGER,0);
            writeInputEvent(deviceId,EV_SYN,SYN_REPORT,0);
            touchDown_[id] = false;
        }
        return 1;
    }

    int NativeInputManager::keyDown(int key) {
        int code = -1;
        {
            auto it = commonKeyMap_.find(key);
            if(it != commonKeyMap_.end()){
                code = it->second;
            }else{
                return -1;
            }
        }
        if(keyDown_.find(code) != keyDown_.end()){
            return 0;
        }
        int fd = 0;
        if(screenDevice_.keys.find(code) != screenDevice_.keys.end()) {
            fd = screenDevice_.fd;
        }else{
            for(auto& device:keyDevice_){
                if(device.keys.find(code) != device.keys.end()){
                    fd = device.fd;
                    break;
                }
            }
        }
        if(fd == 0){
            return -1;
        }
        writeInputEvent(fd,EV_KEY,code,1);
        writeInputEvent(fd,EV_SYN,SYN_REPORT,0);
        keyDown_[code] = fd;
        return 1;
    }

    int NativeInputManager::keyUp(int key) {
        auto it = keyDown_.find(key);
        if(it == keyDown_.end()){
            return 0;
        }
        int fd = it->second;
        keyDown_.erase(it);
        writeInputEvent(fd,EV_KEY,key,0);
        writeInputEvent(fd,EV_SYN,SYN_REPORT,0);
        return 1;
    }

    void NativeInputManager::releaseAllDown() {
        for(auto& it:keyDown_){
            writeInputEvent(it.second,EV_KEY,it.first,0);
            writeInputEvent(it.second,EV_SYN,SYN_REPORT,0);
        }
        keyDown_.clear();

        for(int i=0; i < touchDown_.size(); i++){
            releasePointer(i);
        }
    }

    int NativeInputManager::newTouchId() {
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

    bool NativeInputManager::canInjectTouch() const {
        return screenDevice_.fd > 0;
    }

    int NativeInputManager::newPointerId() {
        for(int i=0; i < touchDown_.size(); i++){
            if(!touchDown_[i])
                return i;
        }
        return -1;
    }
} // autolua