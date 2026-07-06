package com.qingxiang.controller;


import com.qingxiang.dto.Result;
import com.qingxiang.entity.Blog;
import com.qingxiang.service.IBlogService;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import org.springframework.web.bind.annotation.*;

import javax.annotation.Resource;

/**
 * <p>
 *  博客控制器
 * </p>
 *
 * <h3>优化说明</h3>
 * <ul>
 *   <li><b>逻辑下沉：</b> 所有业务逻辑（组装 userId、点赞去重、批量查用户）已从 Controller
 *       移至 {@link IBlogService} 实现，Controller 仅做参数接收和结果返回。</li>
 *   <li><b>点赞升级：</b> 从 DB 直接 {@code liked=liked+1} 升级为 Redis Sorted Set，
 *       支持去重 + 按时间排序 + 原子操作。</li>
 *   <li><b>N+1 优化：</b> queryHotBlog 从逐条查用户改为批量查，减少 DB 查询次数。</li>
 * </ul>
 *
 * @author 李锡宏
 * @since 2025-12-22
 */
@RestController
@RequestMapping("/blog")
@Api(tags = "博客模块")
public class BlogController {

    @Resource
    private IBlogService blogService;

    /**
     * 发布探店博客
     */
    @PostMapping
    public Result saveBlog(@RequestBody Blog blog) {
        return blogService.saveBlog(blog);
    }

    /**
     * 点赞/取消点赞（Redis Sorted Set 实现）
     */
    @PutMapping("/like/{id}")
    public Result likeBlog(@PathVariable("id") Long id) {
        return blogService.likeBlog(id);
    }

    /**
     * 查询我的博客（分页）
     */
    @GetMapping("/of/me")
    public Result queryMyBlog(@RequestParam(value = "current", defaultValue = "1") Integer current) {
        return blogService.queryMyBlog(current);
    }

    /**
     * 查询热门博客（按点赞数降序分页，含作者信息批量填充）
     */
    @GetMapping("/hot")
    public Result queryHotBlog(@RequestParam(value = "current", defaultValue = "1") Integer current) {
        return blogService.queryHotBlog(current);
    }

    /**
     * 查询博客详情
     */
    @GetMapping("/{id}")
    @ApiOperation("查询博客详情（含作者信息 + 是否已点赞）")
    public Result queryBlogById(@PathVariable Long id) {
        return blogService.queryBlogById(id);
    }

    /**
     * 查询点赞用户列表（Redis ZSet Top 5）
     */
    @GetMapping("/likes/{id}")
    @ApiOperation("查询博客点赞用户列表")
    public Result queryBlogLikes(@PathVariable Long id) {
        return blogService.queryBlogLikes(id);
    }

    /**
     * 查询指定用户的博客列表
     */
    @GetMapping("/of/user")
    @ApiOperation("查询指定用户的博客列表")
    public Result queryUserBlogs(@RequestParam("id") Long userId,
                                 @RequestParam(value = "current", defaultValue = "1") Integer current) {
        return blogService.queryUserBlogs(userId, current);
    }

    /**
     * 查询关注用户的博客 Feed 流
     */
    @GetMapping("/of/follow")
    @ApiOperation("查询关注用户的博客 Feed 流")
    public Result queryFollowFeed(@RequestParam(value = "lastId", defaultValue = "0") Long lastId,
                                  @RequestParam(value = "offset", defaultValue = "0") Integer offset) {
        return blogService.queryFollowFeed(lastId, offset);
    }
}
