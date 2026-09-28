#include "com_perez_revkiller_Features.h"
#include <cstdlib>
#include <cstdio>
#include <cstdint>
#include <cstring>
#include <fcntl.h>
#include <elf.h>
#include <sys/stat.h>
#include <unistd.h>

/**
 * Checks if the file descriptor points to a regular file and a valid ELF binary.
 * Independent of the host architecture.
 */
static bool is_valid_elf_fd(int fd) {
    struct stat st;
    if (fstat(fd, &st) != 0 || !S_ISREG(st.st_mode)) {
        return false;
    }

    // Read the universal ELF identification segment (16 bytes)
    unsigned char ident[EI_NIDENT];
    ssize_t bytes_read = 0;
    while (bytes_read < EI_NIDENT) {
        ssize_t res = read(fd, ident + bytes_read, EI_NIDENT - bytes_read);
        if (res <= 0) {
            return false;
        }
        bytes_read += res;
    }

    // Verify magic bytes: 0x7F 'E' 'L' 'F'
    if (memcmp(&ident[EI_MAG0], ELFMAG, SELFMAG) != 0) {
        return false;
    }

    // Verify ELF class (32-bit or 64-bit)
    if (ident[EI_CLASS] != ELFCLASS32 && ident[EI_CLASS] != ELFCLASS64) {
        return false;
    }

    // Verify ELF data encoding (Little-endian or Big-endian)
    if (ident[EI_DATA] != ELFDATA2LSB && ident[EI_DATA] != ELFDATA2MSB) {
        return false;
    }

    // Verify ELF specification version
    if (ident[EI_VERSION] != EV_CURRENT) {
        return false;
    }

    // Check full ELF header to confirm valid executable or shared library type
    if (ident[EI_CLASS] == ELFCLASS32) {
        Elf32_Ehdr header;
        memcpy(header.e_ident, ident, EI_NIDENT);
        size_t remaining = sizeof(Elf32_Ehdr) - EI_NIDENT;
        
        uint8_t *ptr = reinterpret_cast<uint8_t *>(&header) + EI_NIDENT;
        bytes_read = 0;
        while (bytes_read < static_cast<ssize_t>(remaining)) {
            ssize_t res = read(fd, ptr + bytes_read, remaining - bytes_read);
            if (res <= 0) {
                return false;
            }
            bytes_read += res;
        }

        uint16_t type = header.e_type;
        // Swap bytes if endianness differs from host
#if __BYTE_ORDER__ == __ORDER_LITTLE_ENDIAN__
        if (ident[EI_DATA] == ELFDATA2MSB) {
            type = __builtin_bswap16(type);
        }
#elif __BYTE_ORDER__ == __ORDER_BIG_ENDIAN__
        if (ident[EI_DATA] == ELFDATA2LSB) {
            type = __builtin_bswap16(type);
        }
#endif
        // Accept executable (ET_EXEC), shared object (ET_DYN), or relocatable object (ET_REL)
        return (type == ET_EXEC || type == ET_DYN || type == ET_REL);
    } else {
        Elf64_Ehdr header;
        memcpy(header.e_ident, ident, EI_NIDENT);
        size_t remaining = sizeof(Elf64_Ehdr) - EI_NIDENT;

        uint8_t *ptr = reinterpret_cast<uint8_t *>(&header) + EI_NIDENT;
        bytes_read = 0;
        while (bytes_read < static_cast<ssize_t>(remaining)) {
            ssize_t res = read(fd, ptr + bytes_read, remaining - bytes_read);
            if (res <= 0) {
                return false;
            }
            bytes_read += res;
        }

        uint16_t type = header.e_type;
#if __BYTE_ORDER__ == __ORDER_LITTLE_ENDIAN__
        if (ident[EI_DATA] == ELFDATA2MSB) {
            type = __builtin_bswap16(type);
        }
#elif __BYTE_ORDER__ == __ORDER_BIG_ENDIAN__
        if (ident[EI_DATA] == ELFDATA2LSB) {
            type = __builtin_bswap16(type);
        }
#endif
        return (type == ET_EXEC || type == ET_DYN || type == ET_REL);
    }
}

static bool iself(const char *path) {
    if (path == nullptr || path[0] == '\0') {
        return false;
    }

    int fd = open(path, O_RDONLY | O_CLOEXEC);
    if (fd == -1) {
        return false;
    }

    bool result = is_valid_elf_fd(fd);
    close(fd);
    return result;
}

JNIEXPORT jboolean JNICALL
Java_com_perez_revkiller_Features_isValidElf(JNIEnv *env, jclass cls, jstring js) {
    if (js == nullptr) {
        return JNI_FALSE;
    }

    const char *path = env->GetStringUTFChars(js, nullptr);
    if (path == nullptr) {
        return JNI_FALSE;
    }

    bool valid = iself(path);

    env->ReleaseStringUTFChars(js, path);
    return valid ? JNI_TRUE : JNI_FALSE;
}
