-- H2 (MODE=MySQL) schema for yuanyu safety system
-- 与 deploy/sql/init.sql 保持一致，仅方言差异

CREATE TABLE IF NOT EXISTS sys_role (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    role_code VARCHAR(30) NOT NULL UNIQUE,
    role_name VARCHAR(50) NOT NULL,
    description VARCHAR(200)
);

CREATE TABLE IF NOT EXISTS sys_user (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    username VARCHAR(50) NOT NULL UNIQUE,
    password VARCHAR(100) NOT NULL,
    real_name VARCHAR(50),
    phone VARCHAR(20),
    role_code VARCHAR(30) NOT NULL DEFAULT 'INSPECTOR',
    status TINYINT NOT NULL DEFAULT 1,
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP,
    update_time DATETIME DEFAULT CURRENT_TIMESTAMP,
    deleted TINYINT NOT NULL DEFAULT 0
);

CREATE TABLE IF NOT EXISTS biz_site (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    site_name VARCHAR(100) NOT NULL,
    address VARCHAR(200),
    manager VARCHAR(50),
    phone VARCHAR(20),
    status TINYINT NOT NULL DEFAULT 1,
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP,
    update_time DATETIME DEFAULT CURRENT_TIMESTAMP,
    deleted TINYINT NOT NULL DEFAULT 0
);

CREATE TABLE IF NOT EXISTS biz_camera (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    site_id BIGINT,
    camera_name VARCHAR(100) NOT NULL,
    stream_url VARCHAR(500),
    stream_type VARCHAR(20) NOT NULL DEFAULT 'DEMO',
    location VARCHAR(100),
    enabled TINYINT NOT NULL DEFAULT 1,
    last_heartbeat DATETIME,
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP,
    update_time DATETIME DEFAULT CURRENT_TIMESTAMP,
    deleted TINYINT NOT NULL DEFAULT 0
);

CREATE TABLE IF NOT EXISTS biz_area (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    camera_id BIGINT NOT NULL,
    area_name VARCHAR(100) NOT NULL,
    area_type VARCHAR(20) NOT NULL DEFAULT 'DANGER',
    points TEXT,
    enabled TINYINT NOT NULL DEFAULT 1,
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP,
    update_time DATETIME DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS biz_rule (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    rule_name VARCHAR(100) NOT NULL,
    behavior_type VARCHAR(30) NOT NULL,
    camera_id BIGINT,
    min_confidence DECIMAL(4,3) NOT NULL DEFAULT 0.450,
    consecutive_frames INT NOT NULL DEFAULT 3,
    cooldown_seconds INT NOT NULL DEFAULT 60,
    level VARCHAR(10) NOT NULL DEFAULT 'MEDIUM',
    start_time VARCHAR(5),
    end_time VARCHAR(5),
    enabled TINYINT NOT NULL DEFAULT 1,
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP,
    update_time DATETIME DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS biz_alarm (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    alarm_no VARCHAR(40) NOT NULL UNIQUE,
    camera_id BIGINT,
    camera_name VARCHAR(100),
    site_id BIGINT,
    behavior_type VARCHAR(30) NOT NULL,
    level VARCHAR(10),
    confidence DECIMAL(4,3),
    track_id INT,
    bbox VARCHAR(100),
    snapshot_url VARCHAR(255),
    alarm_time DATETIME NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    handler_id BIGINT,
    handler_name VARCHAR(50),
    handle_time DATETIME,
    handle_remark VARCHAR(500),
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS biz_detection_record (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    camera_id BIGINT,
    record_time DATETIME NOT NULL,
    person_count INT DEFAULT 0,
    helmet_count INT DEFAULT 0,
    no_helmet_count INT DEFAULT 0,
    vest_count INT DEFAULT 0,
    no_vest_count INT DEFAULT 0,
    fps DECIMAL(6,2),
    inference_ms DECIMAL(6,2),
    model_version VARCHAR(50)
);

CREATE TABLE IF NOT EXISTS biz_model (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    model_name VARCHAR(100) NOT NULL,
    version VARCHAR(50) NOT NULL,
    file_path VARCHAR(255),
    map50 DECIMAL(6,4),
    precision_rate DECIMAL(6,4),
    recall_rate DECIMAL(6,4),
    status VARCHAR(20) NOT NULL DEFAULT 'INACTIVE',
    deploy_time DATETIME,
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS sys_operation_log (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id BIGINT,
    username VARCHAR(50),
    module VARCHAR(50),
    operation VARCHAR(100),
    method VARCHAR(200),
    params VARCHAR(1000),
    ip VARCHAR(50),
    cost_ms BIGINT,
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_alarm_time ON biz_alarm (alarm_time);
CREATE INDEX IF NOT EXISTS idx_alarm_status ON biz_alarm (status);
CREATE INDEX IF NOT EXISTS idx_alarm_behavior ON biz_alarm (behavior_type);
CREATE INDEX IF NOT EXISTS idx_camera_site ON biz_camera (site_id);
CREATE INDEX IF NOT EXISTS idx_area_camera ON biz_area (camera_id);
CREATE INDEX IF NOT EXISTS idx_detection_time ON biz_detection_record (record_time);
