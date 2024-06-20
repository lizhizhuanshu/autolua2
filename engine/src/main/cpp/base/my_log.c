//
// Created by lizhi on 2024/6/1.
//

#include "my_log.h"
#include <android/log.h>
#include <stdio.h>
#include <string.h>
#define LOG_TAG "autolua2"

static void android_logI(const char*fmt,...){
    va_list args;
    va_start(args,fmt);
    __android_log_vprint(ANDROID_LOG_INFO,LOG_TAG,fmt,args);
    va_end(args);
}

static void android_logD(const char*fmt,...){
    va_list args;
    va_start(args,fmt);
    __android_log_vprint(ANDROID_LOG_DEBUG,LOG_TAG,fmt,args);
    va_end(args);
}

static void android_logE(const char*fmt,...){
    va_list args;
    va_start(args,fmt);
    __android_log_vprint(ANDROID_LOG_ERROR,LOG_TAG,fmt,args);
    va_end(args);
}

static void std_log(const char *header, const char*fmt,...){
    va_list args;
    va_start(args, fmt);
    char buffer[2048];
    // 使用 snprintf 将 header 和 fmt 合并到 buffer 中
    snprintf(buffer, sizeof(buffer), "%s%s\n", header, fmt);
    // 使用 vfprintf 处理可变参数列表
    vfprintf(stdout, buffer, args);
    fflush(stdout);
    va_end(args);
}

static void std_logD(const char*fmt,...){
    va_list args;
    va_start(args, fmt);
    const char* header = "debug:"LOG_TAG" ";
    char buffer[2048];
    // 使用 snprintf 将 header 和 fmt 合并到 buffer 中
    snprintf(buffer, sizeof(buffer), "%s%s\n", header, fmt);
    // 使用 vfprintf 处理可变参数列表
    vfprintf(stdout, buffer, args);
    fflush(stdout);
    va_end(args);
}

static void std_logE(const char*fmt,...){
    va_list args;
    va_start(args, fmt);
    const char* header = "error:" LOG_TAG " ";
    char buffer[2048];
    // 使用 snprintf 将 header 和 fmt 合并到 buffer 中
    snprintf(buffer, sizeof(buffer), "%s%s\n", header, fmt);
    // 使用 vfprintf 处理可变参数列表
    vfprintf(stdout, buffer, args);
    fflush(stdout);
    va_end(args);
}

static void std_logI(const char*fmt,...){
    va_list args;
    va_start(args, fmt);
    const char* header = "info:" LOG_TAG " ";
    char buffer[2048];
    // 使用 snprintf 将 header 和 fmt 合并到 buffer 中
    snprintf(buffer, sizeof(buffer), "%s%s\n", header, fmt);
    // 使用 vfprintf 处理可变参数列表
    vfprintf(stdout, buffer, args);
    fflush(stdout);
    va_end(args);
}


void changeLogChannel(int level){
    if(level == 0){
        g_logI = android_logI;
        g_logD = android_logD;
        g_logE = android_logE;
    }else{
        g_logI = std_logI;
        g_logD = std_logD;
        g_logE = std_logE;
    }
}

void (*g_logI)(const char*fmt,...) = android_logI;
void (*g_logD)(const char*fmt,...) = android_logD;
void (*g_logE)(const char*fmt,...) = android_logE;
