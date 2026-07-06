package com.qingxiang.service;

import com.qingxiang.dto.Result;
import com.qingxiang.entity.Blog;
import com.baomidou.mybatisplus.extension.service.IService;

/**
 * <p>
 *  博客服务接口
 * </p>
 *
 * @author 李锡宏
 * @since 2025-12-22
 */
public interface IBlogService extends IService<Blog> {

    /**
     * 发布探店博客
     * <p>
     * 优化说明：原 Controller 里手动 setUserId + save，现在统一封装在 Service 中。
     */
    Result saveBlog(Blog blog);

    /**
     * 点赞/取消点赞博客（Redis Sorted Set 实现，支持去重和排行）
     * <p>
     * 大厂亮点：用 Redis ZSet 替代 DB 直接 liked+1，解决：
     * <ul>
     *   <li>去重：同一个用户只能点一次赞</li>
     *   <li>排行：ZREVRANGE 可以查最早点赞的前 N 个用户</li>
     *   <li>高性能：Redis 单线程原子操作，无锁无并发问题</li>
     * </ul>
     */
    Result likeBlog(Long blogId);

    /**
     * 查询当前用户发布的博客（分页）
     */
    Result queryMyBlog(Integer current);

    /**
     * 查询热门博客（按点赞数降序，分页）
     * <p>
     * 优化说明：原 Controller 内联 N+1 查用户（每条博客查一次 userService.getById），
     * 现在 Service 层批量查用户后填充。
     */
    Result queryHotBlog(Integer current);

    /**
     * 根据 ID 查询博客详情（含作者信息）
     */
    Result queryBlogById(Long id);

    /**
     * 查询点赞用户列表（Redis ZSet ZRANGE）
     */
    Result queryBlogLikes(Long blogId);

    /**
     * 查询指定用户的博客列表（分页）
     */
    Result queryUserBlogs(Long userId, Integer current);

    /**
     * 查询关注用户的博客 Feed 流（滚动分页）
     */
    Result queryFollowFeed(Long lastId, Integer offset);
}
