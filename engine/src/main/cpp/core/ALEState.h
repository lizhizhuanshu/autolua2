//
// Created by lizhi on 2024/6/1.
//

#ifndef AUTOLUA2_ALESTATE_H
#define AUTOLUA2_ALESTATE_H
enum class ALEState:int {
    kIdle = 0,
    kStarting = 1,
    kRunning= 2,
    kStopping = 3,
    kPausing = 4,
    kPaused = 5,
    kResuming = 6,
};
#endif //AUTOLUA2_ALESTATE_H
