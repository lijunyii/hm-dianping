package com.hmdp.service.impl;

import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONUtil;
import com.hmdp.dto.Result;
import com.hmdp.entity.Shop;
import com.hmdp.mapper.ShopMapper;
import com.hmdp.service.IShopService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;

import static com.hmdp.utils.RedisConstants.CACHE_SHOP_KEY;

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
        // 定义 Redis 键
        String key = CACHE_SHOP_KEY + id;
        //1.从redis查询商铺缓存
        String shopJson=stringRedisTemplate.opsForValue().get(CACHE_SHOP_KEY+id);
        //2判断是否存在
        if (StrUtil.isNotBlank(shopJson)){
            //3，存在直接返回
            Shop shop=JSONUtil.toBean(shopJson,Shop.class);
            return Result.ok(shop);
        }

        //4.不存在查询id数据库
        Shop shop =getById(id);
        //5，不存在返回错误
        if(shop==null){
            return Result.fail("店铺不存在！");
        }
        //6.存在写入redis
        stringRedisTemplate.opsForValue().set(key,JSONUtil.toJsonStr(shop));
        //6.返回
        return Result.ok(shop);
    }
}
