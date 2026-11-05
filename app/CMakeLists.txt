cmake_minimum_required(VERSION 3.4.1)

# Add the function library
file(GLOB DUMPDEX_FILES "src/main/cpp/dumpdex/*.cpp")
file(GLOB RAR_FILES "src/main/cpp/unrar/*.cpp")
file(GLOB ASTYLE_FILES "src/main/cpp/astyle/*.cpp")

add_library(function SHARED src/main/cpp/tracepath.c src/main/cpp/miniTelnet.c src/main/cpp/Provider.c src/main/cpp/elf.cpp src/main/cpp/rarext.cpp
        src/main/cpp/getdex.cpp src/main/cpp/utils.cpp src/main/cpp/files.cpp ${DUMPDEX_FILES} ${RAR_FILES} ${ASTYLE_FILES})

target_include_directories(function PRIVATE src/main/cpp/dumpdex)

target_link_libraries(function PRIVATE log z)
target_compile_options(function PRIVATE -DASTYLE_JNI -DSILENT -DRARDLL -fexceptions -Os -frtti -ffunction-sections -fdata-sections -fvisibility=hidden -w -Wl,--gc-sections)
set_target_properties(function PROPERTIES CXX_STANDARD 11)