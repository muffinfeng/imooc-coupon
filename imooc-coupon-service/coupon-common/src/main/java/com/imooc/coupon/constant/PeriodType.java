package com.imooc.coupon.constant;

import lombok.AllArgsConstructor;
import lombok.Getter;

import java.util.stream.Stream;

// 有效期类型
@Getter
@AllArgsConstructor
public enum PeriodType {

    REGULAR("固定的(固定日期)",1),
    SHIFT("变动的(以领取之日开始计算)",2);

    private String description;

    private Integer code;

    public static PeriodType of(Integer code){
        return Stream.of(values())
                .filter(bean -> bean.code.equals(code))
                .findAny()
                .orElseThrow(() -> new IllegalArgumentException(code + "not exist"));

    }
}
