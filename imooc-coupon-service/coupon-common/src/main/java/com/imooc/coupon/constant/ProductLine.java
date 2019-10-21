package com.imooc.coupon.constant;

//产品线枚举

import lombok.AllArgsConstructor;
import lombok.Getter;

import java.util.stream.Stream;

@Getter
@AllArgsConstructor
public enum ProductLine {

    DAMAO("大猫",1),
    DABAO("大宝",2);

    private String description;

    private Integer code;

    public static  ProductLine of(Integer code){
        return Stream.of(values())
                .filter(bean -> bean.code.equals(code))
                .findAny()
                .orElseThrow(() -> new IllegalArgumentException(code + "not exist"));

    }
}
