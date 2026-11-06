package com.tuowei.erp.inventory.mrp.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.tuowei.erp.inventory.mrp.model.MrpCalculationSnapshot;
import org.springframework.stereotype.Component;

@Component
public class MrpSnapshotCodec {
    private final ObjectMapper objectMapper;
    public MrpSnapshotCodec(ObjectMapper objectMapper) { this.objectMapper = objectMapper; }

    public String write(Object snapshot) {
        try { return objectMapper.writeValueAsString(snapshot); }
        catch (JsonProcessingException ex) { throw new IllegalStateException("无法保存MRP计算依据", ex); }
    }
    public MrpCalculationSnapshot snapshot(String json) { return read(json, MrpCalculationSnapshot.class); }
    public MrpCalculationSnapshot.Node node(String json) { return read(json, MrpCalculationSnapshot.Node.class); }
    private <T> T read(String json, Class<T> type) {
        if (json == null || json.isBlank()) return null;
        try { return objectMapper.readValue(json, type); }
        catch (JsonProcessingException ex) { throw new IllegalStateException("MRP计算依据无法读取，请重新运行计划", ex); }
    }
}
