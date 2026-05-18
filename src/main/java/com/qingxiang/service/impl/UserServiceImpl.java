package com.qingxiang.service.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.bean.copier.CopyOptions;
import cn.hutool.core.lang.UUID;
import cn.hutool.core.util.RandomUtil;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.qingxiang.dto.LoginFormDTO;
import com.qingxiang.dto.Result;
import com.qingxiang.dto.UserDTO;
import com.qingxiang.entity.User;
import com.qingxiang.mapper.UserMapper;
import com.qingxiang.service.IUserService;
import com.qingxiang.utils.RegexUtils;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import javax.servlet.http.HttpSession;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import static com.qingxiang.utils.RedisConstants.*;
import static com.qingxiang.utils.SystemConstants.USER_NICK_NAME_PREFIX;

/**
 * <p>
 * 服务实现类
 * </p>
 *
 * @author 李锡宏
 * @since 2025-12-22
 */
@Service
@Slf4j
public class UserServiceImpl extends ServiceImpl<UserMapper, User> implements IUserService {


    @Resource
    private StringRedisTemplate stringRedisTemplate;

    @Override
    public Result sendCode(String phone, HttpSession session) {
        //1.校验手机号
        if (RegexUtils.isPhoneInvalid(phone)) {
            //2.如果不符合，返回错误信息
            return Result.fail("手机号格式错误！");
        }

        //3.符合，生成验证码
        String code = RandomUtil.randomNumbers(6);

        //4.保存验证码到session
        //session.setAttribute("code", code);
        //->优化
        // 4.保存到Redis中，并设置有效期(相当于 set key value ex 120)
        stringRedisTemplate.opsForValue().set(LOGIN_CODE_KEY + phone, code, LOGIN_CODE_TTL, TimeUnit.MINUTES);

        //5.发送验证码
        log.debug("发送短信验证码成功，验证码：{}", code);
        //PS：这里只是通过日志打印模拟短信发送成功的情况
        // 实际项目中需要调用真实的短信服务接口发送验证码

        //6.返回成功信息，表示验证码生成和保存操作成功
        return Result.ok();
    }

    @Override
    public Result login(LoginFormDTO loginForm, HttpSession session) {
        //1.校验手机号
        String phone = loginForm.getPhone();
        if (RegexUtils.isPhoneInvalid(phone)) {
            //2.如果不符合，返回错误信息
            return Result.fail("手机号格式错误！");
        }

        //2.校验验证码：从session中获取验证码，跟表单中的验证码进行比较
        //->优化
        //2.校验验证码：从Redis中获取验证码，跟表单中的验证码进行比较
        String cacheCode = stringRedisTemplate.opsForValue().get(LOGIN_CODE_KEY + phone);
        String code = loginForm.getCode();
        if (cacheCode == null || !cacheCode.equals(code)) {
            //3.不一致就报错
            return Result.fail("验证码错误");
        }

        //4.一致，根据手机号查询用户
        User user = query().eq("phone", phone).one();
        //使用 MyBatis - Plus 提供的查询方式，根据手机号从MySQL数据库中查询用户信息
        //这里的query()方法是继承自ServiceImpl类的便捷查询方法


        //5.判断用户是否存在
        if (user == null) {
            //6.如果用户不存在，创建新用户并保存
            user = createUserWithPhone(phone);

        }

        //7.保存用户到session
        // 注意：避免敏感信息泄露，使用hutool包中的BeanUtil工具类进行拷贝
        // 拷贝后的UserDTO对象只包含nickName和id和头像icon等公共属性
        //->优化：保存用户信息到Redis中，随机Token作为key
        //7.1.随机生成Token，作为登录凭证
        String token = UUID.randomUUID().toString(true);

        //7.2.将User对象转为HashMap存储
        UserDTO userDTO = BeanUtil.copyProperties(user, UserDTO.class);
        Map<String, Object> userMap = BeanUtil.beanToMap(userDTO,new HashMap<>(),
                CopyOptions.create()
                        .setIgnoreNullValue( true)
                        .setFieldValueEditor((fieldName,fieldValue)->fieldValue.toString()));
                        //设置字段值编辑器,忽略null值,long类型的id也转为字符串

        //7.3存储
        //session.setAttribute("user", BeanUtil.copyProperties(user, UserDTO.class));
        stringRedisTemplate.opsForHash().putAll(LOGIN_USER_KEY + token, userMap);
        //7.4设置token有效期
        stringRedisTemplate.expire(LOGIN_USER_KEY + token, LOGIN_USER_TTL, TimeUnit.MINUTES);
        return Result.ok(token);
    }

    private User createUserWithPhone(String phone) {
        //1.创建用户
        User user = new User();
        user.setPhone(phone);
        user.setNickName(USER_NICK_NAME_PREFIX + RandomUtil.randomString(10));

        //2.保存用户
        save(user);
        return user;
    }
}
