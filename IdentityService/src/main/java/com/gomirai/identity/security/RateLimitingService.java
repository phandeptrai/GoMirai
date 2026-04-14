package com.gomirai.identity.security;

import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.stereotype.Service;

import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.Bucket;
import io.github.bucket4j.Refill;

/**
 * Service Rate Limiting sử dụng thuật toán Token Bucket (Bucket4j).
 * 
 * Thuật toán Token Bucket:
 * - Mỗi client (IP) có một "bucket" chứa tokens
 * - Mỗi request tiêu thụ 1 token
 * - Tokens được tự động nạp lại theo thời gian
 * - Khi bucket rỗng → request bị từ chối
 * 
 * Ưu điểm:
 * - Cho phép burst traffic trong giới hạn
 * - Không block hoàn toàn, chỉ giới hạn tốc độ
 * - Memory-efficient với ConcurrentHashMap
 */
@Service
public class RateLimitingService {

    /**
     * Cache lưu bucket cho mỗi IP address.
     * ConcurrentHashMap đảm bảo thread-safe trong môi trường multi-thread.
     */
    private final Map<String, Bucket> cache = new ConcurrentHashMap<>();

    /**
     * Lấy hoặc tạo bucket cho một key (thường là IP address).
     * 
     * Cấu hình: 100 requests/phút cho endpoints login/register.
     * Đủ cho người dùng bình thường, nhưng chặn được brute force.
     */
    public Bucket resolveBucket(String key) {
        // computeIfAbsent: chỉ tạo bucket mới nếu chưa tồn tại
        return cache.computeIfAbsent(key, k -> createNewBucket());
    }

    /**
     * Tạo bucket mới với cấu hình rate limit.
     * 
     * Bandwidth.classic(1000, Refill.intervally(1000, Duration.ofMinutes(1))):
     * - Dung lượng tối đa: 1000 tokens
     * - Nạp lại: 1000 tokens mỗi 1 phút (intervally = nạp cùng lúc)
     * 
     * NOTE: Tăng từ 100 lên 1000 để hỗ trợ load testing với nhiều users
     */
    private Bucket createNewBucket() {
        Bandwidth limit = Bandwidth.classic(1000, Refill.intervally(1000, Duration.ofMinutes(1)));
        return Bucket.builder()
                .addLimit(limit)
                .build();
    }

    /**
     * Thử tiêu thụ 1 token từ bucket.
     * 
     * @param key Key để xác định bucket (thường là IP address)
     * @return true nếu còn token (request được phép), false nếu hết (bị block)
     */
    public boolean tryConsume(String key) {
        Bucket bucket = resolveBucket(key);
        return bucket.tryConsume(1);
    }
}
