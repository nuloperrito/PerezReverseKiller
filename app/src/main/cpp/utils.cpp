#include "utils.h"
#include <cstring>
#include <cstdlib>

char *j2c(JNIEnv *env, jstring jstr) {
    char *rtn = nullptr;
    jclass clsstring = env->FindClass("java/lang/String");
    jstring strencode = env->NewStringUTF("utf-8");
    jmethodID mid = env->GetMethodID(clsstring, "getBytes", "(Ljava/lang/String;)[B");
    jbyteArray barr = (jbyteArray)env->CallObjectMethod(jstr, mid, strencode);
    jsize alen = env->GetArrayLength(barr);
    jbyte *ba = env->GetByteArrayElements(barr, JNI_FALSE);
    if (alen > 0) {
        rtn = (char *)malloc(alen + 1);
        memcpy(rtn, ba, alen);
        rtn[alen] = 0;
    }
    env->ReleaseByteArrayElements(barr, ba, 0);
    return rtn;
}

jobject createStatusMsg(JNIEnv *env, jboolean success, const char *msg) {
    jclass statusClass = env->FindClass("com/perez/revkiller/Features$StatusMsg");
    if (statusClass == nullptr) {
        return nullptr;
    }

    jmethodID constructor = env->GetMethodID(statusClass, "<init>", "(ZLjava/lang/String;)V");
    if (constructor == nullptr) {
        return nullptr;
    }

    jstring jMsg = env->NewStringUTF(msg != nullptr ? msg : "");
    return env->NewObject(statusClass, constructor, success, jMsg);
}
