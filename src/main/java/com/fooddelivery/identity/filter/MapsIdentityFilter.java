package com.fooddelivery.identity.filter;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fooddelivery.identity.config.MapsIdentityProperties;
import com.fooddelivery.identity.exception.MapsIdentityException;
import com.fooddelivery.identity.exception.MaxSessionsReachedException;
import com.fooddelivery.identity.security.MapsPeerAuthenticator;
import com.fooddelivery.identity.service.MapsIdentityOperations;
import io.micrometer.core.instrument.MeterRegistry;
import jakarta.servlet.AsyncContext;
import jakarta.servlet.AsyncEvent;
import jakarta.servlet.AsyncListener;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ReadListener;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.time.Duration;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Future;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.SynchronousQueue;
import java.util.concurrent.Semaphore;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import org.springframework.http.MediaType;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.server.ResponseStatusException;
import static com.fooddelivery.identity.exception.MapsIdentityException.Code.*;

/**
 * Narrow asynchronous internal transport, before legacy servlet/mesh filters. Nonblocking body
 * acquisition and one total deadline include slow uploads; no task reads a recycled servlet request.
 * Only the five declared operations are reachable. Human/account policy stays in existing services.
 */
@lombok.extern.slf4j.Slf4j
public final class MapsIdentityFilter extends OncePerRequestFilter implements AutoCloseable {
    public static final String PREFIX = "/internal/maps/v1/";
    public static final int MAX_BODY_BYTES = 8192;
    private final MapsIdentityProperties properties;
    private final MapsPeerAuthenticator peer;
    private final MapsIdentityOperations operations;
    private final MeterRegistry metrics;
    private final ObjectMapper mapper = new ObjectMapper();
    private final ThreadPoolExecutor workers;
    private final Semaphore admitted;
    private final ScheduledThreadPoolExecutor deadlines;

    public MapsIdentityFilter(MapsIdentityProperties properties, MapsPeerAuthenticator peer,
                              MapsIdentityOperations operations, MeterRegistry metrics) {
        this.properties = properties; this.peer = peer; this.operations = operations; this.metrics = metrics;
        int limit = properties.getConcurrency();
        admitted = new Semaphore(limit * 4);
        deadlines = new ScheduledThreadPoolExecutor(1, task -> {
            Thread thread = new Thread(task, "maps-authority-deadlines"); thread.setDaemon(true); return thread;
        });
        deadlines.setRemoveOnCancelPolicy(true);
        workers = new ThreadPoolExecutor(limit, limit, 0, TimeUnit.MILLISECONDS, new SynchronousQueue<>(), task -> {
            Thread thread = new Thread(task, "maps-current-authority"); thread.setDaemon(true); return thread;
        }, new ThreadPoolExecutor.AbortPolicy());
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String raw = request.getRequestURI().substring(request.getContextPath().length());
        if (!raw.startsWith("/internal/maps") && !request.getServletPath().startsWith("/internal/maps")) {
            // This opt-in instance is dedicated to Maps. Its trusted adapter is
            // not a caller of ordinary Food Delivery token/admin endpoints.
            if (properties.isEnabled()) {
                write(response, NOT_FOUND, null, "unknown", System.nanoTime(), UUID.randomUUID().toString()); return;
            }
            chain.doFilter(request, response); return;
        }
        long started = System.nanoTime();
        var requestCorrelation = new AtomicReference<>(UUID.randomUUID().toString().replace("-", ""));
        String operation = raw.startsWith(PREFIX) ? raw.substring(PREFIX.length()) : "unknown";
        try {
            if (!properties.isEnabled()) throw new MapsIdentityException(NOT_FOUND);
            peer.require(request);
            // Only the directly authorised mTLS peer may supply this opaque ID.
            // Browser and forwarded-certificate assertions cannot select it.
            var ids = java.util.Collections.list(request.getHeaders("X-Request-ID"));
            if (!ids.isEmpty()) {
                if (ids.size() != 1 || !ids.getFirst().matches("[0-9a-f]{32}")) throw new MapsIdentityException(INVALID_REQUEST);
                requestCorrelation.set(ids.getFirst());
            }
            if (raw.equals(PREFIX + "ready")) {
                // Credential-free readiness for the authorised Maps adapter: proves the
                // mTLS peer is admitted and this application answers. It never reads a
                // session, user, cache or database, and records no per-call log line.
                if (request.getQueryString() != null) throw new MapsIdentityException(INVALID_REQUEST);
                if (!"GET".equals(request.getMethod())) throw new MapsIdentityException(METHOD_NOT_ALLOWED);
                ready(response, started, requestCorrelation.get()); return;
            }
            if (!MapsIdentityOperations.OPERATIONS.contains(operation) || !raw.equals(PREFIX + operation)
                    || request.getQueryString() != null) throw new MapsIdentityException(INVALID_REQUEST);
            if (!"POST".equals(request.getMethod())) throw new MapsIdentityException(METHOD_NOT_ALLOWED);
            MediaType content;
            try { content = MediaType.parseMediaType(request.getContentType() == null ? "" : request.getContentType()); }
            catch (Exception ex) { throw new MapsIdentityException(UNSUPPORTED_MEDIA_TYPE); }
            if (!"application".equalsIgnoreCase(content.getType()) || !"json".equalsIgnoreCase(content.getSubtype())
                    || content.getCharset() != null && !java.nio.charset.StandardCharsets.UTF_8.equals(content.getCharset())
                    || request.getHeader("Content-Encoding") != null) throw new MapsIdentityException(UNSUPPORTED_MEDIA_TYPE);
            if (request.getContentLengthLong() > MAX_BODY_BYTES) throw new MapsIdentityException(TOO_LARGE);
            if (!request.isAsyncSupported()) throw new MapsIdentityException(UNAVAILABLE);
        } catch (MapsIdentityException denied) {
            write(response, denied.code(), null, operation, started, requestCorrelation.get()); return;
        }
        // The request remains owned by the async servlet lifecycle until upload/response completes.
        // A bounded worker receives only immutable bytes; it never retains a servlet request.
        AsyncContext async = request.startAsync();
        if (!admitted.tryAcquire()) {
            write(response, OVERLOADED, null, operation, started, requestCorrelation.get()); async.complete(); return;
        }
        // The owned timer is the actual deadline. A later servlet backup avoids two
        // completion callbacks racing at the same boundary and triggering Tomcat's
        // automatic 500 dispatch while our finite response is being written.
        async.setTimeout(properties.getRequestTimeout().toMillis() + 2000);
        var done = new AtomicBoolean();
        var future = new AtomicReference<Future<?>>();
        var timer = new AtomicReference<Future<?>>();
        long deadline = started + properties.getRequestTimeout().toNanos();
        class Completion {
            void cancel() { Future<?> task = future.get(); if (task != null) task.cancel(true); }
            void finish(MapsIdentityException.Code code, Map<String, Object> value) { finish(code, value, 1); }
            synchronized void finish(MapsIdentityException.Code code, Map<String, Object> value, int retryAfterSeconds) {
                if (!done.compareAndSet(false, true)) return;
                if (code == null && System.nanoTime() >= deadline) { code = UNAVAILABLE; value = null; }
                Future<?> expiry = timer.get(); if (expiry != null) expiry.cancel(false);
                try { write(response, code, value, operation, started, requestCorrelation.get(), retryAfterSeconds); }
                catch (IOException lost) {
                    // A lost response is not proof a mutation did not commit, so it is never retried here; it is counted
                    // and logged with its correlation id (never the exception text) so the caller's failed exchange can be matched.
                    String counted = MapsIdentityOperations.OPERATIONS.contains(operation) ? operation : "unknown";
                    metrics.counter("maps.identity.responses.lost", "operation", counted).increment();
                    log.warn("maps_identity operation={} stage=response_lost correlation={} cause={}", counted, requestCorrelation.get(), lost.getClass().getSimpleName());
                }
                finally { admitted.release(); async.complete(); }
            }
        }
        var completion = new Completion();
        async.addListener(new AsyncListener() {
            public void onTimeout(AsyncEvent event) { completion.cancel(); completion.finish(UNAVAILABLE, null); }
            public void onError(AsyncEvent event) { completion.cancel(); completion.finish(UNAVAILABLE, null); }
            public void onComplete(AsyncEvent event) { if (done.compareAndSet(false, true)) admitted.release(); }
            public void onStartAsync(AsyncEvent event) { }
        });
        // Tomcat's async timeout scanner has coarse ticks. An owned monotonic timer enforces
        // the actual whole-call bound; the servlet timeout remains a backup, not the deadline.
        try {
            Future<?> expiry = deadlines.schedule(() -> { completion.cancel(); completion.finish(UNAVAILABLE, null); },
                    Math.max(1, deadline - System.nanoTime()), TimeUnit.NANOSECONDS);
            timer.set(expiry); if (done.get()) expiry.cancel(false);
        } catch (RejectedExecutionException stopped) { completion.finish(UNAVAILABLE, null); return; }
        var input = request.getInputStream();
        var bytes = new ByteArrayOutputStream();
        input.setReadListener(new ReadListener() {
            public void onDataAvailable() throws IOException {
                byte[] block = new byte[1024];
                while (!done.get() && input.isReady() && !input.isFinished()) {
                    if (System.nanoTime() >= deadline) { completion.finish(UNAVAILABLE, null); return; }
                    int count = input.read(block);
                    if (count < 0) return;
                    if (bytes.size() + count > MAX_BODY_BYTES) { completion.finish(TOO_LARGE, null); return; }
                    bytes.write(block, 0, count);
                }
            }
            public void onAllDataRead() {
                if (done.get()) return;
                byte[] body = bytes.toByteArray();
                try {
                    Future<?> submitted = workers.submit(() -> {
                        if (done.get() || System.nanoTime() >= deadline) { completion.finish(UNAVAILABLE, null); return; }
                        // Only the opaque correlation enters the worker's log context, and only for this call.
                        org.slf4j.MDC.put(com.fooddelivery.identity.service.MapsSessionTokens.CORRELATION, requestCorrelation.get());
                        try { completion.finish(null, operations.execute(operation, body)); }
                        catch (MapsIdentityException ex) { completion.finish(ex.code(), null); }
                        catch (MaxSessionsReachedException ex) {
                            var choices = ex.getActiveSessions().stream()
                                    .filter(s -> s.getSessionId().matches("[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}"))
                                    .limit(16).map(s -> Map.of("id",s.getSessionId(),"device",bounded(s.getDeviceInfo()),
                                            "os",bounded(s.getOs()),"browser",bounded(s.getBrowser()))).toList();
                            completion.finish(SESSION_LIMIT, Map.of("version",1,"active",false,"code","SESSION_LIMIT","sessions",choices));
                        }
                        catch (ResponseStatusException ex) {
                            int status = ex.getStatusCode().value();
                            int retry = ex instanceof com.fooddelivery.identity.exception.MapsRateLimitException limited
                                    ? limited.retryAfterSeconds() : 1;
                            completion.finish(status == 401 ? INACTIVE : status == 404 ? NOT_FOUND : status == 429 ? OVERLOADED : UNAVAILABLE, null, retry);
                        } catch (Exception ex) { completion.finish(UNAVAILABLE, null); }
                        finally { org.slf4j.MDC.remove(com.fooddelivery.identity.service.MapsSessionTokens.CORRELATION); }
                    });
                    future.set(submitted);
                    if (done.get()) submitted.cancel(true);
                } catch (RejectedExecutionException rejected) { completion.finish(OVERLOADED, null); }
            }
            public void onError(Throwable error) { completion.finish(UNAVAILABLE, null); }
        });
    }

    private void write(HttpServletResponse response, MapsIdentityException.Code code, Map<String, Object> value,
                       String operation, long started, String correlation) throws IOException {
        write(response, code, value, operation, started, correlation, 1);
    }

    private void write(HttpServletResponse response, MapsIdentityException.Code code, Map<String, Object> value,
                       String operation, long started, String correlation, int retryAfterSeconds) throws IOException {
        response.setStatus(code == null ? 200 : code.status());
        response.setContentType("application/json");
        response.setHeader("Cache-Control", "no-store");
        response.setHeader("Pragma", "no-cache");
        response.setHeader("X-Content-Type-Options", "nosniff");
        response.setHeader("X-Request-ID", correlation);
        if (code == OVERLOADED || code == UNAVAILABLE) response.setHeader("Retry-After", Integer.toString(retryAfterSeconds));
        String outcome = code == null ? "completed" : code.name().toLowerCase(java.util.Locale.ROOT);
        String metricOperation = MapsIdentityOperations.OPERATIONS.contains(operation) ? operation : "unknown";
        long elapsed = System.nanoTime() - started;
        metrics.counter("maps.identity.requests", "operation", metricOperation, "outcome", outcome).increment();
        metrics.timer("maps.identity.duration", "operation", metricOperation, "outcome", outcome).record(Duration.ofNanos(elapsed));
        log.info("maps_identity operation={} stage=complete cause={} correlation={} elapsed_ms={}",
                metricOperation, outcome, correlation, TimeUnit.NANOSECONDS.toMillis(elapsed));
        mapper.writeValue(response.getOutputStream(), code == null || value != null ? value
                : Map.of("version", 1, "active", false, "code", code.name()));
    }

    private void ready(HttpServletResponse response, long started, String correlation) throws IOException {
        response.setStatus(200);
        response.setContentType("application/json");
        response.setHeader("Cache-Control", "no-store");
        response.setHeader("X-Content-Type-Options", "nosniff");
        response.setHeader("X-Request-ID", correlation);
        metrics.timer("maps.identity.duration", "operation", "ready", "outcome", "completed")
                .record(Duration.ofNanos(System.nanoTime() - started));
        mapper.writeValue(response.getOutputStream(), Map.of("version", 1, "ready", true));
    }

    private static String bounded(String value) { return value == null ? "" : value.substring(0,Math.min(value.length(),80)); }
    @Override public void close() { workers.shutdownNow(); deadlines.shutdownNow(); }
    @Override public void destroy() { close(); }
}
