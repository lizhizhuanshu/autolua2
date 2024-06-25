
#include <string>
#include <hv/hv.h>
#include <hv/EventLoop.h>

#include<lua.hpp>
#include <cJSON.h>
#include "engine.h"
#include "util.h"
#include "lua_context.h"
#include "JsonRPCParser.h"
#include "my_log.h"
#include "Display.h"
#include <jni.h>

#include "lua_vision.h"


extern "C"
JNIEXPORT jlong JNICALL
Java_com_autolua_engine_core_AutoLuaEngineOnLocal_createNative(JNIEnv *env, jobject thiz,jobject display) {
    auto service = new AutoLuaEngineService(env,thiz,display);
    return (jlong)service;
}



extern "C"
JNIEXPORT void JNICALL
Java_com_autolua_engine_core_AutoLuaEngineOnLocal_releaseNative(JNIEnv *env, jobject thiz,
                                                                jlong ptr) {
    auto service = (AutoLuaEngineService*)ptr;
    delete service;
}
extern "C"
JNIEXPORT jint JNICALL
Java_com_autolua_engine_core_AutoLuaEngineOnLocal_executeNative(JNIEnv *env, jobject thiz,
                                                                jlong ptr, jbyteArray script,
                                                                jint code_type, jint flags) {
    auto service = (AutoLuaEngineService*)ptr;
    const char* code = (const char*)env->GetByteArrayElements(script, nullptr);
    auto len = env->GetArrayLength(script);
    jint result = service->execute(code,len,code_type,flags);
    env->ReleaseByteArrayElements(script, (jbyte*)code, JNI_ABORT);
    return result;
}
extern "C"
JNIEXPORT void JNICALL
Java_com_autolua_engine_core_AutoLuaEngineOnLocal_interruptNative(JNIEnv *env, jobject thiz,
                                                                  jlong ptr) {
    auto service = (AutoLuaEngineService*)ptr;
    service->interrupt();
}

static void attachJavaThread(){
    JNIEnv *env;
    auto javaVM = GetJavaVM();
    if (javaVM->GetEnv((void **) &env, JNI_VERSION_1_6) != JNI_OK) {
        javaVM->AttachCurrentThread(&env, NULL);
    }
}

static void detachJavaThread(){
    auto javaVM = GetJavaVM();
    javaVM->DetachCurrentThread();
}

AutoLuaEngineService::AutoLuaEngineService(JNIEnv *env, jobject obj,jobject display)
    :state_(ALEState::kIdle),
    running_(false){
    this->display_ = env->NewWeakGlobalRef(display);
    this->thiz = env->NewWeakGlobalRef(obj);
    jclass aClass = env->FindClass("com/autolua/engine/core/AutoLuaEngineOnLocal");
    newLuaContextMethodID = env->GetMethodID(aClass, "newLuaContext", "()J");
    releaseContextMethodID = env->GetMethodID(aClass, "releaseContext", "(J)V");
    getModuleMethodID = env->GetMethodID(aClass,"getModule",
                                         "(Ljava/lang/String;)Lcom/autolua/engine/core/AutoLuaEngine$CodeProvider$Code;");
    getFileMethodID = env->GetMethodID(aClass,"getFile","(Ljava/lang/String;)Lcom/autolua/engine/core/AutoLuaEngine$CodeProvider$Code;");
    getResourceMethodID = env->GetMethodID(aClass,"getResource", "(Ljava/lang/String;)[B");
    onStateChangedMethodID = env->GetMethodID(aClass,"onStateChanged", "(II)V");
    this->clazz = (jclass)env->NewWeakGlobalRef(aClass);
    env->DeleteLocalRef(aClass);

    aClass = env->FindClass("com/autolua/engine/core/AutoLuaEngine$CodeProvider$Code");
    codeTypeField = env->GetFieldID(aClass, "nType", "I");
    codeContentField = env->GetFieldID(aClass, "code", "[B");
    codeClazz = (jclass)env->NewWeakGlobalRef(aClass);
    env->DeleteLocalRef(aClass);

    this->eventLoop_ = std::make_shared<hv::EventLoop>();

    this->serviceManager_ = std::make_shared<ServiceManager>(this->eventLoop_);
    this->serviceManager_->init();
    this->luaStateFactory_ = std::make_shared<LuaStateFactory>(this);
    this->observer_ = std::make_shared<LuaInterpreterObserver>(this);
    this->localCodeProvider_ = std::make_shared<InterpreterCodeProvider>(this);
    this->localResourceProvider_ = std::make_shared<InterpreterResourceProvider>(this);

    this->interpreter_ = std::make_shared<LuaInterpreter>(luaStateFactory_);
    this->serviceManager_->setLuaInterpreter(this->interpreter_);

    this->interpreter_->onInitializeLuaThread(attachJavaThread);

    this->interpreter_->onReleaseLuaThreadBefore(detachJavaThread);
    this->interpreter_->attach(this->observer_);
    this->interpreter_->addCodeProvider(this->localCodeProvider_);
    this->interpreter_->addResourceProvider(this->localResourceProvider_);
    this->interpreter_->addCodeProvider(this->serviceManager_->getCodeProvider());
    this->interpreter_->addResourceProvider(this->serviceManager_->getResourceProvider());
    this->interpreter_->attach(this->serviceManager_->getObserver());

    this->input_ = std::make_shared<autolua::Input>();
    this->input_->init();
}

AutoLuaEngineService::~AutoLuaEngineService() {
    destroy();
}


void AutoLuaEngineService::interrupt() {
    interpreter_->interrupt();
}

void AutoLuaEngineService::rawRun() {
    auto exception = ALEState::kStarting;
    if(state_.compare_exchange_strong(exception,ALEState::kRunning)){
        notifyStateChanged(ALEState::kRunning);
        if(auto p = debugService_) p->start();
        if(auto p = fatherService_) p->start();
        serviceManager_->start();
        eventLoop_->run();
        state_.store(ALEState::kIdle);
        notifyStateChanged(ALEState::kIdle);
    }
}

void AutoLuaEngineService::stop(bool wait_for_stop) {
    auto exception = ALEState::kRunning;
    if(state_.compare_exchange_strong(exception,ALEState::kStopping)){
        notifyStateChanged(ALEState::kStopping);
        if(auto p = debugService_) p->stop();
        if(auto p = fatherService_) p->stop();
        serviceManager_->stop();
        interpreter_->interrupt();
        eventLoop_->stop();
    }
    if(wait_for_stop) {
        waitForStop();
    }
}


void AutoLuaEngineService::destroy() {
    stop(true);
    if(interpreter_ != nullptr){
        interpreter_->destroy();
        interpreter_ = nullptr;
    }
    auto env = GetJNIEnv();
    if(thiz){
        env->DeleteWeakGlobalRef(thiz);
        thiz = nullptr;
    }
    if(clazz){
        env->DeleteWeakGlobalRef(clazz);
        clazz = nullptr;
    }
    if(codeClazz){
        env->DeleteWeakGlobalRef(codeClazz);
        codeClazz = nullptr;
    }
}

int AutoLuaEngineService::execute(const char *code, int32_t len, int codeType, int flags) {
    return interpreter_->execute(std::string(code, len),
                                 LuaInterpreter::CodeMode(codeType),
                                 flags);
}

bool AutoLuaEngineService::addService(const RemoteServerInfo info) {
    return serviceManager_->addService(info);
}

void AutoLuaEngineService::removeService(const std::string &name) {
    serviceManager_->removeService(name);
}

void AutoLuaEngineService::startDebugService(const RemoteServerInfo info) {
    LOGD("starting debug service");
    debugService_.reset();
    std::shared_ptr parser = std::make_shared<JsonRPCParser>();
    debugService_ = std::make_shared<DebugService>(info,parser,eventLoop_);
    LOGD("debug service created");
    if(debugService_->init()){
        LOGD("debug service init success");
        debugService_->setLuaInterpreter(interpreter_);
        debugService_->setStateListener([this](ALEState state){
            if(auto father = fatherService_) father->debuggerStateChanged(state);
            auto env = GetJNIEnv();
            env->CallVoidMethod(thiz,onStateChangedMethodID,(jint)state,(jint)Target::kDebugger);
        });
        debugService_->onScreenShotRequest([this](std::vector<uint8_t>&data){
            Display display(display_);
            display.screenshot(-1,-1,-1,-1,Display::SCREEN_SHOT_FORMAT::kPng,data);
        });
        debugService_->start();
    }else {
        LOGD("startDebugService failed");
    }
}

void AutoLuaEngineService::stopDebugService() {
    if(auto p = debugService_) p->stop();
}

void AutoLuaEngineService::startFatherService(const RemoteServerInfo& info) {
    if(!fatherService_) {
        fatherService_ = std::make_shared<FatherService>(info,std::make_shared<JsonRPCParser>(),eventLoop_);
        fatherService_->setLuaInterpreter(interpreter_);
        fatherService_->onStartDebugServiceCommand([this](const RemoteServerInfo& info){
            startDebugService(info);
        });
        fatherService_->onStopDebugServiceCommand([this](){
            stopDebugService();
        });
        fatherService_->onStopEngineCommand([this](){
            stop();
        });
        fatherService_->onSetRootDir([this](const std::string& dir){
            setRootDir(dir.c_str());
        });
    }
}

void AutoLuaEngineService::notifyStateChanged(ALEState state) {
    if(auto father = fatherService_) father->engineStateChanged(state);
    auto env = GetJNIEnv();
    env->CallVoidMethod(thiz,onStateChangedMethodID,(jint)state,(jint)Target::kEngine);
}

int AutoLuaEngineService::start() {
    auto exception = ALEState::kIdle;
    if(state_.compare_exchange_strong(exception,ALEState::kStarting)){
        notifyStateChanged(ALEState::kStarting);
        running_ = true;
        std::thread t(std::bind(&AutoLuaEngineService::onRun, this));
        t.detach();
        return 0;
    }
    return 1;
}

void AutoLuaEngineService::onRun() {
    attachJavaThread();
    rawRun();
    detachJavaThread();
    running_ = false;
    condVal_.notify_all();
};



void AutoLuaEngineService::setRootDir(const char *dir) {
    std::shared_lock lock(rootDirMutex);
    rootDir.assign(dir);
}

std::string AutoLuaEngineService::getRootDir() {
    std::shared_lock lock(rootDirMutex);
    return rootDir;
}

int AutoLuaEngineService::getState(AutoLuaEngineService::Target target) {
    switch (target) {
        case Target::kEngine:
            return static_cast<int>(state_.load());
        case Target::kWorker:
            return static_cast<int>(interpreter_->getState());
        case Target::kDebugger:
            if(auto p = debugService_) return static_cast<int>(p->getState());
    }
    return static_cast<int>(ALEState::kIdle);
}

void AutoLuaEngineService::waitForStop() {
    std::unique_lock<std::mutex> locker(mutex_);
    condVal_.wait(locker,[this]{
        return !running_;
    });
}


extern "C"
JNIEXPORT void JNICALL
Java_com_autolua_engine_core_AutoLuaEngineOnLocal_stopNative(JNIEnv *env, jobject thiz,
                                                             jlong ptr) {
    auto service = (AutoLuaEngineService*)ptr;
    service->stop();
}

lua_State *AutoLuaEngineService::LuaStateFactory::create() {
    auto jniEnv = GetJNIEnv();
    auto ptr = jniEnv->CallLongMethod(engine->thiz, engine->newLuaContextMethodID);
    auto L = (lua_State*)ptr;
    auto aRootDir = engine->rootDir;
    if(!aRootDir.empty()) {
        lua_pushlstring(L,aRootDir.c_str(),aRootDir.size());
        lua_setglobal(L,"ROOT_DIR");
    }
    luaL_requiref(L,"alv",luaopen_alv,1);
    Display::pushObjectToLua(L,engine->display_);
    lua_setglobal(L,"Screen");
    engine->input_->pushObjectToLua(L);
    lua_setglobal(L,"Input");
    if(auto p = engine->debugService_)p->setup(L);
    if(auto p = engine->fatherService_)p->setup(L);
    engine->serviceManager_->setup(L);
    return (lua_State*)ptr;
}

void AutoLuaEngineService::LuaStateFactory::release(lua_State *L) {
    auto context = toLuaContext(L);
    auto jniEnv = context->env;
    jniEnv->CallVoidMethod(engine->thiz, engine->releaseContextMethodID, (jlong)L);
}

#define JAVA2CPP_STR(env,jclazz,jobj,cobj,name) {    auto value = env->GetObjectField(jobj, env->GetFieldID(jclazz, #name, "Ljava/lang/String;")); \
if(value){auto sValue = env->GetStringUTFChars((jstring)value, nullptr);if(sValue) {cobj.name = sValue; env->ReleaseStringUTFChars((jstring)value, sValue);}env->DeleteLocalRef(value); }\
}

static bool javaRPCServiceInfo2CppRPCServiceInfo(JNIEnv*env,jobject jobj,ServiceInfo &info){
    auto clazz = env->GetObjectClass(jobj);
    JAVA2CPP_STR(env,clazz,jobj,info,name);
    auto methods = env->GetObjectField(jobj, env->GetFieldID(clazz, "methods", "Ljava/util/List;"));
    if(methods){
        auto methodsClazz = env->GetObjectClass(methods);
        jmethodID sizeMethod = env->GetMethodID(methodsClazz, "size", "()I");
        jmethodID getMethod = env->GetMethodID(methodsClazz, "get", "(I)Ljava/lang/Object;");
        auto size = env->CallIntMethod(methods, sizeMethod);
        for(int i = 0; i < size; i++){
            auto method = env->CallObjectMethod(methods, getMethod, i);
            auto str = env->GetStringUTFChars((jstring)method, nullptr);
            if(str){
                info.methodsName.insert(str);
                env->ReleaseStringUTFChars((jstring)method, str);
            }
            env->DeleteLocalRef(method);
        }
        env->DeleteLocalRef(methodsClazz);
        env->DeleteLocalRef(methods);
    }
    env->DeleteLocalRef(clazz);
    return true;
}

static void javaRemoteServiceInfo2CRemoteServiceInfo(JNIEnv *env, jobject remote_service_info, RemoteServerInfo &info) {
    auto clazz = env->GetObjectClass(remote_service_info);
    JAVA2CPP_STR(env, clazz, remote_service_info, info,name);
    JAVA2CPP_STR(env, clazz, remote_service_info, info,host);
    JAVA2CPP_STR(env,clazz,remote_service_info,info,auth);
    jfieldID port = env->GetFieldID(clazz, "port", "I");
    info.port = env->GetIntField(remote_service_info, port);
    jfieldID services = env->GetFieldID(clazz, "services", "I");
    info.services = env->GetIntField(remote_service_info, services);
    LOGE("services %d\n",info.services);
    jfieldID rpcServices = env->GetFieldID(clazz, "rpcServices", "Ljava/util/List;");
    auto rpcServicesList = (jobject)env->GetObjectField(remote_service_info, rpcServices);
    if(rpcServicesList){
        auto rpcServicesListClazz = env->GetObjectClass(rpcServicesList);
        jmethodID sizeMethod = env->GetMethodID(rpcServicesListClazz, "size", "()I");
        jmethodID getMethod = env->GetMethodID(rpcServicesListClazz, "get", "(I)Ljava/lang/Object;");
        auto size = env->CallIntMethod(rpcServicesList, sizeMethod);
        for(int i = 0; i < size; i++){
            auto rpcService = env->CallObjectMethod(rpcServicesList, getMethod, i);
            ServiceInfo serviceInfo;
            if(javaRPCServiceInfo2CppRPCServiceInfo(env,rpcService,serviceInfo)){
                info.rpcServices.insert(serviceInfo);
            }
            env->DeleteLocalRef(rpcService);
        }
        env->DeleteLocalRef(rpcServicesListClazz);
        env->DeleteLocalRef(rpcServicesList);
    }
    env->DeleteLocalRef(clazz);
}

extern "C"
JNIEXPORT void JNICALL
Java_com_autolua_engine_core_AutoLuaEngineOnLocal_addRemoteServiceNative(JNIEnv *env,
                                                                         jobject thiz,
                                                                         jlong ptr,
                                                                         jobject remote_server_configure) {
    RemoteServerInfo info;
    javaRemoteServiceInfo2CRemoteServiceInfo(env,remote_server_configure,info);
    auto service = (AutoLuaEngineService*)ptr;
    service->addService(info);
}
extern "C"
JNIEXPORT void JNICALL
Java_com_autolua_engine_core_AutoLuaEngineOnLocal_removeRemoteServiceNative(JNIEnv *env,
                                                                            jobject thiz,
                                                                            jlong ptr,
                                                                            jstring name) {
    auto service = (AutoLuaEngineService*)ptr;
    auto str = env->GetStringUTFChars(name, nullptr);
    if(str){
        service->removeService(str);
        env->ReleaseStringUTFChars(name, str);
    }
}
extern "C"
JNIEXPORT void JNICALL
Java_com_autolua_engine_core_AutoLuaEngineOnLocal_startDebugService(JNIEnv *env,
                                                                    jobject thiz, jlong ptr,
                                                                    jobject configure) {
    RemoteServerInfo info;
    javaRemoteServiceInfo2CRemoteServiceInfo(env,configure,info);
    auto service = (AutoLuaEngineService*)ptr;
    service->startDebugService(info);
}
extern "C"
JNIEXPORT void JNICALL
Java_com_autolua_engine_core_AutoLuaEngineOnLocal_stopDebugService(JNIEnv *env, jobject thiz,
                                                                   jlong ptr) {
    auto service = (AutoLuaEngineService*)ptr;
    service->stopDebugService();
}

void AutoLuaEngineService::LuaInterpreterObserver::onStateChange(ALEState state) {
    if(auto p = engine->debugService_) p->onStateChange(state);
    if(auto p = engine->fatherService_) p->onStateChange(state);
    auto env = GetJNIEnv();
    env->CallVoidMethod(engine->thiz,engine->onStateChangedMethodID,(jint)state,(jint)Target::kWorker);
}

static int readSource(const std::string&path,std::string &out){
    FILE *f = fopen(path.c_str(),"rb");
    if(f){
        fseek(f,0,SEEK_END);
        auto size = ftell(f);
        fseek(f,0,SEEK_SET);
        out.resize(size);
        fread((void*)out.data(),1,size,f);
        fclose(f);
        return 0;
    }
    return -1;
}

static int loadLocalScript(const std::string &root,const std::string &path, std::string &out){
    auto pathOne = root + path;
    if(readSource(pathOne,out) == 0) return 0;
    auto pathTwo = root + "libs/" + path;
    if(readSource(pathTwo,out) == 0) return 0;
    return -1;
}


int
AutoLuaEngineService::InterpreterCodeProvider::loadModule(LuaInterpreter::SignalEvent *signalEvent,
                                                          const char *path, std::string &out,
                                                          int &type) {
    if(auto p = engine->debugService_){
        int r = p->loadModule(signalEvent,path,out,type);
        if(r != 0) return r;
        if(!out.empty()) return 0;
    }
    {
        auto scriptRootDir = engine->getRootDir();
        if(!scriptRootDir.empty()){
            scriptRootDir.append("src/");
            std::string targetPath(path);
            for(auto &c:targetPath){
                if(c == '.') c = '/';
            }
            if(loadLocalScript(scriptRootDir,targetPath,out) == 0){
                type = (int)LuaInterpreter::CodeMode::kTextOrBinary;
                return 0;
            }
        }
    }

    if(auto p = engine->fatherService_){
        int r = p->loadModule(signalEvent,path,out,type);
        if(r != 0) return r;
        if(!out.empty()) return 0;
    }

    auto env = GetJNIEnv();
    auto jpath = env->NewStringUTF(path);
    auto code = env->CallObjectMethod(engine->thiz, engine->getModuleMethodID, jpath);
    env->DeleteLocalRef(jpath);
    if(code){
        auto nType = env->GetIntField(code, engine->codeTypeField);
        type = nType;
        auto content = (jbyteArray)env->GetObjectField(code, engine->codeContentField);
        auto len = env->GetArrayLength(content);
        auto data = env->GetByteArrayElements(content, nullptr);
        out.assign((const char*)data, len);
        env->ReleaseByteArrayElements(content, (jbyte*)data, JNI_ABORT);
        env->DeleteLocalRef(content);
    }
    return 0;
}

int
AutoLuaEngineService::InterpreterCodeProvider::loadFile(LuaInterpreter::SignalEvent *signalEvent,
                                                        const char *path, std::string &out,
                                                        int &type) {
    if(auto p = engine->debugService_){
        int r = p->loadFile(signalEvent,path,out,type);
        if(r != 0) return r;
        if(!out.empty()) return 0;
    }

    {
        auto scriptRootDir = engine->getRootDir();
        if(!scriptRootDir.empty()){
            scriptRootDir.append("src/");
            std::string targetPath(path);
            LOGI("loadFile %s  root %s",targetPath.c_str(),scriptRootDir.c_str());
            if(loadLocalScript(scriptRootDir,targetPath,out) == 0) {
//                LOGI("code = %s",out.c_str());
                type = (int)LuaInterpreter::CodeMode::kTextOrBinary;
                return 0;
            }
        }
    }


    if(auto p = engine->fatherService_){
        int r = p->loadFile(signalEvent,path,out,type);
        if(r != 0) return r;
        if(!out.empty()) return 0;
    }


    auto env = GetJNIEnv();
    auto jpath = env->NewStringUTF(path);
    auto code = env->CallObjectMethod(engine->thiz, engine->getFileMethodID, jpath);
    env->DeleteLocalRef(jpath);
    if(code){
        auto nType = env->GetIntField(code, engine->codeTypeField);
        type = nType;
        auto content = (jbyteArray)env->GetObjectField(code, engine->codeContentField);
        auto len = env->GetArrayLength(content);
        auto data = env->GetByteArrayElements(content, nullptr);
        out.assign((const char*)data, len);
        env->ReleaseByteArrayElements(content, (jbyte*)data, JNI_ABORT);
        env->DeleteLocalRef(content);
    }
    return 0;
}



int AutoLuaEngineService::InterpreterResourceProvider::loadResource(
        LuaInterpreter::SignalEvent *signalEvent, const char *path, std::string &out) {
    if(auto p = engine->debugService_){
        int r = p->loadResource(signalEvent,path,out);
        if(r != 0) return r;
        if(!out.empty()) return 0;
    }
    {
        auto scriptRootDir = engine->rootDir;
        if(!scriptRootDir.empty()){
            scriptRootDir.append("res/");
            scriptRootDir.append(path);
            if(readSource(scriptRootDir,out) == 0) return 0;
        }
    }

    if(auto p = engine->fatherService_){
        int r = p->loadResource(signalEvent,path,out);
        if(r != 0) return r;
        if(!out.empty()) return 0;
    }

    auto env = GetJNIEnv();
    auto jpath = env->NewStringUTF(path);
    auto buffer = (jbyteArray )env->CallObjectMethod(engine->thiz, engine->getResourceMethodID, jpath);
    env->DeleteLocalRef(jpath);
    if (buffer != nullptr) {
        auto address = env->GetByteArrayElements(buffer, nullptr);
        auto capacity = env->GetArrayLength(buffer);
        out.assign((char *)address, capacity);
        env->ReleaseByteArrayElements(buffer, (jbyte *)address, JNI_ABORT);
        env->DeleteLocalRef(buffer);
    }
    return 0;
}

extern "C"
JNIEXPORT void JNICALL
Java_com_autolua_engine_core_AutoLuaEngineOnLocal_startFatherService(JNIEnv *env,
                                                                     jobject thiz, jlong ptr,
                                                                     jobject configure) {
    RemoteServerInfo info;
    javaRemoteServiceInfo2CRemoteServiceInfo(env,configure,info);
    auto service = (AutoLuaEngineService*)ptr;
    service->startFatherService(info);
}
extern "C"
JNIEXPORT void JNICALL
Java_com_autolua_engine_core_AutoLuaEngineOnLocal_00024Companion_changeLogChannel(
        JNIEnv *env, jobject thiz, jint channel) {
    changeLogChannel(channel);
}
extern "C"
JNIEXPORT jint JNICALL
Java_com_autolua_engine_core_AutoLuaEngineOnLocal_start(JNIEnv *env, jobject thiz,
                                                        jlong ptr) {
    auto service = (AutoLuaEngineService*)ptr;
    return service->start();
}
extern "C"
JNIEXPORT void JNICALL
Java_com_autolua_engine_core_AutoLuaEngineOnLocal_setRootDir(JNIEnv *env, jobject thiz,
                                                             jlong ptr, jstring root_dir) {
    auto service = (AutoLuaEngineService*)ptr;
    auto str = env->GetStringUTFChars(root_dir, nullptr);
    if(str){
        service->setRootDir(str);
        env->ReleaseStringUTFChars(root_dir, str);
    }
}
extern "C"
JNIEXPORT jint JNICALL
Java_com_autolua_engine_core_AutoLuaEngineOnLocal_getState(JNIEnv *env, jobject thiz, jlong ptr,
                                                           jint target) {
    auto service = (AutoLuaEngineService*)ptr;
    return service->getState((AutoLuaEngineService::Target)target);
}

extern "C"
JNIEXPORT jint JNI_OnLoad(JavaVM * vm, void * reserved)
{
    JNIEnv * env = NULL;
    if (vm->GetEnv((void**)&env, JNI_VERSION_1_6) != JNI_OK)
        return -1;
    InitJavaVM(vm);
    Display::initializeJavaDisplayClass(env);
    LOGE("JNI_OnLoad success\n");
    return  JNI_VERSION_1_6;
}

extern "C"
JNIEXPORT void JNI_OnUnload(JavaVM * vm, void * reserved)
{
    JNIEnv * env = NULL;
    if (vm->GetEnv((void**)&env, JNI_VERSION_1_6) != JNI_OK)
        return;
    Display::releaseJavaDisplayClass(env);
    LOGE("JNI_OnUnload success\n");
}

extern "C"
JNIEXPORT void JNICALL
Java_com_autolua_engine_core_AutoLuaEngineOnLocal_waitForStop(JNIEnv *env, jobject thiz,
                                                              jlong ptr) {
    auto service = (AutoLuaEngineService*)ptr;
    service->waitForStop();
}