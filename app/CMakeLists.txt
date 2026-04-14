cmake_minimum_required(VERSION 3.22.1)
project("jerometranslator")

# Fetch llama.cpp at build time (no source in repo)
include(FetchContent)
FetchContent_Declare(
    llama_cpp
    GIT_REPOSITORY https://github.com/ggerganov/llama.cpp.git
    GIT_TAG        b8739
    GIT_SHALLOW    TRUE
    GIT_PROGRESS   TRUE
)

# llama.cpp build options
set(GGML_VULKAN OFF CACHE BOOL "" FORCE)
set(LLAMA_BUILD_TESTS OFF CACHE BOOL "" FORCE)
set(LLAMA_BUILD_EXAMPLES OFF CACHE BOOL "" FORCE)
set(LLAMA_BUILD_SERVER OFF CACHE BOOL "" FORCE)
set(LLAMA_OPENSSL OFF CACHE BOOL "" FORCE)

if(ANDROID_ABI STREQUAL "arm64-v8a")
    set(GGML_CPU_ARM_ARCH "armv8.2-a+dotprod+fp16" CACHE STRING "" FORCE)
    set(GGML_OPENMP ON CACHE BOOL "" FORCE)
endif()

FetchContent_MakeAvailable(llama_cpp)

# JNI bridge library
add_library(jerometranslator SHARED
    src/main/cpp/llama_jni.cpp
)

target_include_directories(jerometranslator PRIVATE
    ${llama_cpp_SOURCE_DIR}/include
    ${llama_cpp_SOURCE_DIR}/ggml/include
)

target_link_libraries(jerometranslator
    llama
    android
    log
)
