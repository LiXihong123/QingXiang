package com.qingxiang.service.impl;

import com.qingxiang.dto.Result;
import com.qingxiang.dto.UserDTO;
import com.qingxiang.entity.Follow;
import com.qingxiang.mapper.FollowMapper;
import com.qingxiang.service.IFollowService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.qingxiang.utils.UserHolder;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.util.Set;

/**
 * <p>关注服务实现 — Redis Set 关注关系 + DB 持久化</p>
 *
 * <h3>大厂面试亮点：Redis 数据结构选型</h3>
 * <ul>
 *   <li><b>为什么用 Set？</b> 关注/粉丝关系是典型的"集合"模型：无序、不重复。
 *       Redis Set 的 SADD/SREM/SISMEMBER 都是 O(1)，天然适合。</li>
 *   <li><b>共同关注：</b> 使用 {@code SINTER} 求两个 Set 的交集。
 *       如果关注数很大（如微博大 V 关注数千人），可以用 Bloom Filter 做前置过滤。</li>
 *   <li><b>DB 双写：</b> Redis Set 用于快速读写（C 端接口），MySQL 用于持久化和数据分析（B 端）。</li>
 * </ul>
 *
 * @author 李锡宏
 * @since 2025-12-22
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class FollowServiceImpl extends ServiceImpl<FollowMapper, Follow> implements IFollowService {

    private static final String FOLLOW_KEY = "follow:";

    private final StringRedisTemplate stringRedisTemplate;

    @Override
    public Result follow(Long followUserId) {
        Long userId = UserHolder.getUser().getId();

        // 不能关注自己
        if (userId.equals(followUserId)) {
            return Result.fail(com.qingxiang.enums.ErrorCode.PARAM_INVALID, "不能关注自己");
        }

        String key = FOLLOW_KEY + userId;

        // SADD 是幂等操作，重复关注不会出错
        // 大厂亮点：Redis Set 的 SADD 命令原子性保证并发安全
        stringRedisTemplate.opsForSet().add(key, followUserId.toString());

        // 异步双写 DB（简化处理：直接同步写）
        Follow follow = new Follow();
        follow.setUserId(userId);
        follow.setFollowUserId(followUserId);
        save(follow);

        return Result.ok();
    }

    @Override
    public Result unfollow(Long followUserId) {
        Long userId = UserHolder.getUser().getId();

        String key = FOLLOW_KEY + userId;
        stringRedisTemplate.opsForSet().remove(key, followUserId.toString());

        // 删除 DB 中的记录
        remove(query().eq("user_id", userId).eq("follow_user_id", followUserId));

        return Result.ok();
    }

    @Override
    public Result isFollowed(Long followUserId) {
        Long userId = UserHolder.getUser().getId();
        String key = FOLLOW_KEY + userId;

        // SISMEMBER 时间复杂度 O(1)，判断 member 是否在 Set 中
        Boolean isFollowed = stringRedisTemplate.opsForSet().isMember(key, followUserId.toString());
        return Result.ok(Boolean.TRUE.equals(isFollowed));
    }

    @Override
    public Result commonFollows(Long targetUserId) {
        Long userId = UserHolder.getUser().getId();

        String myKey = FOLLOW_KEY + userId;
        String targetKey = FOLLOW_KEY + targetUserId;

        /*
         * SINTER 求交集：取"我关注的"和"他关注的"的交集，即共同关注
         *
         * 大厂亮点：这种查询如果用 MySQL，需要：
         *   SELECT follow_user_id FROM tb_follow WHERE user_id = A
         *   INTERSECT
         *   SELECT follow_user_id FROM tb_follow WHERE user_id = B
         * 而 Redis SINTER 一条命令完成，性能碾压 SQL 方案。
         */
        Set<String> common = stringRedisTemplate.opsForSet().intersect(myKey, targetKey);

        return Result.ok(common);
    }
}
