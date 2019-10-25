package com.imooc.coupon.service;

import com.imooc.coupon.entity.CouponTemplate;

//异步服务接口定义  给优惠券模板生成优惠券码
public interface IAsyncService {

    //根据模板异步地创建优惠券码
    void asyncConstructCouponByTemplate(CouponTemplate couponTemplate);
}
