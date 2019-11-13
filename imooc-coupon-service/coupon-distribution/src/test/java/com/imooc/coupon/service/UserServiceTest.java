package com.imooc.coupon.service;

import com.alibaba.fastjson.JSON;
import com.imooc.coupon.constant.CouponStatus;
import com.imooc.coupon.exception.CouponException;
import com.netflix.discovery.converters.Auto;
import net.bytebuddy.asm.Advice;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.junit4.SpringRunner;

import javax.xml.bind.SchemaOutputResolver;
import java.util.ArrayList;
import java.util.List;

/**
 * 用户服务功能测试用例
 */
@SpringBootTest
@RunWith(SpringRunner.class)
public class UserServiceTest {

    //FAKE 一个 UserID
    private Long fakeUserId = 20001L;

    @Autowired
    private IUserService userService;

    @Test
    public void testFindCouponByStatus() throws CouponException{

        System.out.println(JSON.toJSONString(
                userService.findCouponByStatus(
                        fakeUserId,
                        CouponStatus.USABLE.getCode()
                )
        ));
    }

    @Test
    public void testFindAvailableTemplate() throws CouponException{

        System.out.println(JSON.toJSONString(
                userService.findAvailableTemplate(fakeUserId)
        ));
    }


}
