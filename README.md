# NexTalk Backend

Backend for NexTalk, a real-time messaging application. The service provides REST APIs, WebSocket/STOMP, JWT authentication, file storage, and third-party integrations for the frontend.

Frontend: <https://github.com/TruongChiBao1506/NexTalk_FE>

## Key Features

- Registration, email verification, login, Google login, refresh token, and session management.
- User profiles, user search, chat PINs, friends, and blocking users.
- Private chats, groups, text/voice channels, group invitations, and join requests.
- Real-time messaging: attachments, reactions, pinning, recalling, polls, searching, and forwarding.
- Message requests for strangers with anti-spam limits; shared messages are restored when the recipient accepts.
- Notifications, FCM push notifications, sticker packs, voice/video calls via Agora, and conversation summaries via n8n.

## Technology Stack

- Java 17, Spring Boot 4.0.6, Maven
- MongoDB, Redis
- Spring Security, JWT, Spring WebSocket/STOMP
- Cloudinary, Firebase Admin SDK, Agora, Google API Client
- Springdoc OpenAPI

## Requirements

- JDK 17
- Running MongoDB and Redis (or managed service URI/host)
- Maven 3.9+ or Maven Wrapper included in the repository
- Cloudinary account if media upload is needed

Firebase, Agora, Google OAuth, and n8n are optional. Not configuring them will safely disable the respective integrations without affecting basic chat flows.

## Environment Configuration

The application reads environment variables directly. You can set them in your terminal/IDE, via your deployment platform's secret manager, or by using a `.env` file with Docker `--env-file`. The Spring Boot app in this repository **does not automatically load `.env` when running local Maven commands**. The `.env` file is in `.gitignore`; never commit secrets to Git.

```dotenv
# Required
SPRING_DATA_MONGODB_URI=mongodb://localhost:27017/nextalk
JWT_SECRET=replace-with-a-long-random-secret
SPRING_MAIL_USERNAME=your-email@example.com
SPRING_MAIL_PASSWORD=your-app-password
CLOUDINARY_CLOUD_NAME=your-cloud-name
CLOUDINARY_API_KEY=your-api-key
CLOUDINARY_API_SECRET=your-api-secret

# Optional / Has default values
PORT=8080
CORS_ALLOWED_ORIGINS=http://localhost:5173,http://localhost:3000
REDIS_HOST=localhost
REDIS_PORT=6379
REDIS_USERNAME=
REDIS_PASSWORD=
SPRING_MAIL_HOST=smtp.gmail.com
SPRING_MAIL_PORT=587
AGORA_APP_ID=
AGORA_APP_CERTIFICATE=
N8N_SUMMARY_WEBHOOK_URL=
SUMMARY_MESSAGE_LIMIT=15
SUMMARY_PREFERRED_MODEL=gemini-2.5-flash
GOOGLE_CLIENT_ID=
```

FCM uses `FIREBASE_CREDENTIALS` (JSON service account directly or Base64 encoded). If this variable is missing, the application will also attempt to read `src/main/resources/firebase-service-account.json`; if both are absent, FCM is safely disabled.

`SPRING_MAIL_HOST`, `SPRING_MAIL_PORT`, `REDIS_HOST`, `REDIS_PORT`, `PORT`, summary, and Agora configurations all have default values in `application.properties`. MongoDB URI, JWT secret, mail credentials, and Cloudinary do not have default values.

## Run Locally

```bash
git clone https://github.com/TruongChiBao1506/NexTalk_BE.git
cd NexTalk_BE
```

macOS/Linux:

```bash
./mvnw spring-boot:run
```

Windows PowerShell:

```powershell
.\mvnw.cmd spring-boot:run
```

Or using pre-installed Maven:

```bash
mvn spring-boot:run
```

The application runs at `http://localhost:8080` by default.

## Run with Docker

The `Dockerfile` builds the application using Maven and runs JRE 17. You need to pass the environment variables mentioned above, and MongoDB/Redis must be accessible from the container.

```bash
docker build -t nextalk-be .
docker run --env-file .env -p 8080:8080 nextalk-be
```

## API and WebSocket

- Health check: `GET /api/health` or `GET /api/health/ping`
- Swagger UI: `http://localhost:8080/swagger-ui.html`
- OpenAPI JSON: `http://localhost:8080/api-docs`
- SockJS/STOMP endpoint: `/ws`
- STOMP application prefix: `/app`
- Broker destinations: `/topic`, `/queue`; user destination prefix: `/user`

STOMP connections must send the native header `Authorization: Bearer <access-token>` upon `CONNECT`.

Main REST API groups:

| Group | Base path |
| --- | --- |
| Authentication | `/api/auth` |
| Users | `/api/users` |
| Friends and blocks | `/api/friends`, `/api/blocks` |
| Conversations and messages | `/api/conversations`, `/api/messages` |
| Groups and channels | `/api/groups`, `/api/groups/{groupId}/channels` |
| Message requests | `/api/chat-requests` |
| Files, stickers, notifications, FCM | `/api/files`, `/api/stickers`, `/api/notifications`, `/api/fcm` |
| Calls | `/api/calls` |

Swagger is the most comprehensive and up-to-date reference for request/response models of each endpoint.

## Limits and Security Notes

- Access tokens expire in 15 minutes; refresh tokens expire in 7 days.
- Uploads are limited to 50 MB per file, and 55 MB per request.
- Message requests to strangers are limited to 10 messages/day/sender; respects block status and prevents sharing messages from private channels to strangers.
- Set `CORS_ALLOWED_ORIGINS` to the correct frontend domain when deploying. REST CORS uses this variable; the WebSocket endpoint currently accepts any origin pattern.
- Do not commit `.env`, Firebase service account, or Cloudinary/Agora/JWT keys.

## Testing

```bash
./mvnw test
```

On Windows, use `.\mvnw.cmd test` or `mvn test`.
