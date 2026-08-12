package com.gateforge.ratelimit;

import com.gateforge.routing.GatewayProperties;
import org.springframework.stereotype.Component;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

@Component
public class RateLimiter {

    private final int maxRequestsPerWindow;
    private final long windowSizeMillis;
    private final ConcurrentHashMap<String, Window> clientWindows = new ConcurrentHashMap<>();

    public RateLimiter(GatewayProperties gatewayProperties) {
        this.maxRequestsPerWindow = gatewayProperties.getRateLimit().getMaxRequests();
        this.windowSizeMillis = gatewayProperties.getRateLimit().getWindowSeconds() * 1000L;
    }

    public boolean isAllowed(String clientKey) {
        long now = System.currentTimeMillis();

        Window window = clientWindows.computeIfAbsent(clientKey, k -> new Window(now));

        synchronized (window) {
            if (now - window.startTime >= windowSizeMillis) {
                window.startTime = now;
                window.count.set(0);
            }

            int currentCount = window.count.incrementAndGet();
            return currentCount <= maxRequestsPerWindow;
        }
    }

    private static class Window {
        long startTime;
        AtomicInteger count = new AtomicInteger(0);

        Window(long startTime) {
            this.startTime = startTime;
        }
    }
}
