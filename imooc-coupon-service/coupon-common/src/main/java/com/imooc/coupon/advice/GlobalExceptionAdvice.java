package com.imooc.coupon.advice;

//全局异常处理

import com.imooc.coupon.exception.CouponException;
import com.imooc.coupon.vo.CommonResponse;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import javax.servlet.http.HttpServletRequest;

@RestControllerAdvice
public class GlobalExceptionAdvice {

    //对 CounponException 进行统一处理
    @ExceptionHandler(value = CouponException.class)
    public CommonResponse<String> handlerCouponException(
            HttpServletRequest req, CouponException ex
            ){
        CommonResponse<String> response = new CommonResponse<>(-1,"business error");
        response.setData(ex.getMessage());
        return response;
    }
}
