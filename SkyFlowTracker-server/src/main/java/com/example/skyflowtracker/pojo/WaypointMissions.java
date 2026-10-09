package com.example.skyflowtracker.pojo;

import java.time.LocalDateTime;

public class WaypointMissions {
    private Long id;
    private String name;
    private String description;
    private String waypoints; // JSON 字符串
    private String creatorId;
    private String assigneeId;
    private Integer status; // 0=草稿 1=已发布 2=已分派 3=执行中 4=已完成 5=已取消
    private Double totalDistance;
    private Integer estimatedTime;
    private LocalDateTime createTime;
    private LocalDateTime modificationTime;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    public String getWaypoints() { return waypoints; }
    public void setWaypoints(String waypoints) { this.waypoints = waypoints; }

    public String getCreatorId() { return creatorId; }
    public void setCreatorId(String creatorId) { this.creatorId = creatorId; }

    public String getAssigneeId() { return assigneeId; }
    public void setAssigneeId(String assigneeId) { this.assigneeId = assigneeId; }

    public Integer getStatus() { return status; }
    public void setStatus(Integer status) { this.status = status; }

    public Double getTotalDistance() { return totalDistance; }
    public void setTotalDistance(Double totalDistance) { this.totalDistance = totalDistance; }

    public Integer getEstimatedTime() { return estimatedTime; }
    public void setEstimatedTime(Integer estimatedTime) { this.estimatedTime = estimatedTime; }

    public LocalDateTime getCreateTime() { return createTime; }
    public void setCreateTime(LocalDateTime createTime) { this.createTime = createTime; }

    public LocalDateTime getModificationTime() { return modificationTime; }
    public void setModificationTime(LocalDateTime modificationTime) { this.modificationTime = modificationTime; }
}
