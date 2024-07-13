//
// Created by lizhi on 2024/6/29.
//

#ifndef AUTOLUA2_INPUT_H
#define AUTOLUA2_INPUT_H
#include <cstdint>
namespace autolua {
    class Input{
    public:
        struct PointerState{
            int id = -1;
            float x = -1;
            float y = -1;
            float major = -1;
            float minor = -1;
            float pressure = -1;
            float size = -1;
            uint32_t flags = 0;
            enum Target{
                kX = 1 << 1,
                kY = 1 << 2,
                kMajor = 1 << 3,
                kMinor = 1 << 4,
                kPressure = 1 << 5,
                kSize = 1 << 6,
                kAll = 0xFFFFFFFF
            };
            void setFlag(uint32_t target){
                flags |= target;
            };
            void clearFlag(Target target = kAll){
                flags &= ~target;
            };

            [[nodiscard]] bool hasFlag(Target target) const{
                return flags & target;
            };
            bool tryChangeX(float x){
                if(x == this->x) return false;
                this->x = x;
                setFlag(kX);
                return true;
            }
            bool tryChangeY(float y){
                if(y == this->y) return false;
                this->y = y;
                setFlag(kY);
                return true;
            }
            bool tryChangeMajor(float major){
                if(major == this->major) return false;
                this->major = major;
                setFlag(kMajor);
                return true;
            }
            bool tryChangeMinor(float minor){
                if(minor == this->minor) return false;
                this->minor = minor;
                setFlag(kMinor);
                return true;
            }
            bool tryChangePressure(float pressure){
                if(pressure == this->pressure) return false;
                this->pressure = pressure;
                setFlag(kPressure);
                return true;
            }
            bool tryChangeSize(float size){
                if(size == this->size) return false;
                this->size = size;
                setFlag(kSize);
                return true;
            }

            void clear(){
                id = -1;
                x = -1;
                y = -1;
                major = -1;
                minor = -1;
                pressure = -1;
                size = -1;
                flags = 0;
            };
        };
        virtual int syncPointer(PointerState* pointerState) = 0;
        virtual int releasePointer(int id) = 0;
        //返回1 表示成功，0表示失败，-1表示不支持
        virtual int keyDown(int key) = 0;
        virtual int keyUp(int key) = 0;
        virtual void releaseAllDown() = 0;
    };
}
#endif //AUTOLUA2_INPUT_H
