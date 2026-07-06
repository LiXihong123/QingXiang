-- ============================================
-- 增量迁移：为 tb_voucher_order 添加联合唯一索引
-- 目的：数据库层面防重复领取，作为 Redis 兜底
-- 执行方式：在 MySQL 中执行此脚本即可
-- ============================================

-- 如果表已存在数据，先检查是否有重复（理论上不会有，但安全起见先查一下）
-- SELECT user_id, voucher_id, COUNT(*) as cnt
-- FROM tb_voucher_order
-- GROUP BY user_id, voucher_id
-- HAVING cnt > 1;

-- 添加联合唯一索引（如果不存在）
-- 使用 ALTER IGNORE 已废弃，所以直接 ADD UNIQUE
ALTER TABLE `tb_voucher_order`
ADD UNIQUE KEY `uk_user_voucher` (`user_id`, `voucher_id`) COMMENT '防重复领取：同一用户对同一秒杀券只能有一笔订单';