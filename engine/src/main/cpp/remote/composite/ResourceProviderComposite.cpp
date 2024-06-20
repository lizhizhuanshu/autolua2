//
// Created by lizhi on 2024/5/30.
//

#include "ResourceProviderComposite.h"

bool
ResourceProviderComposite::addProvider(std::shared_ptr<LuaInterpreter::ResourceProvider> provider) {
    std::unique_lock<std::shared_mutex> lock(mutex_);
    return providers_.insert(provider).second;
}

void ResourceProviderComposite::removeProvider(
        std::shared_ptr<LuaInterpreter::ResourceProvider> provider) {
    std::unique_lock<std::shared_mutex> lock(mutex_);
    providers_.erase(provider);
}

int
ResourceProviderComposite::loadResource(LuaInterpreter::SignalEvent *signalEvent, const char *path,
                                        std::string &out) {
    std::vector<std::shared_ptr<LuaInterpreter::ResourceProvider>> providers;
    {
        std::shared_lock<std::shared_mutex> lock(mutex_);
        for (auto provider:providers_) {
            if (auto p = provider.lock()) {
                providers.push_back(p);
            }
        }
    }
    for (auto &provider:providers) {
        auto result = provider->loadResource(signalEvent, path, out);
        if (result != 0) {
            return result;
        }
        if (out.size() > 0) {
            break;
        }
    }
    return 0;
}
