package com.hmdp.service.impl;

import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONUtil;
import com.hmdp.dto.Result;
import com.hmdp.entity.ShopType;
import com.hmdp.mapper.ShopTypeMapper;
import com.hmdp.service.IShopTypeService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;

import java.util.List;

import static com.hmdp.utils.RedisConstants.CACHE_SHOP_KEY;


@Service
public class ShopTypeServiceImpl extends ServiceImpl<ShopTypeMapper, ShopType> implements IShopTypeService {

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    @Override
    public Result queryTypeList() {
        // 1.从redis中查询店铺类型的缓存
        String shopTypeJson  = stringRedisTemplate.opsForValue().get(CACHE_SHOP_KEY);
        // 2.判断是否命中
        if (StrUtil.isNotBlank(shopTypeJson)){
            // 命中则直接返回 --> 将json转为字符串
            List<ShopType> shopTypes = JSONUtil.toList(shopTypeJson, ShopType.class);
            return Result.ok(shopTypes);
        }

        // 3.未命中 ———》查询数据库
        List<ShopType> shopTypes = query().orderByAsc("sort").list();

        // 4.判断店铺类型是否存在
        if (shopTypes == null){
            return Result.fail("店铺类型不存在");
        }

        // 5.店铺类型存在，将店铺类型信息写入redis中
        stringRedisTemplate.opsForValue().set(CACHE_SHOP_KEY,JSONUtil.toJsonStr(shopTypes));

        return Result.ok(shopTypes);
    }
}
