package com.qingxiang.service.impl;

import com.qingxiang.entity.ShopType;
import com.qingxiang.mapper.ShopTypeMapper;
import com.qingxiang.service.IShopTypeService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * <p>
 *  商铺类型服务实现 — 将 Controller 中的内联查询下沉到 Service 层
 * </p>
 *
 * <h3>优化说明</h3>
 * <ul>
 *   <li><b>分层原则：</b> Controller 只做参数接收和结果返回，Service 负责业务逻辑（包括查询条件组装）。
 *       MyBatis-Plus 的 Lambda 查询链虽然写起来方便，但在 Controller 中直接使用会导致：
 *       <ol>
 *         <li>单元测试困难（无法 Mock Lambda 链）</li>
 *         <li>查询逻辑散落各处，无法统一调整（如加缓存、改写 SQL）</li>
 *       </ol>
 *   </li>
 * </ul>
 *
 * @author 李锡宏
 * @since 2025-12-22
 */
@Service
public class ShopTypeServiceImpl extends ServiceImpl<ShopTypeMapper, ShopType> implements IShopTypeService {

    @Override
    public List<ShopType> queryAllSorted() {
        // 大厂实践：即使是简单排序查询也应封装在 Service 中，
        // 后续如果要加缓存（如 @Cacheable）或调整排序策略，只需改这里
        return query().orderByAsc("sort").list();
    }
}
