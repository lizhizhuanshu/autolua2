//
// Created by lizhi on 2024/7/3.
//

#include "InputManager.h"


#include "mlua.h"
#include<cstring>
#include <unistd.h>


namespace autolua {
    struct Pointer:public Input::PointerState{
        InputManager* manager = nullptr;
    };

    struct Point{
        short x;
        short y;
    };

    int InputManager::createPointer(lua_State *L) {
        auto self = (InputManager*)lua_touserdata(L,lua_upvalueindex(1));
        auto pointer = luaL_pushNewObject(Pointer,L);
        pointer->manager = self;
        return 1;
    }
    static int pointerSync(lua_State *L) {
        auto pointer = luaL_checkObject(Pointer,L,1);
        auto r = pointer->manager->syncPointer(pointer);
        lua_pushinteger(L,r);
        return 1;
    }
    static int pointerRelease(lua_State *L) {
        auto pointer = luaL_checkObject(Pointer,L,1);
        auto r = pointer->manager->releasePointer(pointer->id);
        pointer->clear();
        lua_pushinteger(L,r);
        return 1;
    }
    static int pointerIndex(lua_State *L) {
        auto pointer = luaL_checkObject(Pointer,L,1);
        size_t size = 0;
        auto str = luaL_checklstring(L,2,&size);
        if(size == 1 && str[0] == 'x'){
            lua_pushnumber(L,pointer->x);
        }else if(size == 1 && str[0] == 'y'){
            lua_pushnumber(L,pointer->y);
        }else if(strcmp(str,"major") == 0){
            lua_pushnumber(L,pointer->major);
        }else if(strcmp(str,"minor") == 0){
            lua_pushnumber(L,pointer->minor);
        }else if(strcmp(str,"pressure") == 0){
            lua_pushnumber(L,pointer->pressure);
        }else if(strcmp(str,"size") == 0){
            lua_pushnumber(L,pointer->size);
        }else if (strcmp(str,"sync") == 0){
            lua_pushcfunction(L, pointerSync);
        }else if(strcmp(str,"up") == 0){
            lua_pushcfunction(L, pointerRelease);
        }else{
            lua_pushnil(L);
        }
        return 1;
    }
    static int pointerNewIndex(lua_State *L) {
        auto pointer = luaL_checkObject(Pointer,L,1);
        size_t size = 0;
        auto str = luaL_checklstring(L,2,&size);
        if(size == 1 && str[0] == 'x'){
            pointer->tryChangeX(luaL_checknumber(L,3));
        }else if(size == 1 && str[0] == 'y'){
            pointer->tryChangeY(luaL_checknumber(L,3));
        }else if(strcmp(str,"major") == 0){
            pointer->tryChangeMajor(luaL_checknumber(L,3));
        }else if(strcmp(str,"minor") == 0){
            pointer->tryChangeMinor(luaL_checknumber(L,3));
        }else if(strcmp(str,"pressure") == 0){
            pointer->tryChangePressure(luaL_checknumber(L,3));
        }else if(strcmp(str,"size") == 0){
            pointer->tryChangeSize(luaL_checknumber(L,3));
        }else{
            luaL_error(L,"can't set %s",str);
        }
        return 0;
    }
    void InputManager::injectToLua(struct lua_State *L) {
        if(luaL_newClassMetatable(Pointer,L)){
            luaL_Reg methods[] = {
                {"__index",pointerIndex},
                {"__newindex",pointerNewIndex},
                {nullptr,nullptr}
            };
            luaL_setfuncs(L,methods,0);
        }
        lua_pop(L,1);

        luaL_Reg  method[] = {
            {"createPointer",    createPointer},
            {"tap",              tap},
            {"swipe",            swipe},
            {"keyDown",          keyDown},
            {"keyUp",            keyUp},
            {"keyPress",         keyPress},
            {"useHardwareInput", useHardwareInput},
            {nullptr,            nullptr}
        };

        lua_geti(L,LUA_REGISTRYINDEX,LUA_RIDX_GLOBALS);
        lua_pushlightuserdata(L,this);
        luaL_setfuncs(L,method,1);
        lua_pop(L,1);


        lua_newuserdata(L,sizeof(void*));

        lua_createtable(L,1,0);
        lua_pushlightuserdata(L,this);
        lua_pushcclosure(L,releaseAllDown,1);
        lua_setfield(L,-2,"__gc");
        lua_setmetatable(L,-2);

        luaL_ref(L,LUA_REGISTRYINDEX);

    }

    InputManager::InputManager(Display *display, jobject obj,bool isRoot) :
            nativeInputManager_(display),
            javaInputManager_(obj),
            majorDown_(5.5, 13.5),
            minorDown_(4.0,11.0),
            majorMove_(9.0,17.0),
            minorMove_(7.0,13.0),
            majorUp_(5.5,13.5),
            minorUp_(4.5,11.5),
            pressKey_(140.0,210.0),
            tapTime_(90,200),
            isRoot_(isRoot){
    }

    int InputManager::syncPointer(Input::PointerState *pointerState) {
        if(shouldUseHardware() && nativeInputManager_.canInjectTouch())
            return nativeInputManager_.syncPointer(pointerState);
        return javaInputManager_.syncPointer(pointerState);
    }

    int InputManager::releasePointer(int id) {
        if(shouldUseHardware() && nativeInputManager_.canInjectTouch())
            return nativeInputManager_.releasePointer(id);
        return javaInputManager_.releasePointer(id);
    }

    InputManager::~InputManager() = default;

    int InputManager::keyDown(lua_State *L) {
        auto self = (InputManager*)lua_touserdata(L,lua_upvalueindex(1));
        auto key = luaL_checkint(L,1);
        auto r = self->keyDown(key);
        lua_pushboolean(L,r);
        return 1;
    }

    int InputManager::keyUp(lua_State *L) {
        auto self = (InputManager*)lua_touserdata(L,lua_upvalueindex(1));
        auto key = luaL_checkint(L,1);
        auto r = self->keyUp(key);
        lua_pushboolean(L,r);
        return 1;
    }

    int InputManager::keyDown(int key) {
        if(keyDownMap_.find(key) != keyDownMap_.end())
            return 0;
        int r;
        if(shouldUseHardware()){
            r = nativeInputManager_.keyDown(key);
            if(r == 1){
                keyDownMap_[key] = 1;
                return 1;
            }
        }
        r = javaInputManager_.keyDown(key);
        if(r == 1){
            keyDownMap_[key] = 2;
            return 1;
        }
        return 0;
    }

    int InputManager::keyUp(int key){
        auto it = keyDownMap_.find(key);
        if(it == keyDownMap_.end()){
            return 0;
        }
        int r = 0;
        if(it->second == 1){
            r = nativeInputManager_.keyUp(key);
        }else if(it->second == 2){
            r = javaInputManager_.keyUp(key);
        }
        keyDownMap_.erase(it);
        return r;
    }

    int InputManager::keyPress(lua_State *L) {
        auto self = (InputManager*)lua_touserdata(L,lua_upvalueindex(1));
        auto key = luaL_checkint(L,1);
        auto time = luaL_optint(L, 2, self->pressKey_.next());
        auto r = self->keyDown(key);
        if(r){
            usleep(time*1000);
            self->keyUp(key);
        }
        lua_pushboolean(L,r);
        return 1;
    }

    int InputManager::releaseAllDown(lua_State *L) {
        auto self = (InputManager*)lua_touserdata(L,lua_upvalueindex(1));
        self->keyDownMap_.clear();
        self->nativeInputManager_.releaseAllDown();
        self->javaInputManager_.releaseAllDown();
        return 0;
    }

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
    int InputManager::swipe(lua_State *L) {
        auto self = (InputManager*)lua_touserdata(L,lua_upvalueindex(1));
        auto x1 = luaL_checkint(L,1);
        auto y1 = luaL_checkint(L,2);

        auto x2 = luaL_checkint(L,3);
        auto y2 = luaL_checkint(L,4);
        float time = luaL_checkint(L,5);
        std::vector<Point> points;
        auto count = std::round(time/50);
        Pointer pointer;
        pointer.x = x1;
        pointer.y = y1;
        pointer.major = self->majorDown_.next();
        pointer.minor = self->minorDown_.next();
        pointer.setFlag(Pointer::Target::kX |
            Pointer::Target::kY |
            Pointer::Target::kMajor |
            Pointer::Target::kMinor);
        for(auto & point : points){
            usleep(50*1000);
            pointer.tryChangeX(point.x);
            pointer.tryChangeY(point.y);
            pointer.tryChangeMajor(self->majorMove_.next());
            pointer.tryChangeMinor(self->minorMove_.next());
            self->syncPointer(&pointer);
        }
        self->releasePointer(pointer.id);
        return 0;
    }

    int InputManager::tap(lua_State *L) {
        auto self = (InputManager*)lua_touserdata(L,lua_upvalueindex(1));
        auto x = luaL_checkint(L,1);
        auto y = luaL_checkint(L,2);
        auto time = luaL_optint(L,3,self->tapTime_.next());
        Pointer pointer;
        pointer.x = x;
        pointer.y = y;
        pointer.major = self->majorDown_.next();
        pointer.minor = self->minorDown_.next();
        pointer.setFlag(Pointer::Target::kX |
            Pointer::Target::kY |
            Pointer::Target::kMajor |
            Pointer::Target::kMinor);
        self->syncPointer(&pointer);
        usleep(time*1000);
        self->releasePointer(pointer.id);
        return 0;
    }

    int InputManager::useHardwareInput(lua_State *L) {
        auto self = (InputManager*)lua_touserdata(L,lua_upvalueindex(1));
        if(lua_isnoneornil(L,1)){
            lua_pushboolean(L,self->isUseHardware_);
            return 1;
        }
        self->isUseHardware_ = lua_toboolean(L, 1);
        return 0;
    }



} // autolua