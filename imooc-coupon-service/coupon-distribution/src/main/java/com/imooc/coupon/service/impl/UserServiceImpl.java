package com.imooc.coupon.service.impl;

import com.alibaba.fastjson.JSON;
import com.imooc.coupon.constant.Constant;
import com.imooc.coupon.constant.CouponStatus;
import com.imooc.coupon.dao.CouponDao;
import com.imooc.coupon.entity.Coupon;
import com.imooc.coupon.exception.CouponException;
import com.imooc.coupon.feign.SettlementClient;
import com.imooc.coupon.feign.TemplateClient;
import com.imooc.coupon.service.IRedisService;
import com.imooc.coupon.service.IUserService;
import com.imooc.coupon.vo.*;


import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.tuple.Pair;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

/**
 * 用户服务相关的接口实现
 * 所有的操作过程，状态都保存在Redis 中，并通过kafka 把消息传递到MySQL中
 *
 */
@Slf4j
@Service
public class UserServiceImpl implements IUserService {

    //dao
    private final CouponDao couponDao;

    // Redis 服务
    private final IRedisService redisService;

    //模板微服务客户端
    private final TemplateClient templateClient;

    //结算微服务客户端
    private final SettlementClient settlementClient;

    //Kafka 客户端
    private final KafkaTemplate<String,String> kafkaTemplate;

    @Autowired
    public UserServiceImpl(CouponDao couponDao, IRedisService redisService, TemplateClient templateClient, SettlementClient settlementClient, KafkaTemplate<String, String> kafkaTemplate) {
        this.couponDao = couponDao;
        this.redisService = redisService;
        this.templateClient = templateClient;
        this.settlementClient = settlementClient;
        this.kafkaTemplate = kafkaTemplate;
    }

    /**
     * 根据用户id 和 状态查询优惠券记录
     * @param userId
     * @param status
     * @return 返回优惠券的信息
     * @throws CouponException
     */
    @Override
    public List<Coupon> findCouponByStatus(Long userId, Integer status) throws CouponException {

        List<Coupon> curCached = redisService.getCachedCoupons(userId, status);
        List<Coupon> preTarget;

        /**
         * 第一步，从redis中拿数据
         */
        if(CollectionUtils.isNotEmpty(curCached)){
            log.debug("coupon cache is not empty: {} , {}",userId,status);
            preTarget = curCached;
        } else {
            /**
             * 第二步，若是redis中没有，则从db中拿数据
             * 这时则要填充templateSDK字段
             */
            log.debug("coupon cache is empty,get Coupon from db: {}, {}",
                    userId,status);
            List<Coupon> dbcoupons = couponDao.findAllByUserIdAndStatus(
                    userId, CouponStatus.of(status)
            );
            //如果数据库中没有记录，直接返回就可以，Cache 中已经加入了一张无效的优惠券
            if(CollectionUtils.isEmpty(dbcoupons)){
                log.debug("current user do not have coupon: {} ,{}",userId,status);
                return  dbcoupons;
            }

            //填充 dbCoupons 的 templateSDK 字段,因为该字段是transient，数据库
            //里没有,而插入redis的时候需要这个字段
            Map<Integer, CouponTemplateSDK> id2TemplateSDK =
                    templateClient.findIds2TemplateSDK(
                            dbcoupons.stream()
                                .map(Coupon::getTemplateId)
                                .collect(Collectors.toList())
                    ).getData();  //hystrix在map之上包装了一层CommonResponse
            dbcoupons.forEach(dc -> {
                dc.setTemplateSDK(id2TemplateSDK.get(dc.getTemplateId()));
            });
            //数据库中存在记录
            preTarget = dbcoupons;
            //将记录写入Cache
            redisService.addCouponToCache(userId,preTarget, status);
        }

        //将无效优惠券剔除
        preTarget = preTarget.stream()
                .filter(c -> c.getId() != -1)
                .collect(Collectors.toList());

        /**
         * 第三步，
         * 如果当前获取的是可用优惠券,还需要做对已过期优惠券的延迟处理，并 通过kafka 修改db的优惠券状态
         */
        //
        if(CouponStatus.of(status) == CouponStatus.USABLE){
            CouponClassify classify = CouponClassify.classify(preTarget);
            //如果已过期状态的list不为空,需要做延迟处理
            if(CollectionUtils.isNotEmpty(classify.getExpired())){
                log.info("Add Expired Coupons To Cache From FindCouponsByStatus:" +
                        "{},{} ",userId,status);
                redisService.addCouponToCache(
                        userId,classify.getExpired(),
                        CouponStatus.EXPIRED.getCode()
                );
                // 发送到 kafka 中做异步处理,修改优惠券状态
                kafkaTemplate.send(
                        Constant.TOPIC,
                        JSON.toJSONString(new CouponKafkaMessage(
                                CouponStatus.EXPIRED.getCode(),
                                classify.getExpired().stream()
                                .map(Coupon::getId).collect(Collectors.toList())
                        ))
                );
            }

            return classify.getUsable();
        }
        return preTarget;
    }


    /**
     * 根据用户id 查找当前可以领取的优惠券模板
     * @param userId
     * @return
     * @throws CouponException
     */
    @Override
    public List<CouponTemplateSDK> findAvailableTemplate(Long userId) throws CouponException {

        long curTIme = new Date().getTime();
        List<CouponTemplateSDK> templateSDKS =
                templateClient.findAllUsableTemplate().getData();

        log.debug("Find All Template(From TemplateClient) Count: {}",
                templateSDKS.size());

        //过滤过期的优惠券模板
        templateSDKS = templateSDKS.stream().filter(
                t -> t.getRule().getExpiration().getDeadline() > curTIme
        ).collect(Collectors.toList());

        log.info("Find Usable Template Count: {}", templateSDKS.size());

        /** 下面有两个Map 一是由 TemplateId limitation CouponTemplateSDK组成的map
         *  是从db查出来的
         *  二是 根据userId 查出来的该用户的可用的优惠券的map，key是templateId，
         *  value是 该模板的优惠券
         *  **/
        //1.key 是 TemplateId
        //value 中的 left 是Template limitation， right 是优惠券模板
        Map<Integer, Pair<Integer,CouponTemplateSDK>> limit2Template =
                new HashMap<>(templateSDKS.size());
        templateSDKS.forEach(
                t -> limit2Template.put(
                        t.getId(),
                        Pair.of(t.getRule().getLimitation(),t)
                )
        );

        List<CouponTemplateSDK> result = new ArrayList<>(limit2Template.size());

        List<Coupon> userUsableCoupons = findCouponByStatus(
                userId,CouponStatus.USABLE.getCode()
        );

        log.debug("Current User Has Usable Coupons: {} , {}",userId,
                userUsableCoupons.size());

        //2.key 是 TemplateId
        Map<Integer, List<Coupon>> templateId2Coupons = userUsableCoupons
                .stream()
                .collect(Collectors.groupingBy(Coupon::getTemplateId));

        //根据 Template 的Rule 判断是否可以领取优惠券模板
        //遍历可用的优惠券模板map
        limit2Template.forEach((k,v) -> {

            int limitation = v.getLeft();
            CouponTemplateSDK templateSDK = v.getRight();
            //如果该用户已有的可用的优惠券map中包含这个templateid 并且该templateId 的优惠券数量大于等于限制的数量，则返回
            if(templateId2Coupons.containsKey(k)
                && templateId2Coupons.get(k).size() >= limitation){
                return;
            }

            //否则加入到结果集中
            result.add(templateSDK);
        });

        return result;

    }



    @Override
    public Coupon acquireTemplate(AcquiredTemplateRequest request) throws CouponException {
        return null;
    }

    @Override
    public SettlementInfo settlement(SettlementInfo info) throws CouponException {
        return null;
    }
}
