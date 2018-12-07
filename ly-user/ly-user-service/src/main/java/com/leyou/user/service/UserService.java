package com.leyou.user.service;

import com.leyou.common.exception.LyException;
import com.leyou.common.utils.NumberUtils;
import com.leyou.user.mapper.UserMapper;
import com.leyou.user.pojo.User;
import com.leyou.user.utils.CodecUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.amqp.core.AmqpTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.util.Date;
import java.util.HashMap;
import java.util.concurrent.TimeUnit;

@Service
public class UserService {
    @Autowired
    private UserMapper userMapper;
    @Autowired
    private StringRedisTemplate redisTemplate;
    @Autowired
    private AmqpTemplate amqpTemplate;

    private static final String KEY_PREFIX = "user:verify:phone";

    public Boolean checkUserData(String data, Integer type) {
        //查询条件
        User user = new User();
        switch (type){
            case 1:
                user.setUsername(data);
                break;
            case 2:
                user.setPhone(data);
                break;
            default:
                throw new LyException(HttpStatus.BAD_REQUEST,"请求参数有误");
        }
        return userMapper.selectCount(user) == 0;
    }

    public void sendVerifyCode(String phone) {
        String key = KEY_PREFIX + phone;
        //如果code已经存在，不发短信
        if (redisTemplate.hasKey(key)) {
            throw new LyException(HttpStatus.BAD_REQUEST,"发送短信频率过高");
        }
        //校验手机格式
        if (!phone.matches("^((13[0-9])|(14[5|7])|(15([0-3]|[5-9]))|(18[0,5-9]))\\d{8}$")) {
            throw new LyException(HttpStatus.BAD_REQUEST,"手机号码不正确");
        }
        //生成随机验证码
        String code = NumberUtils.generateCode(6);
        //保存验证码
        redisTemplate.opsForValue().set(key,code,80, TimeUnit.SECONDS);
        //发送验证码
        HashMap<String,String> msg = new HashMap<>();
        msg.put("phone",phone);
        msg.put("code",code);
        amqpTemplate.convertAndSend("ly.sms.exchange","sms.verify.code",msg);
    }

    /**
     * 注册
     * @param user
     * @param code
     */
    public void register(User user, String code) {
        //补充字段
        user.setId(null);
        user.setCreated(new Date());
        //取出redis中的验证码
        String key = KEY_PREFIX + user.getPhone();
        String cacheCode = redisTemplate.opsForValue().get(key);
        //校验验证码
        if (!StringUtils.equals(code, cacheCode)) {
            throw new LyException(HttpStatus.BAD_REQUEST, "验证码错误");
        }
        //生成盐
        String salt = CodecUtils.generateSalt();
        user.setSalt(salt);
        //对密码加密
        user.setPassword(CodecUtils.md5Hex(user.getPassword(), salt));
        //写入数据库
        userMapper.insert(user);
        //删除redis中的key
        redisTemplate.delete(key);
    }

    /**
     * 查询用户
     * @param username
     * @param password
     * @return
     */
    public User queryByUserNameAndPassword(String username, String password) {
        //查询用户
        User user = new User();
        user.setUsername(username);
        user = userMapper.selectOne(user);
        //判断用户名
        if (user == null){
            //用户名错误
            throw new LyException(HttpStatus.BAD_REQUEST,"用户名或密码错误");
        }
        //校验密码
        if (!user.getPassword().equals(CodecUtils.md5Hex(password,user.getSalt()))){
            //密码错误
            throw new LyException(HttpStatus.BAD_REQUEST,"用户名或密码错误");
        }
        return user;
    }
}