package com.tuowei.erp.system.attachment;

import com.tuowei.erp.system.attachment.service.AttachmentBusinessType;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 守住 {@link AttachmentBusinessType#GATED} 与真实闸门调用点的一致性。
 *
 * 只要有人往 GATED 里加了业务类型却没在对应单据的写事务里调用
 * {@code attachmentService.requireIfConfigured(...)}，本测试就会失败——
 * 否则 {@code erp.attachment.required-business-types} 配上该类型也是静默不生效。
 */
class AttachmentGateCoverageTest {

    private static final Path MAIN_SOURCE_ROOT = Path.of("src/main/java");

    /** 匹配 {@code requireIfConfigured(AttachmentBusinessType.X, ...)} 或 {@code requireIfConfigured("X", ...)}。 */
    private static final Pattern GATE_CALL_PATTERN = Pattern.compile(
            "requireIfConfigured\\(\\s*(?:AttachmentBusinessType\\.([A-Z][A-Z0-9_]*)|\"([A-Z][A-Z0-9_]*)\")\\s*,"
    );

    @Test
    void everyGatedBusinessTypeHasAtLeastOneRealGateCallSite() throws Exception {
        Map<String, Set<String>> callSites = gateCallSitesByBusinessType();

        Set<String> ungated = new TreeSet<>(AttachmentBusinessType.GATED);
        ungated.removeAll(callSites.keySet());

        assertThat(ungated)
                .as("AttachmentBusinessType.GATED 里的类型必须在某个单据的写事务里真实调用 "
                        + "requireIfConfigured，否则配置了也不会拦截；未接线: " + ungated)
                .isEmpty();
    }

    @Test
    void everyGateCallSiteUsesADeclaredGatedBusinessType() throws Exception {
        Map<String, Set<String>> callSites = gateCallSitesByBusinessType();

        Map<String, Set<String>> undeclared = new TreeMap<>(callSites);
        undeclared.keySet().removeAll(AttachmentBusinessType.GATED);

        assertThat(undeclared)
                .as("闸门调用点使用的业务类型必须先在 AttachmentBusinessType.GATED 里声明，"
                        + "否则启动校验器无法阻止拼写错误的配置值")
                .isEmpty();
    }

    @Test
    void everyDeclaredConstantIsListedInGated() {
        Set<String> constants = declaredBusinessTypeConstants();

        Set<String> notGated = new TreeSet<>(constants);
        notGated.removeAll(AttachmentBusinessType.GATED);

        assertThat(notGated)
                .as("AttachmentBusinessType 里声明的常量必须同时列入 GATED，"
                        + "否则常量存在但配置校验器不接受该值")
                .isEmpty();
    }

    private Map<String, Set<String>> gateCallSitesByBusinessType() throws Exception {
        Map<String, Set<String>> callSites = new TreeMap<>();
        try (Stream<Path> paths = Files.walk(MAIN_SOURCE_ROOT)) {
            for (Path path : paths.filter(p -> p.getFileName().toString().endsWith(".java")).toList()) {
                Matcher matcher = GATE_CALL_PATTERN.matcher(Files.readString(path));
                while (matcher.find()) {
                    String businessType = matcher.group(1) != null ? matcher.group(1) : matcher.group(2);
                    callSites.computeIfAbsent(businessType, key -> new TreeSet<>())
                            .add(path.getFileName().toString());
                }
            }
        }
        return callSites;
    }

    private Set<String> declaredBusinessTypeConstants() {
        Set<String> constants = new TreeSet<>();
        for (Field field : AttachmentBusinessType.class.getDeclaredFields()) {
            if (Modifier.isPublic(field.getModifiers())
                    && Modifier.isStatic(field.getModifiers())
                    && field.getType() == String.class) {
                try {
                    constants.add((String) field.get(null));
                } catch (IllegalAccessException e) {
                    throw new IllegalStateException("无法读取常量 " + field.getName(), e);
                }
            }
        }
        return constants;
    }
}
