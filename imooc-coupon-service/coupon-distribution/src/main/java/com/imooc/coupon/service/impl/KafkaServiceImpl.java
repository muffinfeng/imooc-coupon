package com.imooc.coupon.service.impl;

import com.alibaba.fastjson.JSON;
import com.imooc.coupon.constant.Constant;
import com.imooc.coupon.constant.CouponStatus;
import com.imooc.coupon.dao.CouponDao;
import com.imooc.coupon.entity.Coupon;
import com.imooc.coupon.service.IKafkaService;
import com.imooc.coupon.vo.CouponKafkaMessage;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;

//kafka相关服务接口实现
@Slf4j
@Component
public class KafkaServiceImpl implements IKafkaService {

    //CouponDao
    private final CouponDao couponDao;

    @Autowired
    public KafkaServiceImpl(CouponDao couponDao) {
        this.couponDao = couponDao;
    }

    @Override
    @KafkaListener(topics = {Constant.TOPIC}, groupId = "imooc-coupon-1")
    public void consumeCouponKafkaMessage(ConsumerRecord<?, ?> record) {

        Optional<?> kafkaMessage = Optional.ofNullable(record.value());
        if(kafkaMessage.isPresent()){
            Object message = kafkaMessage.get();
            CouponKafkaMessage couponInfo = JSON.parseObject(
                    message.toString(),CouponKafkaMessage.class
            );

            log.info("Receive CouponKafkaMessage: {}",message.toString());

            CouponStatus status = CouponStatus.of(couponInfo.getStatus());

            switch (status){
                //USABLE不用操作，因为领取优惠券时还没由Coupon的id生成
                case USABLE:
                    break;
                case USED:
                    processUsedCoupons(couponInfo,status);
                    break;
                case EXPIRED:
                    processExpiredCoupons(couponInfo,status);
                    break;
            }
        }
    }

    //处理已使用的优惠券
    private void processUsedCoupons(CouponKafkaMessage kafkaMessage,
                                    CouponStatus status){
        //TODO 给用户发送信息
        processCouponsByStatus(kafkaMessage,status);
    }
    //处理已过期的优惠券
    private void processExpiredCoupons(CouponKafkaMessage kafkaMessage,
                                    CouponStatus status){
        processCouponsByStatus(kafkaMessage,status);
    }

    private void processCouponsByStatus(CouponKafkaMessage kafkaMessage,
                                        CouponStatus status){
        List<Coupon> coupons = couponDao.findAllById(
                kafkaMessage.getIds()
        );
        if(CollectionUtils.isEmpty(coupons)
            || coupons.size() != kafkaMessage.getIds().size()){
            log.error("Can not find right Coupon INfo: {}",
                    JSON.toJSONString(kafkaMessage));
            return;
        }

        coupons.forEach(c -> c.setStatus(status));
        log.info("CouponKafkaMessage Op Coupon Count: {}",
                couponDao.saveAll(coupons).size());
    }
}
