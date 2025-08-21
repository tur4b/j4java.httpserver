package com.j4httpserver.config;

import com.j4httpserver.annotation.*;
import com.j4httpserver.enums.HttpMethod;
import com.j4httpserver.model.ErrorResponse;
import com.j4httpserver.util.ClassScanner;
import com.j4httpserver.util.JsonUtil;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.lang.reflect.Type;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.*;
import java.util.logging.Logger;

public final class HttpServerAutoConfiguration {

    // FIXME: not good usage of thread pool - make it more production ready
    private static final ExecutorService EXECUTOR_SERVICE = Executors.newFixedThreadPool(10);

    private static final Class<HttpServerAutoConfiguration> httpServerAutoConfigurationClass = HttpServerAutoConfiguration.class;

    private static final Logger log = Logger.getLogger(httpServerAutoConfigurationClass.getSimpleName());

    public static void run(Class<?> mainClass) throws Exception {

        if(mainClass == null || !mainClass.isAnnotationPresent(EnableHttpServer.class)) {
            log.info("HttpServer auto configuration is in disabled state...");
            return;
        }
        log.info("HttpServer auto configuration is started...");

        EnableHttpServer enableHttpServer = mainClass.getAnnotation(EnableHttpServer.class);
        int port = enableHttpServer.port();
        String[] packagesToScan = enableHttpServer.scanPackages();
        Set<Class<?>> httpServerExchangeClasses = ClassScanner.getLoadedClassesWithAnnotation(HttpServerExchange.class, packagesToScan);

        HttpServer httpServer = HttpServer.create(new InetSocketAddress(port), 0);
        httpServer.setExecutor(EXECUTOR_SERVICE);

        for(Class<?> httpServerExchangeClass : httpServerExchangeClasses) {
            HttpServerExchange httpServerExchange = httpServerExchangeClass.getAnnotation(HttpServerExchange.class);
            log.info(">> class: " + httpServerExchangeClass.getName() + " was defined as HttpServerExchange");

            Object httpServerExchangeInstance = httpServerExchangeClass.getDeclaredConstructor().newInstance();
            Method[] methods = httpServerExchangeClass.getMethods();

            for(Method method : methods) {
                registerEndpoint(httpServerExchange, httpServer, httpServerExchangeInstance, method, packagesToScan);
            }
        }

        // start the http server
        httpServer.start();
    }

    // ******************************** helper methods *********************************

    private static void registerEndpoint(HttpServerExchange httpServerExchange,
                                         HttpServer httpServer,
                                         Object httpServerExchangeInstance,
                                         Method method,
                                         String... interceptorPackages) {
        HttpEndpoint httpEndpoint = method.getAnnotation(HttpEndpoint.class);
        if(httpEndpoint != null) {
            String path = httpServerExchange.path() + httpEndpoint.path();
            HttpMethod httpMethod = httpEndpoint.method();

            log.info(">> path: " + path + " - method: " + httpMethod);
            int statusCode = httpEndpoint.statusCode();

            httpServer.createContext(path, exchange -> {
                try {
                    exchange.getResponseHeaders().add("Content-Type", "application/json");

                    // check if valid endpoint
                    String requestURIPath = exchange.getRequestURI().getPath();
                    if(!path.equals(requestURIPath)) {
                        writeResponse(exchange,  new ErrorResponse("invalid request path", "invalid.endpoint"), 404);
                        return;
                    }

                    // check if valid method
                    if(!exchange.getRequestMethod().equalsIgnoreCase(httpMethod.name())) {
                        writeResponse(exchange,  new ErrorResponse(exchange.getRequestMethod() + " method not allowed for this endpoint",
                                "method not allowed"), 405);
                        return;
                    }

                    // apply preHandle interceptor method
                    List<Class<?>> interceptorClasses = ClassScanner.getLoadedClassesWithAnnotation(Interceptor.class, interceptorPackages)
                            .stream()
                            .sorted(Comparator.comparing(cls -> cls.getAnnotation(Interceptor.class).order()))
                            .toList();

                    for (Class<?> interceptorClass : interceptorClasses) {
                        if(HttpExchangeInterceptor.class.isAssignableFrom(interceptorClass)) {
                            log.info(">> interceptor: " + interceptorClass.getName());
                            Object interceptorInstance = interceptorClass.getDeclaredConstructor().newInstance();
                            interceptorClass.getMethod("preHandle", HttpExchange.class).invoke(interceptorInstance, exchange);
                        }
                    }

                    // invoke the method according to the endpoint
                    log.info(">> exchange handler handles: " + exchange.getRequestMethod() + " " + exchange.getRequestURI());
                    final Object[] args = resolveMethodArguments(method, exchange);
                    Object response = method.invoke(httpServerExchangeInstance, args);
                    writeResponse(exchange, response, statusCode);

                    // apply postHandle interceptor method
                    for (Class<?> interceptorClass : interceptorClasses) {
                        if(HttpExchangeInterceptor.class.isAssignableFrom(interceptorClass)) {
                            log.info(">> interceptor: " + interceptorClass.getName());
                            Object interceptorInstance = interceptorClass.getDeclaredConstructor().newInstance();
                            interceptorClass.getMethod("postHandle", HttpExchange.class, Object.class).invoke(interceptorInstance, exchange, response);
                        }
                    }

                } catch (Exception e) {
                    e.printStackTrace();
                    Throwable cause = e.getCause();
                    String errorMessage = e.getMessage() == null ? cause.getMessage() : e.getMessage();
                    writeResponse(exchange,  new ErrorResponse(errorMessage, "internal server error"), 500);
                }
            });
        }
    }

    private static Object[] resolveMethodArguments(Method method, HttpExchange exchange) throws IOException {
        final Parameter[] parameters = method.getParameters();
        final Object[] args = new Object[parameters.length];

        for(int i = 0; i < method.getParameterCount(); i++) {
            Parameter parameter = parameters[i];
            Type parameterizedType = parameter.getParameterizedType();
            String parameterName = parameter.getName();
            log.info(">> parameter: " + parameterName + " - " + parameterizedType.getTypeName());

            if(HttpExchange.class.isAssignableFrom(parameter.getType())) {
                args[i] = exchange;
                continue;
            }
            if(parameter.isAnnotationPresent(RequestBody.class)) {
                try(InputStream requestInputStream = exchange.getRequestBody()) {
                    Object requestBody = JsonUtil.readAs(requestInputStream, parameterizedType);
                    args[i] = requestBody;
                    continue;
                }
            }
            if(parameter.isAnnotationPresent(RequestParam.class)) {
                RequestParam requestParamAnnotation = parameter.getAnnotation(RequestParam.class);
                boolean required = requestParamAnnotation.required();
                String defaultValue = requestParamAnnotation.defaultValue();
//                String requestParamName = requestParamAnnotation.name().isEmpty() ? parameterName : requestParamAnnotation.name();
                Object arg = getRequestParamByNameOrElse(exchange, requestParamAnnotation.name(), parameterizedType, defaultValue);

                if(required && arg == null) {
                    throw new RuntimeException("Required parameter " + requestParamAnnotation.name() + " not found");
                }
                args[i] = arg;
                continue;
            }
            args[i] = parameter;
        }
        return args;
    }

    private static void writeResponse(HttpExchange exchange, Object response, int statusCode) throws IOException {
        String responseAsJson = JsonUtil.writeAsString(response);
        byte[] responseAsJsonBytes = responseAsJson.getBytes(StandardCharsets.UTF_8);
        exchange.sendResponseHeaders(statusCode, responseAsJsonBytes.length);

        try(OutputStream responseOutputStream = exchange.getResponseBody()) {
            responseOutputStream.write(responseAsJsonBytes);
            responseOutputStream.flush();
        }
    }

    private static Map<String, String> getRequestParams(HttpExchange httpExchange) {
        Map<String, String> requestParams = new HashMap<>();
        String query = httpExchange.getRequestURI().getQuery();
        if(query != null && !(query = query.trim()).isEmpty()) {
            String[] parameters = query.split("&");
            for(String parameter : parameters) {
                String[] keyValue = parameter.split("=", 2);
                requestParams.put(keyValue[0], keyValue[1]);
            }
        }
        return requestParams;
    }

    private static Object getRequestParamByNameOrElse(HttpExchange httpExchange, String paramName, Type paramType, Object defaultValue) {
        String value = getRequestParams(httpExchange).get(paramName);
        return (value != null && !value.isEmpty()) ? JsonUtil.convertValue(value, paramType) : (defaultValue != null ? JsonUtil.convertValue(defaultValue, paramType) : null);
    }
}
