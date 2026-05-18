package com.qingxiang.service.impl;

import com.qingxiang.entity.Blog;
import com.qingxiang.mapper.BlogMapper;
import com.qingxiang.service.IBlogService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.springframework.stereotype.Service;

/**
 * <p>
 *  服务实现类
 * </p>
 *
 * @author 李锡宏
 * @since 2025-12-22
 */
@Service
public class BlogServiceImpl extends ServiceImpl<BlogMapper, Blog> implements IBlogService {

}
