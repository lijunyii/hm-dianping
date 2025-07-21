package com.hmdp.service;

import com.hmdp.dto.LoginFormDTO;
import com.hmdp.dto.Result;
import com.hmdp.entity.User;
import com.baomidou.mybatisplus.extension.service.IService;
import javax.servlet.http.HttpSession;

/**
 * <p>
 *  服务接口
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
public interface IUserService extends IService<User> {

    Result sendCode(String phone, HttpSession session);

    /**
     * 登录功能
     * @param loginForm 登录参数，包含手机号、验证码；或者手机号、密码
     * @param session HttpSession 对象
     * @return 包含 token 的结果
     */
    Result login(LoginFormDTO loginForm, HttpSession session);

    Result sign();

    Result signCount();
}
