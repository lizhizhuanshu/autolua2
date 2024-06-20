//
// Created by lizhi on 2024/5/16.
//


#include "util.h"
#include <jni.h>
static JavaVM *G_JavaVM = NULL;

JavaVM *GetJavaVM()
{
    return G_JavaVM;
}

JNIEnv* GetJNIEnv()
{
    JNIEnv *env;
    if (G_JavaVM->GetEnv((void**)&env, JNI_VERSION_1_6) != JNI_OK) {
        return NULL;
    }
    return env;
}

void InitJavaVM(JavaVM * vm)
{
    G_JavaVM = vm;
}
