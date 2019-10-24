package com.imooc.coupon.converter;

//优惠券分类枚举属性转换器

import com.imooc.coupon.constant.CouponCategory;

import javax.persistence.AttributeConverter;
import javax.persistence.Converter;

//AttributeConverter<X,Y>
//X 是实体属性的类型
//Y 是数据库字段的类型
@Converter
public class CouponCategoryConverter implements
        AttributeConverter<CouponCategory,String> {

    //将实体属性X转换为Y存储到数据库中，插入和记录执行的动作
    @Override
    public String convertToDatabaseColumn(CouponCategory couponCategory) {
        return couponCategory.getCode();
    }

    //将数据库中的字段Y 转换为实体属性X ，查询操作时执行的动作
    @Override
    public CouponCategory convertToEntityAttribute(String s) {
        return CouponCategory.of(s);
    }
}
