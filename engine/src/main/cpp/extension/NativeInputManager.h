//
// Created by lizhi on 2024/7/3.
//

#ifndef AUTOLUA2_NATIVEINPUTMANAGER_H
#define AUTOLUA2_NATIVEINPUTMANAGER_H
#include "Input.h"
#include <unordered_set>
#include <vector>
#include <map>
#include <array>

#include "Display.h"

namespace autolua {

    class NativeInputManager:Input {
    public:
        explicit NativeInputManager(Display*display);
        ~NativeInputManager();
        bool canInjectTouch() const;
        int syncPointer(PointerState* pointerState) override;
        int releasePointer(int id) override;
        int keyDown(int key) override;
        int keyUp(int key) override;
        void releaseAllDown() override;

    private:
        struct DeviceInfo {
            int fd;
            std::unordered_set<int> keys;
        };
        DeviceInfo screenDevice_;
        std::vector<DeviceInfo> keyDevice_;
        std::array<bool,10> touchDown_{};
        int touchId_ = 1;
        std::map<int,int> commonKeyMap_;
        std::map<int,int> keyDown_;
        Display * display_;
        int newTouchId();
        int newPointerId();
    };
} // autolua

#endif //AUTOLUA2_NATIVEINPUTMANAGER_H
