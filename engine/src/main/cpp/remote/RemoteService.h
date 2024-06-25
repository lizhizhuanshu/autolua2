//
// Created by lizhi on 2024/5/29.
//

#ifndef AUTOLUA2_REMOTESERVICE_H
#define AUTOLUA2_REMOTESERVICE_H
#include "LuaInterpreter.h"
#include "CommonService.h"
#include <hv/TcpClient.h>

class RemoteService : public CommonService{
public:
    RemoteService(const RemoteServerInfo info,std::shared_ptr<RPCParser> parser,
                  std::shared_ptr<hv::EventLoop> loop);
    ~RemoteService();
    bool init();
    void start();
    void stop();
private:
    void onHandleMessage(const hv::ChannelPtr& conn, hv::Buffer* buf);
    void send(int type, const char* data, int length) override ;
    bool isRunning() override;
    std::shared_ptr<hv::EventLoop> loop_;
    hv::TcpClientEventLoopTmpl<> client_;
    std::mutex sendMutex_;
    bool inited_ = false;
};


#endif //AUTOLUA2_REMOTESERVICE_H
