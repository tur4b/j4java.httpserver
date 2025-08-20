package com.j4httpserver.util;

import com.j4httpserver.annotation.HttpServerExchange;

import java.io.File;
import java.net.URL;
import java.util.HashSet;
import java.util.Set;
import java.util.logging.Logger;

public interface ClassScanner {

    Logger log = Logger.getLogger(ClassScanner.class.getSimpleName());

    static Set<Class<?>> getLoadedHttpServerExchangeClasses(String... packagesToScan) throws ClassNotFoundException {
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
