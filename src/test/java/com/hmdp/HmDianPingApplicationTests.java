package com.hmdp;

import com.hmdp.entity.Shop;
import com.hmdp.service.impl.ShopServiceImpl;
import com.hmdp.utils.CacheClient;
import com.hmdp.utils.RedisIdWorker;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

import javax.annotation.Resource;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static com.hmdp.utils.RedisConstants.CACHE_SHOP_KEY;

@SpringBootTest
class HmDianPingApplicationTests {
    @Resource
    private ShopServiceImpl shopService;

    @Resource
    private CacheClient cacheClient;

    @Resource
    private RedisIdWorker redisIdWorker;

    private ExecutorService es = Executors.newFixedThreadPool(500);

    /**
     * 测试 RedisIdWorker 生成分布式唯一 ID 的性能。
     * 该方法会创建 300 个线程并发执行，每个线程生成 100 个唯一 ID，
     * 最后统计生成 30000 个 ID 所花费的总时间。
     *
     * @throws 、
     */
    @Test
    void testIdWorker() throws InterruptedException {
        CountDownLatch latch = new CountDownLatch(300);
        Runnable tast = () -> {
            for (int i = 0; i < 100; i++) {
                long id = redisIdWorker.nextId("order");
                System.out.println("id = " + id);
            }
            latch.countDown();
        };
        long begin = System.currentTimeMillis();
        for (int i = 0; i < 300; i++) {
            es.submit(tast);
        }
        latch.await();
        long end = System.currentTimeMillis();
        System.out.println("耗时 = " + (end - begin));
    }

    /**
     * 测试将店铺信息以逻辑过期的方式存入缓存的功能。
     * 该方法会从数据库中获取指定 ID 的店铺信息，
     * 然后调用缓存客户端的方法将店铺信息存入 Redis 缓存，并设置逻辑过期时间。
     *
     * @throws
     */
    @Test
    void testSaveShop() throws InterruptedException {
        Shop shop = shopService.getById(1L);
        cacheClient.setWithLogicaExpire(CACHE_SHOP_KEY + 1L, shop, 10L, TimeUnit.SECONDS);

    }


}
