package com.imooc.coupon.service;

//用户服务相关的接口定义

import com.imooc.coupon.entity.Coupon;
import com.imooc.coupon.exception.CouponException;
import com.imooc.coupon.vo.AcquiredTemplateRequest;
import com.imooc.coupon.vo.CouponTemplateSDK;
import com.imooc.coupon.vo.SettlementInfo;

import java.util.List;

/**
 * 1.用户三类状态优惠券信息展示服务
 * 2.查看用户当前可以领取的优惠券模板 -- coupon-template（各种规则。。。）
 * 3.用户领取优惠券服务
 * 4.用户消费优惠券服务  -- coupon-settlement 结算微服务配合实现
 */
public interface IUserService {

    /**
     * 根据用户id 和 状态查询优惠券记录
     * @param userId
     * @param status
     * @return 返回优惠券的信息
     * @throws CouponException
     */
    List<Coupon> findCouponByStatus(Long userId,Integer status) throws CouponException;

    /**
     * 根据用户id 查找当前可以领取的优惠券模板
     * @param userId
     * @return
     * @throws CouponException
     */
    List<CouponTemplateSDK> findAvailableTemplate(Long userId)
            throws CouponException;

    /**
     * 用户领取优惠券服务
     * @param request
     * @return
     * @throws CouponException
     */
    Coupon acquireTemplate(AcquiredTemplateRequest request)
            throws CouponException;

    /**
     *
     * 结算（核销）优惠券
     *
     * @param info
     * @return
     * @throws CouponException
     */
    SettlementInfo settlement(SettlementInfo info) throws CouponException;
}
