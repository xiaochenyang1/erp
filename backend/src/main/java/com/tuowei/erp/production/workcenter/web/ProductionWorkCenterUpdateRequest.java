package com.tuowei.erp.production.workcenter.web;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

import java.time.LocalTime;

public record ProductionWorkCenterUpdateRequest(
        @NotBlank(message = "工作中心名称不能为空") String workCenterName,
        @Min(value = 1, message = "每日产能分钟数必须在1到1440之间")
        @Max(value = 1440, message = "每日产能分钟数必须在1到1440之间")
        Integer dailyCapacityMinutes,
        LocalTime workdayStartTime,
        String remark
) {
    public ProductionWorkCenterUpdateRequest(String workCenterName, String remark) {
        this(workCenterName, null, null, remark);
    }
}
