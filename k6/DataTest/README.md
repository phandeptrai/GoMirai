/**
 * DataTest Module
 * 
 * Mục đích:
 * Thư mục này dùng để chứa các kịch bản tạo dữ liệu mẫu (mock data) cần thiết cho việc
 * Load Test, Stress Test có sử dụng SharedArray hoặc đọc từ file JSON lớn thay vì tạo data
 * cho mỗi VU trong hàm setup().
 * 
 * Cách dùng:
 * 1. Viết script k6 để /api/auth/register tự động sinh ra 1000 users.
 * 2. Lưu kết quả ra file /k6/DataTest/users.json
 * 3. Ở các test script khác, load file đó:
 * 
 * import { SharedArray } from 'k6/data';
 * const users = new SharedArray('users', function() {
 *   return JSON.parse(open('./users.json'));
 * });
 * 
 * export default function () {
 *   const userIndex = __VU % users.length;
 *   const user = users[userIndex];
 *   // ... run test bằng token của riêng user này để tránh lock
 * }
 */
