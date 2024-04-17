package com.hmdp.service.impl;

import cn.hutool.core.util.BooleanUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONString;
import cn.hutool.json.JSONUtil;
import com.hmdp.dto.Result;
import com.hmdp.entity.Shop;
import com.hmdp.mapper.ShopMapper;
import com.hmdp.service.IShopService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.utils.RedisData;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;

import java.time.LocalDateTime;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
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
        //Shop shop = queryWithMutex(id);

        //使用逻辑锁来解决缓存击穿问题
        Shop shop = queryWithLogicalExpire(id);

        if (shop == null) {
            return Result.fail("商铺信息不存在");
        }

        return Result.ok(shop);
    }

    // 创建10个线程池
    private static final ExecutorService CACHE_REBUILD_EXECUTOR = Executors.newFixedThreadPool(10);

    private Shop queryWithLogicalExpire(Long id) {
        String key = CACHE_SHOP_KEY + id;
        // 1.从redis查询商铺缓存
        String json = stringRedisTemplate.opsForValue().get(key);
        // 2.判断是否存在
        if (StrUtil.isBlank(json)) {
            // 3.存在，直接返回
            return null;
        }

        // 4.命中，需要先把json反序列化为对象
        RedisData redisData = JSONUtil.toBean(json, RedisData.class);
        Shop shop = JSONUtil.toBean((JSONObject) redisData.getData(), Shop.class);
        LocalDateTime expireTime = redisData.getExpireTime();

        // 5.判断是否过期
        if(expireTime.isAfter(LocalDateTime.now())) {
            // 5.1.未过期，直接返回店铺信息
            return shop;
        }

        // 5.2.已过期，需要缓存重建
        // 6.缓存重建
        // 6.1.获取互斥锁
        String lockKey = LOCK_SHOP_KEY + id;
        boolean isLock = tryLock(lockKey);

        // 6.2.判断是否获取锁成功
        if (isLock){
            CACHE_REBUILD_EXECUTOR.submit( ()->{
                try{
                    //重建缓存
                    this.saveShop2Redis(id,20L);
                }catch (Exception e){
                    throw new RuntimeException(e);
                }finally {
                    unLock(lockKey);
                }
            });
        }

        // 6.4.返回过期的商铺信息
        return shop;
    }


    /**
     * 向redis写入店铺数据并且设置逻辑过期时间
     * @param id
     * @param expireSeconds
     */
    public void saveShop2Redis(Long id, Long expireSeconds) {
        // 1. 查询商铺数据
        Shop shop = getById(id);
        // 2.封装逻辑过期时间
        RedisData redisData = new RedisData();
        redisData.setData(shop);
        redisData.setExpireTime(LocalDateTime.now().plusSeconds(expireSeconds));

        // 3.写入redis中
        stringRedisTemplate.opsForValue().set(CACHE_SHOP_KEY + id, JSONUtil.toJsonStr(redisData));
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
        String lockKey = LOCK_SHOP_KEY + id;
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
