package com.example.skyflowtracker.dto;

import jakarta.validation.constraints.NotNull;

public class UpdateFlyZoneDto {
    @NotNull(message = "禁飞区ID不能为空")
    private Long id;
    private String name;
    private Integer category;
    private Integer shape;
    private Double centerLat;
    private Double centerLng;
    private Double radius;
    private String polygonPoints;
    private Double maxAltitude;
    private String description;
    private Integer enabled;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

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

    public Integer getEnabled() { return enabled; }
    public void setEnabled(Integer enabled) { this.enabled = enabled; }
}
