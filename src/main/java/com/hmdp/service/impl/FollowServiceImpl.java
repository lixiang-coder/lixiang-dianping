package com.hmdp.service.impl;

import cn.hutool.core.bean.BeanUtil;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.conditions.query.QueryChainWrapper;
import com.hmdp.dto.Result;
import com.hmdp.dto.UserDTO;
import com.hmdp.entity.Follow;
import com.hmdp.mapper.FollowMapper;
import com.hmdp.service.IFollowService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.service.IUserService;
import com.hmdp.utils.UserHolder;
import org.aspectj.weaver.ast.Var;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;


@Service
public class FollowServiceImpl extends ServiceImpl<FollowMapper, Follow> implements IFollowService {

    @Resource
    private StringRedisTemplate stringRedisTemplate;
    @Resource
    private IUserService userService;


    /**
     * 关注，取关操作
     *
     * @param followId
     * @param isfollow
     * @return
     */
    @Override
    public Result follow(Long followId, boolean isfollow) {
        Long userId = UserHolder.getUser().getId();
        String key = "follows:" + userId;
        // 1.判断一下这个是关注还是取关
        if (isfollow) {
            // 2.关注，数据库中新增数据
            Follow follow = new Follow();
            follow.setUserId(userId);
            follow.setFollowUserId(followId);
            boolean isSuccess = save(follow);
            if (isSuccess) {
                //保存成功，将关注数据同样保存进redis set集合
                stringRedisTemplate.opsForSet().add(key,followId.toString());
            }
        } else {
            // 3.取关，数据库中删除数据,sql:delete from tb_follow where user_id = ? and follow_user_id = ?
            boolean isSuccess = remove(new QueryWrapper<Follow>().eq("user_id", userId)
                    .eq("follow_user_id", followId));
            if (isSuccess){
                //删除成功,将redis种的数据删除
                stringRedisTemplate.opsForZSet().remove(key,followId.toString());
            }
        }
        return Result.ok();
    }

    /**
     * 判断是否关注
     *
     * @param followId
     * @return
     */
    @Override
    public Result isFollow(Long followId) {
        // 1.获取用户的id
        Long userId = UserHolder.getUser().getId();
        // 2.在数据库中查询是否关注 select count(*) from tb_follow where user_id = ? and follow_user_id = ?
        Integer count = query().eq("user_id", userId).eq("follow_user_id", followId).count();
        // 3.判断
        return Result.ok(count > 0);
    }

    /**
     * 根据用户id查询共同关注
     * @param id
     * @return
     */
    @Override
    public Result followCommons(Long id) {
        // 1.获取当前用户
        Long userId = UserHolder.getUser().getId();
        String key = "follows:" + userId;
        // 2.求交集
        String key2 = "follows:" + id;
        Set<String> intersect = stringRedisTemplate.opsForSet().intersect(key, key2);
        if (intersect == null || intersect.isEmpty()) {
            // 无交集
            return Result.ok(Collections.emptyList());
        }
        // 3.解析id集合
        List<Long> ids = intersect.stream().map(Long::valueOf).collect(Collectors.toList());
        // 4.查询用户
        List<UserDTO> users = userService.listByIds(ids)
                .stream()
                .map(user -> BeanUtil.copyProperties(user, UserDTO.class))
                .collect(Collectors.toList());
        return Result.ok(users);
    }
}
