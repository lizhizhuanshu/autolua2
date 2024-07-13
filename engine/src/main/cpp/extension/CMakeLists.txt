
cmake_minimum_required(VERSION 3.22.0)

project(extension)

add_library(extension STATIC
        Display.cpp
        JavaInputManager.cpp
        NativeInputManager.cpp
        InputManager.cpp
)

target_include_directories(extension PUBLIC ../3rd/autolua_vision/src
        ../3rd/autolua_vision/lodepng)
target_link_libraries(extension PUBLIC alv_static)




