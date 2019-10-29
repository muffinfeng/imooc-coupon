package com.imooc.coupon.schedule;

// 定时清理已过起的优惠券模板

import com.imooc.coupon.dao.CouponTemplateDao;
import com.imooc.coupon.entity.CouponTemplate;
import com.imooc.coupon.vo.TemplateRule;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.CollectionUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;



import java.util.ArrayList;
import java.util.Date;
import java.util.List;

@Slf4j
@Component
public class ScheduledTask {

    // CouponTemplate Dao
    private final CouponTemplateDao couponTemplateDao;

    @Autowired
    public ScheduledTask(CouponTemplateDao couponTemplateDao) {
        this.couponTemplateDao = couponTemplateDao;
    }

    //下线已过期的优惠券模板  每60分钟将数据库中没过期的优惠券模板取出来判断日期
    @Scheduled(fixedRate = 60 * 60 * 1000)
    public void offlineCouponTemplate(){
        log.info("Start To Expire CouponTemplate");

        List<CouponTemplate> templates =
                couponTemplateDao.findAllByExpired(false);
        if(CollectionUtils.isEmpty(templates)){
            log.info("Done to expire CouponTemplate.");
            return;
        }

        Date cur = new Date();
        List<CouponTemplate> expiredTemplates =
                new ArrayList<>(templates.size());

        templates.forEach(t -> {

            // 根据优惠券模板规则中的 “过期规则” 校验模板是否过期
            TemplateRule rule = t.getRule();
            if(rule.getExpiration().getDeadline() < cur.getTime()){
                t.setExpired(true);
                expiredTemplates.add(t);
            }
        });

        if(CollectionUtils.isNotEmpty(expiredTemplates)){
            log.info("Expired CouponTemplate Num: {}",
                    couponTemplateDao.saveAll(expiredTemplates));
        }

        log.info("Done to expire CouponTemplate.");

    }
}
