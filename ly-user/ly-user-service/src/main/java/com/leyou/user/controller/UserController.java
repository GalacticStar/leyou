package com.leyou.user.controller;

import com.leyou.common.exception.LyException;
import com.leyou.user.pojo.User;
import com.leyou.user.service.UserService;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;

import javax.validation.Valid;
import java.util.List;
import java.util.stream.Collectors;

@RestController
public class UserController {
    @Autowired
    private UserService userService;

    /**
     * 校验用户是否存在
     * @param data
     * @param type
     * @return
     */
    @GetMapping("/check/{data}/{type}")
    public ResponseEntity<Boolean> checkUserData(
            @PathVariable("data") String data,@PathVariable("type") Integer type) {
        return ResponseEntity.ok(userService.checkUserData(data,type));
    }

    /**
     * 发送验证码
     * @param phone
     * @return
     */
    @PostMapping("/send")
    public ResponseEntity<Void> sendVerifyCode(@RequestParam("phone") String phone){
        userService.sendVerifyCode(phone);
        return ResponseEntity.status(HttpStatus.NO_CONTENT).build();
    }

    /**
     * 注册
     * @param user
     * @param code
     * @return
     */
    @PostMapping("register")
    public ResponseEntity<Void> register(@Valid User user, BindingResult result, @RequestParam("code") String code){
        //数据校验，用BindingResult返回一句话错误信息，更为简略
        if(result.hasFieldErrors()){
            List<String> list = result.getFieldErrors().stream()
                    .map(e -> e.getDefaultMessage()).collect(Collectors.toList());
            throw new LyException(HttpStatus.BAD_REQUEST, StringUtils.join(list,","));
        }
        userService.register(user,code);
        return ResponseEntity.status(HttpStatus.CREATED).build();
    }

    /**
     * 查询用户
     * @param username
     * @param password
     * @return
     */
    @GetMapping("query")
    public ResponseEntity<User> queryByUserNameAndPassword(
            @RequestParam("username") String username,@RequestParam("password")String password){
        return ResponseEntity.ok(userService.queryByUserNameAndPassword(username,password));
    }
}