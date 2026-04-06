/**
 * GoMirai - Data Preparation Tool
 * 
 * Mục tiêu: Đăng ký 1000 User + 1000 Driver một cách chậm rãi (avoid congestion).
 * Xuất kết quả ra file CSV để k6 Load Test sử dụng.
 * 
 * Cách chạy: node prepare_data.js [userCount] [driverCount]
 */

const fs = require('fs');
const path = require('path');

// --- Cấu hình ---
const BASE_URL = process.env.BASE_URL || 'http://34.146.249.41';
const ADMIN_PHONE = '01111111111';
const ADMIN_PASSWORD = 'TestPass123';
const CSV_PATH = path.join(__dirname, 'users_prepared.csv');
const DELAY_MS = 200; // Độ trễ giữa mỗi account để Auth-Service không bị overload

// --- Helper Functions ---
const sleep = (ms) => new Promise(resolve => setTimeout(resolve, ms));
const randomPhone = () => '09' + Math.floor(Math.random() * 1000000000).toString().padStart(8, '0');

async function callApi(endpoint, method, body, token = null) {
    const headers = { 'Content-Type': 'application/json' };
    if (token) headers['Authorization'] = `Bearer ${token}`;

    try {
        const response = await fetch(`${BASE_URL}${endpoint}`, {
            method,
            headers,
            body: body ? JSON.stringify(body) : null
        });
        
        let data = {};
        if (response.status !== 204) {
            data = await response.json();
        }
        return { status: response.status, data };
    } catch (error) {
        return { status: 500, error: error.message };
    }
}

async function prepareData() {
    console.log('🚀 Starting Data Preparation for GoMirai...');
    
    // 1. Login Admin để Approve Driver
    const adminLogin = await callApi('/api/auth/login', 'POST', { phoneNumber: ADMIN_PHONE, password: ADMIN_PASSWORD });
    const adminToken = adminLogin.data?.accessToken;
    if (!adminToken) {
        console.error('❌ Admin login failed. Cannot proceed with driver approval.');
        return;
    }
    console.log('✅ Admin logged in.');

    // Khởi tạo file CSV
    fs.writeFileSync(CSV_PATH, 'phoneNumber,password,role,userId,driverId,token\n');

    const totalUsers = 1000;
    const totalDrivers = 1000;
    const password = 'TestPass123';

    // ─── PHẦN 1: ĐĂNG KÝ USER (CUSTOMER) ───
    console.log(`\n--- Registering ${totalUsers} Customers ---`);
    for (let i = 1; i <= totalUsers; i++) {
        const phone = randomPhone();
        const reg = await callApi('/api/auth/register', 'POST', { phoneNumber: phone, password });
        
        if (reg.status === 201 || reg.status === 200) {
            const row = `${phone},${password},CUSTOMER,${reg.data.userId},,${reg.data.accessToken}\n`;
            fs.appendFileSync(CSV_PATH, row);
            if (i % 10 === 0) console.log(`[Customer] Registered ${i}/${totalUsers}`);
        } else {
            console.error(`[Customer] Failed ${phone}: ${reg.status}`);
        }
        await sleep(DELAY_MS);
    }

    // ─── PHẦN 2: ĐĂNG KÝ DRIVER (PHÊ DUYỆT LUÔN) ───
    console.log(`\n--- Registering & Approving ${totalDrivers} Drivers ---`);
    for (let i = 1; i <= totalDrivers; i++) {
        const phone = randomPhone();
        
        // 1. Register
        const reg = await callApi('/api/auth/register', 'POST', { phoneNumber: phone, password });
        if (reg.status !== 201 && reg.status !== 200) {
            console.error(`[Driver] Reg failed ${phone}`);
            continue;
        }
        const driverToken = reg.data.accessToken;
        const driverId = reg.data.userId;

        // 2. Apply
        const apply = await callApi('/api/drivers/apply', 'POST', {
            licenseNumber: '79C1-' + Math.floor(Math.random() * 900000 + 100000),
            vehicleBrand: 'Honda', vehicleModel: 'City',
            plateNumber: '51A-' + Math.floor(Math.random() * 90000 + 10000),
            color: 'White', vehicleType: 'CAR_4',
            registrationDate: '2023-01-01'
        }, driverToken);
        const internalDriverId = apply.data?.driverId;

        // 3. Approve (Admin)
        if (internalDriverId) {
            await callApi(`/api/drivers/${internalDriverId}/approve`, 'PATCH', null, adminToken);
            
            // 4. Login lại để lấy Token có ROLE_DRIVER
            const login = await callApi('/api/auth/login', 'POST', { phoneNumber: phone, password });
            const finalToken = login.data?.accessToken || driverToken;

            // 5. Cập nhật tọa độ ban đầu (Mới!)
            const lat = 10.7769 + (Math.random() - 0.5) * 0.05; // Loanh quanh trung tâm
            const lng = 106.7009 + (Math.random() - 0.5) * 0.05;
            await callApi('/api/tracking/location', 'POST', {
                driverId: driverId,
                latitude: lat,
                longitude: lng,
                status: 'ONLINE',
                vehicleType: 'CAR_4'
            }, finalToken);

            const row = `${phone},${password},DRIVER,${driverId},${internalDriverId},${finalToken}\n`;
            fs.appendFileSync(CSV_PATH, row);
            
            if (i % 10 === 0) console.log(`[Driver] Approved & Located ${i}/${totalDrivers}`);
        }

        await sleep(DELAY_MS * 2); // Driver tốn nhiều request hơn nên delay lâu hơn chút
    }

    console.log(`\n✨ DONE! Data saved to: ${CSV_PATH}`);
}

prepareData();
