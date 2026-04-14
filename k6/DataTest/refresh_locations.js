/**
 * GoMirai - Location Refresh Tool (High Tech & Fast Mode)
 */

const fs = require('fs');
const path = require('path');

const BASE_URL = process.env.BASE_URL || 'http://localhost:8080';
const CSV_PATH = path.join(__dirname, 'users_prepared.csv');

async function callApi(endpoint, method, body, token) {
    try {
        const response = await fetch(`${BASE_URL}${endpoint}`, {
            method,
            headers: { 
                'Content-Type': 'application/json',
                'Authorization': `Bearer ${token}`
            },
            body: JSON.stringify(body)
        });
        return response.status;
    } catch (error) {
        return 500;
    }
}

async function refreshLocations() {
    console.log('🚀 High Speed Refreshing Driver Locations...');
    
    if (!fs.existsSync(CSV_PATH)) {
        console.error('❌ CSV file not found at:', CSV_PATH);
        return;
    }

    const fileContent = fs.readFileSync(CSV_PATH, 'utf8');
    const rows = fileContent.split(/\r?\n/).filter(row => row.trim() !== '');
    const dataRows = rows.slice(1); // Bỏ header

    console.log(`📊 Total rows in CSV (excluding header): ${dataRows.length}`);
    if (dataRows.length > 0) {
        console.log(`📝 Sample first row: ${dataRows[0]}`);
    }

    let count = 0;
    let errorCount = 0;

    for (const row of dataRows) {
        const cols = row.split(',').map(c => c.trim());
        const phone = cols[0];
        const password = cols[1];
        const role = cols[2];
        const userId = cols[3];

        if (role === 'DRIVER') {
            // 1. Phải Login lại để lấy Token mới (vì token cũ trong CSV đã hết hạn)
            const loginRes = await fetch(`${BASE_URL}/api/auth/login`, {
                method: 'POST',
                headers: { 'Content-Type': 'application/json' },
                body: JSON.stringify({ phoneNumber: phone, password: password })
            });
            
            if (loginRes.status !== 200) {
                errorCount++;
                if (errorCount <= 5) console.error(`❌ Login failed for ${phone}: ${loginRes.status}`);
                continue;
            }
            
            const loginData = await loginRes.json();
            const freshToken = loginData.accessToken;

            // 2. Cập nhật vị trí
            const lat = Number((10.7769 + (Math.random() - 0.5) * 0.05).toFixed(6));
            const lng = Number((106.7009 + (Math.random() - 0.5) * 0.05).toFixed(6));

            const status = await callApi('/api/tracking/location', 'POST', {
                driverId: userId,
                latitude: lat,
                longitude: lng,
                status: 'ONLINE',
                vehicleType: 'CAR_4'
            }, freshToken);

            if (status === 200 || status === 201) {
                count++;
                if (count % 100 === 0) console.log(`📍 Updated ${count} drivers...`);
            } else {
                errorCount++;
                if (errorCount <= 5) console.error(`❌ Tracking failed for ${userId}: ${status}`);
            }
            
            // Thêm delay nhỏ để tránh làm nghẽn Auth-Service
            await new Promise(r => setTimeout(r, 20)); 
        }
    }

    console.log(`\n✨ DONE! Fast-Refreshed ${count} drivers.`);
    if (errorCount > 0) console.warn(`⚠️ Warning: ${errorCount} updates failed.`);
}

refreshLocations();
