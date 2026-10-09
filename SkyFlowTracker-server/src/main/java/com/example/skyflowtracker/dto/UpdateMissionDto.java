package com.example.skyflowtracker.dto;

import jakarta.validation.constraints.NotNull;

public class UpdateMissionDto {
    @NotNull(message = "任务ID不能为空")
    private Long id;
    private String name;
    private String description;
    private String waypoints;
    private Double totalDistance;
    private Integer estimatedTime;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    public String getWaypoints() { return waypoints; }
    public void setWaypoints(String waypoints) { this.waypoints = waypoints; }

    public Double getTotalDistance() { return totalDistance; }
    public void setTotalDistance(Double totalDistance) { this.totalDistance = totalDistance; }

    public Integer getEstimatedTime() { return estimatedTime; }
    public void setEstimatedTime(Integer estimatedTime) { this.estimatedTime = estimatedTime; }
}
