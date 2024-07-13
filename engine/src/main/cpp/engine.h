//
// Created by lizhi on 2024/5/10.
//

#ifndef AUTOLUA2_ENGINE_H
#define AUTOLUA2_ENGINE_H

#include <jni.h>
#include <memory>
#include <set>
#include <cJSON.h>
#include <atomic>
#include <thread>

#include "core/LuaInterpreter.h"
#include <hv/EventLoop.h>
#include "remote/ServiceManager.h"
#include "DebugService.h"
#include "FatherService.h"
#include "InputManager.h"
#include "Display.h"

class AutoLuaEngineService {
public:
    AutoLuaEngineService(JNIEnv*env, jobject obj,jobject display,jobject inputManager,bool isRoot);
    ~AutoLuaEngineService();
    int execute(const char*code,int32_t len,int codeType,int flags);
    void startFatherService(const RemoteServerInfo& info);
    void interrupt();
    void setRootDir(const char*dir);
    int start();
    void stop();
    void waitForStop();
    void destroy();
    bool addService(RemoteServerInfo info);
    void removeService(const std::string &name);
    void startDebugService(RemoteServerInfo info);
    void stopDebugService();
    enum class Target:int {
        kEngine = 0,
        kWorker = 1,
        kDebugger = 2,
    };

    int getState(Target target);
private:
    void onRun();
    void notifyStateChanged(ALEState state);


    std::string getRootDir();

    std::atomic<ALEState> state_;
    std::shared_mutex rootDirMutex;
    std::string rootDir;
    std::atomic_bool running_;
    std::condition_variable condVal_;
    std::mutex mutex_;
    std::thread thread_;

    Display display_;
    autolua::InputManager inputManager_;

    std::shared_ptr<hv::EventLoop> eventLoop_;
    std::shared_ptr<LuaInterpreter> interpreter_;
    std::shared_ptr<ServiceManager> serviceManager_;
    std::shared_ptr<DebugService> debugService_;
    std::shared_ptr<FatherService> fatherService_;

    std::shared_ptr<LuaInterpreter::LuaStateFactory> luaStateFactory_;
    std::shared_ptr<LuaInterpreter::Observer> observer_;
    std::shared_ptr<LuaInterpreter::CodeProvider> localCodeProvider_;
    std::shared_ptr<LuaInterpreter::ResourceProvider> localResourceProvider_;



    jclass clazz;
    jobject thiz;

    jmethodID newLuaContextMethodID;
    jmethodID releaseContextMethodID;
    jmethodID getModuleMethodID;
    jmethodID getFileMethodID;
    jmethodID getResourceMethodID;
    jmethodID onStateChangedMethodID;

    jclass codeClazz;
    jfieldID codeTypeField;
    jfieldID codeContentField;

    class LuaStateFactory:public LuaInterpreter::LuaStateFactory {
        AutoLuaEngineService * engine;
    public:
        LuaStateFactory(AutoLuaEngineService*engine):engine(engine){}
        lua_State* create() override;
        void release(lua_State* L) override;
    };

    class LuaInterpreterObserver:public LuaInterpreter::Observer {
        AutoLuaEngineService * engine;
    public:
        LuaInterpreterObserver(AutoLuaEngineService*engine):engine(engine){}
        void onStateChange(ALEState state) override;
    };

    class InterpreterCodeProvider:public LuaInterpreter::CodeProvider {
        AutoLuaEngineService * engine;
    public:
        InterpreterCodeProvider(AutoLuaEngineService*engine):engine(engine){}
        int loadModule(LuaInterpreter::SignalEvent*signalEvent,const char* path, std::string & out,int &type) override;
        int loadFile(LuaInterpreter::SignalEvent*signalEvent,const char* path, std::string & out,int &type) override;
    };

    class InterpreterResourceProvider:public LuaInterpreter::ResourceProvider {
        AutoLuaEngineService * engine;
    public:
        InterpreterResourceProvider(AutoLuaEngineService*engine):engine(engine){}
        int loadResource(LuaInterpreter::SignalEvent*signalEvent, const char* path,std::string & out) override;
    };

    friend LuaStateFactory;
    friend LuaInterpreterObserver;
    friend InterpreterCodeProvider;
    friend InterpreterResourceProvider;
};


#endif //AUTOLUA2_ENGINE_H
