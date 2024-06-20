//
// Created by lizhi on 2024/5/29.
//

#ifndef AUTOLUA2_JSONRPCPARSER_H
#define AUTOLUA2_JSONRPCPARSER_H
#include "CommonService.h"
#include "RPCParser.h"
class JsonRPCParser: public RPCParser{
public:
    int parse(struct lua_State*L,const char* data, int length) override ;
    int serialize(struct lua_State*L,int originIndex,std::string & out) override ;
};


#endif //AUTOLUA2_JSONRPCPARSER_H
