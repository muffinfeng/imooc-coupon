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
import org.apache.commons.collections4.MapUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.tuple.Pair;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.*;
import java.util.function.Function;
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

    /**
     * 用户领取优惠券
     * 1.从 TemplateClient 拿到对应的优惠券， 并检查是否过期
     * 2.根据 limitation 判断用户是否可以领取
     * 3.save to db
     * 4.填充 CouponTemplateSDK
     * 5.save to cache
     * @param request
     * @return
     * @throws CouponException
     */
    @Override
    public Coupon acquireTemplate(AcquiredTemplateRequest request) throws CouponException {

        //这是根据request里的couponTemplateSDK里的id获取到的一个couponTemplateSDK,因为request里只有id
        Map<Integer,CouponTemplateSDK> id2Template =
                templateClient.findIds2TemplateSDK(
                        Collections.singletonList(
                                request.getTemplateSDK().getId()
                        )
                ).getData();

        if(id2Template.size() <= 0){
            log.error("Cant Not Acquire Template From TemplateClient : {}",
                    request.getTemplateSDK().getId());
            throw new CouponException("Cant Acquire TEmplate From TemplateClient");
        }

        //用户是否可以领取这张优惠券
        List<Coupon> userUsableCoupons = findCouponByStatus(
                request.getUserId(), CouponStatus.USABLE.getCode()
        );
        Map<Integer,List<Coupon>> templateId2Coupons = userUsableCoupons
                .stream().collect(Collectors.groupingBy(Coupon::getId));

        if(templateId2Coupons.containsKey(request.getTemplateSDK().getId())
            && templateId2Coupons.get(request.getTemplateSDK().getId()).size() >=
        request.getTemplateSDK().getRule().getLimitation()){

            log.error("Exceed Template Assign Limitation : {}",
                    request.getTemplateSDK().getId());
            throw new CouponException("Exceed Template Assign Limitation : ");
        }

        //尝试去获取优惠券码
        String couponCode = redisService.tryToAcquireCouponCodeFromCache(
                request.getTemplateSDK().getId()
        );
        if(StringUtils.isEmpty(couponCode)){
            log.error("Can not Acquire Coupon Code: {}",
                    request.getTemplateSDK().getId());
            throw new CouponException("can not Acquire Coupon Code");
        }

        Coupon newCoupon = new Coupon(
                request.getTemplateSDK().getId(), request.getUserId(),
                couponCode,CouponStatus.USABLE
        );
        //返回的对象有id
        newCoupon = couponDao.save(newCoupon);

        // 填充 Coupon 对象的 CouponTemplateSDK， 一定要在放入缓存前去填充
        newCoupon.setTemplateSDK(request.getTemplateSDK());

        //放入缓存中
        redisService.addCouponToCache(
                request.getUserId(),
                Collections.singletonList(newCoupon),
                CouponStatus.USABLE.getCode()
        );


        return newCoupon;

    }

    /**
     * 结算（核销）优惠卷
     * 这里需要注意, 规则相关处理需要由 Settlement 系统去做, 当前系统仅仅做
     * 业务处理过程(校验过程)
     * @param info
     * @return
     * @throws CouponException
     */
    @Override
    public SettlementInfo settlement(SettlementInfo info) throws CouponException {

        //当没有传递优惠券时，直接返回商品总价
        List<SettlementInfo.CouponAndTemplateInfo> ctInfos =
                info.getCouponAndTemplateInfos();
        if(CollectionUtils.isEmpty(ctInfos)){

            log.info("Empty Coupons For settle.");
            double goodsSum = 0.0;

            for (GoodsInfo gi : info.getGoodsInfos()){
                goodsSum += gi.getPrice() * gi.getCount();
            }

            //没有优惠券也就不存在优惠券的核销，SettlementInfo 其他的字段不需要修改
            info.setCost(retain2Decimals(goodsSum));
        }

        /**
         * 第一步  校验传递的优惠券是否是用户自己的
         */

        List<Coupon> coupons = findCouponByStatus(
                info.getUserId(),CouponStatus.USABLE.getCode()
        );
        Map<Integer,Coupon> id2Coupon = coupons.stream()
                .collect(Collectors.toMap(
                        Coupon::getId, Function.identity()
                ));

        if(MapUtils.isEmpty(id2Coupon) || !CollectionUtils.isSubCollection(
                ctInfos.stream().map(SettlementInfo.CouponAndTemplateInfo::getId)
                .collect(Collectors.toList()), id2Coupon.keySet()
        )){
            log.info("{}", id2Coupon.keySet());
            log.info("{}", ctInfos.stream()
                    .map(SettlementInfo.CouponAndTemplateInfo::getId)
                    .collect(Collectors.toList()));
            log.error("User Coupon Has Some Problem, It Is Not SubCollection" +
                    "Of Coupons!");
            throw new CouponException("User Coupon Has Some Problem, " +
                    "It Is Not SubCollection Of Coupons!");
        }
        //来到这里的话，就是 传递的优惠券是是用户自己的
        log.debug("Current Settlement Coupons Is User's: {}", ctInfos.size());

        /**
         * 第二步  利用id的list 获取 coupons 的 list
         */
        List<Coupon> settleCoupons = new ArrayList<>(ctInfos.size());
        ctInfos.forEach(ct -> settleCoupons.add(id2Coupon.get(ct.getId())));

        // 通过结算服务获取结算信息
        SettlementInfo processedInfo =
                settlementClient.computeRule(info).getData();

        /**
         * 第三步  做核销或者优惠券没有出错，出错的话约定getCouponAndTemplateInfos为空
         */
        if(processedInfo.getEmploy() && CollectionUtils.isNotEmpty(
                processedInfo.getCouponAndTemplateInfos()
        )){
            log.info("Settle User Coupon: {}, {}", info.getUserId(),
                    JSON.toJSONString(settleCoupons));
            // 更新缓存
            redisService.addCouponToCache(
                    info.getUserId(),
                    settleCoupons,
                    CouponStatus.USED.getCode()
            );

            //更新db
            kafkaTemplate.send(
                    Constant.TOPIC,
                    JSON.toJSONString(new CouponKafkaMessage(
                            CouponStatus.USED.getCode(),
                            settleCoupons.stream().map(Coupon::getId).
                                    collect(Collectors.toList())
                    ))
            );
        }

        return processedInfo;
    }

    //保留两位小数
    private double retain2Decimals(double value){
        return new BigDecimal(value)
                .setScale(2,BigDecimal.ROUND_HALF_UP)
                .doubleValue();
    }

}
