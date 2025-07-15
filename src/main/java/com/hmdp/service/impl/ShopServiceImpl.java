package com.hmdp.service.impl;

import cn.hutool.core.util.BooleanUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONUtil;
import com.hmdp.dto.Result;
import com.hmdp.entity.Shop;
import com.hmdp.mapper.ShopMapper;
import com.hmdp.service.IShopService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.Resource;

import java.util.concurrent.TimeUnit;

import static com.hmdp.utils.RedisConstants.*;

/**
 * <p>
 *  服务实现类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@Service
public class ShopServiceImpl extends ServiceImpl<ShopMapper, Shop> implements IShopService {
    @Resource
    private StringRedisTemplate stringRedisTemplate;

    @Override
    public Result queryById(Long id) {
        //缓存穿透
        //Shop shop =queryWithPassThrough(id);
        //缓存击穿（互斥锁）
        Shop shop =queryWithMutex(id);
        if(shop==null){
            return Result.fail ("店铺不存在");
        }
        //6.返回
        return Result.ok(shop);
    }

    public  Shop queryWithMutex(Long id){
        // 定义 Redis 键
        String key = CACHE_SHOP_KEY + id;
        //1.从redis查询商铺缓存
        String shopJson=stringRedisTemplate.opsForValue().get(CACHE_SHOP_KEY+id);
        //2判断是否存在
        if (StrUtil.isNotBlank(shopJson)){
            //3，存在直接返回
            Shop shop=JSONUtil.toBean(shopJson,Shop.class);
            return shop;
        }
        //判断命中是否是空值
        if (shopJson !=null){
            //返回错误信息
            return null;
        }
        String lockKey= null;
        Shop shop = null;
        try {
            //4.实现缓存重建
            //4.1.获取互斥锁
            lockKey = "lock:shop"+id;
            boolean isLock=trtLock(lockKey);
            //4.2判断是否获取成功
            if(! isLock){
                //4.3失败则休眠且重试
                Thread.sleep(50);
                return  queryWithPassThrough(id);
            }
            //4.4成功根据id查询数据库
            shop = getById(id);
            //模拟重建的延时
            Thread.sleep(200);
            //5，不存在返回错误
            if(shop==null){
                //将空值写入 redis
                stringRedisTemplate.opsForValue().set(key,"",CACHE_NULL_TTL, TimeUnit.MINUTES);
                //返回错误信息
                return null;
            }
            //6.存在写入redis
            stringRedisTemplate.opsForValue().set(key,JSONUtil.toJsonStr(shop),CACHE_SHOP_TTL, TimeUnit.MINUTES);
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }finally {
            //7释放互斥锁
            unlock(lockKey);
        }
        //8.返回
        return shop;
    }


    public  Shop queryWithPassThrough(Long id){
        // 定义 Redis 键
        String key = CACHE_SHOP_KEY + id;
        //1.从redis查询商铺缓存
        String shopJson=stringRedisTemplate.opsForValue().get(CACHE_SHOP_KEY+id);
        //2判断是否存在
        if (StrUtil.isNotBlank(shopJson)){
            //3，存在直接返回
            Shop shop=JSONUtil.toBean(shopJson,Shop.class);
            return shop;
        }
        //判断命中是否是空值
        if (shopJson !=null){
            //返回错误信息
            return null;
        }

        //4.不存在查询id数据库
        Shop shop =getById(id);
        //5，不存在返回错误
        if(shop==null){
            //将空值写入 redis
            stringRedisTemplate.opsForValue().set(key,"",CACHE_NULL_TTL, TimeUnit.MINUTES);
            //返回错误信息
            return null;
        }
        //6.存在写入redis
        stringRedisTemplate.opsForValue().set(key,JSONUtil.toJsonStr(shop),CACHE_SHOP_TTL, TimeUnit.MINUTES);
        //6.返回
        return shop;
    }



    //获取互斥锁
    private boolean trtLock(String key){
        Boolean flag=stringRedisTemplate.opsForValue().setIfAbsent(key,"1",10,TimeUnit.SECONDS);
        return BooleanUtil.isTrue(flag);
    }

    //释放互斥锁
    private  void unlock(String key){
        stringRedisTemplate.delete(key);
    }





    @Override
    @Transactional
    public Result updata(Shop shop) {
        Long id = shop.getId();
        if(id==null){
            return Result.fail("店铺id不能为空");
        }
        //1.更新数据库
        updateById(shop);
        //2.删除缓存
        stringRedisTemplate.delete(CACHE_SHOP_KEY+id);
        return Result.ok();
    }
}
