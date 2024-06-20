//
// Created by lizhi on 2023/4/30.
//

#ifndef AUTOLUA2_MY_LOG_H
#define AUTOLUA2_MY_LOG_H

#define  DEBUG 1
#if DEBUG
extern void (*g_logI)(const char*fmt,...);
extern void (*g_logD)(const char*fmt,...);
extern void (*g_logE)(const char*fmt,...);
#define LOGI(fmt, args...) g_logI(fmt, ##args)
#define LOGD(fmt, args...) g_logD(fmt, ##args)
#define LOGE(fmt, args...) g_logE(fmt, ##args)
#else
#define LOGI(fmt, args...)
#define LOGD(fmt, args...)
#define LOGE(fmt, args...)
#define LOG_PRINTF(fmt, args...)
#endif
#ifdef __cplusplus
extern "C" {
#endif
void changeLogChannel(int level);
#ifdef __cplusplus
}
#endif


#endif //AUTOLUA2_MY_LOG_H
