package com.imooc.coupon.vo;

//优惠券kafka 消息对象定义

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class CouponKafkaMessage {

    //优惠券状态
    private Integer status;

    //Coupon主键
    private List<Integer> ids;
}
