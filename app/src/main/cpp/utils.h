#ifndef PEREZREVERSEKILLER_UTILS_H
#define PEREZREVERSEKILLER_UTILS_H
#include <stdint.h>
#include <jni.h>

#ifdef __cplusplus
extern "C" {
#endif
    char *j2c(JNIEnv *env, jstring jstr);
    jobject createStatusMsg(JNIEnv *env, jboolean success, const char *msg);
#ifdef __cplusplus
};
#endif

#endif //PEREZREVERSEKILLER_UTILS_H
