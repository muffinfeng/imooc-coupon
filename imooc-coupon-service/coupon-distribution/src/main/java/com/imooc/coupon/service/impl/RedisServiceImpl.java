package com.imooc.coupon.service.impl;

//Redis 相关的操作服务接口实现

import com.alibaba.fastjson.JSON;
import com.imooc.coupon.constant.Constant;
import com.imooc.coupon.constant.CouponStatus;
import com.imooc.coupon.entity.Coupon;
import com.imooc.coupon.exception.CouponException;
import com.imooc.coupon.service.IRedisService;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.RandomUtils;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.core.RedisOperations;
import org.springframework.data.redis.core.SessionCallback;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;



import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

@Slf4j
@Service
public class RedisServiceImpl implements IRedisService {

    private final StringRedisTemplate redisTemplate;

    @Autowired
    public RedisServiceImpl(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
    }


    //用户优惠券缓存信息
    //KV
    //K：status + userId -> redisKey
    //V: {coupon_id: 序列化的 Coupon}


    //优惠券模板缓存信息
    //KV
    //K : templateId
    //V : Set<String> 里面是couponCodesSet<String> 里面是couponCodes
    
    /** 方法一
     * <h2>根据 userId 和状态找到缓存的优惠券列表数据</h2> 优惠券列表tag页面
     * @param userId 用户 id
     * @param status 优惠券状态 {@link com.imooc.coupon.constant.CouponStatus}
     * @return {@link Coupon}s, 注意, 可能会返回 null, 代表从没有过记录
     * */
    @Override
    public List<Coupon> getCachedCoupons(Long userId, Integer status) {

        log.info("Get Coupons From Cache:{},{}", userId,status);
        String redisKey = status2RedisKey(status,userId);

        List<String> couponStrs = redisTemplate.opsForHash().values(redisKey)
                .stream()
                .map(o -> Objects.toString(o,null))
                .collect(Collectors.toList());

        //防止内存穿透
        if(CollectionUtils.isEmpty(couponStrs)){
            saveEmptyCouponListToCache(userId,
                    Collections.singletonList(status));
            return Collections.emptyList();
        }

        return couponStrs.stream()
                .map(cs -> JSON.parseObject(cs,Coupon.class))
                .collect(Collectors.toList());
    }

    /**
     * <h2>保存空的优惠券列表到缓存中</h2>
     * @param userId 用户 id
     * @param status 优惠券状态列表
     * */
    @Override
    public void saveEmptyCouponListToCache(Long userId, List<Integer> status) {

        log.info("Save Empty List to Cache for user:{},Status:{}",
                userId, JSON.toJSONString(status));

        //key 是 coupon_id,value 是序列化的Coupon
        Map<String,String> invalidCouponMap = new HashMap<>();
        invalidCouponMap.put("-1",JSON.toJSONString(Coupon.invalidCoupon()));



        //使用 SessionCallBack  把数据命令放入到Redis 的pipeline
        SessionCallback<Object> sessionCallback = new SessionCallback<Object>() {
            @Override
            public  Object execute(RedisOperations redisOperations) throws DataAccessException {
                status.forEach(s -> {
                    String redisKey = status2RedisKey(s,userId);
                    redisOperations.opsForHash().putAll(redisKey,invalidCouponMap);
                });

                return null;
            }
        };

        log.info("Pipeline Exe Result: {}",
                JSON.toJSONString(redisTemplate.executePipelined(sessionCallback)));
    }



    /**  方法二
     * <h2>尝试从 Cache 中获取一个优惠券码</h2>  1.这个要从template模块处获取
     * 2.根据优惠券的领取限制，对比当前用户所拥有的优惠券作出判断
     * @param templateId 优惠券模板主键
     * @return 优惠券码
     * */
    @Override
    public String tryToAcquireCouponCodeFromCache(Integer templateId) {

        //redis 中优惠券模板和优惠券
        //key：     templateId
        //value：   List<String>  (CouponCode的String)
        String redisKey = String.format("%s%s",
                Constant.RedisPrefix.COUPON_TEMPLATE,templateId.toString());
        // 优惠券不存在顺序关系 ，左边pop和右边pop都行
        String couponCode = redisTemplate.opsForList().leftPop(redisKey);

        log.info("Acquire Coupon Code: {} ,{} ,{}",
                templateId,redisKey,couponCode);
        return couponCode;
    }

    /**方法三
     * <h2>将优惠券保存到 Cache 中</h2>
     * @param userId 用户 id
     * @param coupons {@link Coupon}s
     * @param status 优惠券状态
     * @return 保存成功的个数
     * */
    @Override
    public Integer addCouponToCache(Long userId, List<Coupon> coupons,
                     Integer status) throws CouponException {

        log.info("Add Coupon To Cache : {},{} ,{}",
                userId, JSON.toJSONString(coupons),status);

        Integer result = -1;
        CouponStatus couponStatus = CouponStatus.of(status);

        switch (couponStatus){
            case USABLE:
                result = addCouponToCacheForUsable(userId,coupons);
                break;
            case USED:
                result = addCouponToCacheForUsed(userId,coupons);
                break;
            case EXPIRED:
                result = addCouponToCacheForExpired(userId,coupons);
                break;
        }

        return result;
    }

    /**
     * 新增加优惠券到 Cache中  Usable
     * @param userId
     * @param coupons
     * @return
     */
    private Integer addCouponToCacheForUsable(Long userId,List<Coupon> coupons){
        //如果 status是 USABLE ，代表是新增加的优惠券
        //只会影响一个cache：USER_COUPON_USABLE
        log.debug("Add Coupon TO Cache For Usable");

        Map<String,String> needCachedObject = new HashMap<>();
        coupons.forEach(c -> {
            needCachedObject.put(c.getId().toString(),JSON.toJSONString(c));
        });

        String redisKey = status2RedisKey(CouponStatus.USABLE.getCode(),userId);
        redisTemplate.opsForHash().putAll(redisKey,needCachedObject);
        log.info("Add {} Coupons To Cache: {} , {}",
                needCachedObject.size(),userId,redisKey);

        redisTemplate.expire(
                redisKey,
                getRandomExpirationTime(1,2),
                TimeUnit.SECONDS
        );

        return needCachedObject.size();
    }

    /**
     * 新增加优惠券到 Cache中  Used
     * @param userId
     * @param coupons
     * @return
     */
    @SuppressWarnings("all")
    private Integer addCouponToCacheForUsed(
            Long userId,List<Coupon> coupons) throws CouponException{

        // 如果status 是 USED, 代表用户操作是使用当前的优惠券,影响到两个 Cache
        // USABLE， USED

        log.debug("Add Coupon To Cache For Used.");

        /**
         * 第一步  定义两种key 和 定义 USABLE 的 map
         */
        Map<String,String> needCachedForUsed = new HashMap<>(coupons.size());

        String redisKeyForUsable = status2RedisKey(
                CouponStatus.USABLE.getCode(),userId
        );
        String redisKeyForUsad = status2RedisKey(
                CouponStatus.USED.getCode(),userId
        );

        //获取当前用户可用的优惠券
        List<Coupon> curUsableCoupons = getCachedCoupons(
                userId,CouponStatus.USABLE.getCode()
        );
        //这个curUsableCoupons 是 当前用户可用的所有优惠券 加 一个无效的空优惠券
        //参数 coupons 是用户现在准备用的优惠券
        assert curUsableCoupons.size() > coupons.size();

        coupons.forEach(c -> needCachedForUsed.put(
                c.getId().toString(),
                JSON.toJSONString(c)
        ));

        /**
         * 第二步  校验当前的优惠券是否包含在当前用户可用的优惠券里面
         */
        List<Integer> curUsableIds = curUsableCoupons.stream()
                .map(c -> c.getId()).collect(Collectors.toList());

        List<Integer> paramIds = coupons.stream()
                .map(Coupon::getId).collect(Collectors.toList());

        if(!CollectionUtils.isSubCollection(paramIds,curUsableIds)){
            log.error("CurCoupons Is Not Equal ToCache: {}, {} ,{}",
                    userId,JSON.toJSONString(curUsableIds),
                    JSON.toJSONString(paramIds));
            throw new CouponException("CurCoupons Is Not Equal To Cache");
        }

        /**
         * 第三步   操作缓存
         */
        List<String> needCleanKey = paramIds.stream()
                .map(e -> e.toString()).collect(Collectors.toList());
        SessionCallback<Objects> sessionCallback = new SessionCallback<Objects>() {
            @Override
            public  Objects execute(RedisOperations redisOperations) throws DataAccessException {

                //1.已使用的优惠券 Cache 缓存添加
                redisOperations.opsForHash().putAll(redisKeyForUsad,needCachedForUsed);
                //2.可用的优惠券 Cache 需要清理
                redisOperations.opsForHash().delete(
                        redisKeyForUsable,needCleanKey.toArray()
                );
                //3.重置过期时间
                redisOperations.expire(
                        redisKeyForUsable,
                        getRandomExpirationTime(1,2),
                        TimeUnit.SECONDS
                );
                redisOperations.expire(
                        redisKeyForUsad,
                        getRandomExpirationTime(1,2),
                        TimeUnit.SECONDS
                );

                return null;
            }
        };

        log.info("Pipeline Exe Result: {}",
                JSON.toJSONString(
                        redisTemplate.executePipelined(sessionCallback)));
        return coupons.size();
    }

    @SuppressWarnings("all")
    private Integer addCouponToCacheForExpired(Long userId,
                    List<Coupon> coupons) throws CouponException{
        // 如果status 是 Expired, 代表是已过期的优惠券,影响到两个 Cache
        // USABLE， Expired

        log.debug("Add Coupon To Cache For Expired.");

        //需要保存的Cache
        Map<String,String> needCachedForExpired = new HashMap<>(coupons.size());

        String redisKeyForUsable = status2RedisKey(
                CouponStatus.USABLE.getCode(),userId
        );
        String redisKeyForExpired = status2RedisKey(
                CouponStatus.EXPIRED.getCode(),userId
        );

        List<Coupon> curUsableCoupons = getCachedCoupons(
                userId,CouponStatus.USABLE.getCode()
        );


        //当前可用的优惠券个数一定大于1的
        assert curUsableCoupons.size() > coupons.size();

        coupons.forEach(c -> needCachedForExpired.put(
                c.getId().toString(),
                JSON.toJSONString(c)
        ));

        //校验当前的优惠券参数是否与 Cached 中的匹配
        List<Integer> curUsableIds = curUsableCoupons.stream()
                .map(c -> c.getId()).collect(Collectors.toList());
        List<Integer> paramIds = coupons.stream()
                .map(c -> c.getId()).collect(Collectors.toList());
        if(!CollectionUtils.isSubCollection(paramIds,curUsableIds)){
            log.error("CurCoupons Is Not Equal To Cache: {} ,{}",
                    userId, JSON.toJSONString(curUsableIds),
                    JSON.toJSONString(paramIds));
            throw new CouponException("CurCoupon Is Not Equal TO cache");
        }

        List<String> needCleanKey = paramIds.stream()
                .map(i -> i.toString()).collect(Collectors.toList());

        SessionCallback<Objects> sessionCallback = new SessionCallback<Objects>() {
            @Override
            public  Objects execute(RedisOperations redisOperations) throws DataAccessException {

                //1.已过期的优惠券 Cache 缓存
                redisOperations.opsForHash().putAll(
                        redisKeyForExpired,needCachedForExpired
                );
                //2.可用的优惠券 Cache 需要清理
                redisOperations.opsForHash().delete(
                        redisKeyForUsable,needCleanKey.toArray()
                );
                //重置过期时间
                redisOperations.expire(
                        redisKeyForUsable,
                        getRandomExpirationTime(1,2),
                        TimeUnit.SECONDS
                );
                redisOperations.expire(
                        redisKeyForExpired,
                        getRandomExpirationTime(1,2),
                        TimeUnit.SECONDS
                );
                return null;
            }
        };

        log.info("Pipeline Exe Result: {}",
                JSON.toJSONString(
                        redisTemplate.executePipelined(sessionCallback)
                ));
        return coupons.size();
    }


    //根据status 获取到对应的redis Key
    private String status2RedisKey(Integer status,Long userId){
        String redisKey = null;

        CouponStatus couponStatus = CouponStatus.of(status);

        switch (couponStatus){
            case USABLE:
                redisKey = String.format("%s%s",
                        Constant.RedisPrefix.USER_COUPON_USABLE,userId);
                break;
            case USED:
                redisKey = String.format("%s%s",
                        Constant.RedisPrefix.USER_COUPON_USED,userId);
                break;
            case EXPIRED:
                redisKey = String.format("%s%s",
                        Constant.RedisPrefix.USER_COUPON_EXPIRED,userId);
                break;
        }

        return redisKey;
    }




    /**
     * 获取一个随机的过期时间
     * 缓存雪崩： key在同一时间失效
     * @param min 最小的小时数
     * @param max 最大的小时数
     * @return 【min,max】
     */
    private Long getRandomExpirationTime(Integer min,Integer max){
        return RandomUtils.nextLong(
                min * 60 * 60,
                max * 60 * 60
        );
    }
}
