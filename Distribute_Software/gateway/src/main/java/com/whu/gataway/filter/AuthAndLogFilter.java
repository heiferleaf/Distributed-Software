package com.whu.gataway.filter;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import javax.crypto.SecretKey;

@Component
public class AuthAndLogFilter implements GlobalFilter, Ordered {

    private static final Logger log = LoggerFactory.getLogger(AuthAndLogFilter.class);

    private final String SECRET = "afebf38bd165b4c19efae1a84fbe7d5f0b411d7c385db490a072d829dc7fb508";
    private final SecretKey key = Keys.hmacShaKeyFor(SECRET.getBytes());

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        String path = exchange.getRequest().getURI().getPath();
        String method = exchange.getRequest().getMethod().name();
        String ip = exchange.getRequest().getRemoteAddress() != null ?
                exchange.getRequest().getRemoteAddress().getAddress().getHostAddress() : "UNKNOWN";

        // 日志打印
        log.info("【访问日志】IP: {}, 请求路径: {}, 方法: {}", ip, path, method);

        if ("OPTIONS".equalsIgnoreCase(method)) {
            return chain.filter(exchange);
        }

        // 假设不需要鉴权的路径可以放行
        if (path.contains("/login") || path.contains("/register") || path.contains("/refresh")) {
            return chain.filter(exchange);
        }

        // 允许直接放行商品查询相关的接口（GET请求允许匿名访问）
        if (path.contains("/hello") || (path.contains("/product/") && "GET".equalsIgnoreCase(method))) {
            return chain.filter(exchange);
        }

        String authHeader = exchange.getRequest().getHeaders().getFirst("Authorization");

        // 如果请求头没有 Token，返回401未授权
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            log.warn("【鉴权失败】缺少Token, IP: {}, 路径: {}", ip, path);
            exchange.getResponse().setStatusCode(HttpStatus.UNAUTHORIZED);
            return exchange.getResponse().setComplete();
        }

        String token = authHeader.substring(7);

        try {
            Claims claims = Jwts.parserBuilder()
                    .setSigningKey(key)
                    .build()
                    .parseClaimsJws(token)
                    .getBody();

            if (!"access".equals(claims.get("type"))) {
                throw new RuntimeException("Not an access token");
            }

            ServerHttpRequest request = exchange.getRequest().mutate()
                    .header("X-User-Name", claims.getSubject())
                    .header("X-User-Role", claims.get("role", String.class))
                    .build();

            log.info("【鉴权成功】User: {}", claims.getSubject());
            return chain.filter(exchange.mutate().request(request).build());

        } catch (Exception e) {
            log.warn("【鉴权失败】Token无效/过期: {}, IP: {}, 路径: {}", e.getMessage(), ip, path);
            exchange.getResponse().setStatusCode(HttpStatus.UNAUTHORIZED);
            return exchange.getResponse().setComplete();
        }
    }

    @Override
    public int getOrder() {
        return 0; // 优先级，越小越先执行
    }
}
