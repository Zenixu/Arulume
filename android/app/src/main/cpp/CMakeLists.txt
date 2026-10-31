cmake_minimum_required(VERSION 3.22.1)
project(arulume_native LANGUAGES CXX)

set(CMAKE_CXX_STANDARD 17)
set(CMAKE_CXX_STANDARD_REQUIRED ON)

# ncnn dipakai sebagai pustaka bersama (libncnn.so sudah ikut di jniLibs/).
# Alasannya: build statis ncnn butuh flag whole-archive + OpenMP yang rumit,
# sedangkan versi shared resmi Tencent sudah teruji di Android.
set(NCNN_DIR    ${CMAKE_CURRENT_SOURCE_DIR}/ncnn)
set(JNILIBS_DIR ${CMAKE_CURRENT_SOURCE_DIR}/../jniLibs)

add_library(ncnn SHARED IMPORTED)
set_target_properties(ncnn PROPERTIES
        IMPORTED_LOCATION ${JNILIBS_DIR}/${ANDROID_ABI}/libncnn.so)

add_library(arulume_native SHARED enhancer_jni.cpp)

target_include_directories(arulume_native PRIVATE ${NCNN_DIR})

target_link_libraries(arulume_native PRIVATE
        ncnn
        android
        log)
