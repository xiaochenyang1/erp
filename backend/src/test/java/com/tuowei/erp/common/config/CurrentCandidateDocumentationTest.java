package com.tuowei.erp.common.config;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class CurrentCandidateDocumentationTest {

    @Test
    void currentStatusAndAcceptanceDocsCoverV170ThroughV174() throws IOException {
        String status = readDoc("PROJECT-STATUS-2026-11-09.md");
        String acceptance = readDoc("P0-HUMAN-GATES-EXECUTION-PACKAGE-2026-11-09.md");
        String checklist = readDoc("business-readiness-checklist.md");

        assertThat(status)
                .contains("48763d7")
                .contains("2,593")
                .contains("1,196")
                .contains("30/30")
                .contains("42/42")
                .contains("V168")
                .contains("V174")
                .contains("三大财务报表");

        assertThat(acceptance)
                .contains("MRP-1")
                .contains("COST-1")
                .contains("SCH-1")
                .contains("SCH-9")
                .contains("`ALL` 数据范围")
                .contains("UPG-1")
                .contains("V168 → V174")
                .contains("GO / NO-GO");

        assertThat(checklist)
                .contains("多层分仓 MRP")
                .contains("生产成本核算与期间关单")
                .contains("有限产能排程一期")
                .contains("P0-HUMAN-GATES-EXECUTION-PACKAGE-2026-11-09.md");
    }

    @Test
    void historicalStatusEntryPointsLinkToCurrentCandidateStatus() throws IOException {
        assertThat(readDoc("WHAT_IS_MISSING.md"))
                .contains("PROJECT-STATUS-2026-11-09.md")
                .doesNotContain("当前权威状态以本文件");
        assertThat(readDoc("未完成.md"))
                .contains("PROJECT-STATUS-2026-11-09.md");
        assertThat(readDoc("PREPROD-HANDOFF-2026-10-21.md"))
                .contains("PROJECT-STATUS-2026-11-09.md");
        assertThat(readDoc("P0-HUMAN-GATES-EXECUTION-PACKAGE-2026-10-16.md"))
                .contains("P0-HUMAN-GATES-EXECUTION-PACKAGE-2026-11-09.md");
    }

    private static String readDoc(String fileName) throws IOException {
        return Files.readString(Path.of("docs", fileName), StandardCharsets.UTF_8);
    }
}
