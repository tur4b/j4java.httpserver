package com.j4httpserver.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.time.LocalDate;

@JsonIgnoreProperties(ignoreUnknown = true)
public record WhatDTO(Long id, String title, LocalDate date) {
}
