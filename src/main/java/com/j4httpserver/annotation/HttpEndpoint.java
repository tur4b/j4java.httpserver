package com.j4httpserver.annotation;

import com.j4httpserver.enums.HttpMethod;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface HttpEndpoint {

    String path() default "";
    HttpMethod method() default HttpMethod.GET;
    int statusCode() default 200;

}
