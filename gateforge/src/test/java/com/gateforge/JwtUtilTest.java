package com.gateforge;

import com.gateforge.auth.JwtUtil;
import io.jsonwebtoken.ExpiredJwtException;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class JwtUtilTest {

    private final JwtUtil jwtUtil = new JwtUtil();

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
