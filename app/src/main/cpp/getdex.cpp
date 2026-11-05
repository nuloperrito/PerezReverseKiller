#include "com_perez_revkiller_Features.h"
#include "utils.h"
#include "dumpdex/dump_dex.h"
#include <cstdlib>
#include <cstring>
#include <cstdio>
#include <string>
#include <sys/mman.h>
#include <sys/types.h>
#include <sys/stat.h>
#include <fcntl.h>
#include <unistd.h>
#include <android/log.h>

using namespace std;

#define DEX_OPT_MAGIC   "dey\n"
#define DEX_MAGIC       "dex\n"

struct odex_header {
    uint8_t magic[8];
    uint32_t dex_off;
    uint32_t dex_len;
};

struct dex_header {
    uint8_t magic[8];
    uint8_t padding[24];
    uint32_t file_len;
};

bool write_to_file(char *path, unsigned char *data, int size, int file_count) {
    char filename[10240];
    sprintf(filename, "%s%02d.dex", path, file_count);

    FILE *fp = fopen(filename, "wb");
    if(fp == nullptr) return false;
    fwrite(data, 1, size, fp);
    fclose(fp);
    return true;
}

char *MyGetPrefix(const char *f) {
    string path = string(f);
    int n = path.find_last_of('.');
    string shao = path.substr(0, n);
    return const_cast<char *>(shao.c_str());
}

bool procdex(const char *infile) {
    FILE *infp = fopen(infile, "rb");
    if (infp == nullptr)
        return false;
    fseek(infp, 0, SEEK_END);
    int insize = ftell(infp);
    fseek(infp, 0, SEEK_SET);
    unsigned char *indata = (unsigned char *)malloc(insize);
    fread(indata, 1, insize, infp);
    fclose(infp);
    int file_count = 0;
    int ptr;
    for (ptr = 0; ptr < insize; ptr ++) {
        if (memcmp(indata + ptr, "dex\n035", 8) != 0)
            continue;
        unsigned int dexsize = *(unsigned int *)(indata + ptr + 32);
        if (ptr + dexsize > insize)
            continue;
        if(!write_to_file(MyGetPrefix(infile), indata + ptr, dexsize, ++file_count)) return false;
    }
    return true;
}

static void logerr(char *msg) {
    __android_log_print(ANDROID_LOG_ERROR, "Odex2Dex", "%s", msg);
}

bool procodex(char *in, char *out) {
    int ret = -1;
    int odexFD = 0, dexFD = 0;
    size_t odexSize = 0, dexSize = 0;
    if((odexFD = open(in, O_RDONLY)) == -1) {
        logerr("open odex error");
        return false;
    }
    if((dexFD = open(out, O_RDWR | O_CREAT, 00660)) == -1) {
        logerr("open dex error");
        close(odexFD);
        return false;
    }
    struct stat Stat;
    if(fstat(odexFD, &Stat)) {
        logerr("fstat odex error");
        close(odexFD);
        close(dexFD);
        return false;
    }
    odexSize = Stat.st_size;
    uint8_t *odexBuf = NULL, *dexBuf = NULL;
    if((odexBuf = (uint8_t *)mmap(NULL, odexSize, PROT_READ, MAP_SHARED, odexFD, 0)) == MAP_FAILED) {
        logerr("mmap odex error");
        close(odexFD);
        close(dexFD);
        return false;
    }
    struct odex_header *odexH = (struct odex_header *)odexBuf;
    struct dex_header *dexH = (struct dex_header *)(odexBuf + odexH->dex_off);
    if(memcmp(odexH->magic, DEX_OPT_MAGIC, sizeof(uint8_t))) {
        logerr("odex bad magic error\n");
        munmap(odexBuf, odexSize);
        close(odexFD);
        close(dexFD);
        return false;
    }
    if(memcmp(dexH->magic, DEX_MAGIC, sizeof(uint8_t))) {
        logerr("dex bad magic error");
        munmap(odexBuf, odexSize);
        close(odexFD);
        close(dexFD);
        return false;
    }
    if(odexH->dex_len != dexH->file_len || (odexH->dex_len + 40) > odexSize) {
        logerr("dex bad file len error");
        munmap(odexBuf, odexSize);
        close(odexFD);
        close(dexFD);
        return false;
    }
    dexSize = odexH->dex_len;
    if(ftruncate(dexFD, dexSize)) {
        logerr("ftruncate dex error");
        munmap(odexBuf, odexSize);
        close(odexFD);
        close(dexFD);
        return false;
    }
    if((dexBuf = (uint8_t *)mmap(NULL, dexSize, PROT_READ | PROT_WRITE, MAP_SHARED, dexFD, 0)) == MAP_FAILED) {
        logerr("mmap dex error");
        munmap(odexBuf, odexSize);
        close(odexFD);
        close(dexFD);
        return false;
    }
    memcpy(dexBuf, odexBuf + odexH->dex_off, dexSize);
    munmap(dexBuf, dexSize);
    munmap(odexBuf, odexSize);
    close(odexFD);
    close(dexFD);
    return true;
}

JNIEXPORT jint JNICALL Java_com_perez_revkiller_Features_dumpDex(JNIEnv *env, jclass cls, jint apilevel, jstring entryclz) {
    return dump_dex(env, apilevel, j2c(env, entryclz));
}

JNIEXPORT jboolean JNICALL Java_com_perez_revkiller_Features_Oat2Dex
        (JNIEnv *env, jclass cls, jstring from) {
    const char *f = env->GetStringUTFChars(from, NULL);
    if(procdex(f)) return JNI_TRUE;
    else return JNI_FALSE;
}

JNIEXPORT jboolean JNICALL Java_com_perez_revkiller_Features_Odex2Dex
        (JNIEnv *env, jclass cls, jstring jstr1, jstring jstr2) {
    char *s1 = j2c(env, jstr1);
    char *s2 = j2c(env, jstr2);
    if(procodex(s1, s2)) return JNI_TRUE;
    else return JNI_FALSE;
}
