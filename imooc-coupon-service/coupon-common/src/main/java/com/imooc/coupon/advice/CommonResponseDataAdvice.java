package com.imooc.coupon.advice;

import com.imooc.coupon.annotation.IgnoreResponseAdvice;
import com.imooc.coupon.vo.CommonResponse;
import org.springframework.core.MethodParameter;
import org.springframework.http.MediaType;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.mvc.method.annotation.ResponseBodyAdvice;


//统一相应
@RestControllerAdvice
public class CommonResponseDataAdvice implements ResponseBodyAdvice {

    @Override
    public boolean supports(MethodParameter methodParameter, Class aClass) {

        //如果当前方法所在的类标识了@IgnoreResponseAdvice ,不需要处理
        if(methodParameter.getDeclaringClass().isAnnotationPresent(
                IgnoreResponseAdvice.class
        )){
            return false;
        }

        //如果当前方法标识了@IgnoreResponseAdvice ,不需要处理
        if(methodParameter.getMethod().isAnnotationPresent(
                IgnoreResponseAdvice.class
        )){
            return false;
        }

        //对相应进行处理 执行beforeBodyWrite方法
        return true;
    }


    //相应返回之前的处理
    @Override
    public Object beforeBodyWrite(Object o,
                                  MethodParameter methodParameter,
                                  MediaType mediaType, Class aClass,
                                  ServerHttpRequest serverHttpRequest,
                                  ServerHttpResponse serverHttpResponse) {

        //定义最终的返回对象
        CommonResponse<Object> response = new CommonResponse<>(
                0,""
        );

        //如果 o 是null,response 不需要设置data
        if(null == o){
            return response;
        }else if(o instanceof  CommonResponse){  //如果o已经是CommonResponse 是controller里制作
                                                    //则不需要再次处理
            response = (CommonResponse<Object>)o;
        }else{          //把相应对象作为CommonResponse 的 data部分
            response.setData(o);
        }

        return response;
    }
}
