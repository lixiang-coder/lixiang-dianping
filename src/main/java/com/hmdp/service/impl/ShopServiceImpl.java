package com.hmdp.service.impl;

import cn.hutool.core.util.BooleanUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONUtil;
import com.hmdp.dto.Result;
import com.hmdp.entity.Shop;
import com.hmdp.mapper.ShopMapper;
import com.hmdp.service.IShopService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;

import java.util.concurrent.TimeUnit;

import static com.hmdp.utils.RedisConstants.*;


@Service
public class ShopServiceImpl extends ServiceImpl<ShopMapper, Shop> implements IShopService {

    @Resource
    StringRedisTemplate stringRedisTemplate;

    @Override
    public Result queryById(Long id) {
        // 使用缓存穿透
        //Shop shop = queryWithPassThrough(id);

        //使用互斥锁来解决缓存击穿的问题
        Shop shop = queryWithMutex(id);

        if (shop == null) {
            return Result.fail("商铺信息不存在");
        }

        return Result.ok(shop);
    }

    /**
     * 使用互斥锁来解决缓存击穿的问题
     *
     * @param id
     * @return
     */
    private Shop queryWithMutex(Long id) {
        String key = CACHE_SHOP_KEY + id;
        // 1.从redis中查询商铺的缓存
        String shopJson = stringRedisTemplate.opsForValue().get(key);

        // 2.判断是否命中
        if (StrUtil.isNotBlank(shopJson)) {
            // 命中则直接返回 --> 将json转为字符串
            Shop shop = JSONUtil.toBean(shopJson, Shop.class);
            return shop;
        }

        // 判断是否命中为空值
        if (shopJson != null) {
            // 返回一个错误信息
            return null;
        }

        // 3.实现缓存重构 ————》尝试获取互斥锁
        String lockKey = "lock:shop:" + id;
        Shop shop = null;

        try {
            boolean isLock = tryLock(lockKey);
            // 3.1 判断是否获取互斥锁
            if (!isLock) {
                // 3.2 没有获取到互斥锁 休眠50ms时间
                Thread.sleep(50);
                return queryWithMutex(id);  // 睡眠结束后，递归————》一直到获取到锁为止
            }

            // 3.3 获取到互斥锁 根据id查询数据库 ————》将商铺数据写入redis中
            shop = getById(id);

            // 4.判断商铺是否存在
            if (shop == null) {
                // 写入空值，避免redis穿透，设置过期时间 2分钟
                stringRedisTemplate.opsForValue().set(key, "", CACHE_NULL_TTL, TimeUnit.MINUTES);
                return null;
            }

            // 5.商铺存在，将商铺的信息写入redis中,并且设置缓存的过期时间
            stringRedisTemplate.opsForValue().set(key, JSONUtil.toJsonStr(shop), CACHE_SHOP_TTL, TimeUnit.MINUTES);

        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        } finally {
            // 6. 释放互斥锁
            unLock(lockKey);
        }
        return shop;
    }


    /**
     * redis 缓存穿透
     *
     * @param id
     * @return
     */
    private Shop queryWithPassThrough(Long id) {
        String key = CACHE_SHOP_KEY + id;
        // 1.从redis中查询商铺的缓存
        String shopJson = stringRedisTemplate.opsForValue().get(key);

        // 2.判断是否命中
        if (StrUtil.isNotBlank(shopJson)) {
            // 命中则直接返回 --> 将json转为字符串
            Shop shop = JSONUtil.toBean(shopJson, Shop.class);
            return shop;
        }
        // 判断是否命中为空值
        if (shopJson != null) {
            // 返回一个错误信息
            return null;
        }

        // 3.未命中 ———》根据id查询数据库
        Shop shop = getById(id);

        // 4.判断商铺是否存在
        if (shop == null) {
            // 写入空值，避免redis穿透，设置过期时间 2分钟
            stringRedisTemplate.opsForValue().set(key, "", CACHE_NULL_TTL, TimeUnit.MINUTES);
            return null;
        }

        // 5.商铺存在，将商铺的信息写入redis中,并且设置缓存的过期时间
        stringRedisTemplate.opsForValue().set(key, JSONUtil.toJsonStr(shop), CACHE_SHOP_TTL, TimeUnit.MINUTES);
        return shop;
    }


    /**
     * 创建锁
     *
     * @param key
     * @return
     */
    private boolean tryLock(String key) {
        Boolean falg = stringRedisTemplate.opsForValue().setIfAbsent(key, "1", 10, TimeUnit.SECONDS);
        return BooleanUtil.isTrue(falg);
    }

    /**
     * 删除锁
     *
     * @param key
     */
    private void unLock(String key) {
        stringRedisTemplate.delete(key);
    }

    @Override
    public void updata(Shop shop) {
        // 判断商铺是否存在
        Long id = shop.getId();
        if (id == null) {
            Result.fail("商铺id不能为空");
            return;
        }

        // 1.更新数据库
        updateById(shop);
        // 2.删除缓存
        stringRedisTemplate.delete(CACHE_SHOP_KEY + id);

        Result.ok();
    }
}
