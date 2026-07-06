package com.qingxiang.controller;

import com.qingxiang.dto.Result;
import com.qingxiang.service.IFollowService;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import org.springframework.web.bind.annotation.*;

import javax.annotation.Resource;

/**
 * <p>关注控制器 — Redis Set 实现关注/取关/共同关注</p>
 *
 * <h3>技术亮点</h3>
 * Redis Set SADD/SREM/SISMEMBER/SINTER，O(1) 关注判断，交集求共同关注
 *
 * @author 李锡宏
 * @since 2025-12-22
 */
@RestController
@RequestMapping("/follow")
@Api(tags = "关注模块")
public class FollowController {

    @Resource
    private IFollowService followService;

    @PutMapping("/{followUserId}")
    @ApiOperation("关注用户（Redis Set SADD）")
    public Result follow(@PathVariable Long followUserId) {
        return followService.follow(followUserId);
    }

    @DeleteMapping("/{followUserId}")
    @ApiOperation("取消关注（Redis Set SREM）")
    public Result unfollow(@PathVariable Long followUserId) {
        return followService.unfollow(followUserId);
    }

    @GetMapping("/is/{followUserId}")
    @ApiOperation("判断是否已关注（Redis Set SISMEMBER）")
    public Result isFollowed(@PathVariable Long followUserId) {
        return followService.isFollowed(followUserId);
    }

    @GetMapping("/common/{userId}")
    @ApiOperation("查询共同关注（Redis Set SINTER 交集）")
    public Result commonFollows(@PathVariable Long userId) {
        return followService.commonFollows(userId);
    }

    /**
     * 判断是否已关注（前端兼容路径：/or/not/{id}）
     * <p>前端 shop-detail.html / other-info.html 调用此路径，与 /is/{id} 功能相同</p>
     */
    @GetMapping("/or/not/{followUserId}")
    @ApiOperation("判断是否已关注（前端兼容路径）")
    public Result isFollowedCompat(@PathVariable Long followUserId) {
        return followService.isFollowed(followUserId);
    }

    /**
     * 关注/取关（前端兼容路径：PUT /follow/{id}/{bool}）
     * <p>前端 blog-detail.html / other-info.html 通过 URL 中的 boolean 控制关注/取关，
     * true=关注，false=取关。本方法兼容此调用方式。</p>
     */
    @PutMapping("/{followUserId}/{isFollow}")
    @ApiOperation("关注/取关（前端兼容路径：PUT /follow/{id}/true 或 false）")
    public Result followToggle(@PathVariable Long followUserId, @PathVariable Boolean isFollow) {
        if (Boolean.TRUE.equals(isFollow)) {
            return followService.follow(followUserId);
        }
        return followService.unfollow(followUserId);
    }
}
