package com.tuowei.erp.finance.currency.service;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.tuowei.erp.common.security.AuditMetadataFactory;
import com.tuowei.erp.finance.currency.mapper.CurrencyMapper;
import com.tuowei.erp.finance.currency.mapper.ExchangeRateMapper;
import com.tuowei.erp.finance.currency.model.CurrencyEntity;
import com.tuowei.erp.finance.currency.model.ExchangeRateEntity;
import com.tuowei.erp.finance.currency.web.*;
import org.springframework.stereotype.Service; import org.springframework.transaction.annotation.Transactional;
import java.time.LocalDate; import java.util.List;
@Service public class CurrencyAdminService {
 private final CurrencyMapper currencies; private final ExchangeRateMapper rates; private final AuditMetadataFactory audit;
 public CurrencyAdminService(CurrencyMapper c,ExchangeRateMapper r,AuditMetadataFactory a){currencies=c;rates=r;audit=a;}
 @Transactional(readOnly=true) public List<CurrencyResponse> currencies(){return currencies.selectList(new LambdaQueryWrapper<CurrencyEntity>().eq(CurrencyEntity::getDeletedFlag,0).orderByAsc(CurrencyEntity::getCurrencyCode)).stream().map(e->new CurrencyResponse(e.getId(),e.getCurrencyCode(),e.getCurrencyName(),e.getCurrencySymbol(),e.getDecimalPlaces(),e.getStatus())).toList();}
 @Transactional(readOnly=true) public List<ExchangeRateResponse> rates(String from,String to){var a=audit.current(); return rates.selectList(new LambdaQueryWrapper<ExchangeRateEntity>().eq(ExchangeRateEntity::getCompanyId,a.companyId()).eq(ExchangeRateEntity::getAccountBookId,a.accountBookId()).eq(from!=null,ExchangeRateEntity::getFromCurrencyCode,from).eq(to!=null,ExchangeRateEntity::getToCurrencyCode,to).orderByDesc(ExchangeRateEntity::getEffectiveFrom)).stream().map(e->new ExchangeRateResponse(e.getId(),e.getFromCurrencyCode(),e.getToCurrencyCode(),e.getRate(),e.getEffectiveFrom(),e.getEffectiveTo(),e.getStatus())).toList();}
 @Transactional public ExchangeRateResponse create(ExchangeRateRequest r){if(r.effectiveTo()!=null&&r.effectiveTo().isBefore(r.effectiveFrom()))throw new IllegalArgumentException("effectiveTo must not be before effectiveFrom"); var a=audit.current(); var q=new LambdaQueryWrapper<ExchangeRateEntity>().eq(ExchangeRateEntity::getCompanyId,a.companyId()).eq(ExchangeRateEntity::getAccountBookId,a.accountBookId()).eq(ExchangeRateEntity::getFromCurrencyCode,r.fromCurrencyCode()).eq(ExchangeRateEntity::getToCurrencyCode,r.toCurrencyCode()); for(var e:rates.selectList(q)){if((e.getEffectiveTo()==null||!r.effectiveFrom().isAfter(e.getEffectiveTo()))&&(r.effectiveTo()==null||!r.effectiveTo().isBefore(e.getEffectiveFrom())))throw new IllegalArgumentException("exchange rate date range overlaps");} var e=new ExchangeRateEntity();e.setCompanyId(a.companyId());e.setAccountBookId(a.accountBookId());e.setFromCurrencyCode(r.fromCurrencyCode().toUpperCase());e.setToCurrencyCode(r.toCurrencyCode().toUpperCase());e.setRate(r.rate());e.setEffectiveFrom(r.effectiveFrom());e.setEffectiveTo(r.effectiveTo());e.setStatus("ENABLED");rates.insert(e);return new ExchangeRateResponse(e.getId(),e.getFromCurrencyCode(),e.getToCurrencyCode(),e.getRate(),e.getEffectiveFrom(),e.getEffectiveTo(),e.getStatus());}
}
