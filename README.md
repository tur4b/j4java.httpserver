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
    <groupId>com.j4httpserver</groupId>
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
* **scanPackages** (required): packages to scan for classes annotated with `@HttpServerExchange` and `@Interceptor`

3. Annotate your controller classes with `@HttpServerExchange`:

```java
import com.j4httpserver.annotation.RequestParam;

@HttpServerExchange(path = "/exams")
public class ExamController {

    @HttpEndpoint
    public List<ExamDTO> getAllExams() {
        return ...;
    }

    // we can use HttpExchange as parameter for our endpoint methods
    @HttpEndpoint(path = "/filter")
    public List<ExamDTO> getAllExamsByQueryParameters(HttpExchange exchange) {
        // get request params from exchange
        return ...;
    }
    
    @HttpEndpoint(path = "/special/filter")
    public List<ExamDTO> getAllExamsWithRequestParams(@RequestParam(name = "title", defaultValue = "this is a title") String title,
                                                      @RequestParam(name = "id") Integer id,
                                                      @RequestParam(name = "isSpecial") boolean isSpecial) {
        return ...;
    }


}
```

4. Annotate controller methods with `@HttpEndpoint`.
> `HttpExchange` parameter is not required. We can use it if we want to get request details.

5. We can use interceptors
   * use `@Interceptor` on your interceptor classes 
   * implement `HttpExchangeInterceptor` interface - override required methods of that interface
   * > for example look at [Example](#example)
---

## Annotations Overview

| Annotation            | Usage                                                                                                                                                                                                                                                                 |
|-----------------------|-----------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| `@EnableHttpServer`   | Enable and configure the HTTP server (port, scan packages) <br/>- if subdirectories existed inside scan packages then those packages classes will be considered too)                                                                                                  |
| `@HttpServerExchange` | Mark a class as an HTTP controller with a base path                                                                                                                                                                                                                   |
| `@HttpEndpoint`       | Mark a method as an HTTP endpoint for the controller                                                                                                                                                                                                                  |
| `@RequestBody`        | Mark a method parameter in order to get request body from exchange                                                                                                                                                                                                    |
| `@RequestParam`       | Mark a method parameter in order to get request params from exchange<br/>`name` - is required field<br/>`required` - default is true<br/> `defaultValue`-we ca give default value for the parameter                                                                   |
| `@Interceptor`        | Mark your interceptor classes in order to apply interceptors for your endpoints.<br/>- `order` we can give order to our interceptors<br/> - interceptor classes must implement `HttpExchangeInterceptor` interface and must be annotated with @Interceptor annotation |

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

import com.j4httpserver.annotation.RequestParam;

@HttpServerExchange(path = "/exams")
public class ExamController {

    @HttpEndpoint
    public List<ExamDTO> getAllExams(HttpExchange exchange) { // using HttpExchange as a parameter is optional
        // implement logic here
        return List.of(new ExamDTO("Math"), new ExamDTO("Physics"));
    }

    @HttpEndpoint(path = "/create")
    public ExamDTO createExam(@RequestBody ExamCreateRequest request) {
        // implement logic here
        return ....;
    }

    @HttpEndpoint(path = "/search")
    public List<ExamDTO> searchWithRequestParams(@RequestParam(name = "id", defaultValue = "5") int id,
                                                 @RequestParam(name = "search") String searchQuery) {
        // implement logic here
        return ....;
    }
}
```

> **NOTE**: You can create interceptor classes for your endpoints.
Create your interceptor classes inside your`@EnableHttpServer's scanPackages` package (you can create subdirectory inside `scanPackages` package).
We can give order in order to apply interceptors according to that order. Orders for the interceptors can be the same. 

```java
package com.practice.controller.interceptor; // we have created interceptor directory inside scanPackages package

@Interceptor(order = 1) // order is optional
public class LoggingInterceptor implements HttpExchangeInterceptor {
    private static final Logger log = Logger.getLogger(LoggingInterceptor.class.getName());

    @Override
    public void preHandle(HttpExchange httpExchange) throws Exception {
        log.info(this.getClass().getSimpleName() + " preHandle: " + httpExchange.getRequestURI());
    }

    @Override
    public void postHandle(HttpExchange httpExchange, Object returnObject) throws Exception {
        log.info(this.getClass().getSimpleName() + " postHandle: " + httpExchange.getRequestURI() + " -> " + returnObject);
        HttpExchangeInterceptor.super.postHandle(httpExchange, returnObject);
    }
}

//@Interceptor(order = 2) // order is optional
//class LoggingInterceptor2 implements HttpExchangeInterceptor {...}
```

---

## Contributing

* Contributions and enhancements are welcome!
* Possible improvements:

  * Support for path variables
