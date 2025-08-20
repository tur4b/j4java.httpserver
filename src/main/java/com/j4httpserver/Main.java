package com.j4httpserver;

import com.j4httpserver.annotation.EnableHttpServer;
import com.j4httpserver.config.HttpServerAutoConfiguration;

@EnableHttpServer(port = 8080, scanPackages = {"com.j4httpserver.controller"})
public class Main {

    public static void main(String[] args) throws Exception {
        HttpServerAutoConfiguration.run(Main.class);
    }
}
