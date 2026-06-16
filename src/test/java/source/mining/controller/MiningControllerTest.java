package source.mining.controller;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import source.common.dto.ApiResponse;
import source.mining.dto.MiningAllocationRequestDTO;
import source.mining.dto.MiningAllocationResponseDTO;
import source.mining.dto.MiningRigOfferDTO;
import source.mining.service.MiningService;

import java.util.Collections;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.mock;

@ExtendWith(MockitoExtension.class)
class MiningControllerTest {

    @Mock
    private MiningService miningService;

    @Mock
    private Authentication authentication;

    @InjectMocks
    private MiningController miningController;

    @BeforeEach
    void setUp() {
    }

    @Test
    void listRigOffers_success() {
        MiningRigOfferDTO mockOffer = org.mockito.Mockito.mock(MiningRigOfferDTO.class);
        when(miningService.listRigOffers()).thenReturn(Collections.singletonList(mockOffer));

        ResponseEntity<ApiResponse<List<MiningRigOfferDTO>>> response = miningController.listRigOffers();

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals(1, response.getBody().getData().size());
    }

    @Test
    void createAllocation_success() {
        when(authentication.getName()).thenReturn("123");
        MiningAllocationRequestDTO request = org.mockito.Mockito.mock(MiningAllocationRequestDTO.class);
        MiningAllocationResponseDTO mockResponse = org.mockito.Mockito.mock(MiningAllocationResponseDTO.class);
        when(miningService.createAllocation(123L, request)).thenReturn(mockResponse);

        ResponseEntity<ApiResponse<MiningAllocationResponseDTO>> response = miningController.createAllocation(request, authentication);

        assertEquals(HttpStatus.CREATED, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals(mockResponse, response.getBody().getData());
    }

    @Test
    void listAllocations_success() {
        when(authentication.getName()).thenReturn("123");
        MiningAllocationResponseDTO mockResponse = org.mockito.Mockito.mock(MiningAllocationResponseDTO.class);
        when(miningService.listAllocations(123L)).thenReturn(Collections.singletonList(mockResponse));

        ResponseEntity<ApiResponse<List<MiningAllocationResponseDTO>>> response = miningController.listAllocations(authentication);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals(1, response.getBody().getData().size());
    }

    @Test
    void getAllocation_success() {
        when(authentication.getName()).thenReturn("123");
        UUID id = UUID.randomUUID();
        MiningAllocationResponseDTO mockResponse = org.mockito.Mockito.mock(MiningAllocationResponseDTO.class);
        when(miningService.getAllocation(123L, id)).thenReturn(mockResponse);

        ResponseEntity<ApiResponse<MiningAllocationResponseDTO>> response = miningController.getAllocation(id, authentication);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals(mockResponse, response.getBody().getData());
    }

    @Test
    void cancelAllocation_success() {
        when(authentication.getName()).thenReturn("123");
        UUID id = UUID.randomUUID();
        MiningAllocationResponseDTO mockResponse = org.mockito.Mockito.mock(MiningAllocationResponseDTO.class);
        when(miningService.cancelAllocation(123L, id)).thenReturn(mockResponse);

        ResponseEntity<ApiResponse<MiningAllocationResponseDTO>> response = miningController.cancelAllocation(id, authentication);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals(mockResponse, response.getBody().getData());
    }
}
