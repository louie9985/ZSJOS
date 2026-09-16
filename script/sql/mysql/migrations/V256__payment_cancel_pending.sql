-- V256: ZSJOS 支付单「取消待确认」状态支撑列
-- 依赖 V151/V163 的支付单结构；本脚本只新增 3 列，不改 status 取值，不回填历史数据。
-- 语义：status 仍为 created/waiting/paid/expired/closed；
--       close_requested_at 非空表示已向通联发起取消但结果尚未确认（销售界面显示「取消结果待确认」）。
-- 可重复：每个 ADD COLUMN 均通过 information_schema 守卫。
-- 回滚限制：应用回滚可保留这些可空列；后续删除需单独审计，避免丢失取消重试记录。
SET NAMES utf8mb4;

DROP PROCEDURE IF EXISTS `zsjos_v256_apply`;
DELIMITER $$
CREATE PROCEDURE `zsjos_v256_apply`()
BEGIN
  IF NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema=DATABASE()
      AND table_name='zsjos_payment_order' AND column_name='close_requested_at') THEN
    ALTER TABLE `zsjos_payment_order`
      ADD COLUMN `close_requested_at` datetime DEFAULT NULL COMMENT '取消发起时间；非空表示取消结果待确认'
      AFTER `close_reason`;
  END IF;

  IF NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema=DATABASE()
      AND table_name='zsjos_payment_order' AND column_name='close_attempts') THEN
    ALTER TABLE `zsjos_payment_order`
      ADD COLUMN `close_attempts` int NOT NULL DEFAULT 0 COMMENT '关单尝试次数'
      AFTER `close_requested_at`;
  END IF;

  IF NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema=DATABASE()
      AND table_name='zsjos_payment_order' AND column_name='close_last_error') THEN
    ALTER TABLE `zsjos_payment_order`
      ADD COLUMN `close_last_error` varchar(500) DEFAULT NULL COMMENT '最近一次关单失败或结果未知的原因'
      AFTER `close_attempts`;
  END IF;
END$$
DELIMITER ;

CALL `zsjos_v256_apply`();
DROP PROCEDURE `zsjos_v256_apply`;
