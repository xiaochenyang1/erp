package com.tuowei.erp.common.config;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class CurrentCandidateDocumentationTest {

    @Test
    void currentStatusAndAcceptanceDocsCoverFinancialStatementsCandidate() throws IOException {
        String status = readDoc("PROJECT-STATUS-2026-11-11.md");
        String acceptance = readDoc("FINANCIAL-STATEMENTS-ACCEPTANCE-2026-11-11.md");
        String checklist = readDoc("business-readiness-checklist.md");

        assertThat(status)
                .contains("PR #18")
                .contains("1e1f6c5")
                .contains("1,200")
                .contains("80 项")
                .contains("110 项")
                .contains("31 个路由")
                .contains("43 个工作流")
                .contains("不能声称 31/31 或 43/43 已通过")
                .contains("管理报表最小版")
                .contains("间接法尚未开发");

        assertThat(acceptance)
                .contains("FS-1")
                .contains("FS-12")
                .contains("V175")
                .contains("V176")
                .contains("内部转账")
                .contains("红冲")
                .contains("账套隔离")
                .contains("非法定格式")
                .contains("GO / NO-GO");

        assertThat(checklist)
                .contains("多层分仓 MRP")
                .contains("生产成本核算与期间关单")
                .contains("有限产能排程一期")
                .contains("FINANCIAL-STATEMENTS-ACCEPTANCE-2026-11-11.md")
                .contains("FS-1–FS-12");
    }

    @Test
    void historicalStatusEntryPointsLinkToCurrentCandidateStatus() throws IOException {
        assertThat(readDoc("WHAT_IS_MISSING.md"))
                .contains("PROJECT-STATUS-2026-11-11.md")
                .doesNotContain("当前权威状态以本文件");
        assertThat(readDoc("未完成.md"))
                .contains("PROJECT-STATUS-2026-11-11.md");
        assertThat(readDoc("PROJECT-STATUS-2026-11-09.md"))
                .contains("PROJECT-STATUS-2026-11-11.md");
        assertThat(readDoc("P0-HUMAN-GATES-EXECUTION-PACKAGE-2026-11-09.md"))
                .contains("FINANCIAL-STATEMENTS-ACCEPTANCE-2026-11-11.md");
    }

    private static String readDoc(String fileName) throws IOException {
        return Files.readString(Path.of("docs", fileName), StandardCharsets.UTF_8);
    }
}
