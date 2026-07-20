package com.tuowei.erp.system.dict.controller;

import com.tuowei.erp.common.security.PermissionCodes;
import com.tuowei.erp.common.web.ApiResponse;
import com.tuowei.erp.common.web.PageResponse;
import com.tuowei.erp.system.dict.service.SystemDictService;
import com.tuowei.erp.system.dict.web.DictItemCreateRequest;
import com.tuowei.erp.system.dict.web.DictItemResponse;
import com.tuowei.erp.system.dict.web.DictItemUpdateRequest;
import com.tuowei.erp.system.dict.web.DictTypeCreateRequest;
import com.tuowei.erp.system.dict.web.DictTypePageQuery;
import com.tuowei.erp.system.dict.web.DictTypeResponse;
import com.tuowei.erp.system.dict.web.DictTypeUpdateRequest;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
public class SystemDictController {

    private final SystemDictService systemDictService;

    public SystemDictController(SystemDictService systemDictService) {
        this.systemDictService = systemDictService;
    }

    @PreAuthorize(PermissionCodes.HAS_SYSTEM_DICT_CREATE)
    @PostMapping("/api/system/dict-types")
    public ApiResponse<DictTypeResponse> createType(@Valid @RequestBody DictTypeCreateRequest request) {
        return ApiResponse.success(systemDictService.createType(request));
    }

    @PreAuthorize(PermissionCodes.HAS_SYSTEM_DICT_VIEW)
    @GetMapping("/api/system/dict-types")
    public ApiResponse<PageResponse<DictTypeResponse>> listTypes(DictTypePageQuery query) {
        return ApiResponse.success(systemDictService.listTypes(query));
    }

    @PreAuthorize(PermissionCodes.HAS_SYSTEM_DICT_VIEW)
    @GetMapping("/api/system/dict-types/{id:\\d+}")
    public ApiResponse<DictTypeResponse> detailType(@PathVariable Long id) {
        return ApiResponse.success(systemDictService.getTypeById(id));
    }

    @PreAuthorize(PermissionCodes.HAS_SYSTEM_DICT_UPDATE)
    @PutMapping("/api/system/dict-types/{id}")
    public ApiResponse<DictTypeResponse> updateType(@PathVariable Long id, @Valid @RequestBody DictTypeUpdateRequest request) {
        return ApiResponse.success(systemDictService.updateType(id, request));
    }

    @PreAuthorize(PermissionCodes.HAS_SYSTEM_DICT_ENABLE)
    @PostMapping("/api/system/dict-types/{id}/enable")
    public ApiResponse<DictTypeResponse> enableType(@PathVariable Long id) {
        return ApiResponse.success(systemDictService.enableType(id));
    }

    @PreAuthorize(PermissionCodes.HAS_SYSTEM_DICT_DISABLE)
    @PostMapping("/api/system/dict-types/{id}/disable")
    public ApiResponse<DictTypeResponse> disableType(@PathVariable Long id) {
        return ApiResponse.success(systemDictService.disableType(id));
    }

    @PreAuthorize(PermissionCodes.HAS_SYSTEM_DICT_CREATE)
    @PostMapping("/api/system/dict-items")
    public ApiResponse<DictItemResponse> createItem(@Valid @RequestBody DictItemCreateRequest request) {
        return ApiResponse.success(systemDictService.createItem(request));
    }

    @PreAuthorize(PermissionCodes.HAS_SYSTEM_DICT_VIEW)
    @GetMapping("/api/system/dict-types/{dictType}/items")
    public ApiResponse<List<DictItemResponse>> listItems(@PathVariable String dictType) {
        return ApiResponse.success(systemDictService.listItems(dictType));
    }

    @PreAuthorize(PermissionCodes.HAS_SYSTEM_DICT_UPDATE)
    @PutMapping("/api/system/dict-items/{id}")
    public ApiResponse<DictItemResponse> updateItem(@PathVariable Long id, @Valid @RequestBody DictItemUpdateRequest request) {
        return ApiResponse.success(systemDictService.updateItem(id, request));
    }

    @PreAuthorize(PermissionCodes.HAS_SYSTEM_DICT_ENABLE)
    @PostMapping("/api/system/dict-items/{id}/enable")
    public ApiResponse<DictItemResponse> enableItem(@PathVariable Long id) {
        return ApiResponse.success(systemDictService.enableItem(id));
    }

    @PreAuthorize(PermissionCodes.HAS_SYSTEM_DICT_DISABLE)
    @PostMapping("/api/system/dict-items/{id}/disable")
    public ApiResponse<DictItemResponse> disableItem(@PathVariable Long id) {
        return ApiResponse.success(systemDictService.disableItem(id));
    }
}
