package com.gateforge;

import com.gateforge.auth.JwtUtil;
import com.gateforge.routing.GatewayProperties;
import com.gateforge.routing.JwtConfig;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class JwtUtilTest {

    private final JwtUtil jwtUtil = new JwtUtil(buildProperties());

    private static GatewayProperties buildProperties() {
        JwtConfig jwtConfig = new JwtConfig();
        jwtConfig.setSecret("this-is-a-32-byte-minimum-secret-key-for-hs256!");
        GatewayProperties properties = new GatewayProperties();
        properties.setJwt(jwtConfig);
        return properties;
    }

    @Test
    void generateToken_thenValidate_returnsOriginalUsername() {
        String token = jwtUtil.generateToken("rahul");

        String extracted = jwtUtil.validateAndGetSubject(token);

        assertEquals("rahul", extracted);
    }

    @Test
    void validateAndGetSubject_withGarbageToken_throwsException() {
        assertThrows(Exception.class, () -> {
            jwtUtil.validateAndGetSubject("this.is.not.a.valid.jwt");
        });
    }
}
