package com.gomirai.auth.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.concurrent.Executor;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

/**
 * Cấu hình Executor Pool riêng cho tác vụ BCrypt (CPU-bound Isolation).
 *
 * <p><b>Tại sao cần pool riêng?</b>
 * <pre>
 *   - BCrypt là CPU-bound: không thể được tạm dừng (park) bởi VT scheduler
 *     khi đang chạy, nó chiếm carrier thread suốt quá trình hash (~50ms ở cost=8).
 *
 *   - Không có pool riêng: 200 VUs đồng thời → 200 BCrypt ops → 200 carrier threads
 *     bị giữ đồng thời → CPU 100%, context switching storm, toàn bộ JVM bị trì hoãn.
 *
 *   - Với pool riêng: tối đa maxPoolSize BCrypt ops chạy song song (= số CPU cores).
 *     Các BCrypt ops còn lại xếp hàng trong queue → tải CPU được giới hạn có kiểm soát.
 *     Các tác vụ I/O (DB lookup, JWT validation) không bị ảnh hưởng.
 * </pre>
 *
 * <p><b>Cách dùng trong AuthApplicationService:</b>
 * <pre>
 *   \@Qualifier("bcryptExecutor") Executor bcryptExecutor;
 *   CompletableFuture.supplyAsync(() -> passwordEncoder.matches(raw, hash), bcryptExecutor)
 * </pre>
 *
 * <p><b>Sizing cho GKE e2-standard (24 vCPU / 6 services):</b>
 * <pre>
 *   - Auth pod: ~1 vCPU request, ~2 vCPU limit
 *   - core-pool-size = 2 (= CPU request): luôn sẵn sàng
 *   - max-pool-size  = 4 (= CPU limit):   burst khi có spike
 *   - queue-capacity = 500: đệm tải burst mà không OOM
 * </pre>
 */
@Configuration
public class AsyncBcryptConfig {

    @Value("${auth.bcrypt.executor.core-pool-size:2}")
    private int corePoolSize;

    @Value("${auth.bcrypt.executor.max-pool-size:4}")
    private int maxPoolSize;

    @Value("${auth.bcrypt.executor.queue-capacity:500}")
    private int queueCapacity;

    /**
     * Platform Thread pool riêng cho BCrypt.
     *
     * <p>Chú ý: Phải dùng Platform Threads (không phải Virtual Threads) vì:
     * BCrypt là tác vụ thuần CPU — Virtual Threads chỉ có lợi khi có I/O blocking.
     * Dùng VT cho BCrypt không giúp ích gì và còn có thể gây VT pinning.
     */
    @Bean(name = "bcryptExecutor")
    public Executor bcryptExecutor() {
        ThreadPoolExecutor executor = new ThreadPoolExecutor(
                corePoolSize,
                maxPoolSize,
                60L,
                TimeUnit.SECONDS,
                new LinkedBlockingQueue<>(queueCapacity),
                r -> {
                    // Đặt tên thread để dễ debug trong thread dump / monitoring
                    Thread t = new Thread(r, "bcrypt-worker-" + System.nanoTime());
                    t.setDaemon(true);
                    return t;
                },
                // Nếu queue đầy → reject ngay bằng 503 (không để request treo vô hạn)
                new ThreadPoolExecutor.AbortPolicy()
        );
        // Khởi động sẵn core threads để tránh cold start latency
        executor.prestartAllCoreThreads();
        return executor;
    }
}
