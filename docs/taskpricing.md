# 📌 PRICING SERVICE – REQUIREMENTS SPECIFICATION

## 🎯 Mục tiêu

Service chịu trách nhiệm tính giá chuyến đi dựa trên:

- Loại xe
- Khu vực
- Khoảng cách dự kiến
- Thời gian dự kiến
- Surge giờ cao điểm
- Quy tắc tính giá được cấu hình trong system

⚠️ **Service chỉ trả giá — không xử lý thanh toán và ride history.**

## 🧩 Domain Models

### PricingRule (Cấu hình tính giá)

| Field | Type | Mô tả |
|-------|------|-------|
| ruleId | UUID | ID rule |
| vehicleType | Enum | MOTORBIKE, CAR_4_SEAT, CAR_7_SEAT, … |
| baseFare | double | Giá mở cửa (đơn vị tiền) |
| perKmRate | double | Giá 1 km |
| perMinuteRate | double | Giá 1 phút |
| surgeMultiplier | double | Hệ số giờ cao điểm (VD: 1.2x) |
| region | String | Khu vực áp dụng (VD: HCM, HN…) |
| active | Boolean | Chỉ rule active được áp dụng |

### PricingResponse

| Field | Type | Mô tả |
|-------|------|-------|
| estimatedFare | Money | Giá ước tính |
| appliedRuleId | UUID | Rule ID đang dùng |

## 📍 API DOCUMENTATION

### 1️⃣ Get Estimated Price (Tính giá trước khi đặt xe)

**URL:** `POST api/pricing/estimate`

#### 📥 Input

```json
{
  "vehicleType": "MOTORBIKE",
  "distanceKm": 8.5,
  "durationMinute": 22,
  "region": "HCM"
}
```

#### 🔍 Validation

| Điều kiện | Error |
|-----------|-------|
| vehicleType null/invalid | 400 – INVALID_VEHICLE_TYPE |
| distanceKm <= 0 | 400 – INVALID_DISTANCE |
| durationMinute <= 0 | 400 – INVALID_DURATION |
| region empty | 400 – INVALID_REGION |
| Không tìm thấy rule active | 404 – PRICING_RULE_NOT_FOUND |

#### 📤 Output

```json
{
  "estimatedFare": 35000,
  "appliedRuleId": "uuid-here"
}
```

**Vai trò sử dụng:** CUSTOMER App, DRIVER App, Booking Service

---

### 2️⃣ Recalculate Final Price (Tính lại khi kết thúc chuyến đi)

**URL:** `POST api/pricing/calculate-final`

#### 🛑 Khi nào gọi?

Khi chuyến đi kết thúc, hệ thống đã ghi nhận quãng đường thực tế từ GPS.

#### 📥 Input

```json
{
  "rideId": "uuid-ride",
  "vehicleType": "MOTORBIKE",
  "actualDistanceKm": 10.1,
  "actualDurationMinute": 30,
  "region": "HCM"
}
```

#### ⚠️ Validation (thêm)

| Điều kiện | Error |
|-----------|-------|
| rideId missing | 400 – INVALID_RIDE_ID |
| actualDistanceKm < estimatedDistance*0.5 | 409 – POSSIBLE_CHEAT_DISTANCE |
| actualDurationMinute < estimatedDuration*0.5 | 409 – POSSIBLE_CHEAT_DURATION |

🚨 **Cheat detection được chuyển sang Fraud Service sau này, nhưng Pricing phải cảnh báo.**

#### 📤 Output

```json
{
  "finalFare": 39000,
  "appliedRuleId": "uuid-here"
}
```

#### 👮 Role được phép call

| Role | Quyền |
|------|-------|
| CUSTOMER | ❌ Không được gọi trực tiếp |
| DRIVER | ❌ Không được gọi trực tiếp |
| BOOKING SERVICE | ✅ Gọi |
| ADMIN PANEL | ❌ Không gọi |

**Server-to-server call (internal API)**

---

### 3️⃣ Admin – Create/Update Pricing Rule

**URL:** `POST api/pricing/rules`

#### 📥 Input

```json
{
  "vehicleType": "CAR_4_SEAT",
  "baseFare": 15000,
  "perKmRate": 7000,
  "perMinuteRate": 650,
  "surgeMultiplier": 1.3,
  "region": "HN",
  "active": true
}
```

#### ⚠️ Validation đặc biệt

- `surgeMultiplier < 1` → bị từ chối
- `baseFare, perKmRate, perMinuteRate <= 0` → bị từ chối

#### 📤 Output

```json
{
  "ruleId": "uuid-here",
  "status": "CREATED"
}
```

#### 👮 Role

| Role | Quyền |
|------|-------|
| ADMIN | ✅ |
| CUSTOMER | ❌ |
| DRIVER | ❌ |








