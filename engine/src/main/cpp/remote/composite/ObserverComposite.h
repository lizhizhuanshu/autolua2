//
// Created by lizhi on 2024/5/30.
//

#ifndef AUTOLUA2_OBSERVERCOMPOSITE_H
#define AUTOLUA2_OBSERVERCOMPOSITE_H
#include "LuaInterpreter.h"
#include <memory>
class ObserverComposite: public LuaInterpreter::Observer{
    std::set<std::weak_ptr<LuaInterpreter::Observer>,std::owner_less<>> observers_;
    std::shared_mutex mutex_;
public:
    bool addObserver(std::shared_ptr<LuaInterpreter::Observer> observer);
    void removeObserver(std::shared_ptr<LuaInterpreter::Observer> observer);
    void onStateChange(ALEState state) override;
};


#endif //AUTOLUA2_OBSERVERCOMPOSITE_H
