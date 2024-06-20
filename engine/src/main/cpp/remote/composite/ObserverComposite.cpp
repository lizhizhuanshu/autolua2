//
// Created by lizhi on 2024/5/30.
//

#include "ObserverComposite.h"

bool ObserverComposite::addObserver(std::shared_ptr<LuaInterpreter::Observer> observer) {
    std::unique_lock<std::shared_mutex> lock(mutex_);
    return observers_.insert(observer).second;
}

void ObserverComposite::removeObserver(std::shared_ptr<LuaInterpreter::Observer> observer) {
    std::unique_lock<std::shared_mutex> lock(mutex_);
    observers_.erase(observer);
}

void ObserverComposite::onStateChange(ALEState state) {
    std::vector<std::shared_ptr<LuaInterpreter::Observer>> observers;
    {
        std::shared_lock<std::shared_mutex> lock(mutex_);
        for (auto observer:observers_) {
            if (auto p = observer.lock()) {
                observers.push_back(p);
            }
        }
    }
    for (auto &observer:observers) {
        observer->onStateChange(state);
    }
}