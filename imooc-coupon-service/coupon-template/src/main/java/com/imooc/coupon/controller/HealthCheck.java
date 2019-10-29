package com.imooc.coupon.controller;

import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;


//健康检查接口
@Slf4j
@RestController
public class HealthCheck {

    //健康检查接口
    @GetMapping("/health")
    public String health(){
        log.debug("view health api");
        return "CouponTemplate is OK";
    }
}
