package com.imooc.coupon.filter;
//校验请求中的token

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import javax.servlet.http.HttpServletRequest;

@Slf4j
@Component
public class TokenFilter extends AbstactPreZuulFilter {

    @Override
    protected Object cRun() {

        HttpServletRequest request = context.getRequest();
        log.info(request.getMethod() + request.getRequestURL().toString());

        Object token = request.getParameter("token");
        if(null == token){
            log.error("error: token id empty");
            return fail(401,"error: token id empty");
        }

        return success();
    }

    @Override
    public int filterOrder() {
        return 1;
    }
}
