//
// Created by lizhi on 2024/6/29.
//

#include "JavaInputManager.h"
#include "util.h"

namespace autolua {
    JavaInputManager::JavaInputManager(jobject obj) {
        JNIEnv*env = GetJNIEnv();
        object_ = env->NewWeakGlobalRef(obj);

        jclass jc = env->FindClass("com/autolua/engine/extension/input/InputManager");
        syncPointerMethodID = env->GetMethodID(jc,"syncPointer", "(Lcom/autolua/engine/extension/input/InputManager$PointerState;)Z");
        releasePointerMethodID = env->GetMethodID(jc,"releasePointer","(I)Z");
        keyDownMethodID = env->GetMethodID(jc,"keyDown", "(I)Z");
        keyUpMethodID = env->GetMethodID(jc,"keyUp", "(I)Z");
        releaseAllDownMethodID = env->GetMethodID(jc,"releaseAllDown","()V");
        class_ = (jclass)env->NewWeakGlobalRef(jc);
        env->DeleteLocalRef(jc);
    }

    int JavaInputManager::syncPointer(Input::PointerState *pointerState) {
        JNIEnv *env = GetJNIEnv();
        jclass pointerStateClass = env->FindClass("com/autolua/engine/extension/input/InputManager$PointerState");
        jmethodID constructor = env->GetMethodID(pointerStateClass,"<init>","(I)V");
        jobject state = env->NewObject(pointerStateClass,constructor,pointerState->id);
        jclass floatClass = env->FindClass("java/lang/Float");
        jmethodID floatConstructor = env->GetMethodID(floatClass,"<init>","(F)V");
#define SET_FLOAT(field) jfieldID field##Field = env->GetFieldID(pointerStateClass,#field,"Ljava/lang/Float;"); \
            jobject field = env->NewObject(floatClass,floatConstructor,pointerState->field);\
        env->SetObjectField(state,field##Field,field);\
        env->DeleteLocalRef(field);

        if(pointerState->hasFlag(Input::PointerState::kX)){
            SET_FLOAT(x)
        }
        if(pointerState->hasFlag(Input::PointerState::kY)){
            SET_FLOAT(y)
        }
        if(pointerState->hasFlag(Input::PointerState::kMajor)){
            SET_FLOAT(major)
        }
        if(pointerState->hasFlag(Input::PointerState::kMinor)){
            SET_FLOAT(minor)
        }
        if(pointerState->hasFlag(Input::PointerState::kPressure)){
            SET_FLOAT(pressure)
        }
        if(pointerState->hasFlag(Input::PointerState::kSize)){
            SET_FLOAT(size)
        }
        env->DeleteLocalRef(floatClass);
        jboolean result = env->CallBooleanMethod(object_,syncPointerMethodID,state);
        if(pointerState->id == -1){
            pointerState->id = env->GetIntField(state,env->GetFieldID(pointerStateClass,"id","I"));
        }
        env->DeleteLocalRef(state);
        env->DeleteLocalRef(pointerStateClass);
        pointerState->clearFlag();
        return result;
    }

    int JavaInputManager::releasePointer(int id) {
        JNIEnv *env = GetJNIEnv();
        jboolean result = env->CallBooleanMethod(object_,releasePointerMethodID,id);
        return result;
    }

    int JavaInputManager::keyDown(int key) {
        JNIEnv *env = GetJNIEnv();
        jboolean result = env->CallBooleanMethod(object_,keyDownMethodID,key);
        return result;
    }

    int JavaInputManager::keyUp(int key) {
        JNIEnv *env = GetJNIEnv();
        jboolean result = env->CallBooleanMethod(object_,keyUpMethodID,key);
        return result;
    }

    void JavaInputManager::releaseAllDown() {
        JNIEnv *env = GetJNIEnv();
        env->CallVoidMethod(object_,releaseAllDownMethodID);
    }

    JavaInputManager::~JavaInputManager() {
        JNIEnv*env = GetJNIEnv();
        env->DeleteWeakGlobalRef(object_);
        env->DeleteWeakGlobalRef(class_);
    }
} // autolua