package com.imooc.coupon.service.impl;

import com.imooc.coupon.dao.CouponTemplateDao;
import com.imooc.coupon.entity.CouponTemplate;
import com.imooc.coupon.exception.CouponException;
import com.imooc.coupon.service.ITemplateBaseService;
import com.imooc.coupon.vo.CouponTemplateSDK;



import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

//优惠券模板基础服务接口实现
@Slf4j
@Service
public class TemplateBaseServiceImpl implements ITemplateBaseService {

    @Autowired
    private CouponTemplateDao templateDao;

    //根据优惠券模板id 获取优惠券模板信息
    //id是模板id
    @Override
    public CouponTemplate buildTemplateInfo(Integer id) throws CouponException {

        Optional<CouponTemplate> template = templateDao.findById(id);
        if(!template.isPresent()){
            throw new CouponException("Template Is not exist" + id);
        }

        return template.get();
    }

    //查找所有可用的优惠券模板
    @Override
    public List<CouponTemplateSDK> findAllUsableTemplate() {
        List<CouponTemplate> templates =
                templateDao.findAllByAvailableAndExpired(true,false);

        return templates.stream().map(this::template2TemplateSDK).collect(Collectors.toList());
    }

    //获取模板 ids 到 CouponTemplateSDK 的映射
    //param ids 模板ids
    //return map<key: 模板 id,  value： CouponTemplateSDK>
    @Override
    public Map<Integer, CouponTemplateSDK> findIds2TemplateSDK(Collection<Integer> ids) {

        List<CouponTemplate> templates = templateDao.findAllById(ids);

        return templates.stream().map(this::template2TemplateSDK)
                .collect(Collectors.toMap(
                        CouponTemplateSDK::getId, Function.identity()
                ));

    }




    //将 CouponTemplate 转换为 CouponTemplateSDK
    private CouponTemplateSDK template2TemplateSDK(CouponTemplate template){

        return new CouponTemplateSDK(
                template.getId(),
                template.getName(),
                template.getLogo(),
                template.getDesc(),
                template.getCategory().getCode(),
                template.getProductLine().getCode(),
                template.getKey(),  // 并不是拼装好的 Template Key
                template.getTarget().getCode(),
                template.getRule()
        );
    }
}
