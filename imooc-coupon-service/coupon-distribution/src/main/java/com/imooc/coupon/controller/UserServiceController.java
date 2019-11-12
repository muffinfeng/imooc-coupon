package com.imooc.coupon.controller;

import com.alibaba.fastjson.JSON;
import com.imooc.coupon.entity.Coupon;
import com.imooc.coupon.exception.CouponException;
import com.imooc.coupon.serialization.CouponSerialize;
import com.imooc.coupon.service.IUserService;
import com.imooc.coupon.vo.AcquiredTemplateRequest;
import com.imooc.coupon.vo.CouponTemplateSDK;
import com.imooc.coupon.vo.SettlementInfo;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Slf4j
@RestController
public class UserServiceController {

    //用户服务接口
    private final IUserService userService;


    @Autowired
    public UserServiceController(IUserService userService) {
        this.userService = userService;
    }

    /**
     * 根据用户id 和优惠券状态查找用户优惠券记录
     * @param userId
     * @param status
     * @return
     * @throws CouponException
     */
    //127.0.0.1:7002/coupon-distribution/coupons
    @GetMapping("/coupons")
    public List<Coupon> findCouponsByStatus(
            @RequestParam("userId") Long userId,
            @RequestParam("status") Integer status) throws CouponException {

        log.info("FInd Coupon By Status: {}, {}",userId,status);
        return userService.findCouponByStatus(userId,status);
    }

    /**
     * 根据用户id 查找当前可以领取的优惠券模板
     * @param userId
     * @return
     */
    //127.0.0.1:7002/coupon-distribution/template
    @GetMapping("/template")
    public List<CouponTemplateSDK> findAvailableTemplate(
            @RequestParam("userId")Long userId) throws CouponException{

        log.info("Find Available Template: {}",userId);
        return userService.findAvailableTemplate(userId);
    }

    /**
     * 用户领取优惠券
     * @param request
     * @return
     * @throws CouponException
     */
    //127.0.0.1:7002/coupon-distribution/acquire/template
    @PostMapping("/acquire/template")
    public Coupon acquireTemplate(@RequestBody AcquiredTemplateRequest request)
            throws CouponException{

        log.info("Acquire Template: {}", JSON.toJSONString(request));
        return userService.acquireTemplate(request);
    }

    /**
     * 结算（核销）优惠券
     * @param info
     * @return
     * @throws CouponException
     */
    //127.0.0.1:7002/coupon-distribution/settlement
    @PostMapping("/settlement")
    public SettlementInfo settlement(@RequestBody SettlementInfo info)
            throws CouponException{

        log.info("Settlement : {}", JSON.toJSONString(info));
        return userService.settlement(info);
    }
}
