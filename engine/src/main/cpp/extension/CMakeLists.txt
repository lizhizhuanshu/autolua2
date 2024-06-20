
cmake_minimum_required(VERSION 3.22.0)

project(extension)

aux_source_directory(. DIR_SRCS)
add_library(extension STATIC ${DIR_SRCS} )

target_include_directories(extension PUBLIC ../3rd/autolua_vision/src
        ../3rd/autolua_vision/lodepng)

target_link_libraries(extension PUBLIC alv_static)




