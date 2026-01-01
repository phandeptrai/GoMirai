# Tài Liệu Phát Triển Tính Năng: Wallet Payment Integration

## 📋 Mục Lục
1. [Tổng Quan](#tổng-quan)
2. [Tổ Chức Thư Mục](#tổ-chức-thư-mục)
3. [Luồng Hoạt Động Tổng Thể](#luồng-hoạt-động-tổng-thể)
4. [Luồng Hoạt Động Chi Tiết](#luồng-hoạt-động-chi-tiết)
5. [Chi Tiết Implementation](#chi-tiết-implementation)
6. [Xử Lý Nghiệp Vụ](#xử-lý-nghiệp-vụ)
7. [API Endpoints](#api-endpoints)
8. [Error Handling](#error-handling)

---

## Tổng Quan

### Mục Đích
Tích hợp phương thức thanh toán qua ví điện tử (MyWallet) vào hệ thống đặt xe GoMirai, cho phép khách hàng:
- Chọn thanh toán bằng ví hoặc tiền mặt
- Xem số dư ví trực tiếp khi đặt xe
- Tiền được trừ ngay khi đặt xe thành công
- Nhận hoàn tiền tự động khi hủy chuyến

### Công Nghệ Sử Dụng
- **Backend:** Spring Boot, MongoDB, Consul, RestTemplate
- **Frontend:** React, React Router
- **Communication:** RESTful API
- **Pattern:** Service-to-Service Communication, Saga Pattern

---

## Tổ Chức Thư Mục

> **Chú thích:**
> - 🆕 **TẠO MỚI** - File được tạo mới cho tính năng này
> - ✏️ **BỔ SUNG** - File có sẵn nhưng được chỉnh sửa/bổ sung
> - ✅ **CÓ SẴN** - File có sẵn, không thay đổi (chỉ sử dụng)

### Backend Structure

```
GoMirai/
├── BookingService/
│   ├── src/main/java/com/gomirai/booking/
│   │   ├── client/
│   │   │   ├── MapServiceClient.java              # ✅ CÓ SẴN
│   │   │   ├── PricingServiceClient.java          # ✅ CÓ SẴN
│   │   │   ├── TrackingServiceClient.java         # ✅ CÓ SẴN
│   │   │   └── PaymentServiceClient.java          # 🆕 TẠO MỚI - Client gọi PaymentService
│   │   │
│   │   ├── dto/
│   │   │   ├── external/
│   │   │   │   ├── RidePaymentRequest.java        # 🆕 TẠO MỚI - DTO payment request
│   │   │   │   ├── RefundRequest.java             # 🆕 TẠO MỚI - DTO refund request
│   │   │   │   ├── TransactionResponse.java       # 🆕 TẠO MỚI - DTO payment response
│   │   │   │   ├── PricingServiceResponse.java    # ✅ CÓ SẴN
│   │   │   │   └── MapServiceRouteResponse.java   # ✅ CÓ SẴN
│   │   │   │
│   │   │   ├── request/
│   │   │   │   └── CreateBookingRequest.java      # ✅ CÓ SẴN - Đã có field paymentMethod
│   │   │   │
│   │   │   └── response/
│   │   │       └── BookingResponse.java           # ✅ CÓ SẴN
│   │   │
│   │   ├── enums/
│   │   │   ├── PaymentMethod.java                 # ✅ CÓ SẴN - Enum CASH, WALLET
│   │   │   └── BookingStatus.java                 # ✅ CÓ SẴN
│   │   │
│   │   ├── service/
│   │   │   └── BookingService.java                # ✏️ BỔ SUNG - Thêm payment & refund logic
│   │   │
│   │   ├── model/
│   │   │   └── Booking.java                       # ✅ CÓ SẴN
│   │   │
│   │   ├── repository/
│   │   │   └── BookingRepository.java             # ✅ CÓ SẴN
│   │   │
│   │   └── controller/
│   │       └── BookingController.java             # ✅ CÓ SẴN - Không thay đổi
│   │
│   └── src/main/resources/
│       └── application.properties                 # ✅ CÓ SẴN
│
└── PaymentService/
    ├── src/main/java/com/gomirai/payment/
    │   ├── controller/
    │   │   ├── WalletController.java              # ✅ CÓ SẴN - API lấy wallet info
    │   │   └── PaymentInternalController.java     # ✅ CÓ SẴN - Internal API payRide/refund
    │   │
    │   ├── service/
    │   │   └── WalletServiceImpl.java             # ✅ CÓ SẴN - Logic payRide, refundRide
    │   │
    │   ├── model/
    │   │   ├── Wallet.java                        # ✅ CÓ SẴN
    │   │   └── Transaction.java                   # ✅ CÓ SẴN
    │   │
    │   └── repository/
    │       ├── WalletRepository.java              # ✅ CÓ SẴN
    │       └── TransactionRepository.java         # ✅ CÓ SẴN
    │
    └── src/main/resources/
        └── application.properties                 # ✅ CÓ SẴN
```

### Frontend Structure

```
GoMirai_fe/
├── src/
│   ├── components/
│   │   ├── VehicleSelectionModal.jsx              # ✏️ BỔ SUNG - Tích hợp payment selector
│   │   ├── VehicleSelectionModal.css              # ✅ CÓ SẴN
│   │   ├── PaymentMethodSelector.jsx              # 🆕 TẠO MỚI - Component chọn payment
│   │   └── PaymentMethodSelector.css              # 🆕 TẠO MỚI - Styling payment selector
│   │
│   ├── api/
│   │   ├── booking.api.js                         # ✅ CÓ SẴN - createBooking, cancelBooking
│   │   └── wallet.api.js                          # ✅ CÓ SẴN - getWallet, topUp
│   │
│   ├── pages/
│   │   ├── HomePage/
│   │   │   └── HomePage.jsx                       # ✅ CÓ SẴN
│   │   ├── ActivityPage/
│   │   │   └── ActivityPage.jsx                   # ✅ CÓ SẴN
│   │   └── PaymentPage/
│   │       ├── PaymentPage.jsx                    # ✅ CÓ SẴN - Trang nạp tiền
│   │       └── index.jsx                          # ✅ CÓ SẴN
│   │
│   └── router/
│       └── AppRouter.jsx                          # ✅ CÓ SẴN - Route /payment
```

### Tổng Kết Files

**Backend:**
- 🆕 **Tạo mới:** 4 files
  - `PaymentServiceClient.java`
  - `RidePaymentRequest.java`
  - `RefundRequest.java`
  - `TransactionResponse.java`
- ✏️ **Bổ sung:** 1 file
  - `BookingService.java`
- ✅ **Có sẵn (sử dụng):** PaymentService endpoints, DTOs, repositories

**Frontend:**
- 🆕 **Tạo mới:** 2 files
  - `PaymentMethodSelector.jsx`
  - `PaymentMethodSelector.css`
- ✏️ **Bổ sung:** 1 file
  - `VehicleSelectionModal.jsx`
- ✅ **Có sẵn (sử dụng):** `wallet.api.js`, `booking.api.js`, `PaymentPage.jsx`

---

## Luồng Hoạt Động Tổng Thể

### 1. Flow Tổng Quan: Đặt Xe với Wallet

```
┌─────────────┐         ┌──────────────────┐         ┌─────────────────┐
│   Frontend  │         │  BookingService  │         │ PaymentService  │
└─────────────┘         └──────────────────┘         └─────────────────┘
      │                          │                            │
      │ 1. User opens modal      │                            │
      │────────────────────────► │                            │
      │ 2. Get wallet balance    │                            │
      │──────────────────────────────────────────────────────►│
      │◄─────────────────────────────────────────────────────┤
      │    { balance: 500000 }   │                            │
      │                          │                            │
      │ 3. User selects WALLET   │                            │
      │    & clicks "Đặt xe"     │                            │
      │────────────────────────► │                            │
      │ createBooking({          │                            │
      │   paymentMethod: WALLET  │                            │
      │ })                       │                            │
      │                          │                            │
      │                          │ 4. Calculate route & price │
      │                          │    (MapService, Pricing)   │
      │                          │                            │
      │                          │ 5. Process payment         │
      │                          │───────────────────────────►│
      │                          │ payRide(userId, amount)    │
      │                          │                            │
      │                          │                            │ 6. Deduct wallet
      │                          │                            │    balance
      │                          │                            │
      │                          │◄───────────────────────────┤
      │                          │ Response: SUCCESS          │
      │                          │                            │
      │                          │ 7. Save booking to DB      │
      │                          │    status: PENDING         │
      │                          │                            │
      │◄────────────────────────┤                            │
      │ BookingResponse          │                            │
      │                          │                            │
```

### 2. Flow Tổng Quan: Hủy Chuyến & Hoàn Tiền

```
┌─────────────┐         ┌──────────────────┐         ┌─────────────────┐
│   Frontend  │         │  BookingService  │         │ PaymentService  │
└─────────────┘         └──────────────────┘         └─────────────────┘
      │                          │                            │
      │ 1. User cancels booking  │                            │
      │────────────────────────► │                            │
      │ cancelBooking(id)        │                            │
      │                          │                            │
      │                          │ 2. Check payment method    │
      │                          │    if (WALLET)             │
      │                          │                            │
      │                          │ 3. Request refund          │
      │                          │───────────────────────────►│
      │                          │ refund(bookingId, amount)  │
      │                          │                            │
      │                          │                            │ 4. Add money back
      │                          │                            │    to wallet
      │                          │                            │
      │                          │◄───────────────────────────┤
      │                          │ Response: SUCCESS          │
      │                          │                            │
      │                          │ 5. Update booking status   │
      │                          │    status: CANCELED        │
      │                          │                            │
      │◄────────────────────────┤                            │
      │ BookingResponse          │                            │
      │                          │                            │
```

---

## Luồng Hoạt Động Chi Tiết

### A. Frontend Flow - Payment Selection

**File:** `VehicleSelectionModal.jsx`

```javascript
// 1. MOUNT PHASE - Load wallet balance
useEffect(() => {
  if (isOpen) {
    setIsLoadingWallet(true);
    walletApi.getWallet()
      .then((res) => {
        setWalletBalance(res.balance || 0);  // Store balance in state
        setIsLoadingWallet(false);
      })
      .catch((err) => {
        console.error('Failed to load wallet balance:', err);
        setWalletBalance(0);
        setIsLoadingWallet(false);
      });
  }
}, [isOpen]);

// 2. USER INTERACTION - Select payment method
<PaymentMethodSelector
  selectedMethod={selectedPaymentMethod}     // 'CASH' or 'WALLET'
  onMethodChange={setSelectedPaymentMethod}  // Update state when changed
  walletBalance={walletBalance}
  totalPrice={totalPrice}
  isLoadingWallet={isLoadingWallet}
/>

// 3. VALIDATION - Check wallet balance
const canPayWithWallet = selectedPaymentMethod === 'WALLET' 
  ? walletBalance >= totalPrice 
  : true;

const isBookingDisabled = 
  isBooking || 
  !canPayWithWallet ||  // Disable if insufficient balance
  totalPrice <= 0;

// 4. SUBMIT - Create booking with selected method
const handleConfirm = async () => {
  const bookingData = {
    pickupLocation: {...},
    dropoffLocation: {...},
    vehicleType: 'CAR_4',
    paymentMethod: selectedPaymentMethod  // Send to backend
  };
  
  const response = await bookingAPI.createBooking(bookingData);
};
```

### B. Backend Flow - Payment Processing

**File:** `BookingService.java` - Method: `createBooking()`

```java
@Transactional
public BookingResponse createBooking(CreateBookingRequest request) {
    UUID customerId = securityUtils.getCurrentUserId();
    
    // Step 1-5: Validation, Map, Pricing (existing logic)
    validateBookingRequest(request);
    MapServiceRouteResponse routeResponse = callMapService(...);
    PricingServiceResponse pricingResponse = callPricingService(...);
    
    // Create booking object
    Booking booking = new Booking();
    booking.setBookingId(UUID.randomUUID());
    booking.setCustomerId(customerId);
    booking.setPaymentMethod(request.getPaymentMethod());  // CASH or WALLET
    booking.setPrice(priceSnapshot);
    
    // ===== PAYMENT PROCESSING (NEW LOGIC) =====
    // Step 6: IF payment method is WALLET, deduct money NOW
    if (request.getPaymentMethod() == PaymentMethod.WALLET) {
        try {
            // 6a. Prepare payment request
            RidePaymentRequest paymentRequest = new RidePaymentRequest(
                customerId,
                booking.getBookingId(),
                BigDecimal.valueOf(priceSnapshot.getFinalAmount())
            );
            
            // 6b. Call PaymentService to deduct money
            TransactionResponse paymentResponse = 
                paymentServiceClient.payRide(paymentRequest);
            
            // 6c. Check payment result
            if (!"SUCCESS".equals(paymentResponse.getStatus())) {
                throw new BusinessException("PAYMENT_FAILED");
            }
            
            log.info("Wallet payment successful for bookingId={}", 
                booking.getBookingId());
                
        } catch (BusinessException e) {
            // Payment failed - DON'T save booking
            log.error("Payment failed: {}", e.getMessage());
            throw e;  // Return error to frontend
        }
    }
    // If CASH, skip payment processing
    
    // Step 7: Save booking to database
    Booking savedBooking = bookingRepository.save(booking);
    
    // Step 8: Publish driver search event
    publishDriverSearchEvent(savedBooking);
    
    return toResponse(savedBooking);
}
```

### C. Backend Flow - Refund Processing

**File:** `BookingService.java` - Method: `cancelBooking()`

```java
@Transactional
public BookingResponse cancelBooking(UUID bookingId, CancelBookingRequest request) {
    // Step 1: Get booking from DB
    Booking booking = bookingRepository.findById(bookingId)
        .orElseThrow(() -> new NotFoundException("Booking not found"));
    
    // Step 2: Validate permissions
    UUID currentUserId = securityUtils.getCurrentUserId();
    if (!booking.getCustomerId().equals(currentUserId)) {
        throw new BusinessException("FORBIDDEN");
    }
    
    // Step 3: Check if booking can be canceled
    if (!booking.canBeCanceled()) {
        throw new BusinessException("INVALID_STATUS");
    }
    
    // Step 4: Update booking status
    booking.setStatus(BookingStatus.CANCELED);
    booking.setCancelReason(request.getReason());
    Booking savedBooking = bookingRepository.save(booking);
    
    // ===== REFUND PROCESSING (NEW LOGIC) =====
    // Step 5: IF booking was paid with WALLET, refund money
    if (savedBooking.getPaymentMethod() == PaymentMethod.WALLET 
        && savedBooking.getPrice() != null 
        && savedBooking.getPrice().getFinalAmount() > 0) {
        
        try {
            // 5a. Prepare refund request
            RefundRequest refundRequest = new RefundRequest(
                savedBooking.getBookingId(),
                BigDecimal.valueOf(savedBooking.getPrice().getFinalAmount())
            );
            
            // 5b. Call PaymentService to add money back
            TransactionResponse refundResponse = 
                paymentServiceClient.refund(refundRequest);
            
            log.info("Refund successful for bookingId={}", 
                savedBooking.getBookingId());
                
        } catch (Exception e) {
            // Refund failed - Log error but don't prevent cancellation
            log.error("Refund failed for bookingId={}", savedBooking.getBookingId(), e);
            // TODO: May trigger manual review or compensation event
        }
    }
    
    // Step 6: Publish booking canceled event
    publishBookingCanceledEvent(savedBooking);
    
    return toResponse(savedBooking);
}
```

---

## Chi Tiết Implementation

### 1. PaymentServiceClient.java

**Vị trí:** `BookingService/src/main/java/com/gomirai/booking/client/PaymentServiceClient.java`

**Mục đích:** Service client để giao tiếp với PaymentService qua Consul service discovery

**Các phương thức chính:**

#### 1.1. `payRide(RidePaymentRequest request)`

**Nghiệp vụ:** Trừ tiền từ ví người dùng khi đặt xe

**Input:**
```java
RidePaymentRequest {
    UUID userId;        // ID người dùng
    UUID bookingId;     // ID booking
    BigDecimal amount;  // Số tiền cần trừ
}
```

**Output:**
```java
TransactionResponse {
    UUID transactionId;     // ID giao dịch
    String type;            // "RIDE_PAYMENT"
    String direction;       // "OUT"
    BigDecimal amount;      // Số tiền đã trừ
    String status;          // "SUCCESS" hoặc "FAILED"
    BigDecimal newBalance;  // Số dư mới sau khi trừ
}
```

**Xử lý:**
1. Tìm PaymentService instance qua Consul Discovery
2. Gọi endpoint `/api/payment/internal/ride`
3. Forward JWT token từ request hiện tại
4. Xử lý các lỗi:
   - `INSUFFICIENT_BALANCE`: Số dư không đủ
   - `WALLET_NOT_FOUND`: Ví chưa kích hoạt
   - `PAYMENT_UNAVAILABLE`: Service down
   - `PAYMENT_FAILED`: Lỗi khác

**Code:**
```java
public TransactionResponse payRide(RidePaymentRequest request) {
    ServiceInstance instance = getServiceInstance();
    if (instance == null) {
        throw new BusinessException("PAYMENT_UNAVAILABLE");
    }
    
    URI url = URI.create(String.format("http://%s:%d/api/payment/internal/ride",
        instance.getHost(), instance.getPort()));
    
    HttpHeaders headers = new HttpHeaders();
    headers.setContentType(MediaType.APPLICATION_JSON);
    
    // Forward JWT token
    String jwtToken = getCurrentJwtToken();
    if (jwtToken != null) {
        headers.setBearerAuth(jwtToken);
    }
    
    HttpEntity<RidePaymentRequest> entity = new HttpEntity<>(request, headers);
    
    try {
        ResponseEntity<TransactionResponse> response = restTemplate.exchange(
            url, HttpMethod.POST, entity, TransactionResponse.class);
        
        return response.getBody();
        
    } catch (HttpClientErrorException e) {
        String responseBody = e.getResponseBodyAsString();
        
        // Handle specific errors
        if (responseBody.contains("INSUFFICIENT_BALANCE")) {
            throw new BusinessException("INSUFFICIENT_BALANCE: Số dư không đủ");
        }
        if (responseBody.contains("WALLET_NOT_FOUND")) {
            throw new BusinessException("WALLET_NOT_FOUND: Ví chưa kích hoạt");
        }
        
        throw new BusinessException("PAYMENT_FAILED: " + responseBody);
    }
}
```

#### 1.2. `refund(RefundRequest request)`

**Nghiệp vụ:** Hoàn tiền vào ví khi hủy chuyến

**Input:**
```java
RefundRequest {
    UUID bookingId;     // ID booking cần hoàn tiền
    BigDecimal amount;  // Số tiền cần hoàn
}
```

**Output:** Tương tự `TransactionResponse` nhưng type = "REFUND", direction = "IN"

**Xử lý:** Tương tự `payRide()` nhưng gọi endpoint `/api/payment/internal/refund`

---

### 2. RidePaymentRequest.java

**Vị trí:** `BookingService/src/main/java/com/gomirai/booking/dto/external/RidePaymentRequest.java`

**Mục đích:** DTO để gửi request thanh toán đến PaymentService

**Thuộc tính:**
```java
@Data
@NoArgsConstructor
@AllArgsConstructor
public class RidePaymentRequest {
    private UUID userId;        // ID người dùng
    private UUID bookingId;     // ID booking
    private BigDecimal amount;  // Số tiền (VND)
}
```

---

### 3. RefundRequest.java

**Vị trí:** `BookingService/src/main/java/com/gomirai/booking/dto/external/RefundRequest.java`

**Mục đích:** DTO để gửi request hoàn tiền đến PaymentService

**Thuộc tính:**
```java
@Data
@NoArgsConstructor
@AllArgsConstructor
public class RefundRequest {
    private UUID bookingId;     // ID booking cần hoàn tiền
    private BigDecimal amount;  // Số tiền hoàn (VND)
}
```

---

### 4. TransactionResponse.java

**Vị trí:** `BookingService/src/main/java/com/gomirai/booking/dto/external/TransactionResponse.java`

**Mục đích:** DTO nhận response từ PaymentService sau khi xử lý giao dịch

**Thuộc tính:**
```java
@Data
@NoArgsConstructor
@AllArgsConstructor
public class TransactionResponse {
    private UUID transactionId;     // ID giao dịch
    private String type;            // TOP_UP, RIDE_PAYMENT, REFUND
    private String direction;       // IN (nạp/hoàn), OUT (trừ)
    private BigDecimal amount;      // Số tiền giao dịch
    private String status;          // SUCCESS, FAILED
    private BigDecimal newBalance;  // Số dư sau giao dịch
}
```

---

### 5. PaymentMethodSelector.jsx

**Vị trí:** `GoMirai_fe/src/components/PaymentMethodSelector.jsx`

**Mục đích:** Component React để chọn phương thức thanh toán

**Props:**
```javascript
{
  selectedMethod: string,          // 'CASH' or 'WALLET'
  onMethodChange: function,        // Callback khi thay đổi phương thức
  walletBalance: number,           // Số dư ví (VND)
  totalPrice: number,              // Tổng giá chuyến đi
  isLoadingWallet: boolean         // Trạng thái loading
}
```

**State:**
```javascript
const [isOpen, setIsOpen] = useState(false);  // Trạng thái mở/đóng dropdown
```

**Các hàm chính:**

#### 5.1. `handleSelect(methodId)`

**Nghiệp vụ:** Xử lý khi user chọn phương thức thanh toán

```javascript
const handleSelect = (methodId) => {
  onMethodChange(methodId);  // Gọi callback từ parent
  setIsOpen(false);          // Đóng dropdown
};
```

#### 5.2. `formatCurrency(amount)`

**Nghiệp vụ:** Format số tiền theo định dạng Việt Nam

```javascript
const formatCurrency = (amount) => {
  return amount.toLocaleString('vi-VN') + 'đ';
};
```

#### 5.3. Navigation to Top-up

**Nghiệp vụ:** Navigate đến trang nạp tiền khi số dư không đủ

```javascript
const navigate = useNavigate();

<button onClick={() => navigate('/payment')}>
  Nạp tiền
</button>
```

**UI Components:**

1. **Trigger Button:** Hiển thị phương thức đã chọn + số dư
2. **Warning Banner:** Hiển thị khi số dư không đủ
3. **Bottom Sheet:** Dropdown chọn phương thức (CASH/WALLET)
4. **Payment Options:** Danh sách các phương thức với icon và info

---

### 6. VehicleSelectionModal.jsx (Modified)

**Vị trí:** `GoMirai_fe/src/components/VehicleSelectionModal.jsx`

**Các thay đổi chính:**

#### 6.1. State Management

```javascript
// New states for payment
const [selectedPaymentMethod, setSelectedPaymentMethod] = useState('CASH');
const [walletBalance, setWalletBalance] = useState(0);
const [isLoadingWallet, setIsLoadingWallet] = useState(false);
```

#### 6.2. Fetch Wallet Balance

```javascript
useEffect(() => {
  if (isOpen) {
    setIsLoadingWallet(true);
    walletApi.getWallet()
      .then((res) => {
        setWalletBalance(res.balance || 0);
        setIsLoadingWallet(false);
      })
      .catch((err) => {
        console.error('Failed to load wallet balance:', err);
        setWalletBalance(0);
        setIsLoadingWallet(false);
      });
  }
}, [isOpen]);
```

#### 6.3. Wallet Validation

```javascript
const canPayWithWallet = selectedPaymentMethod === 'WALLET' 
  ? walletBalance >= totalPrice 
  : true;

const isBookingDisabled = 
  isBooking || 
  isVehicleLoading(selectedVehicle.id) || 
  !pickupCoords || 
  !destinationCoords || 
  estimatedDistance <= 0 || 
  totalPrice <= 0 || 
  !canPayWithWallet;  // Disable button if insufficient balance
```

#### 6.4. Updated Booking Request

```javascript
const handleConfirm = async () => {
  const bookingData = {
    pickupLocation: pickupAddressSnapshot,
    dropoffLocation: dropoffAddressSnapshot,
    vehicleType: vehicleTypeForBooking,
    paymentMethod: selectedPaymentMethod,  // Send selected method to backend
  };
  
  const response = await bookingAPI.createBooking(bookingData);
};
```

---

## Xử Lý Nghiệp Vụ

### 1. Nghiệp Vụ: Đặt Xe với Cash

**Flow:**
1. User chọn CASH (default)
2. Click "Đặt xe"
3. Frontend gửi `paymentMethod: 'CASH'`
4. Backend bỏ qua payment processing
5. Lưu booking với status PENDING
6. Search drivers

**Không có thay đổi** so với logic cũ.

---

### 2. Nghiệp Vụ: Đặt Xe với Wallet

**Flow:**
1. User mở modal → Frontend fetch wallet balance
2. User chọn WALLET trong dropdown
3. Frontend check: `walletBalance >= totalPrice`
   - Nếu không đủ → Hiển thị warning + disable button
   - Nếu đủ → Enable button "Đặt xe"
4. User click "Đặt xe"
5. Frontend gửi `paymentMethod: 'WALLET'`
6. **Backend:**
   - Calculate pricing
   - **Call PaymentService.payRide()** → Trừ tiền ngay
   - Nếu thành công → Lưu booking
   - Nếu thất bại → Throw error, không lưu booking
7. Search drivers

**Saga Pattern:** Nếu payment thất bại, booking không được lưu → Tự động rollback

---

### 3. Nghiệp Vụ: Hủy Chuyến Cash

**Flow:**
1. User click "Hủy chuyến"
2. Backend update status → CANCELED
3. Publish event
4. **Không refund** (vì chưa thanh toán)

**Không có thay đổi** so với logic cũ.

---

### 4. Nghiệp Vụ: Hủy Chuyến Wallet

**Flow:**
1. User click "Hủy chuyến"
2. Backend check: `if (paymentMethod == WALLET)`
3. **Call PaymentService.refund()** → Hoàn tiền
4. Update booking status → CANCELED
5. Publish event

**Lưu ý:** Nếu refund thất bại, vẫn hủy booking nhưng log error để kiểm tra thủ công.

---

### 5. Nghiệp Vụ: Nạp Tiền

**Flow:**
1. User click "Nạp tiền" trong warning banner
2. Navigate to `/payment`
3. PaymentPage hiển thị form nạp tiền
4. User nhập số tiền → Submit
5. PaymentService xử lý top-up
6. Wallet balance tăng

**Không phải scope của tính năng này**, đã có sẵn.

---

## API Endpoints

### Backend APIs

#### 1. PaymentService - Internal API

**Base URL:** `http://payment-service:8088`

##### POST `/api/payment/internal/ride`

**Mô tả:** Trừ tiền từ ví khi đặt xe

**Authentication:** JWT Bearer Token

**Request Body:**
```json
{
  "userId": "uuid",
  "bookingId": "uuid",
  "amount": 50000
}
```

**Response 200:**
```json
{
  "transactionId": "uuid",
  "type": "RIDE_PAYMENT",
  "direction": "OUT",
  "amount": 50000,
  "status": "SUCCESS",
  "newBalance": 450000
}
```

**Response 400 - Insufficient Balance:**
```json
{
  "error": "INSUFFICIENT_BALANCE",
  "message": "Wallet balance is insufficient"
}
```

**Response 404 - Wallet Not Found:**
```json
{
  "error": "WALLET_NOT_FOUND",
  "message": "Wallet not found for user"
}
```

---

##### POST `/api/payment/internal/refund`

**Mô tả:** Hoàn tiền vào ví khi hủy chuyến

**Authentication:** JWT Bearer Token

**Request Body:**
```json
{
  "bookingId": "uuid",
  "amount": 50000
}
```

**Response 200:**
```json
{
  "transactionId": "uuid",
  "type": "REFUND",
  "direction": "IN",
  "amount": 50000,
  "status": "SUCCESS",
  "newBalance": 500000
}
```

---

#### 2. PaymentService - Public API

##### GET `/api/payment`

**Mô tả:** Lấy thông tin ví của user hiện tại

**Authentication:** JWT Bearer Token

**Response 200:**
```json
{
  "walletId": "uuid",
  "userId": "uuid",
  "balance": 500000,
  "currency": "VND",
  "lastUpdated": "2026-01-02T10:00:00Z"
}
```

---

#### 3. BookingService API

##### POST `/api/booking`

**Mô tả:** Tạo booking mới

**Authentication:** JWT Bearer Token

**Request Body:**
```json
{
  "pickupLocation": {
    "latitude": 10.7758,
    "longitude": 106.7010,
    "fullAddress": "123 Nguyen Trai, Q1, HCM"
  },
  "dropoffLocation": {
    "latitude": 10.7850,
    "longitude": 106.7150,
    "fullAddress": "456 Le Loi, Q1, HCM"
  },
  "vehicleType": "CAR_4",
  "paymentMethod": "WALLET"
}
```

**Response 200:**
```json
{
  "bookingId": "uuid",
  "customerId": "uuid",
  "status": "PENDING",
  "paymentMethod": "WALLET",
  "price": {
    "finalAmount": 50000,
    "currency": "VND"
  },
  "createdAt": "2026-01-02T10:00:00Z"
}
```

**Response 400 - Payment Failed:**
```json
{
  "error": "INSUFFICIENT_BALANCE",
  "message": "Số dư ví không đủ để thanh toán"
}
```

---

### Frontend APIs

#### 1. Wallet API

**File:** `src/api/wallet.api.js`

```javascript
export const walletApi = {
  // Get wallet info
  getWallet: async () => {
    const response = await client.get('/api/payment');
    return response.data;
  },
  
  // Top up wallet
  topUp: async (amount) => {
    const response = await client.post('/api/payment/topup', { amount });
    return response.data;
  }
};
```

#### 2. Booking API

**File:** `src/api/booking.api.js`

```javascript
export const bookingAPI = {
  // Create booking
  createBooking: async (bookingData) => {
    const response = await client.post('/api/booking', bookingData);
    return response.data;
  },
  
  // Cancel booking
  cancelBooking: async (bookingId, reason) => {
    const response = await client.put(`/api/booking/${bookingId}/cancel`, {
      reason
    });
    return response.data;
  }
};
```

---

## Error Handling

### Frontend Error Handling

**File:** `VehicleSelectionModal.jsx`

```javascript
try {
  const response = await bookingAPI.createBooking(bookingData);
  onConfirm(response);
  onClose();
  
} catch (error) {
  let errorMessage = 'Không thể đặt xe. Vui lòng thử lại.';
  
  if (error.response) {
    const data = error.response.data;
    
    // Handle specific errors
    if (data.message && data.message.includes('INSUFFICIENT_BALANCE')) {
      errorMessage = 'Số dư ví không đủ. Vui lòng nạp thêm tiền.';
    } else if (data.message && data.message.includes('WALLET_NOT_FOUND')) {
      errorMessage = 'Ví chưa được kích hoạt. Vui lòng liên hệ hỗ trợ.';
    } else if (data.message && data.message.includes('PAYMENT_UNAVAILABLE')) {
      errorMessage = 'Hệ thống thanh toán đang bảo trì. Vui lòng thử lại sau.';
    } else if (data.message) {
      errorMessage = data.message;
    }
  }
  
  setBookingError(errorMessage);
}
```

### Backend Error Handling

**File:** `PaymentServiceClient.java`

```java
try {
    ResponseEntity<TransactionResponse> response = restTemplate.exchange(...);
    return response.getBody();
    
} catch (HttpClientErrorException e) {
    String responseBody = e.getResponseBodyAsString();
    
    // 400 Bad Request - Insufficient balance
    if (e.getStatusCode().value() == 400 
        && responseBody.contains("INSUFFICIENT_BALANCE")) {
        throw new BusinessException("INSUFFICIENT_BALANCE: Số dư ví không đủ");
    }
    
    // 404 Not Found - Wallet not found
    if (e.getStatusCode().value() == 404 
        && responseBody.contains("WALLET_NOT_FOUND")) {
        throw new BusinessException("WALLET_NOT_FOUND: Ví chưa được kích hoạt");
    }
    
    throw new BusinessException("PAYMENT_FAILED: " + responseBody);
    
} catch (HttpServerErrorException e) {
    // 500 Server Error
    throw new BusinessException("PAYMENT_UNAVAILABLE: Payment service error");
    
} catch (ResourceAccessException e) {
    // Timeout or connection error
    throw new BusinessException("PAYMENT_UNAVAILABLE: Service timeout");
}
```

---

## Tổng Kết

### Files Đã Tạo Mới

**Backend:**
1. `PaymentServiceClient.java` - Service client gọi PaymentService
2. `RidePaymentRequest.java` - DTO payment request
3. `RefundRequest.java` - DTO refund request
4. `TransactionResponse.java` - DTO payment response

**Frontend:**
1. `PaymentMethodSelector.jsx` - Component chọn payment
2. `PaymentMethodSelector.css` - Styling component

### Files Đã Chỉnh Sửa

**Backend:**
1. `BookingService.java` - Thêm payment/refund logic

**Frontend:**
1. `VehicleSelectionModal.jsx` - Tích hợp payment selector

### Pattern & Best Practices

1. **Service-to-Service Communication:** Sử dụng Consul discovery
2. **Saga Pattern:** Payment thất bại → Không lưu booking (auto rollback)
3. **Error Handling:** Specific error codes cho từng case
4. **Validation:** Client-side + Server-side validation
5. **Idempotency:** Transaction ID unique, có thể retry
6. **Logging:** Log mọi payment transaction để audit

---

**Tài liệu được tạo:** 2026-01-02  
**Version:** 1.0  
**Author:** GoMirai Development Team
