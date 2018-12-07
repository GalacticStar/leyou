package com.leyou.auth.controller;

import com.leyou.auth.config.JwtProperties;
import com.leyou.auth.pojo.UserInfo;
import com.leyou.auth.service.AuthService;
import com.leyou.auth.utils.JwtUtils;
import com.leyou.common.exception.LyException;
import com.leyou.common.utils.CookieUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

@RestController
@EnableConfigurationProperties(JwtProperties.class)
public class AuthController {
    @Autowired
    private AuthService authService;
    @Autowired
    private JwtProperties prop;

    /**
     * 登录
     * @param username
     * @param password
     * @param request
     * @param response
     * @return
     */
    @PostMapping("login")
    public ResponseEntity<Void> login(
            @RequestParam("username") String username,
            @RequestParam("password") String password,
            HttpServletRequest request, HttpServletResponse response) {
        //登录
        String token = authService.login(username, password);
        if (StringUtils.isBlank(token)) {
            throw new LyException(HttpStatus.BAD_REQUEST, "用户名或密码错误");
        }
        //把token写进cookie
        CookieUtils.newBuilder(response).request(request).httpOnly().build(prop.getCookieName(),token);
        return ResponseEntity.ok().build();
    }

    @GetMapping("verify")
    public ResponseEntity<UserInfo> verify(
            @CookieValue("LY_TOKEN") String token,HttpServletResponse response,HttpServletRequest request) {
        try {
            //解析token
            UserInfo userInfo = JwtUtils.getInfoFromToken(token, prop.getPublicKey());
            //刷新token（重写token）
            token = JwtUtils.generateTokenInMinutes(userInfo, prop.getPrivateKey(), prop.getExpire());
            //把token写进cookie
            CookieUtils.newBuilder(response).request(request).httpOnly().build(prop.getCookieName(), token);
            //返回用户
            return ResponseEntity.ok(userInfo);
        } catch (Exception e) {
            //解析失败，未登录或登录超时
            throw new LyException(HttpStatus.UNAUTHORIZED, "登录超时或凭证不完整");
        }
    }

    /**
     * 登出，设置cookie值为null
     * @param response
     * @param request
     * @return
     */
    @GetMapping("logout")
    public ResponseEntity<Void> logout(HttpServletResponse response,HttpServletRequest request){
        CookieUtils.newBuilder(response).request(request).httpOnly().build(prop.getCookieName(), null);
        return ResponseEntity.ok().build();
    }
}