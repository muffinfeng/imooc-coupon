package com.imooc.coupon.service.impl;

import com.imooc.coupon.dao.CouponTemplateDao;
import com.imooc.coupon.entity.CouponTemplate;
import com.imooc.coupon.exception.CouponException;
import com.imooc.coupon.service.IAsyncService;
import com.imooc.coupon.service.IBuildTemplateService;
import com.imooc.coupon.vo.TemplateRequest;
import com.imooc.coupon.vo.TemplateRule;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j
@Service
public class BuildTemplateServiceImpl implements IBuildTemplateService {

    private final IAsyncService asyncService;

    private final CouponTemplateDao couponTemplateDao;

    public BuildTemplateServiceImpl(IAsyncService asyncService, CouponTemplateDao couponTemplateDao) {
        this.asyncService = asyncService;
        this.couponTemplateDao = couponTemplateDao;
    }

    //创建优惠券模板
    //TemplateRequest 模板信息请求对象
    //CouponTemplate  优惠券模板实体
    @Override
    public CouponTemplate buildTemplate(TemplateRequest request) throws CouponException {


        // 参数合法性校验
        if( !request.validate()){
            throw new CouponException("BuildTemplate Param is not valid");
        }

        if(null != couponTemplateDao.findByName(request.getName())){
            throw new CouponException("Exist Same Name Template");
        }

        //构造 CouponTemplate 并保存到数据库中
        CouponTemplate template = requestToTemplate(request);

        template = couponTemplateDao.save(template);

        //根据优惠券模板异步生成优惠券码
        asyncService.asyncConstructCouponByTemplate(template);

        return template;
    }

    //将 TemplateRequest 转换为 CouponTemplate
    private CouponTemplate requestToTemplate(TemplateRequest request){

        return new CouponTemplate(
                request.getName(),
                request.getLogo(),
                request.getDesc(),
                request.getCategory(),
                request.getProductLine(),
                request.getCount(),
                request.getUserId(),
                request.getTarget(),
                request.getRule()
        );
    }
}
