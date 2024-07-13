
#ifndef LUAINTERPRETER_H
#define LUAINTERPRETER_H

#include<functional>
#include<mutex>
#include<thread>
#include<string>
#include<condition_variable>
#include<set>
#include<shared_mutex>
#include<atomic>
#include <memory>
#include <vector>
#include <lua.hpp>
#include "ALEState.h"

class LuaInterpreter
{
public:
    enum class CodeMode:int
    {
        kTextOrBinary = 0,
        kText = 1,
        kBinary = 2
    };

    enum ExecuteFlag
    {
        kNone =         0,
        kNewLua =          0b00000001,
        kRetainLua =    0b00000010
    };

    class SignalEvent {
    public:
        enum Type
        {
            kNon        =0b00000000,
            kExecute    =0b00000001,
            kQuit       =0b00000010,
            kInterrupt  =0b00000100,
            kPause      =0b00001000,
            kResume     =0b00010000,
            kAwake      =0b00100000
        };

        static auto toString(int signal){
            switch(signal){
                case kNon:
                    return "kNon";
                case kExecute:
                    return "kExecute";
                case kQuit:
                    return "kQuit";
                case kInterrupt:
                    return "kInterrupt";
                case kPause:
                    return "kPause";
                case kResume:
                    return "kResume";
                case kAwake:
                    return "kAwake";
                default:
                    return "unknown";
            }
        }

        std::string toString(){
            std::string r;
            if(has(kNon)){
                r.append("kNon");
                return r;
            }
            if(has(kExecute)){
                r.append("kExecute");
            }
            if(has(kQuit)){
                r.append("|kQuit");
            }
            if(has(kInterrupt)){
                r.append("|kInterrupt");
            }
            if(has(kPause)){
                r.append("|kPause");
            }
            if(has(kResume)){
                r.append("|kResume");
            }
            if(has(kAwake)){
                r.append("|kAwake");
            }
            return r;
        }

        auto locker(){
            return std::unique_lock<std::mutex>(mutex_);
        }

        bool has(int signal){
            return signals_ & signal;
        }

        void clean(int signal){
            signals_ &= ~signal;
        }

        void add(int signals){
            signals_ |= signals;
        }

        void set(int signals){
            signals_ = signals;
        }
        void safeNotify(int signals = 0){
            auto locker = std::unique_lock<std::mutex>(mutex_);
            signals_ |= signals;
            condVal_.notify_one();
        }
        void notify(){
            condVal_.notify_one();
        }

        int wait(std::unique_lock<std::mutex>& locker,int target = (kExecute|kAwake)){
            condVal_.wait(locker,[this,&target]{
                if(signals_ & kQuit ||(signals_ & kInterrupt))
                    return true;
                if(signals_ & kPause ){
                    if(signals_ & kResume){
                        signals_ &= ~kResume;
                        signals_ &= ~kPause;
                    }else
                        return false;
                }
                return (signals_ & target) !=0;
            });
            return signals_;
        }

        int wait_for(std::unique_lock<std::mutex>& locker,int timeout);


    private:
        int signals_ = 0;
        std::mutex mutex_;
        std::condition_variable condVal_;
    };

    class LuaStateFactory {
    public:
        virtual ~LuaStateFactory() = default;
        virtual struct lua_State* create() = 0;
        virtual void release(struct lua_State*L) = 0;
    };

    class Observer {
    public:
        virtual ~Observer() = default;
        virtual void onStateChange(ALEState state) = 0;
    };

    class ResourceProvider {
    public:
        virtual ~ResourceProvider() = default;
        virtual int loadResource(SignalEvent*signalEvent, const char* path,std::string & out) = 0;
    };

    class CodeProvider {
    public:
        virtual ~CodeProvider() = default;
        virtual int loadModule(SignalEvent*signalEvent,const char* path, std::string & out,int &type) = 0;
        virtual int loadFile(SignalEvent*signalEvent,const char* path, std::string & out,int &type) = 0;
    };


    typedef std::function<void(int resultCode,int resultIndex,struct lua_State*)> Callback;
    typedef std::function<int(struct lua_State*L)> ErrorCallback;

    explicit LuaInterpreter(std::shared_ptr<LuaStateFactory> luaFactory);
    ~LuaInterpreter();
    void attach(std::shared_ptr<Observer> observer,bool immediately = false);
    void detach(std::shared_ptr<Observer> observer);
    void addCodeProvider(std::shared_ptr<CodeProvider> provider);
    void removeCodeProvider(std::shared_ptr<CodeProvider> provider);
    void addResourceProvider(std::shared_ptr<ResourceProvider> provider);
    void removeResourceProvider(std::shared_ptr<ResourceProvider> provider);
    void onInitializeLuaThread(std::function<void()> callback);
    void onReleaseLuaThreadBefore(std::function<void()> callback);
    //返回错误代码，0表示成功，1表示已经不在空闲状态，2表示正在启动执行，3表示已经或正在销毁
    int execute(const std::string &code, CodeMode codeType =CodeMode::kTextOrBinary,
                int executeMode=kNone,
                Callback callback = nullptr, ErrorCallback errorCallback= nullptr);
    void interrupt();
    void pause();
    void resume();
    ALEState getState();
    void quit();
    void destroy();
private:
    void onReleaseAllResource();

    void ensureHasSignal(int signal);
    static int rawRequire(struct lua_State*L);
    static int lRequire(struct lua_State*L);
    static int lLoadFile(struct lua_State*L);
    static int lLoadResource(struct lua_State*L);

    void injectMethod(const char* name,lua_CFunction func);
    void notifyStateChange(ALEState state);
    void loop();
    void resetLuaState();
    void releaseLuaState();
    static int lSleep(struct lua_State*L);

    std::atomic_bool workerRunning_;
    std::shared_ptr<LuaStateFactory> luaFactory_;

    std::shared_mutex observersMutex_;
    std::set<std::weak_ptr<Observer>,std::owner_less<std::weak_ptr<Observer>>> observers_;

    std::function<void()> initializeLuaThreadCallback_;
    std::function<void()> releaseLuaThreadCallback_;

    std::shared_mutex resourceProvidersMutex_;
    std::shared_mutex codeProvidersMutex_;
    std::set<std::shared_ptr<LuaInterpreter::ResourceProvider>> resourceProviders_;
    std::set<std::shared_ptr<LuaInterpreter::CodeProvider>> codeProviders_;

    struct lua_State* luaState_;

    std::string code_;
    const char* codeType_;
    int executeMode_;
    Callback callback_;
    ErrorCallback errorCallback_;

    SignalEvent signalEvent_;

    ALEState state_;
    std::shared_mutex stateMutex_;

    std::atomic_bool destroy_;
    std::function<void()> workerStopCallback_;
};

#endif
