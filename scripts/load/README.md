# NexTalk WebSocket load test

The k6 scenario in `websocket-stomp.js` opens native WebSocket connections,
performs an authenticated STOMP handshake, maintains transport and application
heartbeats, and optionally subscribes to the global presence topic.

Each JWT is limited to 20 connection attempts per minute by the backend. For
representative tests, provide at least `ceil(VUS / 20)` access tokens, and
prefer one token per virtual user when measuring per-user behavior.

Run a 100-connection baseline:

```powershell
$env:ACCESS_TOKENS = "jwt-user-1,jwt-user-2,jwt-user-3,jwt-user-4,jwt-user-5"
$env:BASE_URL = "http://localhost:8080"
k6 run --vus 100 --duration 2m .\scripts\load\websocket-stomp.js
```

Measure presence fan-out explicitly:

```powershell
$env:SUBSCRIBE_PRESENCE = "true"
k6 run --vus 100 --duration 2m .\scripts\load\websocket-stomp.js
```

Track `stomp_connect_time`, `stomp_connected`, backend CPU, heap, MongoDB and
Redis latency. Increase load gradually (100, 500, 1,000 connections) and keep
the same session duration for comparable results.
