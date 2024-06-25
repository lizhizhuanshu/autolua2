//
// Created by lizhi on 2024/5/30.
//

#ifndef AUTOLUA2_FATHERSERVICE_H
#define AUTOLUA2_FATHERSERVICE_H
#include "CommonService.h"
#include "hv/EventLoop.h"

class FatherService : public CommonService{
public:
    FatherService(RemoteServerInfo info,std::shared_ptr<RPCParser> parser,
                  const std::shared_ptr<hv::EventLoop>& eventLoop);
    ~FatherService() override;
    void start() ;
    void stop();
    void onStartDebugServiceCommand(std::function<void(const RemoteServerInfo&)> listener){
        onStartDebugServiceListener_ = std::move(listener);
    }
    void onStopDebugServiceCommand(std::function<void()> listener){
        onStopDebugServiceListener_ = std::move(listener);
    }
    void onStopEngineCommand(std::function<void()> listener){
        onStopEngineListener_ = std::move(listener);
    }
    void onSetRootDir(std::function<void(const std::string&)> listener){
        onSetRootDirListener_ = std::move(listener);
    }
    void debuggerStateChanged(ALEState state);
    void engineStateChanged(ALEState state);
protected:
    void send(int type, const char* data, int length) override ;
    bool isRunning() override;
private:
    void onHandleOtherMessage(int type, const char* data, int length) override;
    void initHio();
    static void onStart(const std::shared_ptr<FatherService> self);
    static void onStop(const std::shared_ptr<FatherService> self);
    static void onRelease(hio_t* io);
    static void onRead(hio_t* io, void* buf, int readBytes);
    std::shared_ptr<hv::EventLoop> eventLoop_;
    std::mutex mutex_;
    hio_t * hio_ = nullptr;
    hio_t * writeHio_ = nullptr;
    std::function<void(const RemoteServerInfo&)> onStartDebugServiceListener_;
    std::function<void()> onStopDebugServiceListener_;
    std::function<void()> onStopEngineListener_;
    std::function<void(const std::string&)> onSetRootDirListener_;
};


#endif //AUTOLUA2_FATHERSERVICE_H
