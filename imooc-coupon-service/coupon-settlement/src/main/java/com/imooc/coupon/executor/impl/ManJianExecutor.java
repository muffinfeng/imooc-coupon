package com.imooc.coupon.executor.impl;

import com.imooc.coupon.constant.RuleFlag;
import com.imooc.coupon.executor.AbstractExecutor;
import com.imooc.coupon.executor.RuleExecutor;
import com.imooc.coupon.vo.CouponTemplateSDK;
import com.imooc.coupon.vo.SettlementInfo;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Collections;

/**
 * 满减优惠券结算规则执行器
 */

@Slf4j
@Component
public class ManJianExecutor extends AbstractExecutor implements RuleExecutor {


    /**
     * 规则类型标记
     */
    @Override
    public RuleFlag ruleConfig() {
        return RuleFlag.MANJIAN;
    }

    /**
     * 优惠券规则的计算
     *
     * @param settlement {@link SettlementInfo} 包含了选择的优惠券
     * @return {@link SettlementInfo} 修正过的结算信息
     */
    @Override
    public SettlementInfo computeRule(SettlementInfo settlement) {

        double goodsSum = retain2Decimal(
                goodCostSum(settlement.getGoodsInfos())
        );

        SettlementInfo probability = processGoddsTypeNotSatisfy(
                settlement,goodsSum
        );
        //如果不返回空的话，就是这些商品不符合优惠券的使用规则
        if(probability != null){
            log.debug("ManJian Template Is Not Match To GoodsType!");
            return probability;
        }
        //如果返回空的话，就是这些商品符合优惠券的使用规则
        CouponTemplateSDK templateSDK = settlement.getCouponAndTemplateInfos()
                .get(0).getTemplate();
        double base = (double)templateSDK.getRule().getDiscount().getBase();
        double quota = (double) templateSDK.getRule().getDiscount().getQuota();

        //如果不符合标准,则返回商品总价
        if(goodsSum < base){
            log.debug("Current Goods Cost Sum < ManJian Coupon Base!");
            settlement.setCost(goodsSum);
            settlement.setCouponAndTemplateInfos(Collections.emptyList());
            return settlement;
        }

        //计算使用优惠券的价格  -- 结算
        settlement.setCost(retain2Decimal(
                (goodsSum - quota) > minCost() ? (goodsSum - quota) : minCost()
        ));
        log.debug("Use ManJian Coupon Make Goods Cost From {} TO {}",
                goodsSum,settlement.getCost());

        return settlement;
    }
}
