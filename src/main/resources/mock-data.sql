-- =============================================
-- Mock data for aiops-workbench
-- 先执行 schema.sql 建表，再执行本文件插入测试数据
-- =============================================

USE aiops_workbench;

-- 建表（如果还没建）
CREATE TABLE IF NOT EXISTS todo_task (
    id              BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id         VARCHAR(64)  NOT NULL COMMENT '用户标识',
    system_code     VARCHAR(64)  NOT NULL COMMENT '系统编码',
    task_name       VARCHAR(256) NOT NULL COMMENT '任务名称',
    task_level      VARCHAR(32)  NOT NULL COMMENT '任务等级: P1/P2/P3',
    task_detail     TEXT                  COMMENT '任务详情',
    task_link       VARCHAR(512)          COMMENT '任务链接',
    task_suggestion VARCHAR(512)          COMMENT '任务建议',
    estimated_time  VARCHAR(64)           COMMENT '任务预估处理时间',
    created_at      DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at      DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    KEY idx_todo_user_id (user_id),
    KEY idx_todo_system_code (system_code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='待办任务';

CREATE TABLE IF NOT EXISTS focus_event (
    id           BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id      VARCHAR(64)  NOT NULL COMMENT '用户标识',
    system_code  VARCHAR(64)  NOT NULL COMMENT '系统编码',
    event_type   VARCHAR(32)  NOT NULL COMMENT '事件类型: pending_upgrade/completed/not_started/to_be_determined',
    event_count  INT          NOT NULL DEFAULT 0 COMMENT '事件个数',
    ratio        VARCHAR(32)           COMMENT '同比变化: 如 ↑20%',
    event_link   VARCHAR(512)          COMMENT '事件链接',
    created_at   DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at   DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    KEY idx_focus_user_id (user_id),
    KEY idx_focus_system_code (system_code),
    KEY idx_focus_event_type (event_type)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='重点关注事项';

-- =============================================
-- 待办任务 mock 数据
-- =============================================
INSERT INTO todo_task (user_id, system_code, task_name, task_level, task_detail, task_link, task_suggestion, estimated_time) VALUES
('default', 'CRM', '前帐待办清单梳理', 'P1', '先核上一班未结事项，优先锁定阻断业务和临期限。', '/tasks/crm-clearing', '建议 先清故障,推荐 前帐清单', '预计10 分钟'),
('default', 'OSS', '巡检与隐患处理', 'P2', '回看升级单和遗留单，确认隐患处理是否还在挂账。', '/tasks/patrol-issues', '建议 先看升级单,推荐 异常跟踪项', '预计 8 分钟'),
('default', 'BILLING', '数据质量稽核确认', 'P3', '确认高影响系统是否需要专项排查。', '/tasks/data-audit', '建议 先看高影响系统,推荐 稽核确认结果', '预计 12 分钟');

-- =============================================
-- 重点关注事项 mock 数据
-- =============================================
INSERT INTO focus_event (user_id, system_code, event_type, event_count, ratio, event_link) VALUES
('default', 'ALL', 'pending_upgrade', 6,  '↑20%', '/events/pending-upgrade'),
('default', 'ALL', 'completed',       15, '↑50%', '/events/completed'),
('default', 'ALL', 'not_started',     9,  '↑30%', '/events/not-started'),
('default', 'ALL', 'to_be_determined', 9, '↑30%', '/events/to-be-determined');
