# For more information about using CMake with Android Studio, read the
# documentation: https://d.android.com/studio/projects/add-native-code.html.
# For more examples on how to use CMake, see https://github.com/android/ndk-samples.

# Sets the minimum CMake version required for this project.
cmake_minimum_required(VERSION 3.22.1)

# Declares the project name. The project name can be accessed via ${ PROJECT_NAME},
# Since this is the top level CMakeLists.txt, the project name is also accessible
# with ${CMAKE_PROJECT_NAME} (both CMake variables are in-sync within the top level
# build script scope).
project("engine")
set(CMAKE_CXX_STANDARD 17)


#compile lua
add_definitions(-DLUA_DL_DLOPEN -DLUA_COMPAT_5_3 -DLUA_USE_LINUX)
set(MY_LUA_DIR ${CMAKE_CURRENT_SOURCE_DIR}/3rd/lua)
include_directories(${MY_LUA_DIR})
aux_source_directory(${MY_LUA_DIR}  LUA_DIR)
list(REMOVE_ITEM LUA_DIR "${MY_LUA_DIR}/lua.c"
        "${MY_LUA_DIR}/luac.c" "${MY_LUA_DIR}/onelua.c")
add_library(lua_static STATIC ${LUA_DIR})

#compile libhv
option(BUILD_SHARED "Build shared library" OFF)
option(BUILD_STATIC "Build static library" ON)
option(BUILD_EXAMPLES "Build examples" OFF)
add_subdirectory(${CMAKE_CURRENT_SOURCE_DIR}/3rd/libhv)
include_directories(${CMAKE_CURRENT_SOURCE_DIR}/3rd/libhv/include)


include_directories(${CMAKE_CURRENT_SOURCE_DIR}/base)
aux_source_directory(${CMAKE_CURRENT_SOURCE_DIR}/base BASE_DIR)
add_library(engine_base SHARED
        # List C/C++ source files with relative paths to this CMakeLists.txt.
        ${BASE_DIR})
target_link_libraries(engine_base
        # List libraries link to the target library
        log
        lua_static
)


include_directories(${CMAKE_CURRENT_SOURCE_DIR}/core)
aux_source_directory(${CMAKE_CURRENT_SOURCE_DIR}/core CORE_DIR)
add_library(engine_core STATIC
        # List C/C++ source files with relative paths to this CMakeLists.txt.
        ${CORE_DIR})

option(BUILD_SHARED_AND_STATIC_LIBS "Build both shared and static libraries" ON)
option(CJSON_BUILD_SHARED_LIBS "Build shared library" OFF)
set(MY_CJSON_DIR ${CMAKE_CURRENT_SOURCE_DIR}/3rd/cjson)
add_subdirectory(${MY_CJSON_DIR})
include_directories(${MY_CJSON_DIR})


include_directories(.)
add_subdirectory(${CMAKE_CURRENT_SOURCE_DIR}/remote)
include_directories(${CMAKE_CURRENT_SOURCE_DIR}/remote)

option(VISION_SHARED "Build shared library" OFF)
option(VISION_STATIC "Build static library" ON)
set(VISION_SHARED OFF CACHE BOOL "" FORCE)
set(VISION_STATIC ON CACHE BOOL "" FORCE)
set(BUILD_TESTING OFF CACHE BOOL "" FORCE)
add_subdirectory(${CMAKE_CURRENT_SOURCE_DIR}/3rd/autolua_vision)


aux_source_directory(${CMAKE_CURRENT_SOURCE_DIR}  ENGINE_DIR)
add_library(${CMAKE_PROJECT_NAME} SHARED ${ENGINE_DIR}
        ./3rd/autolua_vision/src/lua_vision.h
        ./3rd/autolua_vision/src/Bitmap.h)


add_subdirectory(extension)
include_directories(${CMAKE_CURRENT_SOURCE_DIR}/extension)

# Specifies libraries CMake should link to your target library. You
# can link libraries from various origins, such as libraries defined in this
# build script, prebuilt third-party libraries, or Android system libraries.
target_link_libraries(${CMAKE_PROJECT_NAME}
        # List libraries link to the target library
        android
        log

        lua_static
        engine_base
        engine_core
        hv_static
        cjson
        remote

        extension

)