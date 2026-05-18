package com.qingxiang.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.qingxiang.dto.LoginFormDTO;
import com.qingxiang.dto.Result;
import com.qingxiang.entity.User;

import javax.servlet.http.HttpSession;

/**
 * <p>
 *  服务类
 * </p>
 *
 * @author 李锡宏
 * @since 2025-12-22
 */
public interface IUserService extends IService<User> {

    Result sendCode(String phone, HttpSession session);

    Result login(LoginFormDTO loginForm, HttpSession session);
}
