package com.hmdp.service.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.bean.copier.CopyOptions;
import cn.hutool.core.lang.UUID;
import cn.hutool.core.util.RandomUtil;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.dto.LoginFormDTO;
import com.hmdp.dto.Result;
import com.hmdp.dto.UserDTO;
import com.hmdp.entity.User;
import com.hmdp.mapper.UserMapper;
import com.hmdp.service.IUserService;
import com.hmdp.utils.RegexUtils;
import com.hmdp.utils.SystemConstants;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cglib.beans.BeanMap;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import javax.servlet.http.HttpSession;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import static com.hmdp.utils.RedisConstants.*;


@Slf4j
@Service
public class UserServiceImpl extends ServiceImpl<UserMapper, User> implements IUserService {

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    /**
     * 发送短信验证码
     *
     * @param phone   手机号
     * @param session 存储验证码到reids中
     * @return 是否成功
     */
    @Override
    public Result SendCode(String phone, HttpSession session) {
        // 1.验证手机号是否有效
        if (RegexUtils.isPhoneInvalid(phone)) {
            // 无效直接返回错误信息
            return Result.fail("手机号码格式错误");
        }

        // 2.生成验证码
        String code = RandomUtil.randomString(6);

        // 3.存储验证码到reids中
        //session.setAttribute("code", code);
        stringRedisTemplate.opsForValue().set(LOGIN_CODE_KEY + phone, code, LOGIN_CODE_TTL, TimeUnit.MINUTES);

        // 4.发送验证码
        log.debug("发送验证码成功，验证码：{}", code);

        // 返回ok
        return Result.ok();
    }

    /**
     * 登录功能
     *
     * @param loginForm 提交的手机号码，验证码，密码
     * @param session
     * @return
     */
    @Override
    public Result login(LoginFormDTO loginForm, HttpSession session) {
        String phone = loginForm.getPhone();

        // 1.再校验一下手机号
        if (RegexUtils.isPhoneInvalid(phone)) {
            // 无效直接返回错误信息
            return Result.fail("手机号码格式错误");
        }

        // 2.校验验证码
        String code = loginForm.getCode();  //用户输入的验证码
        //String tempCode = (String) session.getAttribute("code"); //从session取出的验证码
        String tempCode = stringRedisTemplate.opsForValue().get(LOGIN_CODE_KEY + phone);    //从reidis中获取验证码
        if (code == null || !code.equals(tempCode)) {
            // 验证码无效
            return Result.fail("验证码无效");
        }

        // 3.根据手机号查询用户 select * from tb_user where phone = ?
        User user = query().eq("phone", phone).one();

        if (user == null) {
            // 4.数据库中未查到这条信息，则创建一个新用户，保存用户到session中
            user = creatUserWithPhone(phone);
        }

        UserDTO userDTO = BeanUtil.copyProperties(user, UserDTO.class);
        // 保存用户信息到 redis中
        // 随机生成 token，作为登录令牌
        String token = UUID.randomUUID().toString();
        //Map<String, Object> userMap = BeanUtil.beanToMap(userDTO);    //会造成Long类型的id转String报：ClassCastException
        Map<String, Object> userMap = BeanUtil.beanToMap(userDTO, new HashMap<>(),
                CopyOptions.create()
                        .setIgnoreNullValue(true)
                        .setFieldValueEditor((fieldName, fieldValue) -> fieldValue.toString()));

        // 5.保存用户到redis中
        stringRedisTemplate.opsForHash().putAll(LOGIN_USER_KEY + token, userMap);
        //设置 token 有效时间 10分钟
        stringRedisTemplate.expire(LOGIN_USER_KEY + token, LOGIN_USER_TTL, TimeUnit.SECONDS);


        return Result.ok();
    }

    /**
     * 通过手机号码创建用户
     *
     * @param phone
     * @return
     */
    private User creatUserWithPhone(String phone) {
        User user = new User();
        user.setPhone(phone);
        user.setNickName(SystemConstants.USER_NICK_NAME_PREFIX + RandomUtil.randomString(5));
        //保存用户到数据库中
        save(user);
        return user;
    }
}
