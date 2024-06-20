
cmake_minimum_required(VERSION 3.22.1)

project(remote)

set(CMAKE_CXX_STANDARD 17)

set(PROTO_DIR ${PROJECT_SOURCE_DIR}/../../../../build/generated/source/proto/cpp)

set(protobuf_INSTALL OFF)
set(protobuf_BUILD_TESTS OFF)
set(protobuf_BUILD_PROTOC_BINARIES OFF)

add_subdirectory(${CMAKE_CURRENT_SOURCE_DIR}/../3rd/protobuf ${CMAKE_CURRENT_BINARY_DIR}/protobuf)

include_directories(${PROTO_DIR})

aux_source_directory(${CMAKE_CURRENT_SOURCE_DIR} SRC_LIST)
aux_source_directory(${PROTO_DIR} MY_PROTO_LIST)

add_library(remote SHARED
        ${SRC_LIST}
        ${MY_PROTO_LIST}
        ./composite/CodeProviderComposite.cpp
        ./composite/ObserverComposite.cpp
        ./composite/ResourceProviderComposite.cpp)

target_link_libraries(remote
        libprotobuf-lite
        hv_static
        lua_static
        engine_base
        engine_core
        cjson)

