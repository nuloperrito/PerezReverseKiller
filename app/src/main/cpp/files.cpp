#include <sys/syscall.h>
#include <unistd.h>
#include <sys/stat.h>
#include <cerrno>
#include <fcntl.h>
#include <string>
#include <android/log.h>
#include "com_perez_revkiller_Features.h"
#include "utils.h"

using namespace std;

intptr_t openFd(intptr_t fd, const char *path, intptr_t flag) {
    return (intptr_t) syscall(__NR_openat, fd, path, flag);
}

JNIEXPORT jint JNICALL Java_com_perez_revkiller_Features_openFd
        (JNIEnv *env, jclass cls, jstring path) {
    const char* p = env->GetStringUTFChars(path, nullptr);
    intptr_t fd = openFd(AT_FDCWD, p, O_RDONLY);
    __android_log_print(ANDROID_LOG_INFO, "openFd", "path=%s, fd=%p", p, fd);
    return fd;
}

JNIEXPORT jobject JNICALL
Java_com_perez_revkiller_Features_modifyTimestamp(JNIEnv *env, jclass /* clazz */, jint fd, jobject jDate) {
    if (jDate == nullptr) {
        if (fd >= 0) {
            close(fd);
        }
        return createStatusMsg(env, JNI_FALSE, "Date parameter cannot be null");
    }

    // Retrieve Date.getTime() -> milliseconds since January 1, 1970, 00:00:00 GMT
    jclass dateClass = env->GetObjectClass(jDate);
    jmethodID getTimeMethod = env->GetMethodID(dateClass, "getTime", "()J");
    if (getTimeMethod == nullptr) {
        if (fd >= 0) {
            close(fd);
        }
        return createStatusMsg(env, JNI_FALSE, "Failed to resolve Date.getTime() method");
    }

    jlong timeMillis = env->CallLongMethod(jDate, getTimeMethod);

    // Convert milliseconds to seconds and nanoseconds (handling negative offsets)
    time_t sec = static_cast<time_t>(timeMillis / 1000);
    long nsec = static_cast<long>((timeMillis % 1000) * 1000000L);
    if (nsec < 0) {
        sec -= 1;
        nsec += 1000000000L;
    }

    // times[0]: access time (atime), times[1]: modification time (mtime)
    struct timespec times[2];
    times[0].tv_sec = sec;
    times[0].tv_nsec = nsec;
    times[1].tv_sec = sec;
    times[1].tv_nsec = nsec;

    if (futimens(fd, times) == -1) {
        string errMsg = strerror(errno);
        close(fd);
        return createStatusMsg(env, JNI_FALSE, errMsg.c_str());
    }

    close(fd);
    return createStatusMsg(env, JNI_TRUE, "Timestamp updated successfully");
}
