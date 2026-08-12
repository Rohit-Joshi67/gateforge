package com.gateforge.routing;

public class JwtConfig {

    private String secret = "this-is-a-32-byte-minimum-secret-key-for-hs256!";
    private int expirationMinutes = 60;

    public String getSecret() {
        return secret;
    }

    public void setSecret(String secret) {
        this.secret = secret;
    }

    public int getExpirationMinutes() {
        return expirationMinutes;
    }

    public void setExpirationMinutes(int expirationMinutes) {
        this.expirationMinutes = expirationMinutes;
    }
}
