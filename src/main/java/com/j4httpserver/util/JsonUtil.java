package com.j4httpserver.util;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JavaType;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;

public interface JsonUtil {

    ObjectMapper objectMapper = new ObjectMapper();

    static <T> T readAs(InputStream inputStream, Type type) throws IOException {
        String json = new String(inputStream.readAllBytes(), StandardCharsets.UTF_8);
        JavaType javaType = objectMapper.getTypeFactory().constructType(type);
        return objectMapper.readValue(json, javaType);
    }

    static String writeAsString(Object value) throws JsonProcessingException {
        return objectMapper.writeValueAsString(value);
    }
}
