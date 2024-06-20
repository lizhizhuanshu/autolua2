//
// Created by lizhi on 2024/5/30.
//

#include "CodeProviderComposite.h"

bool CodeProviderComposite::addProvider(std::shared_ptr<LuaInterpreter::CodeProvider> provider) {
    std::unique_lock<std::shared_mutex> lock(mutex_);
    return providers_.insert(provider).second;
}

void CodeProviderComposite::removeProvider(std::shared_ptr<LuaInterpreter::CodeProvider> provider) {
    std::unique_lock<std::shared_mutex> lock(mutex_);
    providers_.erase(provider);
}

int CodeProviderComposite::loadModule(LuaInterpreter::SignalEvent *signalEvent, const char *path,
                                      std::string &out, int &type) {
    std::vector<std::shared_ptr<LuaInterpreter::CodeProvider>> providers;
    {
        std::shared_lock<std::shared_mutex> lock(mutex_);
        for (auto provider:providers_) {
            if (auto p = provider.lock()) {
                providers.push_back(p);
            }
        }
    }
    for (auto &provider:providers) {
        auto result = provider->loadModule(signalEvent, path, out, type);
        if (result != 0) {
            return result;
        }
        if(out.size()>0){
            break;
        }
    }
    return 0;
}

int CodeProviderComposite::loadFile(LuaInterpreter::SignalEvent *signalEvent, const char *path,
                                    std::string &out, int &type) {
    std::vector<std::shared_ptr<LuaInterpreter::CodeProvider>> providers;
    {
        std::shared_lock<std::shared_mutex> lock(mutex_);
        for (auto provider:providers_) {
            if (auto p = provider.lock()) {
                providers.push_back(p);
            }
        }
    }
    for (auto &provider:providers) {
        auto result = provider->loadFile(signalEvent, path, out, type);
        if (result != 0) {
            return result;
        }
        if(out.size()>0){
            break;
        }
    }
    return 0;
}

