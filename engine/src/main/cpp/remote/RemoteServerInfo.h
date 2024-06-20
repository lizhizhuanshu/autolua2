//
// Created by lizhi on 2024/5/29.
//

#ifndef AUTOLUA2_REMOTESERVERINFO_H
#define AUTOLUA2_REMOTESERVERINFO_H
#include <string>
#include <unordered_set>
struct ServiceInfo{
    std::string name;
    std::unordered_set<std::string> methodsName;
    bool operator==(const ServiceInfo& other) const {
        return name == other.name; // 根据实际需求添加其他成员变量的比较
    }
};

namespace std {
    template <>
    struct hash<ServiceInfo> {
        size_t operator()(const ServiceInfo& s) const {
            // Implement the hash function
            return std::hash<std::string>()(s.name);
        }
    };
}

struct RemoteServerInfo{
    std::string name;
    std::string host;
    int port;
    int services;
    std::string auth;
    std::unordered_set<ServiceInfo> rpcServices;
    static constexpr int CODE_PROVIDER =     0b00000001;
    static constexpr int RESOURCE_PROVIDER = 0b00000010;
    static constexpr int OBSERVER =          0b00000100;
    static constexpr int CONTROLLER =        0b00001000;
    bool operator==(const RemoteServerInfo& other) const {
        return name == other.name; // 根据实际需求添加其他成员变量的比较
    }
};

namespace std {
    template <>
    struct hash<RemoteServerInfo> {
        size_t operator()(const RemoteServerInfo& s) const {
            // Implement the hash function
            return std::hash<std::string>()(s.name);
        }
    };
}

#endif //AUTOLUA2_REMOTESERVERINFO_H
