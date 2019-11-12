package com.imooc.coupon.executor;

import com.alibaba.fastjson.JSON;
import com.imooc.coupon.vo.GoodsInfo;
import com.imooc.coupon.vo.SettlementInfo;
import org.apache.commons.collections4.CollectionUtils;

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

        List<Integer> goodsType = settlement.getGoodsInfos()
                .stream().map(GoodsInfo::getType)
                .collect(Collectors.toList());

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
}
