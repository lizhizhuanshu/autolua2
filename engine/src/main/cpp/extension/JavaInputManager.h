//
// Created by lizhi on 2024/6/29.
//

#ifndef AUTOLUA2_JAVAINPUTMANAGER_H
#define AUTOLUA2_JAVAINPUTMANAGER_H
#include "Input.h"
#include "jni.h"
namespace autolua {

    class JavaInputManager: Input {
    public:
        explicit JavaInputManager(jobject obj);
        ~JavaInputManager();
        int syncPointer(Input::PointerState* pointerState) override;
        int releasePointer(int id) override;
        int keyDown(int key) override;
        int keyUp(int key) override;
        void releaseAllDown() override;
    private:
        jobject object_;
        jclass class_;
        jmethodID syncPointerMethodID;
        jmethodID releasePointerMethodID;
        jmethodID keyDownMethodID;
        jmethodID keyUpMethodID;
        jmethodID releaseAllDownMethodID;
    };

} // autolua

#endif //AUTOLUA2_JAVAINPUTMANAGER_H
