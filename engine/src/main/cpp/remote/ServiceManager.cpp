//
// Created by lizhi on 2024/5/29.
//

#include "ServiceManager.h"

#include "RemoteService.h"
#include "JsonRPCParser.h"


int
ServiceManager::CodeProvider::loadModule(LuaInterpreter::SignalEvent *signalEvent, const char *path,
                                         std::string &out, int &type) {
    auto manager = manager_.lock();
    if (manager) {
        return manager->loadModule(signalEvent, path, out, type);
    }
    return 0;
}

int
ServiceManager::CodeProvider::loadFile(LuaInterpreter::SignalEvent *signalEvent, const char *path,
                                       std::string &out, int &type) {
    auto manager = manager_.lock();
    if (manager) {
        return manager->loadFile(signalEvent, path, out, type);
    }
    return 0;
}

int ServiceManager::ResourceProvider::loadResource(LuaInterpreter::SignalEvent *signalEvent,
                                                   const char *path, std::string &out) {
    auto manager = manager_.lock();
    if (manager) {
        return manager->loadResource(signalEvent, path, out);
    }
    return 0;
}

void ServiceManager::Observer::onStateChange(ALEState state) {
    auto manager = manager_.lock();
    if (manager) {
        manager->onStateChange(state);
    }
}

void ServiceManager::start() {
    ALEState state = ALEState::kIdle;
    if(state_.compare_exchange_strong(state,ALEState::kRunning)){
        std::shared_lock<std::shared_mutex> lock(services_mutex);
        for (auto &service:services_)
            service->start();
    }
}

void ServiceManager::stop() {
    ALEState state = ALEState::kRunning;
    if (state_.compare_exchange_strong(state, ALEState::kIdle)) {
        std::shared_lock<std::shared_mutex> lock(services_mutex);
        for (auto &service:services_)
            service->stop();
    }
}



ALEState ServiceManager::getState() {
    return state_.load();
}

bool ServiceManager::addService(const RemoteServerInfo info) {
    auto service = std::make_shared<RemoteService>(info, parser_, eventLoop_);
    if(service->init()){
        return addService(service);
    }
}

void ServiceManager::removeService(const std::string &name) {
    std::shared_ptr<RemoteService> service;
    {
        std::shared_lock<std::shared_mutex> lock(services_mutex);
        for(auto &s:services_){
            if(s->getName() == name){
                service = s;
                break;
            }
        }
    }
    if(service){
        removeService(service);
    }
}

void ServiceManager::onStateChange(ALEState state) {
    observers_.onStateChange(state);
}

int ServiceManager::loadResource(LuaInterpreter::SignalEvent *signalEvent, const char *path,
                                 std::string &out) {

    return resourceProviders_.loadResource(signalEvent, path, out);
}

int ServiceManager::loadModule(LuaInterpreter::SignalEvent *signalEvent, const char *path,
                               std::string &out, int &type) {
    return codeProviders_.loadModule(signalEvent, path, out, type);
}

int ServiceManager::loadFile(LuaInterpreter::SignalEvent *signalEvent, const char *path,
                             std::string &out, int &type) {
    return codeProviders_.loadFile(signalEvent, path, out, type);
}

void ServiceManager::setup(struct lua_State *L) {
    std::vector<std::shared_ptr<LuaEnvironmentSetup>> setups;
    {
        std::shared_lock<std::shared_mutex> lock(services_mutex);
        for(auto &service:services_){
            setups.push_back(service);
        }
    }
    for(auto &setup:setups){
        setup->setup(L);
    }
}

bool ServiceManager::addService(std::shared_ptr<RemoteService> service) {
    bool result;
    {
        std::unique_lock<std::shared_mutex> lock(services_mutex);
        result = services_.insert(service).second;
    }
    if(result){
        if(service->hasServices(RemoteServerInfo::OBSERVER)){
            codeProviders_.addProvider(service);
        }
        if(service->hasServices(RemoteServerInfo::CODE_PROVIDER)){
            codeProviders_.addProvider(service);
        }
        if(service->hasServices(RemoteServerInfo::RESOURCE_PROVIDER)){
            resourceProviders_.addProvider(service);
        }
        service->setLuaInterpreter(interpreter_);
        if(state_.load() == ALEState::kRunning){
            service->start();
        }
    }
    return result;
}

void ServiceManager::removeService(std::shared_ptr<RemoteService> service) {
    bool result;
    {
        std::unique_lock<std::shared_mutex> lock(services_mutex);
        result = services_.erase(service) > 0;
    }
    if(result){
        if(service->hasServices(RemoteServerInfo::OBSERVER)){
            codeProviders_.removeProvider(service);
        }
        if(service->hasServices(RemoteServerInfo::CODE_PROVIDER)){
            codeProviders_.removeProvider(service);
        }
        if(service->hasServices(RemoteServerInfo::RESOURCE_PROVIDER)){
            resourceProviders_.removeProvider(service);
        }
    }
}




ServiceManager::ServiceManager(std::shared_ptr<hv::EventLoop> loop_)
                               :eventLoop_(loop_),
                               state_(ALEState::kIdle){
    parser_ = std::make_shared<JsonRPCParser>();

}

void ServiceManager::init() {
    auto self = shared_from_this();
    codeProvider_ = std::make_shared<CodeProvider>(self);
    resourceProvider_ = std::make_shared<ResourceProvider>(self);
    observer_ = std::make_shared<Observer>(self);
}

