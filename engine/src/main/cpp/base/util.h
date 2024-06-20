//
// Created by lizhi on 2023/6/16.
//

#ifndef AUTOLUA2_OTHER_H
#define AUTOLUA2_OTHER_H

#include "jni.h"

template<class T>
class LocalReference
{
    JNIEnv*env;
    T o;
public:
    LocalReference(JNIEnv*env,T v)
            :env(env),o(v)
    {
    }
    ~LocalReference(){
        if (env && o)
        {
            env->DeleteLocalRef((jobject)o);
        }
    }
    T get(){
        return o;
    }
};

class LocalJavaString{
    JNIEnv*env;
    jstring javaString;
    const char* cString;
    jsize stringSize;
public:
    LocalJavaString(JNIEnv*env,jstring javaString)
            : env(env), javaString(javaString), cString(nullptr), stringSize(0)
    {
        if (javaString)
        {
            jboolean isCopy = 0;
            cString = env->GetStringUTFChars(javaString,&isCopy);
            stringSize=env->GetStringUTFLength(javaString);
        }
    }

    ~LocalJavaString(){
        if (javaString)
        {
            env->ReleaseStringUTFChars(javaString,cString);
            env->DeleteLocalRef(javaString);
        }
    }

    const char*str(){
        return cString;
    }

    jsize size() const{
        return stringSize;
    }
};


class LocalJavaBytes{
    JNIEnv*env;
    jbyteArray javaBytes;
    const char* cBytes;
    jsize bytesSize;
public:
    LocalJavaBytes(JNIEnv*env,jbyteArray javaBytes)
            : env(env), javaBytes(javaBytes),cBytes(nullptr), bytesSize(0)
    {
        if (javaBytes)
        {
            jboolean isCopy = 0;
            cBytes = (char*)env->GetByteArrayElements(javaBytes,&isCopy);
            bytesSize = env->GetArrayLength(javaBytes);
        }
    }

    ~LocalJavaBytes(){
        if (javaBytes)
        {
            env->ReleaseByteArrayElements(javaBytes,(jbyte*)cBytes,0);
            env->DeleteLocalRef(javaBytes);
        }
    }

    const char*str(){
        return cBytes;
    }

    jsize size() const{
        return bytesSize;
    }
};

#ifdef __cplusplus
extern "C"
{
#endif
JNIEnv *GetJNIEnv();
JavaVM *GetJavaVM();
void InitJavaVM(JavaVM* vm);
#ifdef __cplusplus
}
#endif


#endif //AUTOLUA2_OTHER_H
