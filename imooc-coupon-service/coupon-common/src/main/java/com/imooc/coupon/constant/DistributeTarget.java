package com.imooc.coupon.constant;

//分发目标

import lombok.AllArgsConstructor;
import lombok.Getter;

import java.util.stream.Stream;

@Getter
@AllArgsConstructor
public enum DistributeTarget {

    SINGLE("",1),
    MULTI("多用户",2);

    private String description;

    private Integer code;

    public static  DistributeTarget of(Integer code){
        return Stream.of(values())
                .filter(bean -> bean.code.equals(code))
                .findAny()
                .orElseThrow(() -> new IllegalArgumentException(code + "not exist"));

    }
}
