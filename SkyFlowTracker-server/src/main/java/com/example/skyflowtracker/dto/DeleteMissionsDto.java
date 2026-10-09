package com.example.skyflowtracker.dto;

import jakarta.validation.constraints.NotNull;
import java.util.List;

public class DeleteMissionsDto {
    @NotNull(message = "ID不能为空")
    private List<Long> ids;

    public List<Long> getIds() { return ids; }
    public void setIds(List<Long> ids) { this.ids = ids; }
}
