//
// Created by lizhi on 2024/5/30.
//
#include "my_log.h"
#include "Authorization.pb.h"
#include "interaction.pb.h"

#include "DebugService.h"
#include "lua_context.h"
#include "JsonRPCParser.h"
#define  LENGTH_FIELD_BYTES 4
#define  LENGTH_FIELD_OFFSET 0
#define  HEADER_LENGTH (LENGTH_FIELD_BYTES+1)


DebugService::DebugService(const RemoteServerInfo info, std::shared_ptr<RPCParser> parser,
                           std::shared_ptr<hv::EventLoop> loop)
                           :CommonService(info,parser),
                           loop_(loop),
                           client_(loop),
                           state_(ALEState::kIdle){
    unpack_setting_t setting{};
    setting.package_max_length = DEFAULT_PACKAGE_MAX_LENGTH;
    setting.mode = UNPACK_BY_LENGTH_FIELD;
    setting.length_field_bytes = LENGTH_FIELD_BYTES;
    setting.length_field_offset = LENGTH_FIELD_OFFSET;
    setting.body_offset = HEADER_LENGTH;
    setting.length_adjustment = -HEADER_LENGTH;
    setting.length_field_coding = ENCODE_BY_BIG_ENDIAN;
    client_.setUnpack(&setting);
    client_.onConnection = std::bind(&DebugService::onConnect, this, std::placeholders::_1);
    client_.onMessage = std::bind(&DebugService::onMessage, this, std::placeholders::_1, std::placeholders::_2);
}

DebugService::~DebugService() {
    client_.closesocket();
}

bool DebugService::init() {
    LOGD("DebugService: init");
    bool result;
    if(!info_.host.empty()){
        result = client_.createsocket(info_.port,info_.host.c_str()) != 0;
    }else{
        result = client_.createsocket(info_.port) != 0;
    }
    return result;
}

void DebugService::onConnect(const hv::SocketChannelPtr &channel) {
    std::string peeraddr = channel->peeraddr();
    if (channel->isConnected()) {
        sendAuthRequest();
        LOGI("%s connected! connfd=%d id=%d \n", peeraddr.c_str(), channel->fd(), channel->id());
    } else {
        failCallback();
        changeAndNotifyState(ALEState::kIdle);
        LOGI("%s disconnected! connfd=%d id=%d\n", peeraddr.c_str(), channel->fd(), channel->id());
    }
}

void DebugService::onMessage(const hv::SocketChannelPtr &channel, hv::Buffer *buf) {
    auto data = (u_char*)buf->data();
    auto type = data[LENGTH_FIELD_BYTES];
    auto bodySize = be32toh(*(uint32_t*)data) - HEADER_LENGTH;
    auto body = (const char*)data+HEADER_LENGTH;
    if(type == 101){
        AuthorizationResponse response;
        if(!response.ParseFromArray(body, bodySize)){
            LOGI("parse auth response failed bodyBytes %d\n",buf->size()-HEADER_LENGTH);
            channel->close();
            return;
        }
        if(response.code() != 0){
            LOGI("auth failed %d\n",response.code());
            channel->close();
            return;
        }
        changeAndNotifyState(ALEState::kRunning);
    }else{
        LOGD("onMessage type %d bodySize %d\n",type,bodySize);
        onHandleMessage(type, body, bodySize);
    }
}

void DebugService::start() {
    LOGD("DebugService: start");
    loop_->runInLoop(std::bind(&DebugService::onStart,this));
}

void DebugService::stop() {
    loop_->runInLoop(std::bind(&DebugService::onStop, getShared<DebugService>()));
}

void DebugService::setup(struct lua_State *L) {
    CommonService::setup(L);
    lua_pushlightuserdata(L,this);
    lua_pushcclosure(L,lPrint,1);
    lua_setglobal(L,"print");
}
static void toString(lua_State*L,int index,std::string &out)
{
    switch (lua_type(L,index)) {
        case LUA_TBOOLEAN:
            out.append(lua_toboolean(L,index)?"true":"false");
            break;
        case LUA_TNUMBER:
            if (lua_isinteger(L,index))
                out.append(std::to_string(lua_tointeger(L,index)));
            else
                out.append(std::to_string(lua_tonumber(L,index)));
            break;
        case LUA_TSTRING:
        {
            size_t size;
            const char* str = lua_tolstring(L,index,&size);
            out.append(str,size);
            break;
        }
        case LUA_TNONE:
        case LUA_TNIL:
            out.append(luaL_typename(L,index));
            break;
        default:
            const void*ptr = lua_topointer(L,index);
            out.append(luaL_typename(L,index))
                    .append("@")
                    .append(std::to_string((uint64_t)ptr));
    }
}

static void toString(lua_State*L,std::string &out)
{
    int top = lua_gettop(L);
    for (int i = 1; i <= top; ++i) {
        toString(L,i,out);
        out.append("  ");
    }
    if(!out.empty())
    {
        out.pop_back();
        out.pop_back();
    }
}


int DebugService::lPrint(lua_State *L) {
    auto self = (DebugService*)lua_touserdata(L, lua_upvalueindex(1));
    std::string  message;
    toString(L,message);
    lua_Debug  debug;
    bzero(&debug,sizeof debug);
    lua_getstack(L,1,&debug);
    lua_getinfo(L,"Sl",&debug);
    LogCommand command;
    command.set_source(debug.source?debug.source:"");
    command.set_line(debug.currentline);
    command.set_message(message);
    command.set_level(1);
    std::string data;
    command.SerializeToString(&data);
    self->send(MessageType::kLog,data.data(),data.size());
    return 0;
}

void
DebugService::sendAuthRequest() {
    AuthorizationRequest request;
    request.set_auth(info_.auth);
    request.set_sessiontype(SessionType::WORKER_AUTO_LUA_DEBUG);
    std::string data;
    request.SerializeToString(&data);
    send(100,data.data(),data.size());
}

void DebugService::send(int type, const char *data, int length) {
    u_char header[HEADER_LENGTH];
    auto len = htobe32(length+HEADER_LENGTH);
    memcpy(header,&len,LENGTH_FIELD_BYTES);
    header[LENGTH_FIELD_BYTES] = type;
    std::lock_guard<std::mutex> lock(mutex_);
    client_.send(header,HEADER_LENGTH);
    if(length == 0){
        printf("data  is null\n");
        return;
    }
    client_.send(data,length);
}

void DebugService::onStart() {
    LOGD("DebugService: onRun");
    if(state_ != ALEState::kIdle){
        return;
    }

    changeAndNotifyState(ALEState::kStarting);
    auto r =client_.startConnect();
    if(r <0){
        LOGE("start debug service failed %d\n",r);
        changeAndNotifyState(ALEState::kIdle);
        return;
    }
}

void DebugService::onStop(const std::shared_ptr<DebugService> self) {
    LOGI("DebugService: onStop");
    if(self->state_ == ALEState::kStopping || self->state_ == ALEState::kIdle){
        return;
    }
    self->changeAndNotifyState(ALEState::kStopping);
    self->client_.channel->close(true);
}

void DebugService::notifyStateChange(ALEState state) {
    if(stateListener_)
        stateListener_(state);
}

void DebugService::changeAndNotifyState(ALEState state) {
    state_ = state;
    notifyStateChange(state);
}

void DebugService::onHandleOtherMessage(int type, const char *data, int length) {
    if(type == MessageType::kScreenShotRequest){
        std::vector<uint8_t> bytes;
        if(screenShotListener_){
            screenShotListener_(bytes);
        }
        ScreenShotResponse response;
        response.set_data(bytes.data(),bytes.size());
        std::string responseData;
        response.SerializeToString(&responseData);
        send(MessageType::kScreenShotResponse, responseData.data(), responseData.size());
    }else{
        CommonService::onHandleOtherMessage(type,data,length);
    }
}

bool DebugService::isRunning() {
    return state_ == ALEState::kRunning;
}

