package com.tuowei.erp.inventory.stock.web;

import java.util.List;

public record GenealogyLimits(
        int maxDepth,
        int perLevelNodeLimit,
        int totalNodeLimit,
        boolean truncated,
        List<String> truncationReasons,
        boolean scopeLimited
) {
}
