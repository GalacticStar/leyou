package com.leyou.user.test;

import com.leyou.auth.pojo.UserInfo;
import com.leyou.auth.utils.JwtUtils;
import com.leyou.auth.utils.RsaUtils;
import org.junit.Before;
import org.junit.Test;

import java.security.PrivateKey;
import java.security.PublicKey;

public class JwtTest {

    private static final String pubKeyPath = "K:\\JetBrains\\rsa\\rsa.pub";

    private static final String priKeyPath = "K:\\JetBrains\\rsa\\rsa.pri";

    private PublicKey publicKey;

    private PrivateKey privateKey;

    @Test
    public void testRsa() throws Exception {
        RsaUtils.generateKey(pubKeyPath, priKeyPath, "leyou");
    }

    @Before
    public void testGetRsa() throws Exception {
        this.publicKey = RsaUtils.getPublicKey(pubKeyPath);
        this.privateKey = RsaUtils.getPrivateKey(priKeyPath);
    }

    @Test
    public void testGenerateToken() throws Exception {
        // 生成token
        String token = JwtUtils.generateTokenInMinutes(new UserInfo(2L, "leyou"), privateKey, 2);
        System.out.println("token = " + token);
    }

    @Test
    public void testParseToken() throws Exception {
        String token = "eyJhbGciOiJSUzI1NiJ9.eyJpZCI6MiwidXNlcm5hbWUiOiJsZXlvdSIsImV4cCI6MTUzNTk0MDAwNn0.GZE7bzRiDKGRpYR2WOG7CwT3TKxQ2cxGhkHZ8I61aqubwqpSqmBKWVDhq1_f8ppMrdrh0wGaKxGRVApvdrmsKj28cpGwC98DE5YcyKSTzX2o6XMyMC_2jB_eaghc0wdWAGIC6g-xZ7mFz5Ces-p_PL2X7WZtc0AcUa8H4vjLQAk";

        // 解析token
        UserInfo user = JwtUtils.getInfoFromToken(token, publicKey);
        System.out.println("id: " + user.getId());
        System.out.println("userName: " + user.getUsername());
    }
}
