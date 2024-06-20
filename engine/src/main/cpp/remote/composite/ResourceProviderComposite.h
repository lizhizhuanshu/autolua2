//
// Created by lizhi on 2024/5/30.
//

#ifndef AUTOLUA2_RESOURCEPROVIDERCOMPOSITE_H
#define AUTOLUA2_RESOURCEPROVIDERCOMPOSITE_H
#include "LuaInterpreter.h"
#include <memory>
class ResourceProviderComposite: public LuaInterpreter::ResourceProvider{
    std::set<std::weak_ptr<LuaInterpreter::ResourceProvider>,std::owner_less<>> providers_;
    std::shared_mutex mutex_;
public:
    bool addProvider(std::shared_ptr<LuaInterpreter::ResourceProvider> provider);
    void removeProvider(std::shared_ptr<LuaInterpreter::ResourceProvider> provider);
    int loadResource(LuaInterpreter::SignalEvent*signalEvent, const char* path,std::string & out) override;
};


#endif //AUTOLUA2_RESOURCEPROVIDERCOMPOSITE_H
