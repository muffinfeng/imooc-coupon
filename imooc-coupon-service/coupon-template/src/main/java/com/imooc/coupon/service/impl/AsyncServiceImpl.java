package com.imooc.coupon.service.impl;

import com.google.common.base.Stopwatch;
import com.imooc.coupon.constant.Constant;
import com.imooc.coupon.dao.CouponTemplateDao;
import com.imooc.coupon.entity.CouponTemplate;
import com.imooc.coupon.service.IAsyncService;
import lombok.extern.slf4j.Slf4j;
import net.bytebuddy.asm.Advice;
import org.apache.commons.lang3.RandomStringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.text.SimpleDateFormat;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

//异步服务接口实现
@Slf4j
@Service
public class AsyncServiceImpl implements IAsyncService {

    private final CouponTemplateDao templateDao;

    private final StringRedisTemplate redisTemplate;

    @Autowired
    public AsyncServiceImpl(CouponTemplateDao templateDao, StringRedisTemplate redisTemplate) {
        this.templateDao = templateDao;
        this.redisTemplate = redisTemplate;
    }

    //根据模板异步地创建优惠券码
    @Async("getAsyncExecutor")
    @Override
    public void asyncConstructCouponByTemplate(CouponTemplate couponTemplate) {

        Stopwatch stopwatch = Stopwatch.createStarted();

        Set<String> couponCodes = buildCouponCode(couponTemplate);

        //imooc_coupon_template_code_1
        String redisKey = String.format("%s%s",
                Constant.RedisPrefix.COUPON_TEMPLATE,couponTemplate.getId().toString());
        log.info("Push CouponCode TO Redis",
                redisTemplate.opsForList().rightPushAll(redisKey,couponCodes));

        couponTemplate.setAvailable(true);
        templateDao.save(couponTemplate);

        stopwatch.stop();
        log.info("Construct CouponCode By Template Cost:{}ms",
                stopwatch.elapsed(TimeUnit.MILLISECONDS));

        log.info("CouponTemplate({}) is Available!", couponTemplate.getId());
        //异步批量创建优惠券结束
    }

    //构造优惠券码
    //优惠券码（每一张18位)
    //前四位 : 产品线 + 类型
    //中间六位 ： 日期随机（190101）
    //后八位： 0-9 随机数构成

    //return Set<String> 与 template.count 相同个数的优惠券码
    private Set<String> buildCouponCode(CouponTemplate couponTemplate){
        Stopwatch watch = Stopwatch.createStarted();

        Set<String> result = new HashSet<>(couponTemplate.getCount());

        //前四位
        String prefix4 = couponTemplate.getProductLine().getCode().toString()
                + couponTemplate.getCategory().getCode();

        String date = new SimpleDateFormat("yyMMdd")
                .format(couponTemplate.getCreateTime());

        for(int i = 0;i != couponTemplate.getCount();++i){
            result.add(prefix4 + buildCouponCodeSuffix14(date));
        }

        while(result.size() < couponTemplate.getCount()){
            result.add(prefix4 + buildCouponCodeSuffix14(date));
        }

        assert result.size() == couponTemplate.getCount();

        watch.stop();
        log.info("Build Coupon Code Cost: {}ms",
                watch.elapsed(TimeUnit.MILLISECONDS));

        return result;
    }


    //构造优惠券码的后14位
    //date 是创建优惠券的日期
    //return 14位优惠卷码
    private String buildCouponCodeSuffix14(String date){

        char[] bases = new char[]{'1','2','3','4','5','6','7','8','9'};

        //中间六位
        List<Character> chars = date.chars()
                .mapToObj(e -> (char) e).collect(Collectors.toList());
        Collections.shuffle(chars);
        String mid6 = chars.stream()
                .map(Objects::toString).collect(Collectors.joining());


        //后八位
        String suffix8 = RandomStringUtils.random(1,bases)
                    + RandomStringUtils.randomNumeric(7);





        return mid6 + suffix8;
    }
}
