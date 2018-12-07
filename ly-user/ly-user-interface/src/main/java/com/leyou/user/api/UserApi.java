package com.leyou.user.api;

import com.leyou.user.pojo.User;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

public interface UserApi {
    /**
     * 查询用户
     * @param username
     * @param password
     * @return
     */
    @GetMapping("query")
    User queryByUserNameAndPassword(
            @RequestParam("username") String username, @RequestParam("password")String password);
}
