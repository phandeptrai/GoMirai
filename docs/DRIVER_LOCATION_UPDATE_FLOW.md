# Driver Location Update Flow - Tổng Quan

## 📋 Tóm Tắt

**Câu trả lời ngắn gọn: KHÔNG có tự động gửi location khi driver chuyển ONLINE.**

App driver phải **tự gọi API** `POST /api/tracking/location` mỗi 5-10 giây để cập nhật vị trí.

---

## 🔄 Flow Chi Tiết

### 1. Khi Driver Chuyển Trạng Thái ONLINE/OFFLINE

#### Bước 1: Driver App gọi API DriverService
```
PATCH /api/drivers/me/status/online
```

#### Bước 2: DriverService xử lý
- **File**: `DriverService/src/main/java/com/gomirai/driver/service/DriverProfileService.java`
- **Method**: `updateAvailability(boolean online)`
- **Logic**:
  1. ✅ Kiểm tra `accountStatus == ACTIVE` (chỉ tài xế đã duyệt mới được online)
  2. ✅ Cập nhật `availabilityStatus = ONLINE` hoặc `OFFLINE` trong MongoDB
  3. ✅ **Publish event lên Kafka**: `DriverAvailabilityChangedEvent`
     - `driverId`
     - `availabilityStatus` (ONLINE/OFFLINE)
     - `vehicleType`

#### Bước 3: TrackingService nhận event từ Kafka
- **File**: `TrackingService/src/main/java/com/gomirai/tracking/consumer/DriverAvailabilityChangedConsumer.java`
- **Method**: `handleDriverAvailabilityChanged(DriverAvailabilityChangedEvent event)`
- **Logic**:
  1. ✅ Nhận event từ Kafka topic `driver-availability-changed`
  2. ✅ Lấy hoặc tạo `DriverGeoState` skeleton trong Redis
  3. ✅ **Chỉ cập nhật metadata** (status, vehicleType) vào Redis Hash
  4. ❌ **KHÔNG cập nhật location** (lat/lon) - vì chưa có!

**Kết luận**: Khi driver chuyển ONLINE, hệ thống chỉ sync **metadata** (status, vehicleType) vào Redis. **KHÔNG có location** vì app chưa gửi.

---

### 2. Update Location (App Driver Phải Tự Gọi)

#### Bước 1: Driver App gọi API TrackingService
```
POST /api/tracking/location
Authorization: Bearer <token>
Content-Type: application/json

{
  "driverId": "uuid",
  "latitude": 10.762622,
  "longitude": 106.660172,
  "status": "ONLINE",
  "vehicleType": "MOTORBIKE",
  "lastUpdatedAt": 0
}
```

**Tần suất**: App phải gọi mỗi **5-10 giây** khi driver đang ONLINE.

#### Bước 2: TrackingService xử lý
- **File**: `TrackingService/src/main/java/com/gomirai/tracking/controller/TrackingController.java`
- **Method**: `updateLocation(@RequestBody DriverGeoState state)`
- **File Service**: `TrackingService/src/main/java/com/gomirai/tracking/service/TrackingService.java`
- **Method**: `updateLocation(DriverGeoState state)`

**Logic**:
1. ✅ Lưu **Geo Point** (lat/lon) vào Redis Geo Set (`drivers:geo`)
2. ✅ Lưu **Metadata** (status, vehicleType, lastUpdatedAt) vào Redis Hash (`drivers:state:{driverId}`)
3. ✅ Set TTL = 5 phút (nếu không update trong 5 phút → tự động xóa)

**Lưu ý**: 
- API này **chỉ lưu location**, không kiểm tra driver có ONLINE hay không
- App driver phải tự đảm bảo chỉ gọi khi `status == ONLINE`
- Nếu app gọi với `status == OFFLINE`, location vẫn được lưu nhưng sẽ bị filter khi search nearby

---

## 📊 Sơ Đồ Flow

```
┌─────────────────────────────────────────────────────────────────┐
│ 1. DRIVER CHUYỂN ONLINE                                         │
└─────────────────────────────────────────────────────────────────┘
                    │
                    ▼
    ┌───────────────────────────────┐
    │ Driver App                    │
    │ PATCH /api/drivers/me/        │
    │ status/online                 │
    └───────────────┬───────────────┘
                    │
                    ▼
    ┌───────────────────────────────┐
    │ DriverService                 │
    │ - Update MongoDB              │
    │ - Publish Kafka Event         │
    └───────────────┬───────────────┘
                    │
                    ▼
    ┌───────────────────────────────┐
    │ Kafka Topic                   │
    │ driver-availability-changed    │
    └───────────────┬───────────────┘
                    │
                    ▼
    ┌───────────────────────────────┐
    │ TrackingService Consumer      │
    │ - Sync metadata (status,      │
    │   vehicleType) vào Redis      │
    │ ❌ KHÔNG có location          │
    └───────────────────────────────┘

┌─────────────────────────────────────────────────────────────────┐
│ 2. UPDATE LOCATION (App Phải Tự Gọi Mỗi 5-10s)                  │
└─────────────────────────────────────────────────────────────────┘
                    │
                    ▼
    ┌───────────────────────────────┐
    │ Driver App                    │
    │ POST /api/tracking/location   │
    │ { lat, lon, status, ... }     │
    └───────────────┬───────────────┘
                    │
                    ▼
    ┌───────────────────────────────┐
    │ TrackingService               │
    │ - Lưu Geo Point vào Redis     │
    │ - Lưu Metadata vào Redis     │
    └───────────────────────────────┘
```

---

## ❓ Câu Hỏi Thường Gặp

### Q1: Khi driver chuyển ONLINE, có tự động gửi location không?

**Trả lời**: **KHÔNG**. 

Khi driver chuyển ONLINE:
- ✅ Hệ thống sync **metadata** (status, vehicleType) vào Redis qua Kafka
- ❌ **KHÔNG có location** (lat/lon) vì app chưa gửi

App driver phải **tự gọi** `POST /api/tracking/location` để gửi location.

---

### Q2: App driver nên gọi update location khi nào?

**Trả lời**: 
- ✅ Khi driver **ONLINE** → gọi mỗi **5-10 giây**
- ❌ Khi driver **OFFLINE** → không cần gọi (hoặc có thể gọi để clear location)

**Logic đề xuất trong App**:
```javascript
if (driverStatus === 'ONLINE') {
  // Gọi API mỗi 5-10s
  setInterval(() => {
    updateLocation(currentLat, currentLon);
  }, 5000);
} else {
  // Dừng interval
  clearInterval(locationUpdateInterval);
}
```

---

### Q3: Nếu app gọi location với status OFFLINE thì sao?

**Trả lời**: 
- Location vẫn được lưu vào Redis
- Nhưng khi search nearby, sẽ bị **filter** (chỉ trả về driver có `status == AVAILABLE` hoặc `ONLINE`)
- Tốt nhất: App nên **không gọi** khi OFFLINE, hoặc gọi để **xóa location** khỏi Redis Geo

---

### Q4: Có cách nào tự động gửi location không?

**Trả lời**: Có thể implement, nhưng **không khuyến khích** vì:

**Option 1: Background Service trong DriverService**
- ❌ Vi phạm nguyên tắc microservice (DriverService không nên biết về location)
- ❌ Tốn tài nguyên (phải lưu location trong DriverService)

**Option 2: Scheduler trong TrackingService**
- ❌ TrackingService không biết driver nào đang ONLINE (phải query DriverService → coupling)
- ❌ Không có GPS data (phải app gửi lên)

**Option 3: App tự động gọi (Hiện tại - Khuyến khích)**
- ✅ Đúng kiến trúc microservice
- ✅ App có GPS data trực tiếp
- ✅ Linh hoạt (app có thể điều chỉnh tần suất)

---

## 🔍 Các API Liên Quan

### 1. Chuyển trạng thái ONLINE/OFFLINE
- **Endpoint**: `PATCH /api/drivers/me/status/online` hoặc `/offline`
- **Service**: DriverService
- **Kết quả**: Publish Kafka event → TrackingService sync metadata

### 2. Update Location
- **Endpoint**: `POST /api/tracking/location`
- **Service**: TrackingService
- **Role**: `ROLE_DRIVER`
- **Tần suất**: Mỗi 5-10 giây khi ONLINE

### 3. Tìm tài xế nearby
- **Endpoint**: `POST /api/tracking/nearby`
- **Service**: TrackingService
- **Role**: `isAuthenticated()` (bất kỳ user đã login)
- **Logic**: 
  - Search Redis Geo
  - Filter theo `status == AVAILABLE` hoặc `ONLINE`
  - Filter theo `vehicleType` (nếu có)

---

## 📝 Tóm Tắt

| Hành Động | Tự Động? | Cách Thức |
|-----------|----------|-----------|
| Chuyển ONLINE | ✅ Có (qua API) | App gọi `PATCH /api/drivers/me/status/online` |
| Sync metadata (status, vehicleType) | ✅ Tự động | Kafka event → TrackingService consumer |
| Update location (lat/lon) | ❌ **KHÔNG** | App phải tự gọi `POST /api/tracking/location` mỗi 5-10s |
| Tìm nearby drivers | ✅ Có (qua API) | App gọi `POST /api/tracking/nearby` |

---

## 🎯 Kết Luận

**Khi driver chuyển ONLINE:**
1. ✅ Metadata (status, vehicleType) được sync tự động qua Kafka
2. ❌ **Location KHÔNG tự động** - App phải tự gọi API

**App driver cần implement:**
- Gọi `POST /api/tracking/location` mỗi 5-10 giây khi `status == ONLINE`
- Dừng gọi khi `status == OFFLINE`
- Gửi đầy đủ: `driverId`, `latitude`, `longitude`, `status`, `vehicleType`

