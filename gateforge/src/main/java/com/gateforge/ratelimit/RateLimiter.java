package com.gateforge.ratelimit;

import org.springframework.stereotype.Component;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

@Component
public class RateLimiter {

    private static final int MAX_REQUESTS_PER_WINDOW = 5;
    private static final long WINDOW_SIZE_MILLIS = 10_000; // 10 seconds

    // Tracks each client's request count + window start time
    private final ConcurrentHashMap<String, Window> clientWindows = new ConcurrentHashMap<>();

    public boolean isAllowed(String clientKey) {
        long now = System.currentTimeMillis();

        Window window = clientWindows.computeIfAbsent(clientKey, k -> new Window(now));

        synchronized (window) {
            if (now - window.startTime >= WINDOW_SIZE_MILLIS) {
                // window expired, reset it
                window.startTime = now;
                window.count.set(0);
            }

            int currentCount = window.count.incrementAndGet();
            return currentCount <= MAX_REQUESTS_PER_WINDOW;
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