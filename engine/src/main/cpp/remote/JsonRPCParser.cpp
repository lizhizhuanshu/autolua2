//
// Created by lizhi on 2024/5/29.
//

#include "JsonRPCParser.h"
#include "lua2json.h"

constexpr const char* RPC_FORMAT_ERROR = "rpc format error";

int JsonRPCParser::parse(struct lua_State *L, const char *data, int length) {
    cJSON* root = cJSON_Parse(data);
    if(root == nullptr){
        return -1;
    }
    auto top = lua_gettop(L);
    auto result = json2lua(L,root);
    cJSON_Delete(root);
    if(result){
        auto count = lua_gettop(L) - top;
        if(count != 1 || !lua_istable(L,-1)){
            lua_pushstring(L, RPC_FORMAT_ERROR);
            return -1;
        }
        auto tableIndex = lua_absindex(L,-1);
        auto type = lua_geti(L,tableIndex,1);
        if(type != LUA_TNUMBER){
            lua_pushstring(L, RPC_FORMAT_ERROR);
            return -1;
        }
        int t = lua_tointeger(L,-1);
        if(t != 0){
            lua_geti(L,tableIndex,2);
            return -1;
        }
        auto size = luaL_len(L,tableIndex);
        for(int i = 2; i <= size; i++){
            lua_geti(L,tableIndex,i);
        }
        return size-1;

    }
    return -1;
}

int JsonRPCParser::serialize(struct lua_State *L, int originIndex, std::string &out) {
    auto r = lua2json(L,originIndex,lua_gettop(L));
    if(r == nullptr){
        return -1;
    }
    out = r;
    free(r);
    return 0;
}
