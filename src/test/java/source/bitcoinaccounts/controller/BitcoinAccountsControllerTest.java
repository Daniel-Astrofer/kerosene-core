package source.bitcoinaccounts.controller;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import source.bitcoinaccounts.service.BitcoinAccountService;
import source.bitcoinaccounts.service.BitcoinTaxEventService;
import source.bitcoinaccounts.service.PsbtWorkflowService;
import source.bitcoinaccounts.service.ReceivingRequestService;
import source.common.dto.ApiResponse;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class BitcoinAccountsControllerTest {

    @Mock
    private BitcoinAccountService accountService;
    @Mock
    private ReceivingRequestService receivingRequestService;
    @Mock
    private PsbtWorkflowService psbtWorkflowService;
    @Mock
    private BitcoinTaxEventService taxEventService;

    @Mock
    private Authentication authentication;

    @InjectMocks
    private BitcoinAccountsController controller;

    @Test
    void testTaxEvents() {
        when(authentication.getName()).thenReturn("123");
        when(taxEventService.listTemporaryEvents(123L)).thenReturn(Collections.emptyList());

        ResponseEntity<ApiResponse<List<Map<String, Object>>>> res = controller.listTaxEvents(authentication);

        assertEquals(HttpStatus.OK, res.getStatusCode());
        assertNotNull(res.getBody());
    }

    @Test
    void testExportTaxEvents() {
        when(authentication.getName()).thenReturn("123");
        when(taxEventService.export(123L, "json")).thenReturn(Collections.emptyMap());

        ResponseEntity<ApiResponse<Map<String, Object>>> res = controller.exportTaxEvents(authentication, "json");

        assertEquals(HttpStatus.OK, res.getStatusCode());
    }

    @Test
    void listReceiveRequestsDelegatesToService() {
        when(authentication.getName()).thenReturn("123");
        UUID accountId = UUID.randomUUID();
        List<Map<String, Object>> requests = List.of(Map.of("accountId", accountId));
        when(receivingRequestService.listForAccount(123L, accountId)).thenReturn(requests);

        ResponseEntity<ApiResponse<List<Map<String, Object>>>> res =
                controller.listReceiveRequests(authentication, accountId);

        assertEquals(HttpStatus.OK, res.getStatusCode());
        assertNotNull(res.getBody());
        assertEquals(requests, res.getBody().getData());
        verify(receivingRequestService).listForAccount(123L, accountId);
    }

    @Test
    void testClassifyTaxEvent() {
        when(authentication.getName()).thenReturn("123");
        UUID id = UUID.randomUUID();
        BitcoinAccountsController.ClassifyTaxEventRequest req = new BitcoinAccountsController.ClassifyTaxEventRequest("GIFT");

        ResponseEntity<ApiResponse<Map<String, Object>>> res = controller.classifyTaxEvent(authentication, id, req);

        verify(taxEventService).classify(123L, id, "GIFT");
        assertEquals(HttpStatus.OK, res.getStatusCode());
    }
}
