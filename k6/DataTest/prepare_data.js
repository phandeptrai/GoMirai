/**
 * GoMirai - Data Preparation Tool (Optimized Version)
 */

const fs = require('fs');
const path = require('path');
const { splitK6Csvs } = require('./split_k6_csvs');

// --- Cấu hình ---
// Mặc định: API Gateway Docker map cổng 8080 → host. Ghi đè: set BASE_URL=...
const BASE_URL = process.env.BASE_URL || 'http://localhost:8080';
// Phải trùng user ADMIN đã seed trong DB (hoặc: ADMIN_PHONE / ADMIN_PASSWORD trong env)
const ADMIN_PHONE = process.env.ADMIN_PHONE || '01111111111';
const ADMIN_PASSWORD = process.env.ADMIN_PASSWORD || 'TestPass123';
const CSV_PATH = path.join(__dirname, 'users_prepared.csv');

// TĂNG TỐC TẠI ĐÂY:
const CONCURRENCY = 15; // Số lượng request chạy song song cùng lúc
const BATCH_DELAY = 100; // Nghỉ một chút giữa các đợt nhỏ

// --- Helper Functions ---
const sleep = (ms) => new Promise(resolve => setTimeout(resolve, ms));
const randomPhone = () => '09' + Math.floor(Math.random() * 100000000).toString().padStart(8, '0');

async function callApi(endpoint, method, body, token = null) {
    const headers = { 'Content-Type': 'application/json' };
    if (token) headers['Authorization'] = `Bearer ${token}`;
    const url = `${BASE_URL}${endpoint}`;
    try {
        const response = await fetch(url, {
            method,
            headers,
            body: body ? JSON.stringify(body) : null
        });
        let data = {};
        if (response.status !== 204) {
            const text = await response.text();
            if (text) {
                try {
                    data = JSON.parse(text);
                } catch {
                    data = { _nonJson: text.slice(0, 500) };
                }
            }
        }
        return { status: response.status, data };
    } catch (error) {
        return { status: 0, data: {}, networkError: error.message || String(error) };
    }
}

// Hàm xử lý giới hạn luồng (tương tự p-limit)
async function asyncPool(poolLimit, array, iteratorFn) {
    const ret = [];
    const executing = [];
    for (const item of array) {
        const p = Promise.resolve().then(() => iteratorFn(item, array));
        ret.push(p);
        if (poolLimit <= array.length) {
            const e = p.then(() => executing.splice(executing.indexOf(e), 1));
            executing.push(e);
            if (executing.length >= poolLimit) {
                await Promise.race(executing);
            }
        }
    }
    return Promise.all(ret);
}

function printAdminLoginHelp(adminLogin) {
    const loginUrl = `${BASE_URL}/api/auth/login`;
    console.error('❌ Admin login failed.');
    console.error(`   BASE_URL: ${BASE_URL}`);
    console.error(`   POST:     ${loginUrl}`);
    console.error(`   Phone:    ${ADMIN_PHONE}`);
    if (adminLogin.status === 0) {
        console.error(`   Lỗi:      Không kết nối được (${adminLogin.networkError || 'ECONNREFUSED / DNS / TLS'})`);
        console.error('   → Bật stack Docker, map đúng cổng (vd. 8080), hoặc set BASE_URL=http://host:port');
    } else {
        console.error(`   HTTP:     ${adminLogin.status}`);
        const d = adminLogin.data || {};
        if (d.message) console.error(`   message:  ${d.message}`);
        if (d._nonJson) console.error(`   body:     ${d._nonJson}`);
        else if (Object.keys(d).length && !d.accessToken) console.error(`   body:     ${JSON.stringify(d)}`);
        console.error(
            '   → Kiểm tra DB đã seed admin (SĐT/mật khẩu trùng script hoặc set ADMIN_PHONE / ADMIN_PASSWORD).'
        );
    }
}

async function prepareData() {
    console.log('🚀 Starting Optimized Data Preparation...');
    console.log(`   Gateway: ${BASE_URL}`);

    const adminLogin = await callApi('/api/auth/login', 'POST', { phoneNumber: ADMIN_PHONE, password: ADMIN_PASSWORD });
    const adminToken = adminLogin.data?.accessToken;
    if (!adminToken) {
        printAdminLoginHelp(adminLogin);
        return;
    }

    fs.writeFileSync(
        CSV_PATH,
        'phoneNumber,password,role,userId,driverId,token,latitude,longitude\n'
    );

    const totalUsers = 1000;
    const totalDrivers = 1000;
    const password = 'TestPass123';

    // ─── PHẦN 1: CUSTOMERS (CHẠY SONG SONG) ───
    console.log(`\n--- Registering ${totalUsers} Customers (Concurrency: ${CONCURRENCY}) ---`);
    const userIndices = Array.from({ length: totalUsers }, (_, i) => i + 1);
    
    await asyncPool(CONCURRENCY, userIndices, async (i) => {
        const phone = randomPhone();
        const reg = await callApi('/api/auth/register', 'POST', { phoneNumber: phone, password });
        
        if (reg.status === 201 || reg.status === 200) {
            const row = `${phone},${password},CUSTOMER,${reg.data.userId},,${reg.data.accessToken},,\n`;
            fs.appendFileSync(CSV_PATH, row);
            if (i % 50 === 0) console.log(`[Customer] Progress: ${i}/${totalUsers}`);
        }
        await sleep(BATCH_DELAY);
    });

    // ─── PHẦN 2: DRIVERS (CHẠY SONG SONG) ───
    console.log(`\n--- Registering & Approving ${totalDrivers} Drivers ---`);
    const driverIndices = Array.from({ length: totalDrivers }, (_, i) => i + 1);

    await asyncPool(CONCURRENCY, driverIndices, async (i) => {
        const phone = randomPhone();
        const reg = await callApi('/api/auth/register', 'POST', { phoneNumber: phone, password });
        
        if (reg.status === 201 || reg.status === 200) {
            const driverToken = reg.data.accessToken;
            const driverId = reg.data.userId;

            // Apply
            const apply = await callApi('/api/drivers/apply', 'POST', {
                licenseNumber: '79C1-' + Math.floor(Math.random() * 900000 + 100000),
                vehicleBrand: 'Honda', vehicleModel: 'City',
                plateNumber: '51A-' + Math.floor(Math.random() * 90000 + 10000),
                color: 'White', vehicleType: 'CAR_4',
                registrationDate: '2023-01-01'
            }, driverToken);

            const internalDriverId = apply.data?.driverId;
            if (internalDriverId) {
                // Approve & Location Update
                await callApi(`/api/drivers/${internalDriverId}/approve`, 'PATCH', null, adminToken);
                const login = await callApi('/api/auth/login', 'POST', { phoneNumber: phone, password });
                const finalToken = login.data?.accessToken || driverToken;

                const lat = Number((10.7769 + (Math.random() - 0.5) * 0.05).toFixed(6));
                const lng = Number((106.7009 + (Math.random() - 0.5) * 0.05).toFixed(6));
                await callApi('/api/tracking/location', 'POST', {
                    driverId: driverId, latitude: lat, longitude: lng, status: 'ONLINE', vehicleType: 'CAR_4'
                }, finalToken);

                const row = `${phone},${password},DRIVER,${driverId},${internalDriverId},${finalToken},${lat},${lng}\n`;
                fs.appendFileSync(CSV_PATH, row);
            }
        }
        if (i % 20 === 0) console.log(`[Driver] Progress: ${i}/${totalDrivers}`);
        await sleep(BATCH_DELAY);
    });

    console.log(`\n✨ DONE! Output: ${CSV_PATH}`);
    splitK6Csvs(CSV_PATH, path.dirname(CSV_PATH));
}

prepareData();