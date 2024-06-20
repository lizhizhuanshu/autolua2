//
// Created by lizhi on 2024/5/30.
//

#ifndef AUTOLUA2_RPCPARSER_H
#define AUTOLUA2_RPCPARSER_H
class RPCParser {
public:
    virtual ~RPCParser() = default;
    virtual int parse(struct lua_State* L, const char* data,int length) =0;
    virtual int serialize(struct lua_State* L,int originIndex,std::string & out) =0;
};

#endif //AUTOLUA2_RPCPARSER_H
