# Spring MDC API

This project demonstrates how to use SLF4J's Mapped Diagnostic Context (MDC) in a Spring Boot application by intercepting HTTP requests and injecting header values into the thread's logging context.

## What is MDC and How Does it Work?
MDC (Mapped Diagnostic Context) is a feature provided by logging frameworks like SLF4J and Logback. It allows developers to enrich log messages with contextual information (like a Request ID, User ID, or Session ID) so that you don't have to manually pass these variables to every single log statement.

Under the hood, MDC is backed by a **`ThreadLocal`**. A `ThreadLocal` is a special Java class that allows you to store data that is only accessible by a specific thread. When you call `MDC.put(key, value)`, the logging framework stores that key-value pair in a map that belongs *exclusively* to the thread executing that code.

## Is it a Good Idea to Populate MDC with All Sorts of Thread Context Data?
MDC is strictly meant for **diagnostic and logging purposes**. It is **not** a general-purpose state management tool or a replacement for passing contextual objects through your application layers.

It is generally a **bad idea** to use MDC to pass heavy business logic state because:
1. **Hidden Dependencies**: It creates hidden side-effects. Methods deep in your code will rely on magic thread-local variables rather than explicit method parameters, making the code extremely hard to read and test.
2. **Memory Leaks**: Dumping large objects or excessive data into MDC can cause memory bloat, as the data is held tightly by the thread.
3. **Async Pitfalls**: If you spawn a new thread (e.g., using `@Async`, `CompletableFuture`, or reactive programming), the `ThreadLocal` MDC context does **not** automatically transfer to the child thread. You have to manually copy it over, which becomes a nightmare if you rely on it for core business logic.

Use MDC only for metadata you explicitly want attached to your log files (e.g., trace IDs, tenant IDs).

## Thread Isolation and Thread Pools
When multiple requests hit a Spring Boot application, they are handled by a pool of worker threads (e.g., Tomcat's thread pool).

**How does MDC remain isolated even if requests use the same thread?**
Because MDC relies on `ThreadLocal`, two *concurrent* requests will be handled by two *different* threads. Therefore, at that exact moment, they have completely isolated MDC maps. 

However, once a request finishes, the thread is returned to the pool and reused for a future request. If you look at the `thread-name` in your API responses, you'll see the exact same thread name (e.g., `http-nio-8080-exec-1`) handling different requests sequentially. 

**This leads to the deletion question...**

## How are MDC Values Getting Deleted?
You mentioned in your question: *"since we are not manually deleting any MDC value, how are values getting deleted?"*

The truth is: **We actually ARE manually deleting them!**

If you look at the `RequestInterceptor.java` class we created, you'll see this specific method:

```java
@Override
public void afterCompletion(HttpServletRequest request, HttpServletResponse response, Object handler, Exception ex) throws Exception {
    MDC.clear();
}
```

The `afterCompletion` method is a hook provided by Spring's `HandlerInterceptor` that is guaranteed to run after the response has been sent back to the client. We use this hook to call `MDC.clear()`.

**What would happen if we didn't call `MDC.clear()`?**
Because Spring uses a thread pool, threads are reused. If we didn't manually clear the MDC at the end of the request, we would experience a **ThreadLocal Leak**. 

When `http-nio-8080-exec-1` finishes Request A, the thread goes back to the pool. When Request B comes in and uses `http-nio-8080-exec-1`, the old MDC data from Request A would still be sitting there! Request B would "inherit" Request A's data, causing logs to get mixed up and potentially leaking sensitive data across user requests.
