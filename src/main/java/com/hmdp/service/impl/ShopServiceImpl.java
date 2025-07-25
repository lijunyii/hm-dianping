package com.hmdp.service.impl;

import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.dto.Result;
import com.hmdp.entity.Shop;
import com.hmdp.mapper.ShopMapper;
import com.hmdp.service.IShopService;
import com.hmdp.utils.CacheClient;
import com.hmdp.utils.SystemConstants;
import org.springframework.data.geo.Distance;
import org.springframework.data.geo.GeoResult;
import org.springframework.data.geo.GeoResults;
import org.springframework.data.redis.connection.RedisGeoCommands;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.domain.geo.GeoReference;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.Resource;
import java.util.*;
import java.util.concurrent.TimeUnit;

import static com.hmdp.utils.RedisConstants.*;

/**
 * <p>
 * 服务实现类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@Service
public class ShopServiceImpl extends ServiceImpl<ShopMapper, Shop> implements IShopService {
    @Resource
    private StringRedisTemplate stringRedisTemplate;

    @Resource
    private CacheClient cacheClient;


    @Override
    public Result queryById(Long id) {
        //缓存穿透
        Shop shop = cacheClient.queryWithPassThrough(CACHE_SHOP_KEY, id, Shop.class, this::getById, CACHE_SHOP_TTL, TimeUnit.MINUTES);
        //缓存击穿（互斥锁）
        //Shop shop =queryWithMutex(id);
        //缓存击穿（逻辑过期）
        //Shop shop = cacheClient.queryWithLogicalExpire(CACHE_SHOP_KEY, id, Shop.class, this::getById, CACHE_SHOP_TTL, TimeUnit.MINUTES);

        if (shop == null) {
            return Result.fail("店铺不存在");
        }
        //6.返回
        return Result.ok(shop);
    }


    @Override
    @Transactional
    public Result updata(Shop shop) {
        Long id = shop.getId();
        if (id == null) {
            return Result.fail("店铺id不能为空");
        }
        //1.更新数据库
        updateById(shop);
        //2.删除缓存
        stringRedisTemplate.delete(CACHE_SHOP_KEY + id);
        return Result.ok();
    }

     @Override
    public Result queryShopByType(Integer typeId, Integer current, Double x, Double y) {
        // 1.判断是否需要根据坐标查询
        if (x == null || y == null) {
            // 不需要坐标查询，按数据库查询
            Page<Shop> page = query()
                    .eq("type_id", typeId)
                    .page(new Page<>(current, SystemConstants.DEFAULT_PAGE_SIZE));
            // 返回数据
            return Result.ok(page.getRecords());
        }

        // 2.计算分页参数
        int from = (current - 1) * SystemConstants.DEFAULT_PAGE_SIZE;
        int end = current * SystemConstants.DEFAULT_PAGE_SIZE;

        // 3.查询redis、按照距离排序、分页。结果：shopId、distance
        String key = SHOP_GEO_KEY + typeId;
        GeoResults<RedisGeoCommands.GeoLocation<String>> results = stringRedisTemplate.opsForGeo() // GEOSEARCH key BYLONLAT x y BYRADIUS 10 WITHDISTANCE
                .search(
                        key,
                        GeoReference.fromCoordinate(x, y),
                        new Distance(5000),
                        RedisGeoCommands.GeoSearchCommandArgs.newGeoSearchArgs().includeDistance().limit(end)
                );
        // 4.解析出id
        if (results == null) {
            return Result.ok(Collections.emptyList());
        }
        List<GeoResult<RedisGeoCommands.GeoLocation<String>>> list = results.getContent();
        if (list.size() <= from) {
            // 没有下一页了，结束
            return Result.ok(Collections.emptyList());
        }
        // 4.1.截取 from ~ end的部分
        List<Long> ids = new ArrayList<>(list.size());
        Map<String, Distance> distanceMap = new HashMap<>(list.size());
        list.stream().skip(from).forEach(result -> {
            // 4.2.获取店铺id
            String shopIdStr = result.getContent().getName();
            ids.add(Long.valueOf(shopIdStr));
            // 4.3.获取距离
            Distance distance = result.getDistance();
            distanceMap.put(shopIdStr, distance);
        });
        // 5.根据id查询Shop
        String idStr = StrUtil.join(",", ids);
        List<Shop> shops = query().in("id", ids).last("ORDER BY FIELD(id," + idStr + ")").list();
        for (Shop shop : shops) {
            shop.setDistance(distanceMap.get(shop.getId().toString()).getValue());
        }
        // 6.返回
        return Result.ok(shops);
    }

    //private static final ExecutorService CACHE_REBUILD_EXECUTOR = Executors.newFixedThreadPool(10);

//    /**
//     * 逻辑过期解决缓存击穿问题
//     * @param id
//     * @return
//     */
//    public  Shop queryWithLogicalExpire(Long id){
//        // 定义 Redis 键
//        String key = CACHE_SHOP_KEY + id;
//        //1.从redis查询商铺缓存
//        String shopJson=stringRedisTemplate.opsForValue().get(CACHE_SHOP_KEY+id);
//        //2判断是否存在
//        if (StrUtil.isBlank(shopJson)){
//            //3，存在直接返回
//            return null;
//        }
//        //4命中需要把json反序列化为对象
//        RedisData redisData = JSONUtil.toBean(shopJson, RedisData.class);
//        Shop shop = JSONUtil.toBean((JSONObject) redisData.getData(), Shop.class);
//        LocalDateTime expireTime = redisData.getExpireTime();
//        //5判断是否过期
//        if (expireTime.isAfter(LocalDateTime.now())){
//            //5.1未过期直接返回店铺信息
//            return shop;
//        }
//        //5.2已过期需要缓存重建
//
//        //6缓存重建
//
//        //6.1获取互斥锁
//        String lockKey = LOCK_SHOP_KEY+id;
//        boolean isLock = trtLock(lockKey);
//
//        //6.2判断是否获取成功
//        if (!isLock){
//            //6.3成功开启独立线程，实现缓存重建
//            CACHE_REBUILD_EXECUTOR.submit(() ->{
//                try {
//                    //重建缓存
//                    this.saveShop2Redis(id,20L);
//                } catch (Exception e) {
//                    throw new RuntimeException(e);
//                } finally {
//                    //释放锁
//                    unlock(lockKey);
//                }
//            });
//        }
//
//
//        //6.4返回过期店铺信息
//
//
//
//        return shop;
//    }
//
//
//    /**
//     * 使用互斥锁解决缓存击穿问题，查询指定 ID 的商铺信息
//     * 缓存击穿指的是热点 key 在缓存过期瞬间，大量请求同时涌入数据库。
//     * 此方法通过互斥锁保证同一时间只有一个线程能进行缓存重建操作。
//     * @param id 商铺的唯一标识
//     * @return 商铺实体对象，如果未找到则返回 null
//     */
//    public  Shop queryWithMutex(Long id){
//        // 定义 Redis 键
//        String key = CACHE_SHOP_KEY + id;
//        //1.从redis查询商铺缓存
//        String shopJson=stringRedisTemplate.opsForValue().get(CACHE_SHOP_KEY+id);
//        //2判断是否存在
//        if (StrUtil.isNotBlank(shopJson)){
//            //3，存在直接返回
//            Shop shop=JSONUtil.toBean(shopJson,Shop.class);
//            return shop;
//        }
//        //判断命中是否是空值
//        if (shopJson !=null){
//            //返回错误信息
//            return null;
//        }
//        String lockKey= null;
//        Shop shop = null;
//        try {
//            //4.实现缓存重建
//            //4.1.获取互斥锁
//            lockKey = "lock:shop"+id;
//            boolean isLock=trtLock(lockKey);
//            //4.2判断是否获取成功
//            if(! isLock){
//                //4.3失败则休眠且重试
//                Thread.sleep(50);
//                return  queryWithPassThrough(id);
//            }
//            //4.4成功根据id查询数据库
//            shop = getById(id);
//            //模拟重建的延时
//            Thread.sleep(200);
//            //5，不存在返回错误
//            if(shop==null){
//                //将空值写入 redis
//                stringRedisTemplate.opsForValue().set(key,"",CACHE_NULL_TTL, TimeUnit.MINUTES);
//                //返回错误信息
//                return null;
//            }
//            //6.存在写入redis
//            stringRedisTemplate.opsForValue().set(key,JSONUtil.toJsonStr(shop),CACHE_SHOP_TTL, TimeUnit.MINUTES);
//        } catch (InterruptedException e) {
//            throw new RuntimeException(e);
//        }finally {
//            //7释放互斥锁
//            unlock(lockKey);
//        }
//        //8.返回
//        return shop;
//    }
//
//
//     /**
//     * 通过缓存穿透方案查询商铺信息
//     * 该方法会先从 Redis 缓存中查询商铺信息，若缓存不存在则查询数据库，
//     * 数据库查询结果为空时会将空值写入缓存，避免缓存穿透问题
//     * @param id 商铺的唯一标识
//     * @return 商铺实体对象，如果未找到则返回 null
//     */
//    public  Shop queryWithPassThrough(Long id){
//        // 定义 Redis 键
//        String key = CACHE_SHOP_KEY + id;
//        //1.从redis查询商铺缓存
//        String shopJson=stringRedisTemplate.opsForValue().get(CACHE_SHOP_KEY+id);
//        //2判断是否存在
//        if (StrUtil.isNotBlank(shopJson)){
//            //3，存在直接返回
//            Shop shop=JSONUtil.toBean(shopJson,Shop.class);
//            return shop;
//        }
//        //判断命中是否是空值
//        if (shopJson !=null){
//            //返回错误信息
//            return null;
//        }
//
//        //4.不存在查询id数据库
//        Shop shop =getById(id);
//        //5，不存在返回错误
//        if(shop==null){
//            //将空值写入 redis
//            stringRedisTemplate.opsForValue().set(key,"",CACHE_NULL_TTL, TimeUnit.MINUTES);
//            //返回错误信息
//            return null;
//        }
//        //6.存在写入redis
//        stringRedisTemplate.opsForValue().set(key,JSONUtil.toJsonStr(shop),CACHE_SHOP_TTL, TimeUnit.MINUTES);
//        //6.返回
//        return shop;
//    }
//
//
//    /**获取互斥锁
//     *
//     * @param key
//     * @return
//     */
//    private boolean trtLock(String key){
//        Boolean flag=stringRedisTemplate.opsForValue().setIfAbsent(key,"1",10,TimeUnit.SECONDS);
//        return BooleanUtil.isTrue(flag);
//    }
//
//    /**
//     * 释放互斥锁
//     * @param key
//     */
//    private  void unlock(String key){
//        stringRedisTemplate.delete(key);
//    }
//
//
//    /**
//     * 将指定 ID 的店铺信息保存到 Redis 中，并设置逻辑过期时间
//     * @param id 店铺的唯一标识，用于从数据库中查询对应的店铺信息
//     * @param expireSeconds 逻辑过期时间的秒数，用于设置店铺信息在 Redis 中的逻辑过期时间
//     */
//    public void  saveShop2Redis(long id,Long expireSeconds){
//        //查询店铺数据
//        Shop shop =getById(id);
//        //封装逻辑过期时间
//        RedisData redisData =new RedisData();
//        redisData.setData(shop);
//        redisData.setExpireTime(LocalDateTime.now().plusSeconds(expireSeconds));
//        //写入redis
//        stringRedisTemplate.opsForValue().set(CACHE_SHOP_KEY+id,JSONUtil.toJsonStr(redisData));
//    }
}
