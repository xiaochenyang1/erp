package com.tuowei.erp.finance.cashflow.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.tuowei.erp.common.security.AuditMetadata;
import com.tuowei.erp.common.security.AuditMetadataFactory;
import com.tuowei.erp.finance.cashflow.mapper.CashEquivalentSubjectMapper;
import com.tuowei.erp.finance.cashflow.mapper.CashFlowItemTemplateMapper;
import com.tuowei.erp.finance.cashflow.mapper.CashFlowSubjectMapMapper;
import com.tuowei.erp.finance.cashflow.model.CashEquivalentSubjectEntity;
import com.tuowei.erp.finance.cashflow.model.CashFlowDefinition;
import com.tuowei.erp.finance.cashflow.model.CashFlowItemDefinition;
import com.tuowei.erp.finance.cashflow.model.CashFlowItemTemplateEntity;
import com.tuowei.erp.finance.cashflow.model.CashFlowSubjectMapEntity;
import com.tuowei.erp.finance.cashflow.web.CashFlowConfigurationItemResponse;
import com.tuowei.erp.finance.cashflow.web.CashFlowConfigurationResponse;
import com.tuowei.erp.finance.cashflow.web.CashFlowSubjectCodesRequest;
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
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class CashFlowConfigurationService {

    public static final String FORMULA_VERSION = "CASH_FLOW_DIRECT_V1";

    private final CashFlowItemTemplateMapper itemTemplateMapper;
    private final CashEquivalentSubjectMapper cashEquivalentSubjectMapper;
    private final CashFlowSubjectMapMapper subjectMapMapper;
    private final AccountSubjectMapper accountSubjectMapper;
    private final AuditMetadataFactory auditMetadataFactory;

    public CashFlowConfigurationService(
            CashFlowItemTemplateMapper itemTemplateMapper,
            CashEquivalentSubjectMapper cashEquivalentSubjectMapper,
            CashFlowSubjectMapMapper subjectMapMapper,
            AccountSubjectMapper accountSubjectMapper,
            AuditMetadataFactory auditMetadataFactory
    ) {
        this.itemTemplateMapper = itemTemplateMapper;
        this.cashEquivalentSubjectMapper = cashEquivalentSubjectMapper;
        this.subjectMapMapper = subjectMapMapper;
        this.accountSubjectMapper = accountSubjectMapper;
        this.auditMetadataFactory = auditMetadataFactory;
    }

    @Transactional(readOnly = true)
    public CashFlowDefinition definition() {
        AuditMetadata audit = auditMetadataFactory.current();
        List<CashFlowItemTemplateEntity> templates = itemTemplateMapper.selectList(
                new LambdaQueryWrapper<CashFlowItemTemplateEntity>()
                        .eq(CashFlowItemTemplateEntity::getFormulaVersion, FORMULA_VERSION)
                        .eq(CashFlowItemTemplateEntity::getStatus, "ACTIVE")
                        .orderByAsc(CashFlowItemTemplateEntity::getSortNo)
                        .orderByAsc(CashFlowItemTemplateEntity::getItemCode)
        );
        if (templates.isEmpty()) {
            throw new IllegalStateException("现金流量项目模板不存在");
        }
        List<CashEquivalentSubjectEntity> cashSubjects = cashEquivalentSubjectMapper.selectList(
                new LambdaQueryWrapper<CashEquivalentSubjectEntity>()
                        .eq(CashEquivalentSubjectEntity::getCompanyId, audit.companyId())
                        .eq(CashEquivalentSubjectEntity::getAccountBookId, audit.accountBookId())
                        .eq(CashEquivalentSubjectEntity::getFormulaVersion, FORMULA_VERSION)
                        .orderByAsc(CashEquivalentSubjectEntity::getSubjectCode)
        );
        List<CashFlowSubjectMapEntity> mappings = subjectMapMapper.selectList(
                new LambdaQueryWrapper<CashFlowSubjectMapEntity>()
                        .eq(CashFlowSubjectMapEntity::getCompanyId, audit.companyId())
                        .eq(CashFlowSubjectMapEntity::getAccountBookId, audit.accountBookId())
                        .eq(CashFlowSubjectMapEntity::getFormulaVersion, FORMULA_VERSION)
                        .orderByAsc(CashFlowSubjectMapEntity::getSubjectCode)
        );
        return buildDefinition(templates, cashSubjects, mappings, audit);
    }

    @Transactional(readOnly = true)
    public CashFlowConfigurationResponse configuration() {
        return toResponse(definition());
    }

    @Transactional
    public CashFlowConfigurationResponse replaceCashSubjects(CashFlowSubjectCodesRequest request) {
        List<String> subjectCodes = normalizeCodes(request.subjectCodes());
        AuditMetadata audit = auditMetadataFactory.current();
        Map<String, AccountSubjectEntity> subjects = subjectsByCode(subjectCodes, audit);
        validateSubjectsExist(subjectCodes, subjects);
        List<String> nonAssets = subjectCodes.stream()
                .filter(code -> !"ASSET".equals(subjects.get(code).getSubjectType()))
                .toList();
        if (!nonAssets.isEmpty()) {
            throw new IllegalArgumentException("现金及现金等价物必须是资产类科目: " + String.join(", ", nonAssets));
        }
        List<CashFlowSubjectMapEntity> counterpartMappings = subjectMapMapper.selectList(
                new LambdaQueryWrapper<CashFlowSubjectMapEntity>()
                        .eq(CashFlowSubjectMapEntity::getCompanyId, audit.companyId())
                        .eq(CashFlowSubjectMapEntity::getAccountBookId, audit.accountBookId())
                        .eq(CashFlowSubjectMapEntity::getFormulaVersion, FORMULA_VERSION)
        );
        List<String> overlapping = counterpartMappings.stream()
                .map(CashFlowSubjectMapEntity::getSubjectCode)
                .filter(subjectCodes::contains)
                .distinct()
                .sorted()
                .toList();
        if (!overlapping.isEmpty()) {
            throw new IllegalArgumentException("对方科目映射不能同时设为现金科目: " + String.join(", ", overlapping));
        }
        List<CashEquivalentSubjectEntity> existing = cashEquivalentSubjectMapper.selectList(
                new LambdaQueryWrapper<CashEquivalentSubjectEntity>()
                        .eq(CashEquivalentSubjectEntity::getCompanyId, audit.companyId())
                        .eq(CashEquivalentSubjectEntity::getAccountBookId, audit.accountBookId())
                        .eq(CashEquivalentSubjectEntity::getFormulaVersion, FORMULA_VERSION)
        );
        if (!existing.isEmpty()) {
            cashEquivalentSubjectMapper.deleteByIds(existing.stream().map(CashEquivalentSubjectEntity::getId).toList());
        }
        LocalDateTime now = audit.now();
        for (String code : subjectCodes) {
            CashEquivalentSubjectEntity entity = new CashEquivalentSubjectEntity();
            setAudit(entity, audit, now);
            entity.setFormulaVersion(FORMULA_VERSION);
            entity.setSubjectCode(code);
            cashEquivalentSubjectMapper.insert(entity);
        }
        return configuration();
    }

    @Transactional
    public CashFlowConfigurationResponse replaceItemSubjects(
            String itemCode,
            CashFlowSubjectCodesRequest request
    ) {
        String normalizedItem = normalizeRequired(itemCode, "现金流量项目编码不能为空");
        CashFlowDefinition current = definition();
        if (current.items().stream().noneMatch(item -> item.itemCode().equals(normalizedItem))) {
            throw new IllegalArgumentException("现金流量项目不存在: " + normalizedItem);
        }
        List<String> subjectCodes = normalizeCodes(request.subjectCodes());
        if (subjectCodes.stream().anyMatch(current.cashSubjectCodes()::contains)) {
            throw new IllegalArgumentException("现金及现金等价物科目不能同时作为对方科目映射");
        }
        AuditMetadata audit = auditMetadataFactory.current();
        Map<String, AccountSubjectEntity> subjects = subjectsByCode(subjectCodes, audit);
        validateSubjectsExist(subjectCodes, subjects);
        List<CashFlowSubjectMapEntity> existing = subjectMapMapper.selectList(
                new LambdaQueryWrapper<CashFlowSubjectMapEntity>()
                        .eq(CashFlowSubjectMapEntity::getCompanyId, audit.companyId())
                        .eq(CashFlowSubjectMapEntity::getAccountBookId, audit.accountBookId())
                        .eq(CashFlowSubjectMapEntity::getFormulaVersion, FORMULA_VERSION)
        );
        List<Long> deleteIds = existing.stream()
                .filter(mapping -> normalizedItem.equals(mapping.getItemCode())
                        || subjectCodes.contains(mapping.getSubjectCode()))
                .map(CashFlowSubjectMapEntity::getId)
                .toList();
        if (!deleteIds.isEmpty()) {
            subjectMapMapper.deleteByIds(deleteIds);
        }
        LocalDateTime now = audit.now();
        for (String code : subjectCodes) {
            CashFlowSubjectMapEntity entity = new CashFlowSubjectMapEntity();
            entity.setCompanyId(audit.companyId());
            entity.setAccountBookId(audit.accountBookId());
            entity.setFormulaVersion(FORMULA_VERSION);
            entity.setItemCode(normalizedItem);
            entity.setSubjectCode(code);
            entity.setCreatedBy(audit.userId());
            entity.setCreatedTime(now);
            entity.setUpdatedBy(audit.userId());
            entity.setUpdatedTime(now);
            entity.setVersion(0);
            subjectMapMapper.insert(entity);
        }
        return configuration();
    }

    private CashFlowDefinition buildDefinition(
            List<CashFlowItemTemplateEntity> templates,
            List<CashEquivalentSubjectEntity> cashSubjects,
            List<CashFlowSubjectMapEntity> mappings,
            AuditMetadata audit
    ) {
        Map<String, CashFlowItemTemplateEntity> templatesByCode = templates.stream()
                .collect(Collectors.toMap(CashFlowItemTemplateEntity::getItemCode, Function.identity()));
        Set<String> cashCodes = cashSubjects.stream()
                .map(CashEquivalentSubjectEntity::getSubjectCode)
                .collect(Collectors.toCollection(LinkedHashSet::new));
        Set<String> allConfiguredCodes = new LinkedHashSet<>(cashCodes);
        mappings.stream().map(CashFlowSubjectMapEntity::getSubjectCode).forEach(allConfiguredCodes::add);
        Map<String, AccountSubjectEntity> subjects = subjectsByCode(List.copyOf(allConfiguredCodes), audit);
        List<String> errors = new ArrayList<>();
        if (cashCodes.isEmpty()) {
            errors.add("未配置现金及现金等价物科目");
        }
        for (String cashCode : cashCodes) {
            AccountSubjectEntity subject = subjects.get(cashCode);
            if (subject == null) {
                errors.add("现金范围引用不存在的会计科目: " + cashCode);
            } else if (!"ASSET".equals(subject.getSubjectType())) {
                errors.add("现金及现金等价物不是资产类科目: " + cashCode);
            }
        }

        Map<String, List<String>> subjectsByItem = new LinkedHashMap<>();
        Map<String, String> subjectItemCodes = new LinkedHashMap<>();
        for (CashFlowSubjectMapEntity mapping : mappings) {
            if (!templatesByCode.containsKey(mapping.getItemCode())) {
                errors.add("映射引用不存在的现金流量项目: " + mapping.getItemCode());
                continue;
            }
            if (!subjects.containsKey(mapping.getSubjectCode())) {
                errors.add("映射引用不存在的会计科目: " + mapping.getSubjectCode());
            }
            if (cashCodes.contains(mapping.getSubjectCode())) {
                errors.add("现金科目不能作为对方科目映射: " + mapping.getSubjectCode());
            }
            String previous = subjectItemCodes.putIfAbsent(mapping.getSubjectCode(), mapping.getItemCode());
            if (previous != null && !previous.equals(mapping.getItemCode())) {
                errors.add("会计科目重复映射: " + mapping.getSubjectCode());
                continue;
            }
            subjectsByItem.computeIfAbsent(mapping.getItemCode(), ignored -> new ArrayList<>())
                    .add(mapping.getSubjectCode());
        }
        List<CashFlowItemDefinition> items = templates.stream()
                .map(template -> new CashFlowItemDefinition(
                        template.getItemCode(), template.getItemName(), template.getActivityType(),
                        template.getSortNo(), List.copyOf(subjectsByItem.getOrDefault(template.getItemCode(), List.of()))
                ))
                .toList();
        return new CashFlowDefinition(
                FORMULA_VERSION,
                items,
                Set.copyOf(cashCodes),
                Map.copyOf(subjectItemCodes),
                errors.stream().distinct().sorted().toList()
        );
    }

    private CashFlowConfigurationResponse toResponse(CashFlowDefinition definition) {
        return new CashFlowConfigurationResponse(
                definition.formulaVersion(),
                definition.cashSubjectCodes().stream().sorted().toList(),
                definition.items().stream().map(item -> new CashFlowConfigurationItemResponse(
                        item.itemCode(), item.itemName(), item.activityType(), item.sortNo(), item.subjectCodes()
                )).toList(),
                definition.validationErrors()
        );
    }

    private Map<String, AccountSubjectEntity> subjectsByCode(List<String> codes, AuditMetadata audit) {
        if (codes.isEmpty()) return Map.of();
        return accountSubjectMapper.selectList(new LambdaQueryWrapper<AccountSubjectEntity>()
                        .eq(AccountSubjectEntity::getCompanyId, audit.companyId())
                        .eq(AccountSubjectEntity::getAccountBookId, audit.accountBookId())
                        .eq(AccountSubjectEntity::getDeletedFlag, 0)
                        .in(AccountSubjectEntity::getSubjectCode, codes))
                .stream()
                .collect(Collectors.toMap(AccountSubjectEntity::getSubjectCode, Function.identity()));
    }

    private void validateSubjectsExist(List<String> codes, Map<String, AccountSubjectEntity> subjects) {
        List<String> missing = codes.stream().filter(code -> !subjects.containsKey(code)).toList();
        if (!missing.isEmpty()) {
            throw new IllegalArgumentException("会计科目不存在: " + String.join(", ", missing));
        }
    }

    private List<String> normalizeCodes(List<String> codes) {
        if (codes == null) throw new IllegalArgumentException("科目列表不能为空");
        LinkedHashSet<String> normalized = new LinkedHashSet<>();
        for (String code : codes) normalized.add(normalizeRequired(code, "科目编码不能为空"));
        if (normalized.size() != codes.size()) throw new IllegalArgumentException("科目列表不能重复");
        return List.copyOf(normalized);
    }

    private String normalizeRequired(String value, String message) {
        if (!StringUtils.hasText(value)) throw new IllegalArgumentException(message);
        return value.trim();
    }

    private void setAudit(CashEquivalentSubjectEntity entity, AuditMetadata audit, LocalDateTime now) {
        entity.setCompanyId(audit.companyId());
        entity.setAccountBookId(audit.accountBookId());
        entity.setCreatedBy(audit.userId());
        entity.setCreatedTime(now);
        entity.setUpdatedBy(audit.userId());
        entity.setUpdatedTime(now);
        entity.setVersion(0);
    }
}
