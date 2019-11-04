package com.imooc.coupon.entity;

//优惠券(用户领取的优惠券记录)实体表

import com.imooc.coupon.constant.CouponStatus;
import com.imooc.coupon.vo.CouponTemplateSDK;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import javax.persistence.*;
import java.util.Date;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Entity
@EntityListeners(AuditingEntityListener.class)
@Table(name = "coupon")
public class Coupon {

    //自增主键
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", nullable = false)
    private Integer id;

    //关联优惠券模板的主键(逻辑外键)
    @Column(name = "template_id",nullable = false)
    private Integer templateId;

    //领取用户
    @Column(name = "user_id",nullable = false)
    private Long userId;

    //优惠券码
    @Column(name = "coupon_code",nullable = false)
    private String couponCode;

    //领取时间
    @CreatedDate
    @Column(name = "assign_time",nullable = false)
    private Date assignTime;

    //优惠券状态
    //basic是默认的，意思是这个字段属于coupon表,和Transient忽略这个字段相反
    @Basic
    @Column(name = "status", nullable = false)
    private CouponStatus status;

    //用户优惠券对应的模板信息
    @Transient
    private CouponTemplateSDK templateSDK;

    //返回一个无效的 Coupon 对象
    public static Coupon invalidCoupon(){
        Coupon coupon = new Coupon();
        coupon.setId(-1);
        return coupon;
    }

    //构造优惠券
    public Coupon(Integer templateId, Long userId,String CouponCode,
                  CouponStatus status){

        this.templateId = templateId;
        this.userId = userId;
        this.couponCode = CouponCode;
        this.status = status;
    }
}