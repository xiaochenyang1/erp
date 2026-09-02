package com.tuowei.erp.system.dict.service;

import com.tuowei.erp.common.web.PageResponse;
import com.tuowei.erp.system.dict.web.DictItemCreateRequest;
import com.tuowei.erp.system.dict.web.DictItemResponse;
import com.tuowei.erp.system.dict.web.DictItemUpdateRequest;
import com.tuowei.erp.system.dict.web.DictTypeCreateRequest;
import com.tuowei.erp.system.dict.web.DictTypePageQuery;
import com.tuowei.erp.system.dict.web.DictTypeResponse;
import com.tuowei.erp.system.dict.web.DictTypeUpdateRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/** Compatibility facade for dictionary queries and commands. */
@Service
public class SystemDictService {

    private final SystemDictQueryService systemDictQueryService;
    private final SystemDictCommandService systemDictCommandService;

    public SystemDictService(
            SystemDictQueryService systemDictQueryService,
            SystemDictCommandService systemDictCommandService
    ) {
        this.systemDictQueryService = systemDictQueryService;
        this.systemDictCommandService = systemDictCommandService;
    }

    @Transactional
    public DictTypeResponse createType(DictTypeCreateRequest request) {
        return systemDictCommandService.createType(request);
    }

    @Transactional(readOnly = true)
    public PageResponse<DictTypeResponse> listTypes(DictTypePageQuery query) {
        DictTypePageQuery safeQuery = query == null ? new DictTypePageQuery() : query;
        return systemDictQueryService.listTypes(safeQuery);
    }

    @Transactional(readOnly = true)
    public DictTypeResponse getTypeById(Long id) {
        return systemDictQueryService.getTypeById(id);
    }

    @Transactional
    public DictTypeResponse updateType(Long id, DictTypeUpdateRequest request) {
        return systemDictCommandService.updateType(id, request);
    }

    @Transactional
    public DictTypeResponse enableType(Long id) {
        return systemDictCommandService.enableType(id);
    }

    @Transactional
    public DictTypeResponse disableType(Long id) {
        return systemDictCommandService.disableType(id);
    }

    @Transactional
    public DictItemResponse createItem(DictItemCreateRequest request) {
        return systemDictCommandService.createItem(request);
    }

    @Transactional(readOnly = true)
    public List<DictItemResponse> listItems(String dictType) {
        return systemDictQueryService.listItems(dictType);
    }

    @Transactional(readOnly = true)
    public String requireEnabledItem(String dictType, String itemValue, String message) {
        return systemDictQueryService.requireEnabledItem(dictType, itemValue, message);
    }

    @Transactional
    public DictItemResponse updateItem(Long id, DictItemUpdateRequest request) {
        return systemDictCommandService.updateItem(id, request);
    }

    @Transactional
    public DictItemResponse enableItem(Long id) {
        return systemDictCommandService.enableItem(id);
    }

    @Transactional
    public DictItemResponse disableItem(Long id) {
        return systemDictCommandService.disableItem(id);
    }
}
