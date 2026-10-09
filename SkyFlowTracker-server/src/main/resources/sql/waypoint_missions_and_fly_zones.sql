-- 航线任务表
CREATE TABLE IF NOT EXISTS waypoint_missions (
    id                BIGINT AUTO_INCREMENT PRIMARY KEY,
    name              VARCHAR(100)  NOT NULL COMMENT '任务名称',
    description       VARCHAR(500)  NULL COMMENT '任务描述',
    waypoints         JSON          NOT NULL COMMENT '航点数据JSON数组',
    creator_id        VARCHAR(36)   NOT NULL COMMENT '创建者ID（管理员）',
    assignee_id       VARCHAR(36)   NULL COMMENT '被分派用户ID',
    status            TINYINT       NOT NULL DEFAULT 0 COMMENT '0=草稿 1=已发布 2=已分派 3=执行中 4=已完成 5=已取消',
    total_distance    DOUBLE        NULL DEFAULT 0 COMMENT '总距离（米）',
    estimated_time    INT           NULL DEFAULT 0 COMMENT '预估时长（秒）',
    create_time       DATETIME      NOT NULL,
    modification_time DATETIME      NOT NULL,
    INDEX idx_creator_id (creator_id),
    INDEX idx_assignee_id (assignee_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='航线任务表';

-- 禁飞区/限飞区表
CREATE TABLE IF NOT EXISTS fly_zones (
    id                BIGINT AUTO_INCREMENT PRIMARY KEY,
    dji_fly_zone_id   INT           NULL COMMENT 'DJI限飞区ID（用于去重），手动创建时为null',
    name              VARCHAR(255)  NOT NULL COMMENT '区域名称',
    category          TINYINT       NOT NULL COMMENT '0=WARNING 1=ENHANCED_WARNING 2=AUTHORIZATION 3=RESTRICTED',
    shape             TINYINT       NOT NULL COMMENT '0=圆形 1=多边形',
    center_lat        DOUBLE        NULL COMMENT '圆心纬度（圆形时必填）',
    center_lng        DOUBLE        NULL COMMENT '圆心经度（圆形时必填）',
    radius            DOUBLE        NULL COMMENT '半径/米（圆形时必填）',
    polygon_points    JSON          NULL COMMENT '多边形顶点JSON数组（多边形时必填）',
    max_altitude      DOUBLE        NULL COMMENT '限高/米，null=完全禁飞',
    description       VARCHAR(500)  NULL,
    source            TINYINT       NOT NULL DEFAULT 0 COMMENT '0=DJI同步 1=管理员手动创建',
    creator_id        VARCHAR(36)   NULL COMMENT '创建者ID（手动创建时有值）',
    enabled           TINYINT       NOT NULL DEFAULT 1 COMMENT '0=禁用 1=启用',
    create_time       DATETIME      NOT NULL,
    modification_time DATETIME      NOT NULL,
    UNIQUE INDEX idx_dji_zone_id (dji_fly_zone_id),
    INDEX idx_enabled (enabled)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='禁飞区/限飞区表';
