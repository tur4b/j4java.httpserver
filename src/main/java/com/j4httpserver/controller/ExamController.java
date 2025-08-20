package com.j4httpserver.controller;

import com.j4httpserver.annotation.HttpEndpoint;
import com.j4httpserver.annotation.HttpServerExchange;
import com.j4httpserver.annotation.RequestBody;
import com.j4httpserver.enums.HttpMethod;
import com.j4httpserver.model.Request;
import com.j4httpserver.model.WhatDTO;

import java.util.List;
import java.util.UUID;

@HttpServerExchange(path = "/exams")
public class ExamController {

    @HttpEndpoint(method = HttpMethod.GET)
    public List<String> getAllExams() {
        return List.of("asdasd", "dsfsdfdsf", "sdfdsfdsf", "fsdfsdf");
    }

    @HttpEndpoint(path = "/add", method = HttpMethod.POST)
    public String createExam(@RequestBody Request request) {
        System.out.println(">> requestBody: " + request);
        return request.title() + " -> " + UUID.randomUUID().toString();
    }

    @HttpEndpoint(path = "/what", method = HttpMethod.POST)
    public List<String> whatwhatwhat(@RequestBody WhatDTO whatDTO) {
        System.out.println(">> requestBody: " + whatDTO);
        return List.of("asdasd", "dsfsdfdsf", "sdfdsfdsf", "fsdfsdf");
    }

}
