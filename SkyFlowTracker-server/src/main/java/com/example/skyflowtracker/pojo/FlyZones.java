package com.example.skyflowtracker.pojo;

import java.time.LocalDateTime;

public class FlyZones {
    private Long id;
    private Integer djiFlyZoneId;
    private String name;
    private Integer category; // 0=WARNING 1=ENHANCED_WARNING 2=AUTHORIZATION 3=RESTRICTED
    private Integer shape; // 0=圆形 1=多边形
    private Double centerLat;
    private Double centerLng;
    private Double radius;
    private String polygonPoints; // JSON 字符串
    private Double maxAltitude;
    private String description;
    private Integer source; // 0=DJI同步 1=管理员手动创建
    private String creatorId;
    private Integer enabled; // 0=禁用 1=启用
    private LocalDateTime createTime;
    private LocalDateTime modificationTime;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public Integer getDjiFlyZoneId() { return djiFlyZoneId; }
    public void setDjiFlyZoneId(Integer djiFlyZoneId) { this.djiFlyZoneId = djiFlyZoneId; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public Integer getCategory() { return category; }
    public void setCategory(Integer category) { this.category = category; }

    public Integer getShape() { return shape; }
    public void setShape(Integer shape) { this.shape = shape; }

    public Double getCenterLat() { return centerLat; }
    public void setCenterLat(Double centerLat) { this.centerLat = centerLat; }

    public Double getCenterLng() { return centerLng; }
    public void setCenterLng(Double centerLng) { this.centerLng = centerLng; }

    public Double getRadius() { return radius; }
    public void setRadius(Double radius) { this.radius = radius; }

    public String getPolygonPoints() { return polygonPoints; }
    public void setPolygonPoints(String polygonPoints) { this.polygonPoints = polygonPoints; }

    public Double getMaxAltitude() { return maxAltitude; }
    public void setMaxAltitude(Double maxAltitude) { this.maxAltitude = maxAltitude; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    public Integer getSource() { return source; }
    public void setSource(Integer source) { this.source = source; }

    public String getCreatorId() { return creatorId; }
    public void setCreatorId(String creatorId) { this.creatorId = creatorId; }

    public Integer getEnabled() { return enabled; }
    public void setEnabled(Integer enabled) { this.enabled = enabled; }

    public LocalDateTime getCreateTime() { return createTime; }
    public void setCreateTime(LocalDateTime createTime) { this.createTime = createTime; }

    public LocalDateTime getModificationTime() { return modificationTime; }
    public void setModificationTime(LocalDateTime modificationTime) { this.modificationTime = modificationTime; }
}
