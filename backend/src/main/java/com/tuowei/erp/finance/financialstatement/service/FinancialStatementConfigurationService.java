package com.tuowei.erp.finance.financialstatement.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.tuowei.erp.common.security.AuditMetadata;
import com.tuowei.erp.common.security.AuditMetadataFactory;
import com.tuowei.erp.finance.financialstatement.mapper.FinancialStatementItemTemplateMapper;
import com.tuowei.erp.finance.financialstatement.mapper.FinancialStatementSubjectMapMapper;
import com.tuowei.erp.finance.financialstatement.model.FinancialStatementDefinition;
import com.tuowei.erp.finance.financialstatement.model.FinancialStatementItemDefinition;
import com.tuowei.erp.finance.financialstatement.model.FinancialStatementItemTemplateEntity;
import com.tuowei.erp.finance.financialstatement.model.FinancialStatementSubjectMapEntity;
import com.tuowei.erp.finance.financialstatement.web.FinancialStatementConfigurationItemResponse;
import com.tuowei.erp.finance.financialstatement.web.FinancialStatementConfigurationResponse;
import com.tuowei.erp.finance.financialstatement.web.StatementMappingUpdateRequest;
import com.tuowei.erp.finance.subject.mapper.AccountSubjectMapper;
import com.tuowei.erp.finance.subject.model.AccountSubjectEntity;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class FinancialStatementConfigurationService {

    public static final String FORMULA_VERSION = "TYPE_MAPPING_V1";
    public static final String BALANCE_SHEET = "BALANCE_SHEET";
    public static final String INCOME_STATEMENT = "INCOME_STATEMENT";
    private static final Set<String> STATEMENT_TYPES = Set.of(BALANCE_SHEET, INCOME_STATEMENT);

    private final FinancialStatementItemTemplateMapper itemTemplateMapper;
    private final FinancialStatementSubjectMapMapper subjectMapMapper;
    private final AccountSubjectMapper accountSubjectMapper;
    private final AuditMetadataFactory auditMetadataFactory;

    public FinancialStatementConfigurationService(
            FinancialStatementItemTemplateMapper itemTemplateMapper,
            FinancialStatementSubjectMapMapper subjectMapMapper,
            AccountSubjectMapper accountSubjectMapper,
            AuditMetadataFactory auditMetadataFactory
    ) {
        this.itemTemplateMapper = itemTemplateMapper;
        this.subjectMapMapper = subjectMapMapper;
        this.accountSubjectMapper = accountSubjectMapper;
        this.auditMetadataFactory = auditMetadataFactory;
    }

    @Transactional(readOnly = true)
    public FinancialStatementDefinition definition(String statementType) {
        String normalizedType = normalizeStatementType(statementType);
        AuditMetadata audit = auditMetadataFactory.current();
        List<FinancialStatementItemTemplateEntity> templates = itemTemplateMapper.selectList(
                new LambdaQueryWrapper<FinancialStatementItemTemplateEntity>()
                        .eq(FinancialStatementItemTemplateEntity::getFormulaVersion, FORMULA_VERSION)
                        .eq(FinancialStatementItemTemplateEntity::getStatementType, normalizedType)
                        .eq(FinancialStatementItemTemplateEntity::getStatus, "ACTIVE")
                        .orderByAsc(FinancialStatementItemTemplateEntity::getSortNo)
                        .orderByAsc(FinancialStatementItemTemplateEntity::getItemCode)
        );
        if (templates.isEmpty()) {
            throw new IllegalStateException("财务报表项目模板不存在: " + normalizedType);
        }

        List<FinancialStatementSubjectMapEntity> mappings = subjectMapMapper.selectList(
                new LambdaQueryWrapper<FinancialStatementSubjectMapEntity>()
                        .eq(FinancialStatementSubjectMapEntity::getCompanyId, audit.companyId())
                        .eq(FinancialStatementSubjectMapEntity::getAccountBookId, audit.accountBookId())
                        .eq(FinancialStatementSubjectMapEntity::getFormulaVersion, FORMULA_VERSION)
                        .eq(FinancialStatementSubjectMapEntity::getStatementType, normalizedType)
                        .orderByAsc(FinancialStatementSubjectMapEntity::getSubjectCode)
        );
        return buildDefinition(templates, mappings, audit);
    }

    @Transactional(readOnly = true)
    public FinancialStatementConfigurationResponse configuration(String statementType) {
        FinancialStatementDefinition definition = definition(statementType);
        return toResponse(definition);
    }

    @Transactional
    public FinancialStatementConfigurationResponse replaceSubjects(
            String statementType,
            String itemCode,
            StatementMappingUpdateRequest request
    ) {
        String normalizedType = normalizeStatementType(statementType);
        String normalizedItemCode = normalizeRequired(itemCode, "报表项目编码不能为空");
        FinancialStatementDefinition definition = definition(normalizedType);
        FinancialStatementItemDefinition item = definition.items().stream()
                .filter(candidate -> candidate.itemCode().equals(normalizedItemCode))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("报表项目不存在: " + normalizedItemCode));
        if (StringUtils.hasText(item.formulaCode())) {
            throw new IllegalArgumentException("公式项目不能配置会计科目");
        }

        List<String> subjectCodes = normalizeSubjectCodes(request.subjectCodes());
        AuditMetadata audit = auditMetadataFactory.current();
        Map<String, AccountSubjectEntity> subjects = subjectsByCode(subjectCodes, audit);
        List<String> missing = subjectCodes.stream().filter(code -> !subjects.containsKey(code)).toList();
        if (!missing.isEmpty()) {
            throw new IllegalArgumentException("会计科目不存在: " + String.join(", ", missing));
        }
        List<String> mismatched = subjectCodes.stream()
                .filter(code -> !item.sectionCode().equals(subjects.get(code).getSubjectType()))
                .toList();
        if (!mismatched.isEmpty()) {
            throw new IllegalArgumentException("科目类别与报表项目不匹配: " + String.join(", ", mismatched));
        }

        List<FinancialStatementSubjectMapEntity> existing = subjectMapMapper.selectList(
                new LambdaQueryWrapper<FinancialStatementSubjectMapEntity>()
                        .eq(FinancialStatementSubjectMapEntity::getCompanyId, audit.companyId())
                        .eq(FinancialStatementSubjectMapEntity::getAccountBookId, audit.accountBookId())
                        .eq(FinancialStatementSubjectMapEntity::getFormulaVersion, FORMULA_VERSION)
                        .eq(FinancialStatementSubjectMapEntity::getStatementType, normalizedType)
        );
        List<Long> deleteIds = existing.stream()
                .filter(mapping -> normalizedItemCode.equals(mapping.getItemCode())
                        || subjectCodes.contains(mapping.getSubjectCode()))
                .map(FinancialStatementSubjectMapEntity::getId)
                .toList();
        if (!deleteIds.isEmpty()) {
            subjectMapMapper.deleteByIds(deleteIds);
        }

        LocalDateTime now = audit.now();
        for (String subjectCode : subjectCodes) {
            FinancialStatementSubjectMapEntity mapping = new FinancialStatementSubjectMapEntity();
            mapping.setCompanyId(audit.companyId());
            mapping.setAccountBookId(audit.accountBookId());
            mapping.setFormulaVersion(FORMULA_VERSION);
            mapping.setStatementType(normalizedType);
            mapping.setItemCode(normalizedItemCode);
            mapping.setSubjectCode(subjectCode);
            mapping.setCreatedBy(audit.userId());
            mapping.setCreatedTime(now);
            mapping.setUpdatedBy(audit.userId());
            mapping.setUpdatedTime(now);
            mapping.setVersion(0);
            subjectMapMapper.insert(mapping);
        }
        return configuration(normalizedType);
    }

    private FinancialStatementDefinition buildDefinition(
            List<FinancialStatementItemTemplateEntity> templates,
            List<FinancialStatementSubjectMapEntity> mappings,
            AuditMetadata audit
    ) {
        Map<String, FinancialStatementItemTemplateEntity> templateByCode = templates.stream()
                .collect(Collectors.toMap(
                        FinancialStatementItemTemplateEntity::getItemCode,
                        Function.identity(),
                        (left, right) -> left,
                        LinkedHashMap::new
                ));
        Map<String, List<String>> subjectsByItem = new LinkedHashMap<>();
        Map<String, String> subjectItemCodes = new LinkedHashMap<>();
        List<String> errors = new ArrayList<>();

        Set<String> mappedCodes = mappings.stream()
                .map(FinancialStatementSubjectMapEntity::getSubjectCode)
                .collect(Collectors.toCollection(LinkedHashSet::new));
        Map<String, AccountSubjectEntity> subjects = subjectsByCode(List.copyOf(mappedCodes), audit);
        for (FinancialStatementSubjectMapEntity mapping : mappings) {
            FinancialStatementItemTemplateEntity template = templateByCode.get(mapping.getItemCode());
            if (template == null) {
                errors.add("映射引用不存在的报表项目: " + mapping.getItemCode() + " <- " + mapping.getSubjectCode());
                continue;
            }
            if (StringUtils.hasText(template.getFormulaCode())) {
                errors.add("公式项目不允许配置科目: " + template.getItemCode());
                continue;
            }
            AccountSubjectEntity subject = subjects.get(mapping.getSubjectCode());
            if (subject == null) {
                errors.add("映射引用不存在的会计科目: " + mapping.getSubjectCode());
            } else if (!template.getSectionCode().equals(subject.getSubjectType())) {
                errors.add("科目类别与报表项目不匹配: " + mapping.getSubjectCode() + " -> " + template.getItemCode());
            }
            String previous = subjectItemCodes.putIfAbsent(mapping.getSubjectCode(), mapping.getItemCode());
            if (previous != null && !previous.equals(mapping.getItemCode())) {
                errors.add("会计科目重复映射: " + mapping.getSubjectCode());
                continue;
            }
            subjectsByItem.computeIfAbsent(mapping.getItemCode(), ignored -> new ArrayList<>())
                    .add(mapping.getSubjectCode());
        }

        List<FinancialStatementItemDefinition> items = templates.stream()
                .map(template -> new FinancialStatementItemDefinition(
                        template.getItemCode(),
                        template.getItemName(),
                        template.getSectionCode(),
                        template.getAmountDirection(),
                        template.getFormulaCode(),
                        template.getSortNo(),
                        List.copyOf(subjectsByItem.getOrDefault(template.getItemCode(), List.of()))
                ))
                .toList();
        return new FinancialStatementDefinition(
                FORMULA_VERSION,
                templates.get(0).getStatementType(),
                items,
                Map.copyOf(subjectItemCodes),
                errors.stream().distinct().sorted().toList()
        );
    }

    private Map<String, AccountSubjectEntity> subjectsByCode(List<String> subjectCodes, AuditMetadata audit) {
        if (subjectCodes.isEmpty()) {
            return Map.of();
        }
        return accountSubjectMapper.selectList(new LambdaQueryWrapper<AccountSubjectEntity>()
                        .eq(AccountSubjectEntity::getCompanyId, audit.companyId())
                        .eq(AccountSubjectEntity::getAccountBookId, audit.accountBookId())
                        .eq(AccountSubjectEntity::getDeletedFlag, 0)
                        .in(AccountSubjectEntity::getSubjectCode, subjectCodes))
                .stream()
                .collect(Collectors.toMap(AccountSubjectEntity::getSubjectCode, Function.identity()));
    }

    private FinancialStatementConfigurationResponse toResponse(FinancialStatementDefinition definition) {
        return new FinancialStatementConfigurationResponse(
                definition.formulaVersion(),
                definition.statementType(),
                definition.items().stream()
                        .map(item -> new FinancialStatementConfigurationItemResponse(
                                item.itemCode(), item.itemName(), item.sectionCode(), item.amountDirection(),
                                item.formulaCode(), item.sortNo(), item.subjectCodes()
                        ))
                        .toList(),
                definition.validationErrors()
        );
    }

    private String normalizeStatementType(String statementType) {
        String normalized = normalizeRequired(statementType, "报表类型不能为空").toUpperCase(Locale.ROOT);
        if (!STATEMENT_TYPES.contains(normalized)) {
            throw new IllegalArgumentException("不支持的报表类型: " + normalized);
        }
        return normalized;
    }

    private List<String> normalizeSubjectCodes(List<String> subjectCodes) {
        if (subjectCodes == null) {
            throw new IllegalArgumentException("科目映射列表不能为空");
        }
        LinkedHashSet<String> normalized = new LinkedHashSet<>();
        for (String subjectCode : subjectCodes) {
            normalized.add(normalizeRequired(subjectCode, "科目编码不能为空"));
        }
        if (normalized.size() != subjectCodes.size()) {
            throw new IllegalArgumentException("科目映射不能重复");
        }
        return List.copyOf(normalized);
    }

    private String normalizeRequired(String value, String message) {
        if (!StringUtils.hasText(value)) {
            throw new IllegalArgumentException(message);
        }
        return value.trim();
    }
}
