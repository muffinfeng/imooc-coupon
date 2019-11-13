package com.imooc.coupon.executor;

import com.alibaba.fastjson.JSON;
import com.imooc.coupon.vo.GoodsInfo;
import com.imooc.coupon.vo.SettlementInfo;
import org.apache.commons.collections4.CollectionUtils;

import java.math.BigDecimal;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 规则执行器抽象类，定义通用方法
 */
public class AbstractExecutor {

    /**
     * 校验商品类型与优惠券是否匹配
     * 需要注意：
     * 1.这里实现的单品类优惠券的校验，多品类优惠券重载此方法
     * 2.商品只需要有一个优惠券要求的商品类型匹配就可以
     */
    @SuppressWarnings("all")
    protected boolean isGoodsTypeSatisfy(SettlementInfo settlement){

        //settlement里所有的商品类型的id
        List<Integer> goodsType = settlement.getGoodsInfos()
                .stream().map(GoodsInfo::getType)
                .collect(Collectors.toList());

        //优惠券里规定所优惠的商品类型id范围
        List<Integer> templateGoodsType = JSON.parseObject(
                settlement.getCouponAndTemplateInfos().get(0).getTemplate()
                .getRule().getUsage().getGoodsType(),
                List.class
        );

        //存在交集即可
        return CollectionUtils.isNotEmpty(
                CollectionUtils.intersection(goodsType,templateGoodsType)
        );
    }

    /**
     * 处理商品类型与优惠券限制不匹配的情况
     * @return
     */
    protected SettlementInfo processGoddsTypeNotSatisfy(
            SettlementInfo settlementInfo,double goodsSum
    ){
        boolean isGoodsTypeSatisfy = isGoodsTypeSatisfy(settlementInfo);

        //当商品类型不满足时，直接返回总价，并清空优惠券
        if(!isGoodsTypeSatisfy){
            settlementInfo.setCost(goodsSum);
            settlementInfo.setCouponAndTemplateInfos(Collections.emptyList());
            return settlementInfo;
        }

        return null;
    }

    //计算商品总价
    protected double goodCostSum(List<GoodsInfo> goodsInfos){

        return goodsInfos.stream().mapToDouble(
                g -> g.getPrice() * g.getCount()
        ).sum();
    }

    //保留两位小数
    protected double retain2Decimal(double value){
        return new BigDecimal(value).setScale(
                2,BigDecimal.ROUND_HALF_UP
        ).doubleValue();
    }

    //最小支付费用
    protected double minCost(){
        return 0.1;
    }
}
