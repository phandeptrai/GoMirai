package com.gomirai.booking.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.test.util.ReflectionTestUtils;

import com.gomirai.booking.client.MapServiceClient;
import com.gomirai.booking.client.PaymentServiceClient;
import com.gomirai.booking.client.PricingServiceClient;
import com.gomirai.booking.client.TrackingServiceClient;
import com.gomirai.booking.dto.external.MapServiceRouteResponse;
import com.gomirai.booking.dto.external.PricingServiceResponse;
import com.gomirai.booking.dto.external.TransactionResponse;
import com.gomirai.booking.dto.request.CancelBookingRequest;
import com.gomirai.booking.dto.request.CompleteBookingRequest;
import com.gomirai.booking.dto.request.CreateBookingRequest;
import com.gomirai.booking.dto.response.BookingResponse;
import com.gomirai.booking.enums.BookingStatus;
import com.gomirai.booking.enums.PaymentMethod;
import com.gomirai.common.enums.VehicleType;
import com.gomirai.booking.messaging.BookingEventsProducer;
import com.gomirai.common.dto.event.BookingCompletedEvent;
import com.gomirai.common.dto.event.RefundRequestedEvent;
import com.gomirai.booking.model.AddressSnapshot;
import com.gomirai.booking.model.Booking;
import com.gomirai.booking.model.BookingPriceSnapshot;
import com.gomirai.booking.repository.BookingRepository;
import com.gomirai.common.exception.BusinessException;
import com.gomirai.common.security.SecurityUtils;

@ExtendWith(MockitoExtension.class)
class BookingServiceTest {

    @Mock
    private BookingRepository bookingRepository;
    @Mock
    private MapServiceClient mapServiceClient;
    @Mock
    private PricingServiceClient pricingServiceClient;
    @Mock
    private PaymentServiceClient paymentServiceClient;
    @Mock
    private TrackingServiceClient trackingServiceClient;
    @Mock
    private BookingEventsProducer eventsProducer;
    @Mock
    private BookingPersistenceService bookingPersistenceService;
    @Mock
    private SecurityUtils securityUtils;
    @Mock
    private MongoTemplate mongoTemplate;

    @InjectMocks
    private BookingService bookingService;

    private UUID customerId;
    private UUID driverId;
    private AddressSnapshot pickup;
    private AddressSnapshot dropoff;
    private Booking mockBooking;

    @BeforeEach
    void setUp() {
        customerId = UUID.randomUUID();
        driverId = UUID.randomUUID();
        pickup = new AddressSnapshot("123 Pickup St", 10.762622, 106.660172);
        dropoff = new AddressSnapshot("456 Dropoff St", 10.773177, 106.700147);

        mockBooking = new Booking();
        mockBooking.setBookingId(UUID.randomUUID());
        mockBooking.setCustomerId(customerId);
        mockBooking.setDriverId(driverId);
        mockBooking.setStatus(BookingStatus.PENDING);
        mockBooking.setPrice(new BookingPriceSnapshot());
        mockBooking.getPrice().setFinalAmount(50000.0);
        mockBooking.getPrice().setCurrency("VND");

        // Mock default behavior for security
        lenient().when(securityUtils.getCurrentUserId()).thenReturn(customerId);

        // Setup configuration values (normally from @Value)
        ReflectionTestUtils.setField(bookingService, "driverSearchRadiusMeters", 2000.0);
        ReflectionTestUtils.setField(bookingService, "mongoTemplate", mongoTemplate);
    }

    @Test
    @DisplayName("1. Create Booking - Success (CASH)")
    void createBooking_Cash_Success() {
        // Arrange
        CreateBookingRequest request = new CreateBookingRequest(
                pickup, dropoff, VehicleType.CAR_4, PaymentMethod.CASH, null, "Note", "idemp-1");

        when(bookingRepository.findByIdempotencyKey("idemp-1")).thenReturn(Optional.empty());
        when(mapServiceClient.getRoute(any(), any()))
                .thenReturn(new MapServiceRouteResponse(5000.0, 600, null, "polyline", null));
        when(pricingServiceClient.estimateFare(anyString(), anyDouble(), anyInt(), anyString()))
                .thenReturn(new PricingServiceResponse(50000L, UUID.randomUUID()));
        when(bookingPersistenceService.saveBookingAndScheduleDriverSearch(any(Booking.class), any()))
                .thenAnswer(i -> i.getArgument(0));

        // Act
        BookingResponse response = bookingService.createBooking(request);

        // Assert
        assertNotNull(response);
        assertEquals(BookingStatus.PENDING, response.getStatus());
        verify(bookingPersistenceService).saveBookingAndScheduleDriverSearch(any(Booking.class), any());
        verify(paymentServiceClient, never()).payRide(any());
    }

    @Test
    @DisplayName("2. Create Booking - Idempotency Key Exists")
    void createBooking_IdempotencyKeyExists_ThrowsException() {
        // Arrange
        CreateBookingRequest request = new CreateBookingRequest(
                pickup, dropoff, VehicleType.CAR_4, PaymentMethod.CASH, null, "Note", "idemp-existing");
        when(bookingRepository.findByIdempotencyKey("idemp-existing")).thenReturn(Optional.of(new Booking()));

        // Act & Assert
        assertThrows(BusinessException.class, () -> bookingService.createBooking(request));
    }

    @Test
    @DisplayName("3. Create Booking - Wallet Payment Failure")
    void createBooking_WalletPaymentFailed_ThrowsException() {
        // Arrange
        CreateBookingRequest request = new CreateBookingRequest(
                pickup, dropoff, VehicleType.CAR_4, PaymentMethod.WALLET, null, "Note", "idemp-wallet");

        when(mapServiceClient.getRoute(any(), any()))
                .thenReturn(new MapServiceRouteResponse(5000.0, 600, null, "polyline", null));
        when(pricingServiceClient.estimateFare(anyString(), anyDouble(), anyInt(), anyString()))
                .thenReturn(new PricingServiceResponse(50000L, UUID.randomUUID()));

        // Mock payment failure
        TransactionResponse failedResponse = new TransactionResponse();
        failedResponse.setStatus("FAILED");
        when(paymentServiceClient.payRide(any())).thenReturn(failedResponse);

        // Act & Assert
        assertThrows(BusinessException.class, () -> bookingService.createBooking(request));
        verify(bookingPersistenceService, never()).saveBookingAndScheduleDriverSearch(any(), any());
    }

    @Test
    @DisplayName("4. Accept Booking - Success (Atomic)")
    void acceptBooking_Success() {
        // Arrange
        UUID bId = mockBooking.getBookingId();
        when(securityUtils.getCurrentUserId()).thenReturn(driverId);

        mockBooking.setStatus(BookingStatus.MATCHED);
        when(mongoTemplate.findAndModify(any(), any(), any(), eq(Booking.class))).thenReturn(mockBooking);

        // Act
        BookingResponse response = bookingService.acceptBooking(bId);

        // Assert
        assertNotNull(response);
        assertEquals(BookingStatus.MATCHED, response.getStatus());
        verify(eventsProducer).publishBookingAssignedEvent(any());
    }

    @Test
    @DisplayName("5. Accept Booking - Already Accepted")
    void acceptBooking_AlreadyAccepted_ThrowsException() {
        // Arrange
        UUID bId = mockBooking.getBookingId();
        when(securityUtils.getCurrentUserId()).thenReturn(UUID.randomUUID());

        // Mock findAndModify returns null (condition not met)
        when(mongoTemplate.findAndModify(any(), any(), any(), eq(Booking.class))).thenReturn(null);

        // Mock repository findById to return an already matching booking
        Booking existingBooking = new Booking();
        existingBooking.setStatus(BookingStatus.MATCHED);
        when(bookingRepository.findById(bId)).thenReturn(Optional.of(existingBooking));

        // Act & Assert
        assertThrows(BusinessException.class, () -> bookingService.acceptBooking(bId));
    }

    @Test
    @DisplayName("6. Driver Arrived SUCCESS")
    void driverArrived_Success() {
        // Arrange
        when(securityUtils.getCurrentUserId()).thenReturn(driverId);
        mockBooking.setStatus(BookingStatus.MATCHED);
        when(bookingRepository.findById(mockBooking.getBookingId())).thenReturn(Optional.of(mockBooking));
        when(bookingRepository.save(any())).thenAnswer(i -> i.getArgument(0));

        // Act
        BookingResponse response = bookingService.driverArrived(mockBooking.getBookingId());

        // Assert
        assertEquals(BookingStatus.DRIVER_ARRIVED, response.getStatus());
        assertNotNull(mockBooking.getActualPickupTime());
    }

    @Test
    @DisplayName("7. Complete Booking SUCCESS")
    void completeBooking_Success() {
        // Arrange
        when(securityUtils.getCurrentUserId()).thenReturn(driverId);
        mockBooking.setStatus(BookingStatus.IN_PROGRESS);
        when(bookingRepository.findById(mockBooking.getBookingId())).thenReturn(Optional.of(mockBooking));
        when(bookingRepository.save(any())).thenAnswer(i -> i.getArgument(0));

        CompleteBookingRequest request = new CompleteBookingRequest();
        request.setActualDistanceKm(5.2);
        request.setActualDurationMinutes(12);

        // Act
        BookingResponse response = bookingService.completeBooking(mockBooking.getBookingId(), request);

        // Assert
        assertEquals(BookingStatus.COMPLETED, response.getStatus());
        verify(eventsProducer).publishBookingCompletedEvent(any(BookingCompletedEvent.class));
    }

    @Test
    @DisplayName("8. Cancel Booking - Wallet Refund Requested")
    void cancelBooking_Wallet_Success() {
        // Arrange
        when(securityUtils.getCurrentUserId()).thenReturn(customerId);
        mockBooking.setStatus(BookingStatus.PENDING);
        mockBooking.setPaymentMethod(PaymentMethod.WALLET);
        when(bookingRepository.findById(mockBooking.getBookingId())).thenReturn(Optional.of(mockBooking));
        when(bookingRepository.save(any())).thenAnswer(i -> i.getArgument(0));

        CancelBookingRequest request = new CancelBookingRequest("Changed my mind");

        // Act
        BookingResponse response = bookingService.cancelBooking(mockBooking.getBookingId(), request);

        // Assert
        assertEquals(BookingStatus.CANCELED, response.getStatus());
        verify(eventsProducer).publishRefundRequestedEvent(any(RefundRequestedEvent.class));
    }

    @Test
    @DisplayName("9. Cancel Booking - No Driver Found SUCCESS")
    void cancelBookingNoDriverFound_Success() {
        // Arrange
        mockBooking.setStatus(BookingStatus.PENDING);
        when(bookingRepository.findById(mockBooking.getBookingId())).thenReturn(Optional.of(mockBooking));
        when(bookingRepository.save(any())).thenAnswer(i -> i.getArgument(0));

        // Act
        BookingResponse response = bookingService.cancelBookingNoDriverFound(mockBooking.getBookingId(), "Timeout");

        // Assert
        assertEquals(BookingStatus.NO_DRIVER_FOUND, response.getStatus());
    }
}
