//
// Created by lizhi on 2024/5/29.
//

#ifndef AUTOLUA2_SERVICEMANAGER_H
#define AUTOLUA2_SERVICEMANAGER_H
#include <memory>
#include "LuaInterpreter.h"
#include "RemoteServerInfo.h"
#include "RemoteService.h"
#include <hv/EventLoop.h>
#include <atomic>
#include "LuaEnvironmentSetup.h"
#include "composite/CodeProviderComposite.h"
#include "composite/ResourceProviderComposite.h"
#include "composite/ObserverComposite.h"
#include "RPCParser.h"

class ServiceManager:
        private LuaInterpreter::CodeProvider,
        private LuaInterpreter::ResourceProvider,
        private LuaInterpreter::Observer,
        public LuaEnvironmentSetup, public std::enable_shared_from_this<ServiceManager> {
public:
    ServiceManager(std::shared_ptr<hv::EventLoop> loop_);
    void init();
    void start();
    void stop();
    ALEState getState();

    bool addService(const RemoteServerInfo);
    void removeService(const std::string&name);

    void setLuaInterpreter(std::shared_ptr<LuaInterpreter> interpreter){
        interpreter_ = std::move(interpreter);
    }

    std::shared_ptr<LuaInterpreter::CodeProvider> getCodeProvider(){
        return codeProvider_;
    }
    std::shared_ptr<LuaInterpreter::ResourceProvider> getResourceProvider(){
        return resourceProvider_;
    }
    std::shared_ptr<LuaInterpreter::Observer> getObserver(){
        return observer_;
    }

    void setup(struct lua_State*L) override;
private:
    std::shared_ptr<hv::EventLoop> eventLoop_;
    std::shared_ptr<RPCParser> parser_;
    std::atomic<ALEState> state_;
    std::condition_variable cv_;
    std::mutex cv_mutex_;

    std::set<std::shared_ptr<RemoteService>> services_;

    CodeProviderComposite codeProviders_;
    ResourceProviderComposite resourceProviders_;
    ObserverComposite observers_;
    std::shared_mutex services_mutex;

    void onStateChange(ALEState state) override;
    int loadResource(LuaInterpreter::SignalEvent*signalEvent, const char* path,std::string & out) override;
    int loadModule(LuaInterpreter::SignalEvent*signalEvent, const char* path, std::string & out,int &type) override;
    int loadFile(LuaInterpreter::SignalEvent*signalEvent, const char* path, std::string & out,int &type) override;


    bool addService(std::shared_ptr<RemoteService> service);
    void removeService(std::shared_ptr<RemoteService> service);


    std::shared_ptr<LuaInterpreter> interpreter_;
    std::shared_ptr<LuaInterpreter::CodeProvider> codeProvider_;
    std::shared_ptr<LuaInterpreter::ResourceProvider> resourceProvider_;
    std::shared_ptr<LuaInterpreter::Observer> observer_;

    class CodeProvider:public LuaInterpreter::CodeProvider{
        std::weak_ptr<ServiceManager> manager_;
    public:
        CodeProvider(std::shared_ptr<ServiceManager> manager):manager_(manager){}
        int loadModule(LuaInterpreter::SignalEvent*signalEvent,const char* path, std::string & out,int &type) override;
        int loadFile(LuaInterpreter::SignalEvent*signalEvent,const char* path, std::string & out,int &type) override;
    };
    class ResourceProvider:public LuaInterpreter::ResourceProvider{
        std::weak_ptr<ServiceManager> manager_;
    public:
        ResourceProvider(std::shared_ptr<ServiceManager> manager):manager_(manager){}
        int loadResource(LuaInterpreter::SignalEvent*signalEvent,const char* path, std::string & out) override;
    };
    class Observer:public LuaInterpreter::Observer{
        std::weak_ptr<ServiceManager> manager_;
    public:
        Observer(std::shared_ptr<ServiceManager> manager):manager_(manager){}
        void onStateChange(ALEState state) override;
    };

    friend CodeProvider;
    friend ResourceProvider;
    friend Observer;
};


#endif //AUTOLUA2_SERVICEMANAGER_H
