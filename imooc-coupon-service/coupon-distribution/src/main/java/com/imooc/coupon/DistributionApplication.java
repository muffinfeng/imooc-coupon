package com.imooc.coupon;

//分发系统的启动入口

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.circuitbreaker.EnableCircuitBreaker;
import org.springframework.cloud.client.loadbalancer.LoadBalanced;
import org.springframework.cloud.netflix.eureka.EnableEurekaClient;
import org.springframework.cloud.openfeign.EnableFeignClients;
import org.springframework.context.annotation.Bean;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;
import org.springframework.web.client.RestTemplate;

@EnableJpaAuditing   //jpa填充数据列
@EnableFeignClients  //调用其他微服务
@EnableCircuitBreaker  //融断降级
@EnableEurekaClient  //作为一个client
@SpringBootApplication
public class DistributionApplication {

    @Bean
    @LoadBalanced
    RestTemplate restTemplate(){
        return new RestTemplate();
    }


    public static void main(String[] args) {
        SpringApplication.run(DistributionApplication.class,args);
    }

}
