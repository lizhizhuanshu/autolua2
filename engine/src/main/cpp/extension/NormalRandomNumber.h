//
// Created by lizhi on 2024/6/18.
//

#ifndef AUTOLUA2_NORMALRANDOMNUMBER_H
#define AUTOLUA2_NORMALRANDOMNUMBER_H
#include <random>
namespace autolua {
    class NormalRandomNumber {
        std::mt19937 gen_;
        std::normal_distribution<double> dis_;
        double min_;
        double max_;
    public:
        NormalRandomNumber(double min, double max)
                :min_(min),max_(max){
            std::random_device rd;
            gen_=std::mt19937(rd());
            auto mean=(min+max)/2;
            auto sigma=(max-min)/7;
            dis_=std::normal_distribution<double>(mean, sigma);
        }
        int next(){
            while (true){
                auto value=dis_(gen_);
                if(value>=min_&&value<=max_){
                    return static_cast<int>(std::round(value));
                }
            }
        }
    };

} // autolua

#endif //AUTOLUA2_NORMALRANDOMNUMBER_H
