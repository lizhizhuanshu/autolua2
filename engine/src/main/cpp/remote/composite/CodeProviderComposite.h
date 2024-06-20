//
// Created by lizhi on 2024/5/30.
//

#ifndef AUTOLUA2_CODEPROVIDERCOMPOSITE_H
#define AUTOLUA2_CODEPROVIDERCOMPOSITE_H
#include "LuaInterpreter.h"
#include <memory>

class CodeProviderComposite:public LuaInterpreter::CodeProvider {
    std::set<std::weak_ptr<LuaInterpreter::CodeProvider>,std::owner_less<>> providers_;
    std::shared_mutex mutex_;
public:
    bool addProvider(std::shared_ptr<LuaInterpreter::CodeProvider> provider);
    void removeProvider(std::shared_ptr<LuaInterpreter::CodeProvider> provider);
    int loadModule(LuaInterpreter::SignalEvent *signalEvent, const char *path, std::string &out, int &type) override;
    int loadFile(LuaInterpreter::SignalEvent *signalEvent, const char *path, std::string &out, int &type) override;
};


#endif //AUTOLUA2_CODEPROVIDERCOMPOSITE_H
