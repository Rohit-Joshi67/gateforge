package com.gateforge.config;

import java.util.Set;

public final class PublicPaths {

    private static final Set<String> PATHS = Set.of("/health", "/health/ready", "/login");

    private PublicPaths() {
    }

    public static boolean isPublic(String requestUri) {
        return PATHS.contains(requestUri);
    }
}
