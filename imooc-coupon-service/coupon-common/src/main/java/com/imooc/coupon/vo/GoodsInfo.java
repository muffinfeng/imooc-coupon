package com.imooc.coupon.vo;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

//fake 商品信息
@Data
@NoArgsConstructor
@AllArgsConstructor
public class GoodsInfo {

    //商品类型 -> GoodsType
    private Integer type;

    //商品价格
    private Double price;

    //商品数量
    private Integer count;
}
