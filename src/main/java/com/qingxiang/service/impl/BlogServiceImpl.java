package com.qingxiang.service.impl;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.qingxiang.dto.Result;
import com.qingxiang.dto.ScrollResult;
import com.qingxiang.dto.UserDTO;
import com.qingxiang.entity.Blog;
import com.qingxiang.entity.User;
import com.qingxiang.mapper.BlogMapper;
import com.qingxiang.service.IBlogService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.qingxiang.service.IUserService;
import com.qingxiang.utils.RedisConstants;
import com.qingxiang.utils.SystemConstants;
import com.qingxiang.utils.UserHolder;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ZSetOperations;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static com.qingxiang.utils.RedisConstants.BLOG_LIKED_KEY;

/**
 * <p>
 *  博客服务实现类 — Redis Sorted Set 点赞 + 批量查询优化
 * </p>
 *
 * <h3>优化亮点（面试重点）</h3>
 * <ul>
 *   <li><b>Redis Sorted Set 点赞：</b>
 *       替换原来 DB 直接 {@code liked = liked + 1} 的粗糙实现。
 *       ZSet 的 member=userId, score=timestamp，天然支持：
 *       <ol>
 *         <li>去重（ZADD 幂等，ZSCORE 判重）</li>
 *         <li>排行榜（ZREVRANGE 按时间排序）</li>
 *         <li>原子性（Redis 单线程执行）</li>
 *       </ol>
 *   </li>
 *   <li><b>批量查询优化：</b>
 *       queryHotBlog 原实现是 N+1 查询（每条博客查一次用户），
 *       现改为先收集所有 userId → 批量 selectBatchIds → Map 映射。
 *       N 条博客从 N+1 次查询降为 2 次查询。
 *   </li>
 * </ul>
 *
 * @author 李锡宏
 * @since 2025-12-22
 */
@Slf4j
@Service
public class BlogServiceImpl extends ServiceImpl<BlogMapper, Blog> implements IBlogService {

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    @Resource
    private IUserService userService;

    @Override
    public Result saveBlog(Blog blog) {
        // 从 ThreadLocal 获取当前登录用户，设置博客作者
        UserDTO user = UserHolder.getUser();
        blog.setUserId(user.getId());
        // 保存到数据库
        save(blog);
        return Result.ok(blog.getId());
    }

    @Override
    public Result likeBlog(Long blogId) {
        Long userId = UserHolder.getUser().getId();
        String likedKey = BLOG_LIKED_KEY + blogId;

        /*
         * Redis Sorted Set 点赞机制（大厂面试亮点）：
         *
         * 数据结构：ZSet → Member=userId, Score=点赞时间戳(毫秒)
         *
         * 为什么用 ZSet 而不是 Set？
         * - Set 只能去重，无法排序
         * - ZSet 可以按 Score（时间戳）排序，实现"最早点赞的前 N 个用户"排行
         *
         * 为什么用 ZSCORE 而不是 SISMEMBER？
         * - ZSet 没有 SISMEMBER 命令
         * - ZSCORE 返回 Score 值（非 null 表示存在），ZADD 可以覆盖更新 Score
         */
        Double score = stringRedisTemplate.opsForZSet().score(likedKey, userId.toString());

        if (score != null) {
            // 已经点过赞 → 取消点赞
            stringRedisTemplate.opsForZSet().remove(likedKey, userId.toString());
            // 同步更新数据库中的 liked 计数（异步更优，此处简化处理）
            update().setSql("liked = liked - 1").eq("id", blogId).update();
            return Result.ok("取消点赞");
        }

        // 未点赞 → 点赞
        // ZADD key score member（score 用当前时间戳，实现按时间排序）
        stringRedisTemplate.opsForZSet().add(likedKey, userId.toString(), System.currentTimeMillis());
        // 同步更新数据库计数
        update().setSql("liked = liked + 1").eq("id", blogId).update();

        return Result.ok("点赞成功");
    }

    @Override
    public Result queryMyBlog(Integer current) {
        UserDTO user = UserHolder.getUser();
        // 分页查询当前用户的博客
        Page<Blog> page = query()
                .eq("user_id", user.getId())
                .orderByDesc("create_time")
                .page(new Page<>(current, SystemConstants.MAX_PAGE_SIZE));
        return Result.ok(page.getRecords());
    }

    @Override
    public Result queryHotBlog(Integer current) {
        // 1. 分页查询热门博客（按点赞数降序）
        Page<Blog> page = query()
                .orderByDesc("liked")
                .page(new Page<>(current, SystemConstants.MAX_PAGE_SIZE));
        List<Blog> records = page.getRecords();

        // 2. 批量查询用户，解决 N+1 问题
        // 原实现：records.forEach(blog -> blog.setName(userService.getById(blog.getUserId()).getNickName()))
        // 问题：每条博客触发一次 DB 查询，N 条博客 = N+1 次查询
        // 优化：一次批量查所有用户 → Map<id, User> → 内存填充
        if (!records.isEmpty()) {
            List<Long> userIds = records.stream()
                    .map(Blog::getUserId)
                    .distinct()
                    .collect(Collectors.toList());
            // 批量查询用户
            List<User> users = userService.listByIds(userIds);
            // 构建 userId → User 映射
            java.util.Map<Long, User> userMap = users.stream()
                    .collect(Collectors.toMap(User::getId, u -> u));
            // 填充博客的作者信息
            for (Blog blog : records) {
                User user = userMap.get(blog.getUserId());
                if (user != null) {
                    blog.setName(user.getNickName());
                    blog.setIcon(user.getIcon());
                }
            }
        }

        return Result.ok(records);
    }

    @Override
    public Result queryBlogById(Long id) {
        Blog blog = getById(id);
        if (blog == null) {
            return Result.fail(com.qingxiang.enums.ErrorCode.NOT_FOUND);
        }
        // 填充作者信息
        User user = userService.getById(blog.getUserId());
        if (user != null) {
            blog.setName(user.getNickName());
            blog.setIcon(user.getIcon());
        }
        // 查询当前用户是否已点赞（未登录用户为 false）
        UserDTO currentUser = UserHolder.getUser();
        if (currentUser != null) {
            Double score = stringRedisTemplate.opsForZSet()
                    .score(BLOG_LIKED_KEY + id, currentUser.getId().toString());
            blog.setIsLike(score != null);
        }
        return Result.ok(blog);
    }

    @Override
    public Result queryBlogLikes(Long blogId) {
        // Redis ZSet ZRANGE 查询点赞用户 Top 5（按时间倒序）
        Set<String> topLikes = stringRedisTemplate.opsForZSet()
                .range(BLOG_LIKED_KEY + blogId, 0, 4);
        return Result.ok(topLikes != null ? topLikes : java.util.Collections.emptySet());
    }

    @Override
    public Result queryUserBlogs(Long userId, Integer current) {
        Page<Blog> page = query()
                .eq("user_id", userId)
                .orderByDesc("create_time")
                .page(new Page<>(current, SystemConstants.MAX_PAGE_SIZE));
        return Result.ok(page.getRecords());
    }

    @Override
    public Result queryFollowFeed(Long lastId, Integer offset) {
        Long userId = UserHolder.getUser().getId();
        // 从 Redis Set 获取关注列表
        Set<String> followSet = stringRedisTemplate.opsForSet()
                .members("follow:" + userId);
        if (followSet == null || followSet.isEmpty()) {
            return Result.ok(java.util.Collections.emptyList());
        }
        // 查询关注用户的博客（按时间降序，滚动分页）
        List<Long> followIds = followSet.stream().map(Long::valueOf).collect(Collectors.toList());
        int pageSize = SystemConstants.MAX_PAGE_SIZE;
        Page<Blog> page = query()
                .in("user_id", followIds)
                .orderByDesc("create_time")
                .page(new Page<>(offset / pageSize + 1, pageSize));
        // 返回 ScrollResult 格式，前端据此做滚动分页
        List<Blog> records = page.getRecords();
        ScrollResult scrollResult = new ScrollResult();
        scrollResult.setList(records);
        scrollResult.setMinTime(records.isEmpty() ? 0 : records.get(records.size() - 1).getCreateTime().toEpochSecond(java.time.ZoneOffset.UTC));
        scrollResult.setOffset(offset + records.size());
        return Result.ok(scrollResult);
    }
}
