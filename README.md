# j4java.httpserver

A simple **Java HttpServer library** built on [HttpServer](https://docs.oracle.com/javase/8/docs/jre/api/net/httpserver/spec/com/sun/net/httpserver/HttpServer.html).

---

## Table of Contents

* [Overview](#overview)
* [Installing as Maven Dependency](#installing-as-maven-dependency)
* [Usage](#usage)
* [Annotations](#annotations)
* [Example](#example)
* [Contributing](#contributing)

---

## Overview

This library simplifies the creation of HTTP servers in Java. It provides annotations to define controllers and endpoints similar to frameworks like Spring, without requiring Spring itself.

---

## Installing as Maven Dependency

1. Clone the repository:

```bash

git clone https://github.com/tur4b/j4java.httpserver.git
```

2. Navigate into the project folder:

```bash

cd j4java.httpserver
```

3. Install it into your local Maven repository:

```bash

mvn clean install
```

> After running this, the dependency will be available in `~/.m2/repository`.

---

## Usage

1. Add the dependency to your Maven project:

```xml
<dependency>
    <groupId>j4httpserver.az</groupId>
    <artifactId>j4java.httpserver</artifactId>
    <version>1.0</version>
</dependency>
```

2. Enable the HTTP server using the `@EnableHttpServer` annotation and
   `HttpServerAutoConfiguration.run(Application.class)` call this method inside your main method


```java
@EnableHttpServer(
    port = 8081,
    scanPackages = { "com.practice.controller" }
)
```

* **port** (optional): default is `8080`
* **scanPackages** (required): packages to scan for classes annotated with `@HttpServerExchange`

3. Annotate your controller classes with `@HttpServerExchange`:

```java
@HttpServerExchange(path = "/exams")
public class ExamController {

    @HttpEndpoint
    public List<ExamDTO> getAllExams(HttpExchange exchange) {
        return ...;
    }

}
```

4. Annotate controller methods with `@HttpEndpoint`.
  > Each method must accept a `HttpExchange` object as a parameter.

---

## Annotations Overview

| Annotation            | Usage                                                                                                                                                                |
| --------------------- |----------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| `@EnableHttpServer`   | Enable and configure the HTTP server (port, scan packages) <br/>- if subdirectories existed inside scan packages then those packages classes will be considered too) |
| `@HttpServerExchange` | Mark a class as an HTTP controller with a base path                                                                                                                  |
| `@HttpEndpoint`       | Mark a method as an HTTP endpoint for the controller                                                                                                                 |

---

## Example

```java
@EnableHttpServer(
    port = 8081,
    scanPackages = { "com.practice.controller" }
)
public class Application {
    public static void main(String[] args) {
        HttpServerAutoConfiguration.run(Application.class);
    }
}
```

```java
package com.practice.controller;

@HttpServerExchange(path = "/exams")
public class ExamController {

    @HttpEndpoint
    public List<ExamDTO> getAllExams(HttpExchange exchange) {
        // implement logic here
        return List.of(new ExamDTO("Math"), new ExamDTO("Physics"));
    }
}
```

---

## Contributing

* Contributions and enhancements are welcome!
* Possible improvements:

    * Support for request bodies
    * Support for query parameters
    * Support for path variables
