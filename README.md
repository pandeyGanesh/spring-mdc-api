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

## Spawning Child Threads and Main Thread Lifecycle
**Scenario**: You populate MDC in a MAIN thread, spawn 10 child threads, copy the MDC context into each child thread, and then the main thread terminates. Will the child threads lose their MDC data?

**Answer**: **No, they will not lose the data.** 
If you explicitly copy the MDC map (e.g., by capturing `MDC.getCopyOfContextMap()` from the main thread and then applying it via `MDC.setContextMap(...)` in the child threads), each child thread gets its own fully independent map. Because MDC is backed by `ThreadLocal`, the data now belongs to the child thread's local memory. The lifecycle or termination of the main thread has zero impact on the `ThreadLocal` data stored inside the child threads.

## MDC with Asynchronous/Non-blocking I/O
**Scenario**: In an asynchronous model, a thread is executing Task 1 with populated MDC. Task 1 makes an HTTP call and waits for I/O, yielding the thread back to the pool. The CPU assigns this thread to Task 2. Task 2 finishes and calls `MDC.clear()` via an interceptor. When Task 1 eventually gets a thread back to resume its work, will its MDC values be lost?

**Answer**: **Yes, the MDC values will be lost (or corrupted).**
MDC is fundamentally tied to the physical **Platform Thread**, not the logical task. If Task 1 yields its thread, and Task 2 takes over that *exact same thread*, Task 2 inherits the `ThreadLocal` state. When Task 2 calls `MDC.clear()`, it wipes the MDC for that physical thread. 

When Task 1 finishes its I/O wait and resumes, it will grab whatever thread is available from the pool. It might be the same thread (which was cleared by Task 2), or a different thread (which might have someone else's MDC or be empty). In either case, Task 1's original MDC context is gone. 

This is the classic pitfall of using standard MDC with reactive programming (like Spring WebFlux) or asynchronous non-blocking code. To solve this, you must either manually propagate the context whenever a task resumes on a thread, or rely on framework-specific context propagation tools (like Reactor Context or Micrometer Tracing) which handle moving the context across thread boundaries automatically.
