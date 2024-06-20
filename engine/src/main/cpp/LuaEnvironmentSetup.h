//
// Created by lizhi on 2024/5/29.
//

#ifndef AUTOLUA2_LUAENVIRONMENTSETUP_H
#define AUTOLUA2_LUAENVIRONMENTSETUP_H
class LuaEnvironmentSetup {
public:
    virtual ~LuaEnvironmentSetup() = default;
    virtual void setup(struct lua_State*L) = 0;
};
#endif //AUTOLUA2_LUAENVIRONMENTSETUP_H
