package com.qingxiang.service;

import com.qingxiang.dto.Result;
import com.qingxiang.entity.Follow;
import com.baomidou.mybatisplus.extension.service.IService;

/**
 * <p>关注服务接口 — Redis Set 实现关注/取关 + 共同关注</p>
 *
 * @author 李锡宏
 * @since 2025-12-22
 */
public interface IFollowService extends IService<Follow> {

    /**
     * 关注用户
     * <p>
     * 大厂亮点：使用 Redis Set 存储关注关系，SADD 原子操作，O(1) 判断是否已关注。
     * DB 持久化保存，Redis 用于快速读写。
     */
    Result follow(Long followUserId);

    /**
     * 取消关注用户
     */
    Result unfollow(Long followUserId);

    /**
     * 判断是否已关注
     */
    Result isFollowed(Long followUserId);

    /**
     * 查询与目标用户的共同关注
     * <p>
     * 大厂亮点：使用 Redis SINTER 求两个 Set 的交集，O(N*M) 但在关注数有限的场景下极快。
     * 避免了 MySQL 的复杂 JOIN 查询。
     */
    Result commonFollows(Long userId);
}
