package com.hmdp.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.conditions.query.QueryChainWrapper;
import com.hmdp.dto.Result;
import com.hmdp.entity.Follow;
import com.hmdp.mapper.FollowMapper;
import com.hmdp.service.IFollowService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.utils.UserHolder;
import org.aspectj.weaver.ast.Var;
import org.springframework.stereotype.Service;


@Service
public class FollowServiceImpl extends ServiceImpl<FollowMapper, Follow> implements IFollowService {

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
        // 1.判断一下这个是关注还是取关
        if (isfollow) {
            // 2.关注，数据库中新增数据
            Follow follow = new Follow();
            follow.setUserId(userId);
            follow.setFollowUserId(followId);
            save(follow);
        } else {
            // 3.取关，数据库中删除数据,sql:delete from tb_follow where user_id = ? and follow_user_id = ?
            remove(new QueryWrapper<Follow>().eq("user_id", userId)
                    .eq("follow_user_id", followId));
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
}
