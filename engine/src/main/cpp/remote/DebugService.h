//
// Created by lizhi on 2024/5/30.
//

#ifndef AUTOLUA2_DEBUGSERVICE_H
#define AUTOLUA2_DEBUGSERVICE_H
#include "CommonService.h"
#include "hv/EventLoop.h"
#include "hv/TcpServer.h"
#include "hv/TcpClient.h"

class DebugService : public CommonService{
protected:
    void send(int type, const char* data, int length) override ;
    bool isRunning() override;
public:
    DebugService(RemoteServerInfo info,std::shared_ptr<RPCParser> parser,
                 std::shared_ptr<hv::EventLoop> loop);
    ~DebugService() override;
    bool init();
    void start();
    void stop();
    void setup(struct lua_State*L) override;
    using StateListener = std::function<void(ALEState state)>;
    void setStateListener(StateListener listener){
        stateListener_ = std::move(listener);
    }
    ALEState getState(){
        std::lock_guard<std::mutex> lock(mutex_);
        return state_;
    }
    void onScreenShotRequest(std::function<void(std::vector<uint8_t>&)> handler){
        screenShotListener_ = std::move(handler);
    }
private:
    void onHandleOtherMessage(int type, const char* data, int length) override;
    std::function<void(std::vector<uint8_t>&)> screenShotListener_;
    std::shared_ptr<hv::EventLoop> loop_;
    std::mutex mutex_;
    ALEState state_;
    StateListener stateListener_;
    hv::TcpClientEventLoopTmpl<> client_;
    void notifyStateChange(ALEState state);
    void changeAndNotifyState(ALEState state);
    void onStart();
    static void onStop(std::shared_ptr<DebugService> self);
    void onConnect(const hv::SocketChannelPtr &channel);
    void onMessage(const hv::SocketChannelPtr &channel, hv::Buffer *buf);

    void sendAuthRequest();
    static int lPrint(lua_State*L);

};


#endif //AUTOLUA2_DEBUGSERVICE_H
