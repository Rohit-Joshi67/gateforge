package com.gateforge;

import com.gateforge.routing.GatewayProperties;
import com.gateforge.routing.RouteConfig;
import com.gateforge.routing.RouteResolver;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

class RouteResolverTest {

    private RouteResolver buildResolver() {
        RouteConfig userRoute = new RouteConfig();
        userRoute.setId("user-service");
        userRoute.setPathPrefix("/api/users");
        userRoute.setTargetUrl("http://localhost:9001");

        RouteConfig orderRoute = new RouteConfig();
        orderRoute.setId("order-service");
        orderRoute.setPathPrefix("/api/orders");
        orderRoute.setTargetUrl("http://localhost:9002");

        GatewayProperties properties = new GatewayProperties();
        properties.setRoutes(List.of(userRoute, orderRoute));

        return new RouteResolver(properties);
    }

    private RouteResolver buildResolverWithOverlappingPrefixes() {
        RouteConfig usersRoute = new RouteConfig();
        usersRoute.setId("user-service");
        usersRoute.setPathPrefix("/api/users");
        usersRoute.setTargetUrl("http://localhost:9001");

        RouteConfig usersAdminRoute = new RouteConfig();
        usersAdminRoute.setId("user-admin-service");
        usersAdminRoute.setPathPrefix("/api/users/admin");
        usersAdminRoute.setTargetUrl("http://localhost:9010");

        GatewayProperties properties = new GatewayProperties();
        properties.setRoutes(List.of(usersRoute, usersAdminRoute));

        return new RouteResolver(properties);
    }

    @Test
    void resolve_matchingPath_returnsCorrectRoute() {
        RouteResolver resolver = buildResolver();

        Optional<RouteConfig> result = resolver.resolve("/api/users/5");

        assertTrue(result.isPresent());
        assertEquals("user-service", result.get().getId());
    }

    @Test
    void resolve_unmatchedPath_returnsEmpty() {
        RouteResolver resolver = buildResolver();

        Optional<RouteConfig> result = resolver.resolve("/api/unknown/1");

        assertTrue(result.isEmpty());
    }

    @Test
    void resolve_overlappingPrefixes_usesLongestMatch() {
        RouteResolver resolver = buildResolverWithOverlappingPrefixes();

        Optional<RouteConfig> adminResult = resolver.resolve("/api/users/admin/settings");
        Optional<RouteConfig> userResult = resolver.resolve("/api/users/5");

        assertTrue(adminResult.isPresent());
        assertEquals("user-admin-service", adminResult.get().getId());

        assertTrue(userResult.isPresent());
        assertEquals("user-service", userResult.get().getId());
    }
}
