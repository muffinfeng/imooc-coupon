package com.imooc.coupon.service;

import com.imooc.coupon.entity.Coupon;
import com.imooc.coupon.exception.CouponException;

import java.util.List;

public interface IRedisService {

    /**功能 1
     * <h2>根据 userId 和状态找到缓存的优惠券列表数据</h2> 优惠券列表tag页面
     * @param userId 用户 id
     * @param status 优惠券状态 {@link com.imooc.coupon.constant.CouponStatus}
     * @return {@link Coupon}s, 注意, 可能会返回 null, 代表从没有过记录
     * */
    List<Coupon> getCachedCoupons(Long userId, Integer status);

    /**
     * <h2>保存空的优惠券列表到缓存中</h2>
     * @param userId 用户 id
     * @param status 优惠券状态列表
     * */
    void saveEmptyCouponListToCache(Long userId, List<Integer> status);

    /**功能 2
     * <h2>尝试从 Cache 中获取一个优惠券码</h2>  1.这个要从template模块处获取
     * 2.根据优惠券的领取限制，对比当前用户所拥有的优惠券作出判断
     * @param templateId 优惠券模板主键
     * @return 优惠券码
     * */
    String tryToAcquireCouponCodeFromCache(Integer templateId);

    /**功能 3
     * <h2>将优惠券保存到 Cache 中</h2>
     * @param userId 用户 id
     * @param coupons {@link Coupon}s
     * @param status 优惠券状态
     * @return 保存成功的个数
     * */
    Integer addCouponToCache(Long userId, List<Coupon> coupons,
                             Integer status) throws CouponException;
}
