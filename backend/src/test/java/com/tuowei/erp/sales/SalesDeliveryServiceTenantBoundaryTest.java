package com.tuowei.erp.sales;

import com.tuowei.erp.common.web.PageResponse;
import com.tuowei.erp.sales.delivery.service.SalesDeliveryCommandService;
import com.tuowei.erp.sales.delivery.service.SalesDeliveryPostingService;
import com.tuowei.erp.sales.delivery.service.SalesDeliveryQueryService;
import com.tuowei.erp.sales.delivery.service.SalesDeliveryService;
import com.tuowei.erp.sales.delivery.web.SalesDeliveryCreateRequest;
import com.tuowei.erp.sales.delivery.web.SalesDeliveryLogisticsUpdateRequest;
import com.tuowei.erp.sales.delivery.web.SalesDeliveryPageQuery;
import com.tuowei.erp.sales.delivery.web.SalesDeliveryResponse;
import com.tuowei.erp.sales.delivery.web.SalesDeliveryUpdateRequest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SalesDeliveryServiceTenantBoundaryTest {

    @Mock
    private SalesDeliveryQueryService salesDeliveryQueryService;

    @Mock
    private SalesDeliveryPostingService salesDeliveryPostingService;

    @Mock
    private SalesDeliveryCommandService salesDeliveryCommandService;

    @Test
    void getByIdDelegatesToQueryService() {
        SalesDeliveryResponse expected = response();
        when(salesDeliveryQueryService.getById(7001L)).thenReturn(expected);

        SalesDeliveryResponse result = service().getById(7001L);

        assertThat(result).isSameAs(expected);
        verify(salesDeliveryQueryService).getById(7001L);
    }

    @Test
    void listDelegatesTheOriginalQueryToQueryService() {
        SalesDeliveryPageQuery query = new SalesDeliveryPageQuery();
        PageResponse<SalesDeliveryResponse> expected = new PageResponse<>(1L, 20L, 0L, List.of());
        when(salesDeliveryQueryService.list(query)).thenReturn(expected);

        PageResponse<SalesDeliveryResponse> result = service().list(query);

        assertThat(result).isSameAs(expected);
        verify(salesDeliveryQueryService).list(query);
    }

    @Test
    void listNormalizesNullQueryBeforeDelegating() {
        service().list(null);

        verify(salesDeliveryQueryService).list(org.mockito.ArgumentMatchers.any(SalesDeliveryPageQuery.class));
    }

    @Test
    void createDelegatesToCommandService() {
        SalesDeliveryCreateRequest request = org.mockito.Mockito.mock(SalesDeliveryCreateRequest.class);
        SalesDeliveryResponse expected = response();
        when(salesDeliveryCommandService.create(request)).thenReturn(expected);

        SalesDeliveryResponse result = service().create(request);

        assertThat(result).isSameAs(expected);
        verify(salesDeliveryCommandService).create(request);
    }

    @Test
    void updateDelegatesToCommandService() {
        SalesDeliveryUpdateRequest request = org.mockito.Mockito.mock(SalesDeliveryUpdateRequest.class);
        SalesDeliveryResponse expected = response();
        when(salesDeliveryCommandService.update(7001L, request)).thenReturn(expected);

        SalesDeliveryResponse result = service().update(7001L, request);

        assertThat(result).isSameAs(expected);
        verify(salesDeliveryCommandService).update(7001L, request);
    }

    @Test
    void cancelDelegatesToCommandService() {
        SalesDeliveryResponse expected = response();
        when(salesDeliveryCommandService.cancel(7001L)).thenReturn(expected);

        SalesDeliveryResponse result = service().cancel(7001L);

        assertThat(result).isSameAs(expected);
        verify(salesDeliveryCommandService).cancel(7001L);
    }

    @Test
    void updateLogisticsDelegatesToCommandService() {
        SalesDeliveryLogisticsUpdateRequest request =
                org.mockito.Mockito.mock(SalesDeliveryLogisticsUpdateRequest.class);
        SalesDeliveryResponse expected = response();
        when(salesDeliveryCommandService.updateLogistics(7001L, request)).thenReturn(expected);

        SalesDeliveryResponse result = service().updateLogistics(7001L, request);

        assertThat(result).isSameAs(expected);
        verify(salesDeliveryCommandService).updateLogistics(7001L, request);
    }

    @Test
    void postDelegatesToPostingService() {
        SalesDeliveryResponse expected = response();
        when(salesDeliveryPostingService.post(7001L)).thenReturn(expected);

        SalesDeliveryResponse result = service().post(7001L);

        assertThat(result).isSameAs(expected);
        verify(salesDeliveryPostingService).post(7001L);
    }

    private SalesDeliveryService service() {
        return new SalesDeliveryService(
                salesDeliveryQueryService,
                salesDeliveryPostingService,
                salesDeliveryCommandService
        );
    }

    private SalesDeliveryResponse response() {
        return org.mockito.Mockito.mock(SalesDeliveryResponse.class);
    }
}
