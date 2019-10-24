package com.imooc.coupon.converter;

import com.imooc.coupon.constant.DistributeTarget;

import javax.persistence.AttributeConverter;

//分发目标枚举属性转换器
public class DistributeTargetConverter
        implements AttributeConverter<DistributeTarget,Integer> {
    @Override
    public Integer convertToDatabaseColumn(DistributeTarget distributeTarget) {
        return distributeTarget.getCode();
    }

    @Override
    public DistributeTarget convertToEntityAttribute(Integer code) {
        return DistributeTarget.of(code);
    }
}
