package com.j4httpserver.config;

import com.sun.net.httpserver.HttpExchange;

public interface HttpExchangeInterceptor {

    void preHandle(HttpExchange exchange) throws Exception;

    default void postHandle(HttpExchange exchange, Object returnObject) throws Exception {}
}
