package com.tuowei.erp.masterdata.location.service;

import com.tuowei.erp.common.security.AuditMetadata;
import com.tuowei.erp.common.web.PageResponse;
import com.tuowei.erp.masterdata.location.web.LocationCreateRequest;
import com.tuowei.erp.masterdata.location.web.LocationPageQuery;
import com.tuowei.erp.masterdata.location.web.LocationResponse;
import com.tuowei.erp.masterdata.location.web.LocationUpdateRequest;
import com.tuowei.erp.masterdata.warehouse.model.WarehouseEntity;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Compatibility facade for location queries and commands. */
@Service
public class LocationService {

    private final LocationQueryService locationQueryService;
    private final LocationCommandService locationCommandService;

    public LocationService(LocationQueryService locationQueryService, LocationCommandService locationCommandService) {
        this.locationQueryService = locationQueryService;
        this.locationCommandService = locationCommandService;
    }

    @Transactional
    public LocationResponse create(LocationCreateRequest request) {
        return locationCommandService.create(request);
    }

    @Transactional
    public LocationResponse ensureDefaultLocation(WarehouseEntity warehouse, AuditMetadata audit) {
        return locationCommandService.ensureDefaultLocation(warehouse, audit);
    }

    @Transactional
    public LocationResponse update(Long id, LocationUpdateRequest request) {
        return locationCommandService.update(id, request);
    }

    @Transactional
    public LocationResponse enable(Long id) {
        return locationCommandService.enable(id);
    }

    @Transactional
    public LocationResponse disable(Long id) {
        return locationCommandService.disable(id);
    }

    @Transactional(readOnly = true)
    public LocationResponse getById(Long id) {
        return locationQueryService.getById(id);
    }

    @Transactional(readOnly = true)
    public PageResponse<LocationResponse> list(LocationPageQuery query) {
        LocationPageQuery safe = query == null ? new LocationPageQuery() : query;
        return locationQueryService.list(safe);
    }
}
