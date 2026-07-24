package com.gateforge.routing;

import org.springframework.stereotype.Component;

import java.util.Comparator;
import java.util.List;
import java.util.Optional;

@Component
public class RouteResolver {

    private final GatewayProperties gatewayProperties;

    public RouteResolver(GatewayProperties gatewayProperties) {
        this.gatewayProperties = gatewayProperties;
    }

    public Optional<RouteConfig> resolve(String requestPath) {
        List<RouteConfig> routes = gatewayProperties.getRoutes();

        return routes.stream()
                .filter(route -> requestPath.startsWith(route.getPathPrefix()))
                .max(Comparator.comparingInt(route -> route.getPathPrefix().length()));
    }
}