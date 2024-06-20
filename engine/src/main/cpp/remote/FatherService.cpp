//
// Created by lizhi on 2024/5/30.
//
#include "interaction.pb.h"
#include "FatherService.h"
#include "my_log.h"
#include "cJSON.h"
#define  LENGTH_FIELD_OFFSET 0
#define  LENGTH_FIELD_BYTES 4
#define  HEADER_LENGTH (LENGTH_FIELD_BYTES+1)

FatherService::FatherService(const RemoteServerInfo info, std::shared_ptr<RPCParser> parser,
                             const std::shared_ptr<hv::EventLoop> &eventLoop)
                             : CommonService(info, parser) {
    eventLoop_ = eventLoop;
    writeHio_ = hio_get(eventLoop_->loop(),2);
    if(writeHio_ == nullptr){
        LOGD("FatherService::FatherService hio_get failed");
    }
    initHio();
}

void FatherService::send(int type, const char *data, int length) {
    LOGD("FatherService::send type %d size %d",type,length);
    u_char header[HEADER_LENGTH];
    *((uint32_t*)(header)) = htobe32(length);
    header[LENGTH_FIELD_BYTES] = type;
    std::lock_guard<std::mutex> lock(mutex_);
    static std::string Header("<AutoLua>");
    hio_write(writeHio_,Header.c_str(),Header.size());
    hio_write(writeHio_,header,HEADER_LENGTH);
    if(length > 0)
        hio_write(writeHio_,data,length);
}

std::string bytesToHexString(const std::vector<uint8_t>& bytes) {
    std::ostringstream oss;
    for (uint8_t byte : bytes) {
        oss << std::hex << std::setw(2) << std::setfill('0') << static_cast<int>(byte);
    }
    return oss.str();
}


void FatherService::onRead(hio_t *io, void *buf, int readBytes) {
    LOGD("FatherService::onRead %d",readBytes);
    std::vector<uint8_t> bytes((uint8_t*)buf,(uint8_t*)buf+80);
    auto self = (FatherService*) hio_context(io);
    auto body = (char*)buf + HEADER_LENGTH;
    auto type = ((u_char*)buf)[LENGTH_FIELD_BYTES];
    auto bodySize = readBytes - HEADER_LENGTH;
    self->onHandleMessage(type, (const char*)body, bodySize);
}

void FatherService::start() {
    LOGD("FatherService::start");
    eventLoop_->runInLoop(std::bind(onStart, getShared<FatherService>()));
}

void FatherService::stop() {
    eventLoop_->runInLoop(std::bind(onStop, getShared<FatherService>()));
}



void FatherService::onStart(const std::shared_ptr<FatherService> self) {
    if(self->hio_ == nullptr){
        self->initHio();
    }
//    LOGD("FatherService::onRun");
    hio_read_start(self->hio_);
}


void FatherService::initHio() {
    this->hio_ = hio_get(eventLoop_->loop(),0);
    unpack_setting_t  setting{};
    setting.mode = UNPACK_BY_LENGTH_FIELD;
    setting.package_max_length = DEFAULT_PACKAGE_MAX_LENGTH;
    setting.length_field_bytes = LENGTH_FIELD_BYTES;
    setting.length_field_offset = LENGTH_FIELD_OFFSET;
    setting.body_offset = HEADER_LENGTH;
    setting.length_field_coding = ENCODE_BY_BIG_ENDIAN;
    hio_set_unpack(hio_,&setting);
    hio_set_context(hio_,this);
    hio_setcb_read(hio_,onRead);
}

void FatherService::onStop(const std::shared_ptr<FatherService> self) {
    if(self->hio_){
        hio_read_stop(self->hio_);
    }
}

void FatherService::onRelease(hio_t *io) {
    if(io){
        hio_close(io);
    }
}

FatherService::~FatherService() {
    eventLoop_->runInLoop(std::bind(onRelease,hio_));
}

static bool parseRemoteServerInfo(const char* info,RemoteServerInfo& serverInfo){
    cJSON* root = cJSON_Parse(info);
    if(root == nullptr){
        return false;
    }
    cJSON* host = cJSON_GetObjectItem(root, "host");
    cJSON* port = cJSON_GetObjectItem(root,"port");
    cJSON* name = cJSON_GetObjectItem(root,"name");
    cJSON* services = cJSON_GetObjectItem(root,"services");
    cJSON* auth = cJSON_GetObjectItem(root,"auth");
    if( port == nullptr || name == nullptr || services == nullptr){
        cJSON_Delete(root);
        return false;
    }
    if(host != nullptr) serverInfo.host = host->valuestring;
    if(auth != nullptr) serverInfo.auth = auth->valuestring;
    serverInfo.port = port->valueint;
    serverInfo.name = name->valuestring;
    serverInfo.services = services->valueint;


    cJSON * rpcServices = cJSON_GetObjectItem(root,"rpcServices");
    if(rpcServices != nullptr){
        cJSON* rpcService = nullptr;
        cJSON_ArrayForEach(rpcService,rpcServices){
            cJSON* rpcName = cJSON_GetObjectItem(rpcService,"name");
            cJSON* methods = cJSON_GetObjectItem(rpcService,"methods");
            if(rpcName == nullptr || methods == nullptr){
                continue;
            }
            ServiceInfo serviceInfo;
            serviceInfo.name = rpcName->valuestring;
            cJSON* method = nullptr;
            cJSON_ArrayForEach(method,methods){
                serviceInfo.methodsName.insert(method->valuestring);
            }
            serverInfo.rpcServices.insert(serviceInfo);
        }
    }

    cJSON_Delete(root);
    return true;
}


void FatherService::onHandleOtherMessage(int type, const char *data, int length) {
    LOGD("FatherService::onHandleOtherMessage type %d size %d",type,length);
    switch (type) {
        case MessageType::kStartDebuggerCommand:
            if(onStartDebugServiceListener_){
                StartDebuggerCommand command;
                if(!command.ParseFromArray(data,length)){
                    LOGD("FatherService::onHandleOtherMessage parse StartDebuggerCommand failed");
                    return;
                }
                RemoteServerInfo serverInfo;
                if(parseRemoteServerInfo(command.info().c_str(),serverInfo)){
                    onStartDebugServiceListener_(serverInfo);
                }else{
                    LOGD("FatherService::onHandleOtherMessage parseRemoteServerInfo failed");
                }
            }
            break;
        case MessageType::kStopDebuggerCommand:
            if(onStopDebugServiceListener_){
                onStopDebugServiceListener_();
            }else{
                LOGD("FatherService::onHandleOtherMessage onStopDebugServiceListener_ is null");
            }
            break;
        case MessageType::kStopEngineCommand:
            LOGD("FatherService::onHandleOtherMessage kStopEngineCommand");
            if(onStopEngineListener_){
                onStopEngineListener_();
            }else{
                LOGD("FatherService::onHandleOtherMessage onStopEngineListener_ is null");
            }
            break;
        case MessageType::kSetRootDirCommand:
            if(onSetRootDirListener_){
                SetRootDirCommand command;
                if(!command.ParseFromArray(data,length)){
                    LOGD("FatherService::onHandleOtherMessage parse SetRootDirCommand failed");
                    return;
                }
                onSetRootDirListener_(command.root_dir());
            }
            break;
        default:
            CommonService::onHandleOtherMessage(type,data,length);
            break;
    }
}

void FatherService::debuggerStateChanged(ALEState state) {
    NotifyState command;
    command.set_state(static_cast<int>(state));
    command.set_other(NotificationSource::kDebugger);
    std::string data;
    command.SerializeToString(&data);
    send(MessageType::kNotifyState,data.data(),data.size());
}

void FatherService::engineStateChanged(ALEState state) {
    NotifyState command;
    command.set_state(static_cast<int>(state));
    command.set_other(NotificationSource::kEngine);
    std::string data;
    command.SerializeToString(&data);
    send(MessageType::kNotifyState,data.data(),data.size());
}
