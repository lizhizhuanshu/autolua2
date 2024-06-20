//
// Created by lizhi on 2024/5/29.
//

#include "interaction.pb.h"
#include "RemoteService.h"
#include "my_log.h"

#define LENGTH_FIELD_BYTES 4
#define LENGTH_FIELD_OFFSET 0
#define HEADER_LENGTH (LENGTH_FIELD_BYTES+1)



RemoteService::RemoteService(const RemoteServerInfo info, std::shared_ptr<RPCParser> parser,
                             std::shared_ptr<hv::EventLoop> loop)
                             :CommonService(info,parser),
                             client_(loop),
                             loop_(loop){

    client_.onConnection = [this](const hv::ChannelPtr& conn) {
        if (conn->isOpened()) {
            LOGD("remote service connected %s", info_.name.c_str());
        }else{
            failCallback();
            LOGD("remote service disconnected %s", info_.name.c_str());
        }
    };
    client_.onMessage = std::bind(&RemoteService::onHandleMessage, this, std::placeholders::_1, std::placeholders::_2);
    unpack_setting_t setting{};
    setting.package_max_length = DEFAULT_PACKAGE_MAX_LENGTH;
    setting.mode = UNPACK_BY_LENGTH_FIELD;
    setting.length_field_bytes = LENGTH_FIELD_BYTES;
    setting.length_field_offset = LENGTH_FIELD_OFFSET;
    setting.body_offset = HEADER_LENGTH;
    setting.length_adjustment = -HEADER_LENGTH;
    setting.length_field_coding = ENCODE_BY_BIG_ENDIAN;
    client_.setUnpack(&setting);
}

void RemoteService::start() {
    if(inited_ && !client_.isConnected()){
        client_.start();
    }
}

bool RemoteService::init() {
    bool result;
    if(!info_.host.empty()){
        result = client_.createsocket(info_.port,info_.host.c_str()) != 0;
    }else{
        result = client_.createsocket(info_.port) != 0;
    }
    inited_ = result;
    return result;
}



void RemoteService::stop() {
    client_.closesocket();
}

void RemoteService::onHandleMessage(const hv::ChannelPtr &conn, hv::Buffer *buf) {
    auto data = (u_char*)buf->data();
    auto type = data[LENGTH_FIELD_BYTES];
    auto length = buf->size() - HEADER_LENGTH;
    CommonService::onHandleMessage(type,(const char*)data+HEADER_LENGTH,length);
}

void RemoteService::send(int type, const char *data, int length) {
    u_char header[HEADER_LENGTH];
    auto len = htobe32(length);
    memcpy(header,&len,LENGTH_FIELD_BYTES);
    header[LENGTH_FIELD_BYTES] = type;
    std::lock_guard<std::mutex> lock(sendMutex_);
    client_.send(header,HEADER_LENGTH);
    if(length > 0)
        client_.send(data,length);
}

RemoteService::~RemoteService() {
    stop();
}
