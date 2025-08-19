package com.j4httpserver.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpServer;
import com.j4httpserver.annotation.EnableHttpServer;
import com.j4httpserver.annotation.HttpEndpoint;
import com.j4httpserver.annotation.HttpServerExchange;

import java.io.File;
import java.io.OutputStream;
import java.lang.reflect.Method;
import java.net.InetSocketAddress;
import java.net.URL;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.*;
import java.util.logging.Logger;

public final class HttpServerAutoConfiguration {

    // FIXME: not good usage of thread pool - make it more production ready
    private static final ExecutorService EXECUTOR_SERVICE = Executors.newFixedThreadPool(10);

    private static final ObjectMapper objectMapper = new ObjectMapper();

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
        Set<Class<?>> httpServerExchangeClasses = getLoadedHttpServerExchangeClasses(packagesToScan);

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
                HttpEndpoint httpEndpoint = method.getAnnotation(HttpEndpoint.class);
                if(httpEndpoint != null) {
                    String path = httpServerExchange.path() + httpEndpoint.path();
                    log.info(">> path: " + path);
                    int statusCode = httpEndpoint.statusCode();

                    httpServer.createContext(path, exchange -> {
                        OutputStream responseOutputStream = exchange.getResponseBody();
                        try {
                            log.info(">> exchange handler handles: " + exchange.getRequestMethod() + " " + exchange.getRequestURI());

                            Object response = method.invoke(httpServerExchangeInstance, exchange);
                            String responseJson = objectMapper.writeValueAsString(response);

                            exchange.getResponseHeaders().add("Content-Type", "application/json");
                            exchange.sendResponseHeaders(statusCode, responseJson.length());
                            responseOutputStream.write(responseJson.getBytes());
                            responseOutputStream.flush();

                        } catch (Exception e) {
                            e.printStackTrace();
                            exchange.sendResponseHeaders(500, 0);
                            responseOutputStream.flush();
                        } finally {
                            responseOutputStream.close();
                        }
                    });
                }
            }
        }

        // start the http server
        httpServer.start();
    }

    private static Set<Class<?>> getLoadedHttpServerExchangeClasses(String... packagesToScan) throws ClassNotFoundException {
        Set<Class<?>> classes = new HashSet<Class<?>>();

        for(String basePackage : packagesToScan) {
            String pckToScan = basePackage.replace('.', '/');

            ClassLoader classLoader = Thread.currentThread().getContextClassLoader();
            URL resource = classLoader.getResource(pckToScan);
            if (resource == null) {
                throw new IllegalArgumentException("Package not found: " + pckToScan);
            }

            File directory = new File(resource.getFile());
            File[] files = directory.listFiles();

            for(File file : files) {
                String fileName = file.getName();
                if (file.isFile()) {
                    log.info("fileName = " + fileName);
                    if (fileName.endsWith(".class")) {
                        String className = basePackage + "." + fileName.replace(".class", "");
                        Class<?> aClass = Class.forName(className);
                        if (aClass.isAnnotationPresent(HttpServerExchange.class)) {
                            classes.add(aClass);
                        }
                    }
                } else if(file.isDirectory()) {
                    log.info(">> file is directory: " + basePackage + "." + fileName);
                    classes.addAll(getLoadedHttpServerExchangeClasses(basePackage + "." + fileName));
                }
            }
        }
        return classes;
    }
}
