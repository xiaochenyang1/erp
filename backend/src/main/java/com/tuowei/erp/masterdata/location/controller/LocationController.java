package com.tuowei.erp.masterdata.location.controller;

import com.tuowei.erp.common.security.PermissionCodes;
import com.tuowei.erp.common.web.ApiResponse;
import com.tuowei.erp.common.web.PageResponse;
import com.tuowei.erp.masterdata.location.service.LocationService;
import com.tuowei.erp.masterdata.location.web.LocationCreateRequest;
import com.tuowei.erp.masterdata.location.web.LocationPageQuery;
import com.tuowei.erp.masterdata.location.web.LocationResponse;
import com.tuowei.erp.masterdata.location.web.LocationUpdateRequest;
import com.tuowei.erp.system.log.annotation.OperationLog;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/masterdata/locations")
public class LocationController {

    private final LocationService locationService;

    public LocationController(LocationService locationService) {
        this.locationService = locationService;
    }

    @PreAuthorize(PermissionCodes.HAS_MASTERDATA_LOCATION_MANAGE)
    @OperationLog(module = "masterdata", operation = "location-create", bizNo = "#result.data.id")
    @PostMapping
    public ApiResponse<LocationResponse> create(@Valid @RequestBody LocationCreateRequest request) {
        return ApiResponse.success(locationService.create(request));
    }

    @PreAuthorize(PermissionCodes.HAS_MASTERDATA_LOCATION_VIEW + " or " + PermissionCodes.HAS_MASTERDATA_WAREHOUSE_VIEW)
    @GetMapping
    public ApiResponse<PageResponse<LocationResponse>> list(LocationPageQuery query) {
        return ApiResponse.success(locationService.list(query));
    }

    @PreAuthorize(PermissionCodes.HAS_MASTERDATA_LOCATION_VIEW + " or " + PermissionCodes.HAS_MASTERDATA_WAREHOUSE_VIEW)
    @GetMapping("/{id}")
    public ApiResponse<LocationResponse> detail(@PathVariable Long id) {
        return ApiResponse.success(locationService.getById(id));
    }

    @PreAuthorize(PermissionCodes.HAS_MASTERDATA_LOCATION_MANAGE)
    @OperationLog(module = "masterdata", operation = "location-update", bizNo = "#id")
    @PutMapping("/{id}")
    public ApiResponse<LocationResponse> update(
            @PathVariable Long id,
            @Valid @RequestBody LocationUpdateRequest request
    ) {
        return ApiResponse.success(locationService.update(id, request));
    }

    @PreAuthorize(PermissionCodes.HAS_MASTERDATA_LOCATION_MANAGE)
    @OperationLog(module = "masterdata", operation = "location-enable", bizNo = "#id")
    @PostMapping("/{id}/enable")
    public ApiResponse<LocationResponse> enable(@PathVariable Long id) {
        return ApiResponse.success(locationService.enable(id));
    }

    @PreAuthorize(PermissionCodes.HAS_MASTERDATA_LOCATION_MANAGE)
    @OperationLog(module = "masterdata", operation = "location-disable", bizNo = "#id")
    @PostMapping("/{id}/disable")
    public ApiResponse<LocationResponse> disable(@PathVariable Long id) {
        return ApiResponse.success(locationService.disable(id));
    }
}
