package com.hmdp.config;

import com.hmdp.utils.LoginInterceptor;
import com.hmdp.utils.RefreshTokenInterceptor;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import javax.annotation.Resource;

@Configuration
public class MvcConfig implements WebMvcConfigurer {

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        //token刷新的拦截器   拦截所有请求    先执行
        registry.addInterceptor(new RefreshTokenInterceptor(stringRedisTemplate)).addPathPatterns("/**").order(0);

        //登录拦截器     拦截部分请求    后执行
        registry.addInterceptor(new LoginInterceptor())
                .excludePathPatterns(
                        //不需要登录的相关资源放行
                        "/user/code",//验证码发送
                        "/user/login",//登录验证
                        "/user/me",//登录验证
                        "/blog/hot",//热点博客
                        "/shop/**",//店铺
                        "/shop-type/**",//店铺类型
                        "/upload/**",//上传资源 方便测试
                        "/voucher/**"//优惠卷信息查询
                ).order(1);

    }
}
