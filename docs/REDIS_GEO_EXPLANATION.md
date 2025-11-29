# Redis Geo Set (`drivers:geo`) - Mục Đích và Cách Hoạt Động

## 📍 Mục Đích Chính

**`drivers:geo`** là một **Redis Geo Set** (Sorted Set) dùng để lưu trữ **vị trí địa lý (latitude/longitude)** của tất cả tài xế đang hoạt động, phục vụ cho chức năng **Geospatial Search** (tìm kiếm tài xế gần nhất).

---

## 🎯 Tại Sao Cần Redis Geo Set?

### Vấn Đề:
- Cần tìm tài xế gần khách hàng nhất (ví dụ: trong bán kính 2km)
- Có hàng nghìn tài xế đang online
- Phải tìm kiếm **real-time** (không thể query database chậm)

### Giải Pháp:
- **Redis Geo Set** hỗ trợ **geospatial queries** cực nhanh
- Tìm kiếm trong bán kính chỉ mất **vài milliseconds**
- Tự động tính toán khoảng cách (Haversine formula)

---

## 📊 Cấu Trúc Dữ Liệu

### Redis Geo Set (`drivers:geo`)
```
Type: Sorted Set (Geo Set)
Key: "drivers:geo"
Members: 
  - driverId1 → Point(longitude, latitude)
  - driverId2 → Point(longitude, latitude)
  - driverId3 → Point(longitude, latitude)
  ...
```

**Ví dụ trong Redis:**
```
drivers:geo (Sorted Set)
├── "63994eca-1964-4a01-9363-331a24f62d9e" → (106.660172, 10.762622)
├── "a7f2e006-2cd1-45a9-abf1-55014f3aedce" → (106.670000, 10.770000)
└── "b8g3f117-3de2-56ba-bcf2-66125g4bfedf" → (106.650000, 10.750000)
```

---

## 🔧 Các Thao Tác Chính

### 1. **Lưu Vị Trí** (Update Location)
```java
// Khi driver app gọi: POST /api/tracking/location
redisTemplate.opsForGeo()
    .add("drivers:geo", 
         new Point(longitude, latitude), 
         driverId);
```

**Ví dụ:**
- Driver ID: `63994eca-1964-4a01-9363-331a24f62d9e`
- Location: `10.762622°N, 106.660172°E` (TP.HCM)
- → Lưu vào `drivers:geo` với key = driverId, value = Point

---

### 2. **Tìm Tài Xế Gần Nhất** (Geospatial Search)
```java
// Khi customer app gọi: POST /api/tracking/nearby
Circle circle = new Circle(
    new Point(customerLon, customerLat),  // Vị trí khách hàng
    new Distance(2000, METERS)            // Bán kính 2km
);

GeoResults<GeoLocation<String>> results = 
    redisTemplate.opsForGeo().radius("drivers:geo", circle, args);
```

**Ví dụ:**
- Khách hàng ở: `10.762622°N, 106.660172°E`
- Bán kính: `2000m` (2km)
- → Redis trả về tất cả driver trong vòng 2km, **đã sắp xếp theo khoảng cách**

**Kết quả:**
```
1. Driver A - 500m
2. Driver B - 1200m
3. Driver C - 1800m
```

---

### 3. **Xóa Vị Trí** (Cleanup)
```java
// Khi metadata hết hạn (TTL = 5 phút)
redisTemplate.opsForGeo().remove("drivers:geo", driverId);
```

---

## 🚀 Lợi Ích Của Redis Geo Set

### ✅ **Performance**
- Tìm kiếm trong **milliseconds** (không phải seconds)
- Hỗ trợ hàng nghìn tài xế đồng thời
- Tự động tính toán khoảng cách (Haversine)

### ✅ **Real-time**
- Cập nhật location mỗi 5-10 giây
- Tìm kiếm ngay lập tức
- Không cần query database

### ✅ **Scalability**
- Redis Geo Set có thể lưu **hàng triệu** điểm
- Memory-efficient (chỉ lưu lat/lon, không lưu metadata)

---

## 🔄 Flow Hoàn Chỉnh

### **Khi Driver Update Location:**
```
1. App Driver → POST /api/tracking/location
   { driverId, lat, lon, status, vehicleType }
   
2. TrackingService.updateLocation()
   ├── Lưu Geo Point vào "drivers:geo" (lat/lon)
   └── Lưu Metadata vào "drivers:state:{driverId}" (status, vehicleType)
```

### **Khi Customer Tìm Nearby Drivers:**
```
1. App Customer → POST /api/tracking/nearby
   { lat, lon, radius: 2000, vehicleType: "MOTORBIKE" }
   
2. TrackingService.findNearbyDrivers()
   ├── Query Redis Geo Set "drivers:geo" trong bán kính 2km
   ├── Lấy metadata từ "drivers:state:{driverId}" cho mỗi driver
   ├── Filter theo status (ONLINE) và vehicleType
   └── Trả về danh sách driver gần nhất (đã sort theo khoảng cách)
```

---

## 📋 So Sánh: Geo Set vs Metadata

| Aspect | `drivers:geo` (Geo Set) | `drivers:state:{driverId}` (String) |
|--------|-------------------------|-------------------------------------|
| **Mục đích** | Lưu vị trí (lat/lon) | Lưu metadata (status, vehicleType) |
| **Type** | Sorted Set (Geo) | String (JSON) |
| **TTL** | ❌ Không có (cleanup manual) | ✅ 5 phút (auto expire) |
| **Dùng để** | Geospatial search | Filter & metadata |
| **Size** | Nhỏ (chỉ lat/lon) | Lớn hơn (JSON string) |

---

## 🎯 Tại Sao Tách Riêng?

### **Tách Geo Set và Metadata:**
1. **Geo Set** (`drivers:geo`):
   - Chỉ lưu **lat/lon** → nhẹ, nhanh
   - Dùng cho **geospatial search**
   - Không có TTL (vì Redis Geo Set không hỗ trợ TTL trực tiếp)

2. **Metadata** (`drivers:state:{driverId}`):
   - Lưu **status, vehicleType, lastUpdatedAt** → đầy đủ thông tin
   - Dùng cho **filter** (chỉ lấy driver ONLINE, MOTORBIKE, ...)
   - Có TTL = 5 phút (tự động xóa khi hết hạn)

### **Kết Hợp:**
- Query Geo Set → lấy danh sách driver gần nhất
- Query Metadata → filter theo status/vehicleType
- → Kết quả: Danh sách driver gần nhất, đã filter đúng yêu cầu

---

## 🔍 Ví Dụ Thực Tế

### **Scenario: Khách hàng tìm xe máy gần nhất**

**Input:**
```json
{
  "latitude": 10.762622,
  "longitude": 106.660172,
  "radius": 2000,
  "vehicleType": "MOTORBIKE",
  "status": "ONLINE"
}
```

**Process:**
1. Query `drivers:geo` → Tìm tất cả driver trong bán kính 2km
   ```
   - Driver A: 500m
   - Driver B: 1200m
   - Driver C: 1800m
   - Driver D: 2500m (ngoài bán kính)
   ```

2. Query `drivers:state:{driverId}` cho mỗi driver:
   ```
   - Driver A: { status: "ONLINE", vehicleType: "MOTORBIKE" } ✅
   - Driver B: { status: "OFFLINE", vehicleType: "MOTORBIKE" } ❌
   - Driver C: { status: "ONLINE", vehicleType: "CAR_4" } ❌
   ```

3. Filter & Return:
   ```json
   [
     {
       "driverId": "driver-a",
       "latitude": 10.765000,
       "longitude": 106.662000,
       "distance": 500.0,
       "status": "ONLINE",
       "vehicleType": "MOTORBIKE"
     }
   ]
   ```

---

## ⚠️ Lưu Ý Quan Trọng

### **1. Geo Set Không Có TTL**
- Redis Geo Set không hỗ trợ TTL trực tiếp
- Phải cleanup manual khi metadata hết hạn
- → Đã implement scheduled task (mỗi 2 phút)

### **2. Metadata Hết Hạn = Driver Không Còn Hoạt Động**
- Nếu metadata hết hạn (5 phút không update) → driver offline
- Geo Point vẫn còn trong Geo Set → phải cleanup
- → Logic cleanup tự động xóa Geo Point khi metadata hết hạn

### **3. Performance**
- Geo Set query rất nhanh (milliseconds)
- Nhưng nếu có quá nhiều driver (hàng chục nghìn) → có thể chậm
- → Nên set limit hợp lý (ví dụ: 50 driver gần nhất)

---

## 📝 Tóm Tắt

**`drivers:geo`** là **Redis Geo Set** dùng để:
1. ✅ Lưu trữ vị trí địa lý (lat/lon) của tất cả tài xế
2. ✅ Hỗ trợ geospatial search (tìm tài xế trong bán kính)
3. ✅ Tính toán khoảng cách tự động (Haversine)
4. ✅ Performance cao (milliseconds)
5. ✅ Real-time tracking

**Kết hợp với `drivers:state:{driverId}`** để:
- Tìm kiếm theo vị trí (Geo Set)
- Filter theo status/vehicleType (Metadata)
- → Kết quả: Danh sách tài xế gần nhất, đã filter đúng yêu cầu

