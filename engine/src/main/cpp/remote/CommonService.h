//
// Created by lizhi on 2024/5/29.
//

#ifndef AUTOLUA2_COMMONSERVICE_H
#define AUTOLUA2_COMMONSERVICE_H

#include <unordered_map>
#include "LuaInterpreter.h"
#include "RemoteServerInfo.h"
#include "LuaEnvironmentSetup.h"
#include "RPCParser.h"

class CommonService: public std::enable_shared_from_this<CommonService>,
                     public LuaInterpreter::CodeProvider ,
                     public LuaInterpreter::ResourceProvider,
                     public LuaInterpreter::Observer,
                     public LuaEnvironmentSetup {
public:
    template <typename T>
    std::shared_ptr<T> getShared() {
        return std::dynamic_pointer_cast<T>(shared_from_this());
    }

    CommonService(const RemoteServerInfo info,std::shared_ptr<RPCParser> parser);
    virtual ~CommonService();
    void onStateChange(ALEState state) override;
    int loadResource(LuaInterpreter::SignalEvent*signalEvent, const char* path,std::string & out) override;
    int loadModule(LuaInterpreter::SignalEvent*signalEvent, const char* path, std::string & out,int &type) override;
    int loadFile(LuaInterpreter::SignalEvent*signalEvent, const char* path, std::string & out,int &type) override;
    virtual void setup(struct lua_State*L) override;

    void setLuaInterpreter(std::shared_ptr<LuaInterpreter> interpreter){
        interpreter_ = std::move(interpreter);
    }
    const std::string &getName(){
        return info_.name;
    }

    bool hasServices(int services){
        return (info_.services & services) == services;
    }

protected:
    const RemoteServerInfo info_;
    std::shared_ptr<LuaInterpreter> interpreter_;
    void onHandleMessage(int type, const char* data, int length);
    virtual void onHandleOtherMessage(int type, const char* data, int length);
    virtual void send(int type, const char* data, int length) = 0;
    void failCallback(int codeTypeOrError = 1);
    using Callback = std::function<void(const char*data,int length,int codeTypeOrError)>;
    Callback getCallback(uint32_t id);
    void putCallback(uint32_t id,Callback callback);
    void clearCallback(uint32_t id);
    virtual bool isRunning()=0;
private:
    void ensurePushServiceMetatable(struct lua_State*L);
    class ServiceInLua:private ServiceInfo {
    public:
        ServiceInLua(const ServiceInfo info,std::shared_ptr<CommonService> ptr);
        static int index(struct lua_State*L);
        static int call(struct lua_State*L);
    private:
        bool hasMethod(const char* name);
        std::weak_ptr<CommonService> service_;
    };


    std::shared_ptr<RPCParser> parser_;
    int rpc(struct lua_State*L,std::string*serviceName,const char*methodName,int methodLength,int &resultCount,int originIndex=2);
    std::shared_mutex mutex_;
    std::unordered_map<uint32_t,Callback> callbacks_;
    std::atomic_uint32_t id_;
};




#endif //AUTOLUA2_COMMONSERVICE_H
