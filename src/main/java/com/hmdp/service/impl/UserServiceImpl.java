package com.hmdp.service.impl;

import cn.hutool.core.bean.BeanUtil;
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
import org.springframework.stereotype.Service;

import javax.servlet.http.HttpSession;


@Slf4j
@Service
public class UserServiceImpl extends ServiceImpl<UserMapper, User> implements IUserService {

    /**
     * 发送短信验证码
     *
     * @param phone   手机号
     * @param session 存储验证码到session中
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

        // 3.存储验证码到session中
        session.setAttribute("code", code);

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
        String tempCode = (String) session.getAttribute("code"); //从session取出的验证码
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

        // 5.查到了，保存用户到session中
        session.setAttribute("user", BeanUtil.copyProperties(user,UserDTO.class));

        return Result.ok();
    }

    /**
     * 通过手机号码创建用户
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
