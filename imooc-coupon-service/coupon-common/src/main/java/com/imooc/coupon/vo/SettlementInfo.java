package com.imooc.coupon.vo;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 *  结算信息对象定义
 *  包含：
 *  1.userId
 *  2.商品信息(list）
 *  3.优惠券列表
 *  4.结算结果金额
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class SettlementInfo {

    private Long userId;

    //商品信息
    private List<GoodsInfo> goodsInfos;

    //优惠券列表
    private List<CouponAndTemplateInfo> couponAndTemplateInfos;

    //结果结算金额
    private Double cost;

    /**
     * 优惠券和模板信息
     */
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class CouponAndTemplateInfo{

        //Coupon 的主键
        private Integer id;

        private CouponTemplateSDK template;
    }
}