//
// Created by lizhi on 2024/5/17.
//

#ifndef AUTOLUA2_LUA2JSON_H
#define AUTOLUA2_LUA2JSON_H
#include "cJSON.h"
char* lua2json(struct lua_State*L,int startIndex,int exitIndex);
bool lua2json(struct lua_State*L ,int index, cJSON*&out);
bool lua2jsonObject(struct lua_State*L ,int index, cJSON*&out);
bool json2lua(struct  lua_State*L,cJSON*json);
#endif //AUTOLUA2_LUA2JSON_H
