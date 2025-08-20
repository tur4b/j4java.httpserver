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
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
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
        Set<Class<?>> httpServerExchangeClasses = ClassScanner.getLoadedHttpServerExchangeClasses(packagesToScan);

        HttpServer httpServer = HttpServer.create(new InetSocketAddress(port), 0);
        httpServer.setExecutor(EXECUTOR_SERVICE);

        for(Class<?> httpServerExchangeClass : httpServerExchangeClasses) {
            HttpServerExchange httpServerExchange = httpServerExchangeClass.getAnnotation(HttpServerExchange.class);
            if(httpServerExchange == null) {
                break;
            }

            log.info(">> class: " + httpServerExchangeClass.getName() + " was defined as HttpServerExchange");
            Object httpServerExchangeInstance = httpServerExchangeClass.getDeclaredConstructor().newInstance();
            Method[] methods = httpServerExchangeClass.getMethods();

            for(Method method : methods) {
                createHttpServerContexts(httpServerExchange, httpServer, httpServerExchangeInstance, method);
            }
        }

        // start the http server
        httpServer.start();
    }

    // ******************************** helper methods *********************************

    private static void createHttpServerContexts(HttpServerExchange httpServerExchange,
                                                 HttpServer httpServer,
                                                 Object httpServerExchangeInstance,
                                                 Method method) {
        HttpEndpoint httpEndpoint = method.getAnnotation(HttpEndpoint.class);
        if(httpEndpoint != null) {
            String path = httpServerExchange.path() + httpEndpoint.path();
            HttpMethod httpMethod = httpEndpoint.method();

            log.info(">> path: " + path + " - method: " + httpMethod);
            int statusCode = httpEndpoint.statusCode();

            httpServer.createContext(path, exchange -> {
                exchange.getResponseHeaders().add("Content-Type", "application/json");
                OutputStream responseOutputStream = exchange.getResponseBody();
                try {
                    if(!exchange.getRequestMethod().equalsIgnoreCase(httpMethod.name())) {
                        ErrorResponse errorResponse = new ErrorResponse(exchange.getRequestMethod() + " method not allowed for this endpoint", "method not allowed", 405);
                        writeErrorResponse(exchange, errorResponse);
                        return;
                    }

                    log.info(">> exchange handler handles: " + exchange.getRequestMethod() + " " + exchange.getRequestURI());

                    final Object[] args = getMethodArguments(method, exchange);

                    Object response = method.invoke(httpServerExchangeInstance, args);
                    String responseJson = JsonUtil.writeAsString(response);

                    exchange.sendResponseHeaders(statusCode, responseJson.length());
                    responseOutputStream.write(responseJson.getBytes(StandardCharsets.UTF_8));
                    responseOutputStream.flush();

                } catch (Exception e) {
                    e.printStackTrace();
                    ErrorResponse errorResponse = new ErrorResponse(e.getMessage(), "internal server error", 500);
                    writeErrorResponse(exchange, errorResponse);
                } finally {
                    responseOutputStream.close();
                }
            });
        }
    }

    private static Object[] getMethodArguments(Method method, HttpExchange exchange) throws IOException {
        final Parameter[] parameters = method.getParameters();
        final Object[] args = new Object[parameters.length];

        for(int i = 0; i < method.getParameterCount(); i++) {
            Parameter parameter = parameters[i];
            Type parameterizedType = parameter.getParameterizedType();
            String parameterName = parameter.getName();
            log.info(">> parameter: " + parameterName + " - " + parameterizedType.getTypeName());

            RequestBody requestBodyAnnotation = parameter.getAnnotation(RequestBody.class);
            RequestParam requestParamAnnotation = parameter.getAnnotation(RequestParam.class);
            System.out.println(">> requestParamAnnotation: " + requestParamAnnotation);

            if("com.sun.net.httpserver.HttpExchange".equalsIgnoreCase(parameterizedType.getTypeName())) {
                args[i] = exchange;
                continue;
            }
            if(requestBodyAnnotation != null) {
                InputStream requestInputStream = exchange.getRequestBody();
                Object requestBody = JsonUtil.readAs(requestInputStream, parameterizedType);
                args[i] = requestBody;
                continue;
            }
            if(requestParamAnnotation != null) {
                boolean required = requestParamAnnotation.required();
//                String requestParamName = requestParamAnnotation.name().isEmpty() ? parameterName : requestParamAnnotation.name();
                Object arg = getRequestParamByName(exchange, requestParamAnnotation.name(), parameterizedType);

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

    private static void writeErrorResponse(HttpExchange exchange, ErrorResponse errorResponse) throws IOException {
        OutputStream responseOutputStream = exchange.getResponseBody();
        String errorResponseAsJson = JsonUtil.writeAsString(errorResponse);
        byte[] responseAsJsonBytes = errorResponseAsJson.getBytes(StandardCharsets.UTF_8);
        exchange.sendResponseHeaders(errorResponse.statusCode(), responseAsJsonBytes.length); // method not allowed
        responseOutputStream.write(responseAsJsonBytes);
        responseOutputStream.flush();
    }

    private static Map<String, String> getRequestParams(HttpExchange httpExchange) {
        Map<String, String> requestParams = new HashMap<>();
        String query = httpExchange.getRequestURI().getQuery();
        if(query != null && !(query = query.trim()).isEmpty()) {
            String[] parameters = query.split("&");
            for(String parameter : parameters) {
                String[] keyValue = parameter.split("=");
                requestParams.put(keyValue[0], keyValue[1]);
            }
        }
        return requestParams;
    }

    private static Object getRequestParamByName(HttpExchange httpExchange, String paramName, Type paramType) {
       String value = getRequestParams(httpExchange).get(paramName);
       return JsonUtil.convertValue(value, paramType);
    }
}
