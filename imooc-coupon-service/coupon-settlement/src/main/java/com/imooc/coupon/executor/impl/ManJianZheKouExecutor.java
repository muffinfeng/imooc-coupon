package com.imooc.coupon.executor.impl;

import com.alibaba.fastjson.JSON;
import com.imooc.coupon.constant.CouponCategory;
import com.imooc.coupon.constant.RuleFlag;
import com.imooc.coupon.executor.AbstractExecutor;
import com.imooc.coupon.executor.RuleExecutor;
import com.imooc.coupon.vo.GoodsInfo;
import com.imooc.coupon.vo.SettlementInfo;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.CollectionUtils;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.stream.Collectors;

/**
 * 满减 + 折扣优惠券结算规则执行器
 */
@Component
@Slf4j
public class ManJianZheKouExecutor extends AbstractExecutor implements RuleExecutor {

    /**
     * 规则类型标记
     */
    @Override
    public RuleFlag ruleConfig() {
        return RuleFlag.MANJIAN_ZHEKOU;
    }

    /**
     * 校验商品类型与优惠券是否匹配
     * 需要注意：
     * 1.这里实现的满减 + 折扣 多品类优惠券的校验，多品类优惠券重载此方法
     * 2.如果想要使用多类优惠券，则必须要所有的商品类型都包含在内，即差集为空
     *
     * @param settlement 用户传递的结算信息
     */
    @Override
    @SuppressWarnings("all")
    protected boolean isGoodsTypeSatisfy(SettlementInfo settlement) {

        log.debug("Check ManJian And Zhekou Is Match Or Not");
        List<Integer> goodsType = settlement.getGoodsInfos().stream()
                .map(GoodsInfo::getType).collect(Collectors.toList());
        List<Integer> templateGoodsType = new ArrayList<>();

        settlement.getCouponAndTemplateInfos().forEach(ct -> {
            templateGoodsType.addAll(JSON.parseObject(
                    ct.getTemplate().getRule().getUsage().getGoodsType()
            ,List.class));
        });

        // 如果想要使用多类优惠券，则必须要所有的商品类型都包含在内，即差集为空，其实也是子集
        return CollectionUtils.isEmpty(CollectionUtils.subtract(
                goodsType,templateGoodsType
        ));

        //goodsType            水果 电脑
        //templateGoodsType    水果 电脑 家具

    }

    /**
     * 优惠券规则的计算
     *
     * @param settlementInfo {@link SettlementInfo} 包含了选择的优惠券
     * @return {@link SettlementInfo} 修正过的结算信息
     */
    @Override
    public SettlementInfo computeRule(SettlementInfo settlementInfo) {

        double goodsSum = retain2Decimal(goodCostSum(
                settlementInfo.getGoodsInfos()
        ));
        SettlementInfo probability = processGoddsTypeNotSatisfy(
                settlementInfo,goodsSum
        );
        /**
         * 第一步，商品类型的校验
         */
        if(null != probability){
            log.debug("Manjian And Zhekou Template Is Not Match GoodsType!");
            return probability;
        }

        //把满减券和折扣券 准确地拿出来
        SettlementInfo.CouponAndTemplateInfo manJian = null;
        SettlementInfo.CouponAndTemplateInfo zheKou = null;

        for (SettlementInfo.CouponAndTemplateInfo ct :
            settlementInfo.getCouponAndTemplateInfos()){
            if(CouponCategory.of(ct.getTemplate().getCategory()) ==
                    CouponCategory.MANJIAN){
                manJian = ct;
            }else{
                zheKou = ct;
            }
        }

        assert null != manJian;
        assert null != zheKou;

        /**
         * 第二步  当前的优惠券和满减券如果不能一起使用，即判断任一个优惠券模板rule中的weight是否包含对方的模板编码
         * 清空优惠券，返回商品原价
         */
        if(!isTemplateCanShared(manJian,zheKou)){
            log.debug("Current ManJian And Zhekou Can Not Shared!");
            settlementInfo.setCost(goodsSum);
            settlementInfo.setCouponAndTemplateInfos(Collections.emptyList());
            return settlementInfo;
        }

        /**
         * 第三步  真正结算
         */
        //先计算满减
        List<SettlementInfo.CouponAndTemplateInfo> ctInfos = new ArrayList<>();
        double manJianBase = (double)manJian.getTemplate().getRule().getDiscount().getBase();
        double manJianQuota = (double)manJian.getTemplate().getRule().getDiscount().getQuota();

        //最终的价格
        double targetSum = goodsSum;
        if(targetSum >= manJianBase){
            targetSum -= manJianQuota;
            ctInfos.add(manJian);
        }

        //再计算折扣
        double zhekouQuota = (double)zheKou.getTemplate().getRule().getDiscount().getQuota();
        targetSum *= zhekouQuota * 1.0 / 100;
        ctInfos.add(zheKou);

        settlementInfo.setCouponAndTemplateInfos(ctInfos);
        settlementInfo.setCost(retain2Decimal(
                targetSum > minCost() ? targetSum : minCost()
        ));

        log.debug("Use ManJian And ZheKou coupon Make Goods Cost From {} To {}",
                goodsSum,settlementInfo.getCost());

        return settlementInfo;
    }

    /**
     * 当前的两张优惠券是否可以共用
     * 即校验 TemplateRule 中的 weight 是否满足条件
     * @param manJian
     * @param zheKou
     * @return
     */
    @SuppressWarnings("all")
    private boolean
    isTemplateCanShared(SettlementInfo.CouponAndTemplateInfo manJian,
                        SettlementInfo.CouponAndTemplateInfo zheKou){
        //优惠券模板的编码
        String manjianKey = manJian.getTemplate().getKey()
                + String.format("%04d",manJian.getTemplate().getId());
        String zhekouKey = zheKou.getTemplate().getKey()
                + String.format("%04d",zheKou.getTemplate().getId());

        List<String> allSharedKeysForManjian = new ArrayList<>();
        allSharedKeysForManjian.add(manjianKey);
        allSharedKeysForManjian.addAll(JSON.parseObject(
                manJian.getTemplate().getRule().getWeight(),List.class
        ));

        List<String> allSharedKeysForZheKou = new ArrayList<>();
        allSharedKeysForZheKou.add(zhekouKey);
        allSharedKeysForZheKou.addAll(JSON.parseObject(
                zheKou.getTemplate().getRule().getWeight(),List.class
        ));

        return CollectionUtils.isSubCollection(
                    Arrays.asList(manjianKey,zhekouKey), allSharedKeysForManjian)
                || CollectionUtils.isSubCollection(
                    Arrays.asList(manjianKey,zhekouKey), allSharedKeysForZheKou
        );
    }

}
