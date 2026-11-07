package com.tuowei.erp.commercial.contract.service;

import com.tuowei.erp.common.web.PageResponse;
import com.tuowei.erp.system.attachment.service.AttachmentBusinessType;
import com.tuowei.erp.system.attachment.service.AttachmentService;
import com.tuowei.erp.system.attachment.web.AttachmentPageQuery;
import com.tuowei.erp.system.attachment.web.AttachmentResponse;
import org.springframework.core.io.Resource;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

@Service
public class ContractAttachmentService {
    private final ContractQueryService contractQueryService;
    private final AttachmentService attachmentService;

    public ContractAttachmentService(ContractQueryService contractQueryService, AttachmentService attachmentService) {
        this.contractQueryService = contractQueryService;
        this.attachmentService = attachmentService;
    }

    public PageResponse<AttachmentResponse> list(Long contractId, Integer pageNo, Integer pageSize) {
        contractQueryService.requireContract(contractId);
        AttachmentPageQuery query = new AttachmentPageQuery();
        query.setPageNo(pageNo); query.setPageSize(pageSize);
        query.setBusinessType(AttachmentBusinessType.COMMERCIAL_CONTRACT); query.setBusinessId(contractId);
        return attachmentService.list(query);
    }

    public AttachmentResponse upload(Long contractId, MultipartFile file) {
        var contract = contractQueryService.requireContract(contractId);
        return attachmentService.upload(AttachmentBusinessType.COMMERCIAL_CONTRACT, contractId, contract.getContractNo(), file);
    }

    public ResponseEntity<Resource> download(Long contractId, Long attachmentId) {
        contractQueryService.requireContract(contractId);
        return attachmentService.downloadForBusiness(attachmentId, AttachmentBusinessType.COMMERCIAL_CONTRACT, contractId);
    }

    public void delete(Long contractId, Long attachmentId) {
        contractQueryService.requireContract(contractId);
        attachmentService.deleteForBusiness(attachmentId, AttachmentBusinessType.COMMERCIAL_CONTRACT, contractId);
    }
}
