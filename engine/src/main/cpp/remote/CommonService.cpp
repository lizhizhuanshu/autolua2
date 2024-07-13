//
// Created by lizhi on 2024/5/30.
//

#include "interaction.pb.h"
#include "CommonService.h"
#include "my_log.h"
#include "mlua.h"
#include "lua_context.h"

CommonService::~CommonService() {
    for(auto&[id,callback]:callbacks_){
        callback(nullptr,0,-1);
    }
}



void CommonService::onHandleMessage(int type, const char *data, int length) {
    switch (type){
        case MessageType::kExecuteCode:{
            ExecuteCode command;
            auto r = command.ParseFromArray(data,length);
            if(!r){
                LOGE("parse execute code failed");
                break;
            }
            if(!hasServices(RemoteServerInfo::CONTROLLER)){
                LOGE("service not support execute code");
                break;
            }
            LuaInterpreter::CodeMode codeMode = LuaInterpreter::CodeMode::kTextOrBinary;
            if(command.code_type() == CodeType::kText){
                codeMode = LuaInterpreter::CodeMode::kText;
            }else if(command.code_type() == CodeType::kBinary){
                codeMode = LuaInterpreter::CodeMode::kBinary;
            }
            interpreter_->execute(command.code(),
                                  codeMode,
                                  LuaInterpreter::kNone,
                                  nullptr,
                                  std::bind(&CommonService::onErrorCall,this,std::placeholders::_1));
            break;
        }
        case MessageType::kInterrupt:{
            if(!hasServices(RemoteServerInfo::CONTROLLER)){
                LOGE("service not support interrupt");
                break;
            }
            interpreter_->interrupt();
            break;
        }
        case MessageType::kPause:{
            if(!hasServices(RemoteServerInfo::CONTROLLER)){
                LOGE("service not support pause");
                break;
            }
            interpreter_->pause();
            break;
        }
        case MessageType::kResume:{
            if(!hasServices(RemoteServerInfo::CONTROLLER)){
                LOGE("service not support resume");
                break;
            }
            interpreter_->resume();
            break;
        }
        case MessageType::kGetCodeResponse:{
            GetCodeResponse response;
            auto r = response.ParseFromArray(data,length);
            if(!r){
                LOGE("parse get code response failed");
                break;
            }
            if(!hasServices(RemoteServerInfo::CODE_PROVIDER)){
                LOGE("service not support get code response");
                break;
            }
            auto callback = getCallback(response.id());
            if(callback){
                callback(response.data().c_str(),response.data().size(),response.code_type());
            }
            break;
        }
        case MessageType::kGetResourceResponse:{
            GetResourceResponse response;
            auto r = response.ParseFromArray(data,length);
            if(!r){
                LOGE("parse get resource response failed");
                break;
            }
            if(!hasServices(RemoteServerInfo::RESOURCE_PROVIDER)){
                LOGE("service not support get resource response");
                break;
            }
            auto callback = getCallback(response.id());
            if(callback){
                callback(response.data().c_str(),response.data().size(),0);
            }
            break;
        }
        case MessageType::kRpcResponse:{
            RpcResponse response;
            auto r = response.ParseFromArray(data,length);
            if(!r){
                LOGE("parse rpc response failed");
                break;
            }
            auto callback = getCallback(response.id());
            if(callback){
                callback(response.data().c_str(),response.data().size(),0);
            }
            break;

        }
        default:
            onHandleOtherMessage(type,data,length);
            break;
    }
}




void CommonService::onStateChange(ALEState state) {
    if(!hasServices(RemoteServerInfo::OBSERVER) || !isRunning()){
        return;
    }
    NotifyState command;
    command.set_other(NotificationSource::kWorker);
    command.set_state(static_cast<int>(state));
    std::string data;
    command.SerializeToString(&data);
    send(MessageType::kNotifyState,data.c_str(),data.size());
}


int CommonService::loadResource(LuaInterpreter::SignalEvent *signalEvent, const char *path,
                                   std::string &out) {
    if(!hasServices(RemoteServerInfo::OBSERVER) || !isRunning()){
        return 0;
    }
    GetResourceRequest request;
    request.set_path(path);
    auto id = this->id_.fetch_add(1);
    request.set_id(id);
    std::string data;
    request.SerializeToString(&data);
    int r = 0;
    auto locker = signalEvent->locker();
    locker.unlock();
    putCallback(id,[signalEvent,&out,&r](const char*data,int length,int codeTypeOrError){
        r = codeTypeOrError;
        if(data){
            out.assign(data,length);
        }
        signalEvent->safeNotify(LuaInterpreter::SignalEvent::kAwake);
    });
    send(MessageType::kGetResource,data.c_str(),data.size());
    locker.lock();
    signalEvent->wait(locker);
    signalEvent->clean(LuaInterpreter::SignalEvent::kAwake);
    locker.unlock();
    clearCallback(id);
    return r;
}


int CommonService::loadModule(LuaInterpreter::SignalEvent *signalEvent, const char *path,
                                 std::string &out, int &type) {

    if(!hasServices(RemoteServerInfo::OBSERVER) || !isRunning()){
        return 0;
    }
    GetCodeRequest request;
    request.set_path(path);
    request.set_from_type(kModule);
    auto id = this->id_.fetch_add(1);
    request.set_id(id);
    std::string data;
    request.SerializeToString(&data);
    int r = 0;
    auto locker = signalEvent->locker();
    locker.unlock();
    putCallback(id,[signalEvent,&out,&r,&type](const char*data,int length,int codeTypeOrError){
        if(length == 0){
            r = codeTypeOrError;
            type = 0;
        }else{
            r = 0;
            type = codeTypeOrError;
        }
        if(data)
            out.assign(data,length);
        signalEvent->safeNotify(LuaInterpreter::SignalEvent::kAwake);
    });
    send(MessageType::kGetCode,data.c_str(),data.size());
    locker.lock();
    signalEvent->wait(locker);
    signalEvent->clean(LuaInterpreter::SignalEvent::kAwake);
    locker.unlock();
    clearCallback(id);
    return r;
}



typename CommonService::Callback CommonService::getCallback(uint32_t id) {
    std::shared_lock lock(mutex_);
    auto it = callbacks_.find(id);
    if(it == callbacks_.end()){
        return nullptr;
    }
    return it->second;
}


void CommonService::clearCallback(uint32_t id) {
    std::unique_lock lock(mutex_);
    callbacks_.erase(id);
}

void CommonService::putCallback(uint32_t id, CommonService::Callback callback) {
    std::unique_lock lock(mutex_);
    callbacks_[id] = callback;
}

int CommonService::loadFile(LuaInterpreter::SignalEvent *signalEvent, const char *path,
                               std::string &out, int &type) {
    if(!hasServices(RemoteServerInfo::OBSERVER) || !isRunning()){
        return 0;
    }
    GetCodeRequest request;
    request.set_path(path);
    request.set_from_type(kFile);
    auto id = this->id_.fetch_add(1);
    request.set_id(id);
    std::string data;
    request.SerializeToString(&data);
    int r = 0;
    auto locker = signalEvent->locker();
    locker.unlock();
    putCallback(id,[signalEvent,&out,&r,&type](const char*data,int length,int codeTypeOrError){
        if(length == 0){
            r = codeTypeOrError;
            type = 0;
        }else{
            r = 0;
            type = codeTypeOrError;
        }
        if(data)
            out.assign(data,length);
        signalEvent->safeNotify(LuaInterpreter::SignalEvent::kAwake);
    });
    send(MessageType::kGetCode,data.c_str(),data.size());
    locker.lock();
    signalEvent->wait(locker);
    signalEvent->clean(LuaInterpreter::SignalEvent::kAwake);
    locker.unlock();
    clearCallback(id);
    return r;
}


void CommonService::setup(struct lua_State *L) {
    if(info_.rpcServices.empty())
        return;
    ensurePushServiceMetatable(L);
    lua_pop(L,1);
    for(auto &rpc:info_.rpcServices){
        luaL_pushNewObject(CommonService::ServiceInLua,L,rpc,this->shared_from_this());
        lua_setglobal(L,rpc.name.c_str());
    }
}

void CommonService::ensurePushServiceMetatable(struct lua_State *L) {
    if(luaL_newClassMetatable(CommonService::ServiceInLua,L)){
        luaL_Reg methods[] = {
                {"__gc",lua::finish<CommonService::ServiceInLua>},
                {"__index",CommonService::ServiceInLua::index},
                {nullptr, nullptr}
        };
        luaL_setfuncs(L,methods,0);
    }
}

int CommonService::rpc(struct lua_State *L, std::string *serviceName, const char *methodName,
                          int methodLength,int &resultCount,int originIndex) {
    auto id = id_.fetch_add(1);
    RpcRequest request;
    request.set_id(id);
    request.set_service(*serviceName);
    request.set_method(methodName,methodLength);
    auto parseResult = parser_->serialize(L, originIndex, *request.mutable_data());
    if(parseResult != 0){
        lua_pushstring(L,"serialize rpc data failed");
        resultCount = -1;
        return 0;
    }
    std::string data;
    request.SerializeToString(&data);
    int r = 0;
    resultCount = 0;
    auto context = toLuaContext(L);
    auto signalEvent = (LuaInterpreter::SignalEvent*)context->signalEvent;
    auto locker = signalEvent->locker();
    locker.unlock();
    putCallback(id,[signalEvent,&r,&resultCount,this,L](const char*data,int length,int codeTypeOrError){
        r = codeTypeOrError;
        if(r == 0 && length>0){
            resultCount = parser_->parse(L,data,length);
        }
        signalEvent->safeNotify(LuaInterpreter::SignalEvent::kAwake);
    });
    send(MessageType::kRpc,data.c_str(),data.size());
    locker.lock();
    signalEvent->wait(locker);
    signalEvent->clean(LuaInterpreter::SignalEvent::kAwake);
    locker.unlock();
    clearCallback(id);
    return r;
}


void CommonService::failCallback(int codeTypeOrError) {
    std::unique_lock<std::shared_mutex> lock(mutex_);
    for(auto&[id,callback]:callbacks_){
        callback(nullptr,0,codeTypeOrError);
    }
    callbacks_.clear();
}


CommonService::CommonService(const RemoteServerInfo info,std::shared_ptr<RPCParser> parser)
        : info_(info),parser_(parser),id_(0){
}

void CommonService::onHandleOtherMessage(int type, const char *data, int length) {
    LOGE("unknown message type %d",type);
}

int CommonService::onErrorCall(struct lua_State *L) {
    size_t size = 0;
    const char* message = luaL_checklstring(L,-1,&size);
    luaL_traceback(L,L,message,1);
    message = luaL_checklstring(L,-1,&size);
    lua_pop(L,1);
    lua_Debug  debug;
    bzero(&debug,sizeof debug);
    lua_getstack(L,2,&debug);
    lua_getinfo(L,"Sl",&debug);
    LogCommand command;
    command.set_source(debug.source?debug.source:"");
    command.set_line(debug.currentline);
    command.set_message(message,size);
    command.set_level(1);
    std::string data;
    command.SerializeToString(&data);
    send(MessageType::kLog,data.data(),data.size());
    return 0;
}


CommonService::ServiceInLua::ServiceInLua(const ServiceInfo info,
                                             std::shared_ptr<CommonService> ptr)
        :ServiceInfo(info),service_(ptr){

}

bool CommonService::ServiceInLua::hasMethod(const char *name) {
    auto r = methodsName.find(name);
    return r  != methodsName.end();
}

int CommonService::ServiceInLua::index(struct lua_State *L) {
    auto self = luaL_checkObject(CommonService::ServiceInLua,L,1);
    auto name = luaL_checkstring(L,2);
    if (self->hasMethod(name)){
        lua_pushcclosure(L,call,1);
        return 1;
    }
    return 0;
}

int CommonService::ServiceInLua::call(struct lua_State *L) {
    auto self = luaL_checkObject(CommonService::ServiceInLua,L,1);
    auto service = self->service_.lock();
    if(!service || !service->isRunning()){
        service.~shared_ptr();
        return luaL_error(L,"service is dead");
    }
    size_t size;
    auto data = luaL_checklstring(L, lua_upvalueindex(1),&size);
    auto *serviceName = &self->name;
    int resultCount = 0;
    auto r = service->rpc(L,serviceName,data,size,resultCount);
    if(r & LuaInterpreter::SignalEvent::kQuit || (r & LuaInterpreter::SignalEvent::kInterrupt)){
        service.~shared_ptr();
        lua_pushstring(L,"interrupt");
        lua_error(L);
    }
    if(r != 0){
        service.~shared_ptr();
        luaL_error(L,"rpc failed %d",r);
    }
    if(resultCount == -1){
        service.~shared_ptr();
        lua_error(L);
    }
    return resultCount;
}

