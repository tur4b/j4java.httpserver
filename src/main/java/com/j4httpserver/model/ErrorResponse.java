package com.j4httpserver.model;

public record ErrorResponse(String message, String description, int statusCode) {

}
