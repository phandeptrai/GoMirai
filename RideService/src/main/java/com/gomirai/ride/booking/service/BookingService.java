package com.gomirai.ride.booking.service;

import com.gomirai.ride.booking.client.MapServiceClient;
import com.gomirai.ride.booking.client.PaymentServiceClient;
import com.gomirai.ride.booking.client.PricingServiceClient;
import com.gomirai.ride.booking.client.TrackingServiceClient;
import com.gomirai.ride.booking.dto.external.RidePaymentRequest;
import com.gomirai.ride.booking.dto.external.TransactionResponse;
import com.gomirai.common.dto.event.RefundRequestedEvent;
import com.gomirai.ride.booking.dto.external.DriverGeoStateResponse;
import com.gomirai.ride.booking.dto.external.GeoPoint;
import com.gomirai.ride.booking.dto.external.MapServiceRouteResponse;
import com.gomirai.ride.booking.dto.external.PricingServiceResponse;
import com.gomirai.ride.booking.dto.request.CancelBookingRequest;
import com.gomirai.ride.booking.dto.request.CompleteBookingRequest;
import com.gomirai.ride.booking.dto.request.CreateBookingRequest;
import com.gomirai.ride.booking.dto.response.BookingResponse;
import com.gomirai.ride.booking.enums.BookingStatus;
import com.gomirai.common.dto.event.BookingAssignedEvent;
import com.gomirai.common.dto.event.BookingCompletedEvent;
import com.gomirai.common.dto.event.BookingSearchDriversEvent;
import com.gomirai.common.dto.event.DriverAcceptedEvent;
import com.gomirai.common.dto.event.DriverDeclinedEvent;
import com.gomirai.ride.booking.messaging.BookingEventsProducer;
import com.gomirai.common.dto.event.BookingStatusChangedEvent;
import com.gomirai.ride.booking.model.AddressSnapshot;
import com.gomirai.ride.booking.model.Booking;
import com.gomirai.ride.booking.model.BookingPriceSnapshot;
import com.gomirai.ride.booking.repository.BookingRepository;
import com.gomirai.common.exception.BusinessException;
import com.gomirai.common.exception.NotFoundException;
import com.gomirai.common.security.SecurityUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.geo.Point;
import org.springframework.data.geo.Distance;
import org.springframework.data.geo.Metrics;

/**
 * Service xử lý logic đặt xe.
 * 
 * Các chức năng chính:
 * 1. Tạo booking mới (createBooking) - Saga Pattern
 * 2. Tài xế nhận cuốc (acceptBooking) - Atomic update tránh race condition
 * 3. Cập nhật trạng thái: MATCHED → DRIVER_ARRIVED → IN_PROGRESS → COMPLETED
 * 4. Hủy booking và hoàn tiền tự động
 * 
 * Luồng tạo booking (Saga Pattern):
 * 1. Validate input
 * 2. Gọi MapService để tính route
 * 3. Gọi PricingService để tính giá
 * 4. Trừ tiền Wallet nếu thanh toán ví (PaymentService)
 * 5. Lưu booking vào DB
 * 6. Publish event tìm tài xế (TrackingService)
 * 
 * First-accept-wins:
 * - Sử dụng MongoDB atomic update để chỉ 1 tài xế nhận được cuốc
 * - Tránh race condition khi nhiều tài xế accept cùng lúc
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class BookingService {

    private final BookingRepository bookingRepository;
    private final MapServiceClient mapServiceClient;
    private final PricingServiceClient pricingServiceClient;
    private final PaymentServiceClient paymentServiceClient;
    private final TrackingServiceClient trackingServiceClient;
    private final BookingEventsProducer eventsProducer;
    private final BookingPersistenceService bookingPersistenceService;
    private final SecurityUtils securityUtils;
    // WebSocket removed - now handled by NotificationService

    @Autowired
    private MongoTemplate mongoTemplate;

    @Value("${booking.driver-search-radius-meters:2000}")
    private double driverSearchRadiusMeters;

    @Value("${booking.expiry-timeout-minutes:10}")
    private int expiryTimeoutMinutes;

    @Value("${booking.driver-nearby-radius-km:5.0}")
    private double driverNearbyRadiusKm;

    /**
     * Create booking:
     * <ul>
     * <li>Pre-processing (no {@code @Transactional}): validate, Map, Pricing,
     * Wallet payment — minimizes time
     * holding DB resources.</li>
     * <li>Persistence: short transactional
     * {@link BookingPersistenceService#saveBookingAndScheduleDriverSearch}
     * only around Mongo save.</li>
     * <li>Post-processing: Kafka driver-search published after commit (see
     * persistence service).</li>
     * </ul>
     */
    public BookingResponse createBooking(CreateBookingRequest request) {
        UUID customerId = securityUtils.getCurrentUserId();

        validateBookingRequest(request);

        AddressSnapshot pickupLocation = normalizeLocation(request.getPickupLocation());
        AddressSnapshot dropoffLocation = normalizeLocation(request.getDropoffLocation());

        Booking booking = new Booking();
        booking.setBookingId(UUID.randomUUID());
        booking.setCustomerId(customerId);
        booking.setStatus(BookingStatus.PENDING);
        booking.setPickupLocation(pickupLocation);
        booking.setDropoffLocation(dropoffLocation);
        booking.setVehicleType(request.getVehicleType());
        booking.setPaymentMethod(request.getPaymentMethod());
        booking.setScheduledAt(request.getScheduledAt());
        booking.setNotes(request.getNotes());
        booking.setIdempotencyKey(request.getIdempotencyKey());
        booking.setCreatedAt(LocalDateTime.now());
        booking.setUpdatedAt(LocalDateTime.now());
        
        // Use client-provided metrics if available
        if (request.getEstimatedDistanceKm() != null) {
            booking.setEstimatedDistanceKm(request.getEstimatedDistanceKm());
        }
        if (request.getEstimatedDurationMinutes() != null) {
            booking.setEstimatedDurationMinutes(request.getEstimatedDurationMinutes());
        }

        booking.setCreatedAt(LocalDateTime.now());
        booking.setUpdatedAt(LocalDateTime.now());

        // Saga Pattern (Async): Initial status based on payment method
        if (request.getPaymentMethod() == com.gomirai.ride.booking.enums.PaymentMethod.WALLET) {
            booking.setStatus(BookingStatus.PENDING_PAYMENT);
        } else {
            booking.setStatus(BookingStatus.CREATED);
        }

        // Atomic Outbox: Save booking and Outbox event in same transaction
        Booking savedBooking;
        try {
            com.gomirai.common.dto.event.BookingCreatedEvent createdEvent = new com.gomirai.common.dto.event.BookingCreatedEvent(
                    null, // bookingId set later by persistence
                    customerId,
                    null, // Amount not yet calculated
                    "VND",
                    booking.getPaymentMethod().name(),
                    booking.getVehicleType().name(),
                    booking.getPickupLocation().getLatitude(),
                    booking.getPickupLocation().getLongitude(),
                    booking.getDropoffLocation().getLatitude(),
                    booking.getDropoffLocation().getLongitude());

            // This replaces saveBookingOnly + eventsProducer.publish
            savedBooking = bookingPersistenceService.saveBookingWithOutbox(booking, createdEvent);
        } catch (DuplicateKeyException e) {
            // Idempotency: unique index on idempotencyKey turns this into single-trip insert.
            if (request.getIdempotencyKey() != null) {
                Booking existing = bookingRepository.findByIdempotencyKey(request.getIdempotencyKey())
                        .orElseThrow(() -> e);
                return toResponse(existing);
            }
            throw e;
        }

        log.info("OUTBOX ENQUEUE SUCCESS: bookingId={}, status={}, customerId={}",
                savedBooking.getBookingId(), savedBooking.getStatus(), customerId);

        return toResponse(savedBooking);
    }

    /**
     * BACKGROUND ENRICHMENT: Called by Kafka Saga Consumer
     * Performs Map/Pricing calls asynchronously and finally sets status to PENDING
     */
    @org.springframework.scheduling.annotation.Async("enrichmentExecutor")
    public void enrichAndReadyBooking(Booking booking) {
        log.info("ENRICHING bookingId={} (Async Virtual Thread)...", booking.getBookingId());

        try {
            // STEP 1: Map Service (Background) - Bypass if already provided by client
            if (booking.getEstimatedDistanceKm() == null || booking.getEstimatedDurationMinutes() == null) {
                log.info("Calling Map Service for bookingId={} (No client-side metrics)", booking.getBookingId());
                MapServiceRouteResponse routeResponse = callMapService(
                        booking.getPickupLocation(),
                        booking.getDropoffLocation());

                booking.setEstimatedDistanceKm(routeResponse.getDistance() / 1000.0);
                booking.setEstimatedDurationMinutes(routeResponse.getDuration() / 60);
                booking.setRoutePolyline(routeResponse.getPolyline());
            } else {
                log.info("Bypassing Map Service for bookingId={} (Using client-side metrics)", booking.getBookingId());
            }

            // STEP 2: Pricing Service (Background)
            String vehicleTypeForPricing = convertVehicleTypeForPricing(booking.getVehicleType());
            String region = extractRegionFromLocation(booking.getPickupLocation());

            PricingServiceResponse pricingResponse = callPricingService(
                    vehicleTypeForPricing,
                    booking.getEstimatedDistanceKm(),
                    booking.getEstimatedDurationMinutes(),
                    region);

            BookingPriceSnapshot priceSnapshot = new BookingPriceSnapshot();
            priceSnapshot.setBaseFare((double) pricingResponse.getEstimatedFare() * 0.4);
            priceSnapshot.setDistanceFare((double) pricingResponse.getEstimatedFare() * 0.5);
            priceSnapshot.setTimeFare((double) pricingResponse.getEstimatedFare() * 0.1);
            priceSnapshot.setSurgeMultiplier(1.0);
            priceSnapshot.setDiscount(0.0);
            priceSnapshot.setFinalAmount((double) pricingResponse.getEstimatedFare());
            priceSnapshot.setCurrency("VND");
            priceSnapshot.setEstimatedDistanceKm(booking.getEstimatedDistanceKm());
            priceSnapshot.setEstimatedDurationMinutes(booking.getEstimatedDurationMinutes());
            priceSnapshot.setPricingRuleId(pricingResponse.getAppliedRuleId().toString());

            booking.setPrice(priceSnapshot);

            // STEP 3: ACTIVATE! Change status from CONFIRMED to PENDING (for drivers to
            // see)
            BookingStatus oldStatus = booking.getStatus();
            booking.setStatus(BookingStatus.PENDING);
            booking.setUpdatedAt(LocalDateTime.now());

            // Build driver search event (POST-ENRICHMENT)
            BookingSearchDriversEvent searchEvent = buildDriverSearchEvent(booking);

            // Save and Trigger Tracking Service via persistence layer
            bookingPersistenceService.saveBookingAndScheduleDriverSearch(booking, searchEvent);

            log.info("✓ ENRICHMENT SUCCESSFUL: bookingId={} is now PENDING", booking.getBookingId());

            // Notify UI status change from CONFIRMED -> PENDING
            publishStatusChange(booking, oldStatus);

        } catch (Exception e) {
            log.error("✗ ENRICHMENT FAILED for bookingId={}: {}", booking.getBookingId(), e.getMessage());
            // Retry logic or fail booking
            throw e;
        }
    }

    /**
     * Call Map Service with error handling (Saga Step 1)
     */
    private MapServiceRouteResponse callMapService(AddressSnapshot pickup, AddressSnapshot dropoff) {
        try {
            GeoPoint origin = new GeoPoint(pickup.getLatitude(), pickup.getLongitude());
            GeoPoint destination = new GeoPoint(dropoff.getLatitude(), dropoff.getLongitude());

            return mapServiceClient.getRoute(origin, destination);
        } catch (BusinessException e) {
            log.error("MapService call failed: {}", e.getMessage());
            throw new BusinessException("MAP_UNAVAILABLE: Unable to calculate route. Please try again later.");
        }
    }

    /**
     * Call Pricing Service with error handling (Saga Step 2)
     */
    private PricingServiceResponse callPricingService(
            String vehicleType, double distanceKm, int durationMinutes, String region) {
        try {
            return pricingServiceClient.estimateFare(vehicleType, distanceKm, durationMinutes, region);
        } catch (BusinessException e) {
            log.error("PricingService call failed: {}", e.getMessage());
            // Forward the original error message if it contains useful information
            String errorMessage = e.getMessage();
            if (errorMessage != null && errorMessage.contains("PRICING_RULE_NOT_FOUND")) {
                // Forward the detailed message from PricingServiceClient
                throw e;
            }
            if (errorMessage != null && errorMessage.contains("PRICING_UNAVAILABLE")) {
                // Already has detailed message, forward it
                throw e;
            }
            // Generic fallback
            throw new BusinessException("PRICING_UNAVAILABLE: Không thể tính toán giá. Vui lòng thử lại sau.");
        }
    }

    /**
     * Builds Kafka payload for Tracking (published after DB commit).
     */
    private BookingSearchDriversEvent buildDriverSearchEvent(Booking booking) {
        return new BookingSearchDriversEvent(
                booking.getBookingId(),
                booking.getPickupLocation() != null ? booking.getPickupLocation().getLatitude() : null,
                booking.getPickupLocation() != null ? booking.getPickupLocation().getLongitude() : null,
                booking.getDropoffLocation() != null ? booking.getDropoffLocation().getLatitude() : null,
                booking.getDropoffLocation() != null ? booking.getDropoffLocation().getLongitude() : null,
                booking.getVehicleType() != null ? booking.getVehicleType().name() : null,
                driverSearchRadiusMeters,
                booking.getPickupLocation() != null ? booking.getPickupLocation().getFullAddress() : null,
                booking.getDropoffLocation() != null ? booking.getDropoffLocation().getFullAddress() : null,
                booking.getEstimatedDistanceKm(),
                booking.getEstimatedDurationMinutes(),
                booking.getPrice() != null ? booking.getPrice().getFinalAmount() : null,
                booking.getPrice() != null && booking.getPrice().getCurrency() != null
                        ? booking.getPrice().getCurrency()
                        : "VND");
    }

    /**
     * Handle driver accepted event (first-accept-wins) - ATOMIC VERSION
     * Uses MongoDB atomic update to prevent race condition when multiple drivers
     * accept simultaneously
     */
    @Transactional
    public void handleDriverAccepted(DriverAcceptedEvent event) {
        UUID bookingId = event.getBookingId();
        UUID driverId = event.getDriverId();

        // Atomic update: Only update if status is still PENDING
        // This prevents race condition when multiple drivers accept at the same time
        Query query = new Query(
                Criteria.where("_id").is(bookingId)
                        .and("status").is(BookingStatus.PENDING));

        Update update = new Update()
                .set("driverId", driverId)
                .set("status", BookingStatus.MATCHED)
                .set("updatedAt", LocalDateTime.now());

        // findAndModify returns the document AFTER update, or null if condition not met
        Booking updatedBooking = mongoTemplate.findAndModify(
                query,
                update,
                org.springframework.data.mongodb.core.FindAndModifyOptions.options().returnNew(true),
                Booking.class);

        if (updatedBooking == null) {
            // Booking not found OR already assigned (status != PENDING)
            // This means another driver already accepted this booking
            log.warn("Booking {} cannot be accepted by driver {} - already assigned or not found",
                    bookingId, driverId);
            return;
        }

        // Successfully assigned - publish event
        BookingAssignedEvent assignedEvent = new BookingAssignedEvent(
                updatedBooking.getBookingId(),
                updatedBooking.getCustomerId(),
                updatedBooking.getDriverId());
        eventsProducer.publishBookingAssignedEvent(assignedEvent);

        log.info("Driver {} successfully accepted booking {} (atomic update)", driverId, bookingId);
    }

    /**
     * Driver accepts a booking via REST API
     * Uses atomic update to prevent race condition
     */
    @Transactional
    public BookingResponse acceptBooking(UUID bookingId) {
        UUID driverId = securityUtils.getCurrentUserId();

        // Use atomic update to accept booking
        Query query = new Query(
                Criteria.where("_id").is(bookingId)
                        .and("status").is(BookingStatus.PENDING));

        Update update = new Update()
                .set("driverId", driverId)
                .set("status", BookingStatus.MATCHED)
                .set("updatedAt", LocalDateTime.now());

        Booking updatedBooking = mongoTemplate.findAndModify(
                query,
                update,
                org.springframework.data.mongodb.core.FindAndModifyOptions.options().returnNew(true),
                Booking.class);

        if (updatedBooking == null) {
            // Booking not found OR already assigned
            Booking existingBooking = bookingRepository.findById(bookingId).orElse(null);
            if (existingBooking == null) {
                throw new NotFoundException("Booking not found: " + bookingId);
            }
            if (existingBooking.getStatus() != BookingStatus.PENDING) {
                throw new BusinessException("Booking was already accepted by another driver");
            }
            throw new BusinessException("Unable to accept booking. Please try again.");
        }

        // Publish booking assigned event
        BookingAssignedEvent assignedEvent = new BookingAssignedEvent(
                updatedBooking.getBookingId(),
                updatedBooking.getCustomerId(),
                updatedBooking.getDriverId());
        eventsProducer.publishBookingAssignedEvent(assignedEvent);

        log.info("Driver {} successfully accepted booking {} via REST API", driverId, bookingId);

        // Broadcast via WebSocket - notify both customer and driver in real-time
        BookingResponse response = toResponse(updatedBooking);
        // webSocketService.notifyBothParties(response); // Removed -
        // NotificationService handles

        // Publish status change event for NotificationService
        publishStatusChange(updatedBooking, BookingStatus.PENDING);

        return response;
    }

    /**
     * Handle driver declined event
     */
    @Transactional
    public void handleDriverDeclined(DriverDeclinedEvent event) {
        Booking booking = bookingRepository.findById(event.getBookingId())
                .orElseThrow(() -> new NotFoundException("Booking not found: " + event.getBookingId()));

        // If booking is still PENDING, we can retry driver search
        if (booking.getStatus() == BookingStatus.PENDING) {
            log.info("Driver {} declined booking {}, retrying driver search",
                    event.getDriverId(), booking.getBookingId());
            // Could republish search event or implement retry logic here
        } else {
            log.warn("Driver {} declined booking {} but status is {}",
                    event.getDriverId(), booking.getBookingId(), booking.getStatus());
        }
    }

    /**
     * Driver arrived at pickup point
     * Changes status from MATCHED to DRIVER_ARRIVED
     */
    @Transactional
    public BookingResponse driverArrived(UUID bookingId) {
        Booking booking = bookingRepository.findById(bookingId)
                .orElseThrow(() -> new NotFoundException("Booking not found: " + bookingId));

        // Validate driver
        UUID currentUserId = securityUtils.getCurrentUserId();
        if (!booking.getDriverId().equals(currentUserId)) {
            throw new BusinessException("FORBIDDEN: Only the assigned driver can mark as arrived");
        }

        if (booking.getStatus() != BookingStatus.MATCHED) {
            throw new BusinessException("INVALID_STATUS: Booking must be MATCHED to mark as arrived. Current status: "
                    + booking.getStatus());
        }

        booking.setStatus(BookingStatus.DRIVER_ARRIVED);
        booking.setActualPickupTime(LocalDateTime.now());
        booking.setUpdatedAt(LocalDateTime.now());

        Booking savedBooking = bookingRepository.save(booking);

        log.info("Driver {} marked booking {} as arrived", currentUserId, bookingId);

        // Broadcast via WebSocket - notify customer that driver has arrived
        BookingResponse response = toResponse(savedBooking);
        // webSocketService.notifyBothParties(response); // Removed -
        // NotificationService handles

        // Publish status change event for NotificationService
        publishStatusChange(savedBooking, BookingStatus.MATCHED);

        return response;
    }

    /**
     * Driver start trip (after customer gets in)
     * Changes status from DRIVER_ARRIVED to IN_PROGRESS
     */
    @Transactional
    public BookingResponse startTrip(UUID bookingId) {
        Booking booking = bookingRepository.findById(bookingId)
                .orElseThrow(() -> new NotFoundException("Booking not found: " + bookingId));

        // Validate driver
        UUID currentUserId = securityUtils.getCurrentUserId();
        if (!booking.getDriverId().equals(currentUserId)) {
            throw new BusinessException("FORBIDDEN: Only the assigned driver can start trip");
        }

        if (booking.getStatus() != BookingStatus.DRIVER_ARRIVED) {
            throw new BusinessException("INVALID_STATUS: Booking must be DRIVER_ARRIVED to start trip. Current status: "
                    + booking.getStatus());
        }

        booking.setStatus(BookingStatus.IN_PROGRESS);
        booking.setUpdatedAt(LocalDateTime.now());

        Booking savedBooking = bookingRepository.save(booking);

        log.info("Driver {} started trip for booking {}", currentUserId, bookingId);

        // Broadcast via WebSocket - notify customer that trip has started
        BookingResponse response = toResponse(savedBooking);
        // webSocketService.notifyBothParties(response); // Removed -
        // NotificationService handles

        // Publish status change event for NotificationService
        publishStatusChange(savedBooking, BookingStatus.DRIVER_ARRIVED);

        return response;
    }

    /**
     * Complete booking (when trip ends)
     */
    @Transactional
    public BookingResponse completeBooking(UUID bookingId, CompleteBookingRequest request) {
        Booking booking = bookingRepository.findById(bookingId)
                .orElseThrow(() -> new NotFoundException("Booking not found: " + bookingId));

        // Validate ownership or driver
        UUID currentUserId = securityUtils.getCurrentUserId();
        if (!booking.getCustomerId().equals(currentUserId) &&
                !booking.getDriverId().equals(currentUserId)) {
            throw new BusinessException("FORBIDDEN: You don't have permission to complete this booking");
        }

        if (booking.getStatus() != BookingStatus.IN_PROGRESS) {
            throw new BusinessException(
                    "INVALID_STATUS: Booking must be IN_PROGRESS to complete. Current status: " + booking.getStatus());
        }

        booking.setActualDistanceKm(request.getActualDistanceKm());
        booking.setActualDurationMinutes(request.getActualDurationMinutes());
        booking.setStatus(BookingStatus.COMPLETED);
        booking.setActualDropoffTime(LocalDateTime.now());
        booking.setUpdatedAt(LocalDateTime.now());

        Booking savedBooking = bookingRepository.save(booking);

        // Publish booking completed event
        BookingCompletedEvent completedEvent = new BookingCompletedEvent(
                savedBooking.getBookingId(),
                savedBooking.getCustomerId(),
                savedBooking.getDriverId(),
                savedBooking.getPrice().getFinalAmount(),
                savedBooking.getPrice().getCurrency());
        eventsProducer.publishBookingCompletedEvent(completedEvent);

        // Broadcast via WebSocket - notify both parties that booking is completed
        BookingResponse response = toResponse(savedBooking);
        // webSocketService.notifyBothParties(response); // Removed -
        // NotificationService handles

        // Publish status change event for NotificationService
        publishStatusChange(savedBooking, BookingStatus.IN_PROGRESS);

        return response;
    }

    /**
     * Cancel booking
     */
    @Transactional
    public BookingResponse cancelBooking(UUID bookingId, CancelBookingRequest request) {
        Booking booking = bookingRepository.findById(bookingId)
                .orElseThrow(() -> new NotFoundException("Booking not found: " + bookingId));

        // Validate ownership
        UUID currentUserId = securityUtils.getCurrentUserId();
        if (!booking.getCustomerId().equals(currentUserId)) {
            throw new com.gomirai.common.exception.ForbiddenException("FORBIDDEN: Only customer can cancel booking");
        }

        if (!booking.canBeCanceled()) {
            throw new BusinessException(
                    "INVALID_STATUS: Booking cannot be canceled. Current status: " + booking.getStatus());
        }

        booking.setStatus(BookingStatus.CANCELED);
        booking.setCancelReason(request.getReason());
        booking.setUpdatedAt(LocalDateTime.now());

        Booking savedBooking = bookingRepository.save(booking);

        // Refund if paid with WALLET - Using Kafka for async processing
        if (savedBooking.getPaymentMethod() == com.gomirai.ride.booking.enums.PaymentMethod.WALLET
                && savedBooking.getPrice() != null
                && savedBooking.getPrice().getFinalAmount() > 0) {

            // Publish refund event to Kafka - PaymentService will process asynchronously
            RefundRequestedEvent refundEvent = new RefundRequestedEvent(
                    savedBooking.getBookingId(),
                    savedBooking.getCustomerId(),
                    BigDecimal.valueOf(savedBooking.getPrice().getFinalAmount()),
                    savedBooking.getPrice().getCurrency() != null ? savedBooking.getPrice().getCurrency() : "VND",
                    savedBooking.getCancelReason());
            eventsProducer.publishRefundRequestedEvent(refundEvent);
            log.info("Published RefundRequestedEvent for bookingId={}, amount={}",
                    savedBooking.getBookingId(), savedBooking.getPrice().getFinalAmount());
        }

        // Publish booking canceled event to notify other services (e.g.,
        // TrackingService)
        com.gomirai.common.dto.event.BookingCanceledEvent canceledEvent = new com.gomirai.common.dto.event.BookingCanceledEvent(
                savedBooking.getBookingId(),
                savedBooking.getCustomerId(),
                savedBooking.getCancelReason(),
                "CUSTOMER");
        eventsProducer.publishBookingCanceledEvent(canceledEvent);
        log.info("Published BookingCanceledEvent for bookingId={}", savedBooking.getBookingId());

        // Broadcast via WebSocket - notify driver if assigned
        BookingResponse response = toResponse(savedBooking);
        // webSocketService.notifyBothParties(response); // Removed -
        // NotificationService handles

        // Publish status change event for NotificationService
        publishStatusChange(savedBooking, booking.getStatus()); // Previous status was whatever it was before

        return response;
    }

    /**
     * Cancel booking - no driver found (called by TrackingService)
     * This method is called when no driver accepts the booking within 15 minutes
     */
    @Transactional
    public BookingResponse cancelBookingNoDriverFound(UUID bookingId, String reason) {
        Booking booking = bookingRepository.findById(bookingId)
                .orElseThrow(() -> new NotFoundException("Booking not found: " + bookingId));

        // Only cancel if still PENDING
        if (booking.getStatus() != BookingStatus.PENDING) {
            log.warn("Booking {} cannot be cancelled - no driver found. Current status: {}",
                    bookingId, booking.getStatus());
            return toResponse(booking);
        }

        booking.setStatus(BookingStatus.NO_DRIVER_FOUND);
        booking.setCancelReason(reason);
        booking.setUpdatedAt(LocalDateTime.now());

        Booking savedBooking = bookingRepository.save(booking);

        log.info("Booking {} cancelled - no driver found. Reason: {}", bookingId, reason);

        // Refund if paid with WALLET - Using Kafka for async processing
        if (savedBooking.getPaymentMethod() == com.gomirai.ride.booking.enums.PaymentMethod.WALLET
                && savedBooking.getPrice() != null
                && savedBooking.getPrice().getFinalAmount() > 0) {

            // Publish refund event to Kafka - PaymentService will process asynchronously
            RefundRequestedEvent refundEvent = new RefundRequestedEvent(
                    savedBooking.getBookingId(),
                    savedBooking.getCustomerId(),
                    BigDecimal.valueOf(savedBooking.getPrice().getFinalAmount()),
                    savedBooking.getPrice().getCurrency() != null ? savedBooking.getPrice().getCurrency() : "VND",
                    "No driver found: " + reason);
            eventsProducer.publishRefundRequestedEvent(refundEvent);
            log.info("Published RefundRequestedEvent for NO_DRIVER_FOUND bookingId={}, amount={}",
                    savedBooking.getBookingId(), savedBooking.getPrice().getFinalAmount());
        }

        // Publish status change event for NotificationService
        publishStatusChange(savedBooking, BookingStatus.PENDING);

        return toResponse(savedBooking);
    }

    /**
     * Get booking by ID
     */
    public BookingResponse getBooking(UUID bookingId) {
        Booking booking = bookingRepository.findById(bookingId)
                .orElseThrow(() -> new NotFoundException("Booking not found: " + bookingId));

        // Validate access
        UUID currentUserId = securityUtils.getCurrentUserId();
        if (!booking.getCustomerId().equals(currentUserId) &&
                (booking.getDriverId() == null || !booking.getDriverId().equals(currentUserId))) {
            throw new com.gomirai.common.exception.ForbiddenException("FORBIDDEN: You don't have permission to view this booking");
        }

        return toResponse(booking);
    }

    /**
     * Get booking by ID (Internal - No Auth Check)
     */
    public BookingResponse getBookingInternal(UUID bookingId) {
        Booking booking = bookingRepository.findById(bookingId)
                .orElseThrow(() -> new NotFoundException("Booking not found: " + bookingId));

        return toResponse(booking);
    }

    /**
     * Get customer bookings
     */
    public Page<BookingResponse> getCustomerBookings(BookingStatus status, Pageable pageable) {
        UUID customerId = securityUtils.getCurrentUserId();

        Page<Booking> bookings;
        if (status != null) {
            bookings = bookingRepository.findByCustomerIdAndStatusOrderByCreatedAtDesc(
                    customerId, status, pageable);
        } else {
            bookings = bookingRepository.findByCustomerIdOrderByCreatedAtDesc(customerId, pageable);
        }

        return bookings.map(this::toResponse);
    }

    /**
     * Get driver bookings
     */
    public Page<BookingResponse> getDriverBookings(BookingStatus status, Pageable pageable) {
        UUID driverId = securityUtils.getCurrentUserId();

        Page<Booking> bookings;
        if (status != null) {
            bookings = bookingRepository.findByDriverIdAndStatusOrderByCreatedAtDesc(
                    driverId, status, pageable);
        } else {
            bookings = bookingRepository.findByDriverIdOrderByCreatedAtDesc(driverId, pageable);
        }

        return bookings.map(this::toResponse);
    }

    /**
     * Get pending bookings for driver (nearby bookings within 5km radius)
     * Filters by:
     * 1. Driver's current location (within 5km radius)
     * 2. Driver's vehicle type
     * 3. Booking creation time (recent bookings only)
     */
    /**
     * PERF IMPROVEMENT #2 — Replaced O(N) Haversine Java loop with MongoDB 2dsphere geo-spatial query.
     *
     * <p><b>Before:</b> Fetch up to 50 PENDING bookings, compute Haversine in JVM, sort in memory.  
     * CPU-bound, scales badly as pending bookings grow.
     *
     * <p><b>After:</b> MongoDB {@code $nearSphere} backed by the 2dsphere index on
     * {@code pickupLocation.point} returns only bookings within the driver's radius,
     * sorted by distance, directly from the DB engine — O(log N) index seek.
     * Vehicle-type and recency filters are pushed to the DB query as well, so no
     * post-processing is needed in Java.
     *
     * <p><b>Fallback:</b> If {@code pickupLocation.point} is null on older bookings
     * (created before the geo-field was added), those documents will be ignored by
     * the $nearSphere query — they would have been excluded anyway since they cannot
     * be matched geographically.
     */
    public List<BookingResponse> getPendingBookingsForDriver() {
        UUID driverId = securityUtils.getCurrentUserId();

        // 1. Get driver's current location from TrackingService
        DriverGeoStateResponse driverLocation = trackingServiceClient.getDriverLocation(driverId);
        if (driverLocation == null || driverLocation.getLatitude() == null || driverLocation.getLongitude() == null) {
            log.warn("Driver location not found for driverId: {}", driverId);
            return List.of();
        }

        double driverLat = driverLocation.getLatitude();
        double driverLng = driverLocation.getLongitude();

        // 2. Build $nearSphere query — MongoDB returns docs sorted by ascending distance
        //    GeoJSON convention: Point(longitude, latitude)
        Point driverPoint = new Point(driverLng, driverLat);
        Distance radius = new Distance(driverNearbyRadiusKm, Metrics.KILOMETERS);

        LocalDateTime thirtyMinutesAgo = LocalDateTime.now().minusMinutes(30);

        Criteria geoCriteria = Criteria.where("pickupLocation.point")
                .nearSphere(driverPoint)
                .maxDistance(radius.getNormalizedValue()); // normalized = radians for 2dsphere

        Criteria statusCriteria = Criteria.where("status").is(BookingStatus.PENDING);
        Criteria recencyCriteria = Criteria.where("createdAt").gte(thirtyMinutesAgo);

        Criteria combined = new Criteria().andOperator(statusCriteria, recencyCriteria, geoCriteria);

        // Vehicle type filter — push to DB if driver has a known vehicle type
        String driverVehicleType = driverLocation.getVehicleType() != null
                ? driverLocation.getVehicleType().name()
                : null;
        if (driverVehicleType != null) {
            Criteria vehicleCriteria = Criteria.where("vehicleType").is(driverVehicleType);
            combined = new Criteria().andOperator(statusCriteria, recencyCriteria, vehicleCriteria, geoCriteria);
        }

        Query geoQuery = new Query(combined).limit(20);

        List<Booking> nearbyBookings = mongoTemplate.find(geoQuery, Booking.class);

        log.debug("Found {} nearby bookings for driver {} within {}km (geo-spatial query)",
                nearbyBookings.size(), driverId, driverNearbyRadiusKm);

        return nearbyBookings.stream()
                .map(this::toResponse)
                .collect(java.util.stream.Collectors.toList());
    }

    // Helper methods
    private void validateBookingRequest(CreateBookingRequest request) {
        if (request.getPickupLocation() == null ||
                request.getPickupLocation().getLatitude() == null ||
                request.getPickupLocation().getLongitude() == null) {
            throw new BusinessException("INVALID_PAYLOAD: Pickup location is required");
        }

        if (request.getDropoffLocation() == null ||
                request.getDropoffLocation().getLatitude() == null ||
                request.getDropoffLocation().getLongitude() == null) {
            throw new BusinessException("INVALID_PAYLOAD: Dropoff location is required");
        }

        if (request.getVehicleType() == null) {
            throw new BusinessException("INVALID_PAYLOAD: Vehicle type is required");
        }

        if (request.getPaymentMethod() == null) {
            throw new BusinessException("INVALID_PAYLOAD: Payment method is required");
        }
    }

    /**
     * Convert VehicleType enum to PricingService format
     * PricingService expects: "CAR_4", "CAR_7", "MOTORBIKE" (same as enum name)
     */
    private String convertVehicleTypeForPricing(com.gomirai.common.enums.VehicleType vehicleType) {
        // Use enum name directly - should match PricingService format
        return vehicleType.name();
    }

    /**
     * Extract region from location coordinates
     * For now, use "VN" as default (Vietnam)
     * TODO: Implement geocoding to determine actual region (HCM, HN, etc.)
     */
    private String extractRegionFromLocation(AddressSnapshot location) {
        // Simple region detection based on coordinates
        // Ho Chi Minh City: ~10.7°N, 106.7°E
        // Hanoi: ~21.0°N, 105.8°E

        double lat = location.getLatitude();
        double lon = location.getLongitude();

        // Ho Chi Minh City area
        if (lat >= 10.0 && lat <= 11.0 && lon >= 106.0 && lon <= 107.0) {
            return "HCM";
        }

        // Hanoi area
        if (lat >= 20.5 && lat <= 21.5 && lon >= 105.5 && lon <= 106.0) {
            return "HN";
        }

        // Default to VN (Vietnam) for other locations
        return "VN";
    }

    private AddressSnapshot normalizeLocation(AddressSnapshot location) {
        // Basic normalization
        if (location.getFullAddress() == null || location.getFullAddress().trim().isEmpty()) {
            location.setFullAddress(String.format("%.6f, %.6f",
                    location.getLatitude(), location.getLongitude()));
        }
        // PERF #2: Keep GeoJsonPoint in sync so the 2dsphere index can be used
        location.syncPoint();
        return location;
    }

    private BookingResponse toResponse(Booking booking) {
        return BookingResponse.builder()
                .bookingId(booking.getBookingId())
                .customerId(booking.getCustomerId())
                .driverId(booking.getDriverId())
                .status(booking.getStatus())
                .pickupLocation(booking.getPickupLocation())
                .dropoffLocation(booking.getDropoffLocation())
                .vehicleType(booking.getVehicleType())
                .paymentMethod(booking.getPaymentMethod())
                .price(booking.getPrice())
                .estimatedDistanceKm(booking.getEstimatedDistanceKm())
                .actualDistanceKm(booking.getActualDistanceKm())
                .estimatedDurationMinutes(booking.getEstimatedDurationMinutes())
                .actualDurationMinutes(booking.getActualDurationMinutes())
                .routePolyline(booking.getRoutePolyline())
                .scheduledAt(booking.getScheduledAt())
                .actualPickupTime(booking.getActualPickupTime())
                .actualDropoffTime(booking.getActualDropoffTime())
                .paymentId(booking.getPaymentId())
                .cancelReason(booking.getCancelReason())
                .notes(booking.getNotes())
                .createdAt(booking.getCreatedAt())
                .updatedAt(booking.getUpdatedAt())
                .build();
    }

    /**
     * Helper to publish status change event for WebSocket updates
     */
    public void publishStatusChange(Booking booking, BookingStatus previousStatus) {
        try {
            BookingStatusChangedEvent event = BookingStatusChangedEvent.builder()
                    .bookingId(booking.getBookingId())
                    .customerId(booking.getCustomerId().toString())
                    .driverId(booking.getDriverId() != null ? booking.getDriverId().toString() : null)
                    .status(booking.getStatus().name())
                    .previousStatus(previousStatus != null ? previousStatus.name() : null)
                    .pickupLatitude(booking.getPickupLocation().getLatitude())
                    .pickupLongitude(booking.getPickupLocation().getLongitude())
                    .pickupAddress(booking.getPickupLocation().getFullAddress())
                    .dropoffLatitude(booking.getDropoffLocation().getLatitude())
                    .dropoffLongitude(booking.getDropoffLocation().getLongitude())
                    .dropoffAddress(booking.getDropoffLocation().getFullAddress())
                    .estimatedFare(booking.getPrice() != null ? booking.getPrice().getFinalAmount() : null)
                    .vehicleType(booking.getVehicleType().name())
                    .build();

            eventsProducer.publishBookingStatusChangedEvent(event);
        } catch (Exception e) {
            log.error("Failed to publish status change event for booking {}", booking.getBookingId(), e);
        }
    }
}
