package com.imooc.coupon.dao;

import com.imooc.coupon.entity.CouponTemplate;
import com.sun.org.apache.xpath.internal.operations.Bool;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

//CouponTemplate Dao接口定义
public interface CouponTemplateDao
        extends JpaRepository<CouponTemplate,Integer> {

    //根据模板名称查询模板
    //where name = ...
    CouponTemplate findByName(String name);

    //根据available 和 expired 标记查找模板记录
    //where available = ... and expired = ...
    List<CouponTemplate> findAllByAvailableAndExpired(
            Boolean available,Boolean expired
    );

    //根据expired标记查找模板记录
    //where expired = ...
    List<CouponTemplate> findAllByExpired(Boolean expired);
}
