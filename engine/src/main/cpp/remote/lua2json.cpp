//
// Created by lizhi on 2024/5/17.
//

#include "lua2json.h"
#include "cJSON.h"
#include "lua.hpp"



bool lua2json(struct lua_State*L ,int index, cJSON*&out){
    switch (lua_type(L, index)) {
        case LUA_TNIL:
            out = cJSON_CreateNull();
            break;
        case LUA_TBOOLEAN:
            out = cJSON_CreateBool(lua_toboolean(L, index));
            break;
        case LUA_TNUMBER:
            out = cJSON_CreateNumber(lua_tonumber(L, index));
            break;
        case LUA_TSTRING:
            out = cJSON_CreateString(lua_tostring(L, index));
            break;
        case LUA_TTABLE:
            lua2jsonObject(L,index,out);
            break;
        default:
            break;
    }
    return true;
}


bool lua2jsonObject(struct lua_State*L ,int index, cJSON*&out){
    index = lua_absindex(L,index);
    auto size = luaL_len(L,index);
    if(size == 0){
        out = cJSON_CreateObject();
        lua_pushnil(L);
        while (lua_next(L, index) != 0) {
            cJSON *item = nullptr;
            lua2json(L, -1, item);
            if(lua_isstring(L,-2)){
                cJSON_AddItemToObject(out,lua_tostring(L,-2),item);
                lua_pop(L, 1);
            }else{
                lua_pushvalue(L,-2);
                cJSON_AddItemToObject(out,lua_tostring(L,-1),item);
                lua_pop(L,2);
            }
        }

    }else{
        for(int i = 1;i<=size;i++){
            cJSON *item = nullptr;
            lua_rawgeti(L,index,i);
            lua2json(L, -1, item);
            cJSON_AddItemToArray(out, item);
            lua_pop(L, 1);
        }
    }
    return true;
}

char* lua2json(struct lua_State*L,int startIndex,int exitIndex){
    cJSON *root = cJSON_CreateArray();
    for(int i = startIndex;i<=exitIndex;i++){
        cJSON *item = nullptr;
        lua2json(L, i, item);
        cJSON_AddItemToArray(root, item);
    }
    auto r = cJSON_Print(root);
    cJSON_Delete(root);
    return r;
}

bool json2lua(struct lua_State*L,cJSON*cj){
    switch (cj->type) {
        case cJSON_False:
            lua_pushboolean(L,0);
            break;
        case cJSON_True:
            lua_pushboolean(L,1);
            break;
        case cJSON_NULL:
            lua_pushnil(L);
            break;
        case cJSON_Number:
            lua_pushnumber(L,cj->valuedouble);
            break;
        case cJSON_String:
            lua_pushstring(L,cj->valuestring);
            break;
        case cJSON_Object:{
            lua_newtable(L);
            cJSON *child = nullptr;
            cJSON_ArrayForEach(child,cj){
                lua_pushstring(L,child->string);
                json2lua(L,child);
                lua_settable(L,-3);
            }
            break;
        }
        case cJSON_Array:{
            lua_newtable(L);
            cJSON *child = nullptr;
            int i = 1;
            cJSON_ArrayForEach(child,cj){
                json2lua(L,child);
                lua_rawseti(L,-2,i++);
            }
            break;
        }
        default:
            break;
    }
    return true;
}