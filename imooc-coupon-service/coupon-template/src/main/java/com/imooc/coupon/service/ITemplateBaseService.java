package com.imooc.coupon.service;

import com.imooc.coupon.entity.CouponTemplate;
import com.imooc.coupon.exception.CouponException;
import com.imooc.coupon.vo.CouponTemplateSDK;

import java.util.Collection;
import java.util.List;
import java.util.Map;

//优惠券模板基础(view,delete...)服务定义
public interface ITemplateBaseService {

    //根据优惠券模板id 获取优惠券模板信息
    //id是模板id
    CouponTemplate buildTemplateInfo(Integer id) throws CouponException;

    //查找所有可用的优惠券模板
    List<CouponTemplateSDK> findAllUsableTemplate();

    //获取模板 ids 到 CouponTemplateSDK 的映射
    //param ids 模板ids
    //return map<key: 模板 id,  value： CouponTemplateSDK>
    Map<Integer,CouponTemplateSDK> findIds2TemplateSDK(Collection<Integer> ids);


}
