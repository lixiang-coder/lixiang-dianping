package com.hmdp;

import com.hmdp.service.impl.ShopServiceImpl;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

import javax.annotation.Resource;

@SpringBootTest
class HmDianPingApplicationTests {

    @Resource
    ShopServiceImpl shopService;

    @Test
    void testsaveshop() {
        // 测试：向redis写入店铺数据并且设置逻辑过期时间
        shopService.saveShop2Redis(1L,10L);
    }
}
