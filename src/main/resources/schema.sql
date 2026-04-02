-- AIOps Workbench Database Schema

CREATE DATABASE IF NOT EXISTS aiops_workbench DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;

USE aiops_workbench;

-- Chat sessions and messages are managed by CoPaw (no local tables needed).

-- Todo tasks (待办任务)
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

-- Focus events (重点关注事项)
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
