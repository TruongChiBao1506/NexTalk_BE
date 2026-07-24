import ws from 'k6/ws';
import { check } from 'k6';
import { Counter, Rate, Trend } from 'k6/metrics';

const accessTokens = (__ENV.ACCESS_TOKENS || '')
  .split(',')
  .map((token) => token.trim())
  .filter(Boolean);

const baseUrl = (__ENV.BASE_URL || 'http://localhost:8080').replace(/\/$/, '');
const wsUrl = __ENV.WS_URL || `${baseUrl.replace(/^http/, 'ws')}/ws-raw`;
const sessionDurationMs = Number(__ENV.SESSION_MS || 60_000);
const subscribePresence = (__ENV.SUBSCRIBE_PRESENCE || 'false').toLowerCase() === 'true';

const stompConnected = new Rate('stomp_connected');
const stompConnectTime = new Trend('stomp_connect_time', true);
const framesReceived = new Counter('stomp_frames_received');

export const options = {
  vus: Number(__ENV.VUS || 100),
  duration: __ENV.DURATION || '2m',
  thresholds: {
    checks: ['rate>0.95'],
    stomp_connected: ['rate>0.95'],
    stomp_connect_time: ['p(95)<2000'],
  },
};

function stompFrame(command, headers = {}, body = '') {
  const headerLines = Object.entries(headers).map(([key, value]) => `${key}:${value}`);
  return `${command}\n${headerLines.join('\n')}\n\n${body}\0`;
}

export default function () {
  if (accessTokens.length === 0) {
    throw new Error('ACCESS_TOKENS must contain one or more valid JWT access tokens');
  }

  const token = accessTokens[(__VU - 1) % accessTokens.length];
  const connectStartedAt = Date.now();
  let connected = false;

  const response = ws.connect(wsUrl, {}, (socket) => {
    socket.on('open', () => {
      socket.send(stompFrame('CONNECT', {
        'accept-version': '1.2',
        'heart-beat': '10000,10000',
        Authorization: `Bearer ${token}`,
      }));
    });

    socket.on('message', (data) => {
      framesReceived.add(1);
      const frame = String(data);
      if (!connected && frame.startsWith('CONNECTED')) {
        connected = true;
        stompConnected.add(true);
        stompConnectTime.add(Date.now() - connectStartedAt);

        if (subscribePresence) {
          socket.send(stompFrame('SUBSCRIBE', {
            id: `presence-${__VU}`,
            destination: '/topic/presence',
            ack: 'auto',
          }));
        }
      }
    });

    socket.setInterval(() => socket.send('\n'), 10_000);
    socket.setInterval(() => {
      if (connected) {
        socket.send(stompFrame('SEND', {
          destination: '/app/presence.heartbeat',
          'content-type': 'application/json',
        }, '{}'));
      }
    }, 30_000);
    socket.setTimeout(() => socket.close(), sessionDurationMs);
  });

  check(response, {
    'WebSocket upgraded': (result) => result && result.status === 101,
  });

  if (!connected) stompConnected.add(false);
}
