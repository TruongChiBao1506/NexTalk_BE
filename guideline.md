# GUIDELINE.md

# Realtime Chat Application Backend

## 1. Project Overview

Xây dựng hệ thống chat realtime tương tự Zalo hoặc Discord.

### Main Features

* Authentication
* User Profile
* Friend System
* Private Chat
* Group Chat
* Realtime Messaging
* File Upload
* Notification
* Online Presence
* Message Status
* Voice/Video Call (Future)

---

# 2. Tech Stack

## Backend

* Java 21
* Spring Boot 3.x
* Spring Security
* Spring Data JPA
* Spring Validation
* Spring Mail
* Spring WebSocket
* JWT
* PostgreSQL (Neon)
* Redis
* Maven

## Third Party Services

### Cloudinary

Lưu trữ:

* Avatar
* Image
* Video
* Attachments

### Email Service

* Gmail SMTP hoặc Resend

---

# 3. Architecture

Áp dụng kiến trúc:

```text
Controller
    ↓
Service
    ↓
Repository
    ↓
Database
```

---

## Package Structure

```text
src/main/java/com/chatapp

├── auth
├── user
├── friend
├── conversation
├── message
├── group
├── notification
├── websocket
├── file
├── common
├── config
├── security
└── exception
```

---

# 4. Global Standards

## Entity

Tất cả entity kế thừa:

```java
BaseEntity
```

Bao gồm:

```text
id (UUID)
createdAt
updatedAt
```

---

## DTO Rule

Không trả Entity trực tiếp.

Luôn sử dụng:

```text
Request DTO
Response DTO
```

Ví dụ:

```text
RegisterRequest
RegisterResponse
```

---

## API Response Format

Success:

```json
{
  "success": true,
  "message": "Success",
  "data": {}
}
```

Error:

```json
{
  "success": false,
  "message": "Validation failed",
  "errors": []
}
```

---

## Security Standards

Password:

```java
BCryptPasswordEncoder
```

JWT:

```text
Access Token: 15 phút

Refresh Token: 7 ngày
```

Refresh Token lưu Database.

Không lưu password dạng plain text.

---

# DATABASE TABLES

Hệ thống cuối cùng phải có:

```text
users

refresh_tokens

email_verifications

friendships

conversations

conversation_members

messages

message_status

groups

group_members

notifications
```

---

# PHASE 1

# AUTHENTICATION & USER

## Goal

Cho phép người dùng đăng ký và đăng nhập.

---

## Features

### Register

* Email
* Username
* Password

---

### Login

* Email
* Password

---

### Refresh Token

Sinh Access Token mới.

---

### Logout

Thu hồi Refresh Token.

---

### Email Verification

Sau đăng ký:

```text
Đăng ký
↓
Gửi Email
↓
Nhấn Link
↓
Kích hoạt tài khoản
```

---

### User Profile

Thông tin:

```text
id
username
email
avatarUrl
bio
status
```

---

## Tables

### users

```text
id
email
username
password
avatar_url
bio
status
is_verified
created_at
updated_at
```

---

### refresh_tokens

```text
id
user_id
token
expires_at
created_at
```

---

### email_verifications

```text
id
user_id
token
expires_at
verified
```

---

## APIs

```http
POST /api/auth/register

POST /api/auth/login

POST /api/auth/refresh

POST /api/auth/logout

GET /api/auth/verify-email

GET /api/users/me

PUT /api/users/profile

GET /api/users/{id}
```

---

## Deliverables

✅ JWT

✅ Refresh Token

✅ Email Verification

✅ User Profile

✅ Global Exception Handler

✅ Swagger

---

# PHASE 2

# FRIEND SYSTEM

## Goal

Cho phép người dùng kết bạn.

---

## Friendship Status

```text
PENDING
ACCEPTED
REJECTED
BLOCKED
```

---

## Table

### friendships

```text
id
sender_id
receiver_id
status
created_at
```

---

## APIs

```http
POST /api/friends/request

PUT /api/friends/accept

PUT /api/friends/reject

DELETE /api/friends/remove

GET /api/friends

GET /api/friends/pending
```

---

## Deliverables

✅ Send Request

✅ Accept Friend

✅ Reject Friend

✅ Remove Friend

---

# PHASE 3

# PRIVATE CHAT

## Goal

Chat realtime giữa 2 người.

---

## WebSocket

Endpoint:

```text
/ws
```

Topic:

```text
/queue/private
```

Application Prefix:

```text
/app
```

---

## Table

### conversations

```text
id
type
created_at
```

Type:

```text
PRIVATE
GROUP
```

---

### conversation_members

```text
conversation_id
user_id
```

---

### messages

```text
id
conversation_id
sender_id
content
message_type
created_at
```

---

### Message Types

```text
TEXT
IMAGE
VIDEO
FILE
```

---

## APIs

```http
GET /api/conversations

GET /api/conversations/{id}

GET /api/messages/{conversationId}
```

---

## Deliverables

✅ Realtime Chat

✅ Chat History

✅ Pagination

✅ WebSocket

---

# PHASE 4

# GROUP CHAT

## Goal

Cho phép chat nhóm.

---

## Tables

### groups

```text
id
name
owner_id
created_at
```

---

### group_members

```text
group_id
user_id
role
```

Role:

```text
OWNER
ADMIN
MEMBER
```

---

## APIs

```http
POST /api/groups

PUT /api/groups/{id}

DELETE /api/groups/{id}

POST /api/groups/{id}/members

DELETE /api/groups/{id}/members/{userId}

GET /api/groups/{id}
```

---

## Deliverables

✅ Create Group

✅ Add Member

✅ Remove Member

✅ Group Chat

---

# PHASE 5

# MESSAGE STATUS

## Goal

Theo dõi trạng thái tin nhắn.

---

## Status

```text
SENT
DELIVERED
SEEN
```

---

## Table

### message_status

```text
id
message_id
user_id
status
updated_at
```

---

## Deliverables

✅ Seen Message

✅ Delivered Message

---

# PHASE 6

# FILE UPLOAD

## Goal

Gửi ảnh và file.

---

## Cloudinary

Chỉ lưu:

```text
URL
PublicId
```

Không lưu binary.

---

## APIs

```http
POST /api/files/upload
```

---

## Deliverables

✅ Image Upload

✅ Video Upload

✅ File Upload

---

# PHASE 7

# NOTIFICATION

## Goal

Thông báo realtime.

---

## Notification Types

```text
NEW_MESSAGE

FRIEND_REQUEST

GROUP_INVITE
```

---

## Table

### notifications

```text
id
user_id
type
content
is_read
created_at
```

---

## APIs

```http
GET /api/notifications

PUT /api/notifications/{id}/read
```

---

## Deliverables

✅ Notification List

✅ Realtime Notification

---

# PHASE 8

# ONLINE PRESENCE

## Goal

Hiển thị trạng thái online.

---

## Status

```text
ONLINE

OFFLINE

AWAY
```

---

## Redis

Dùng Redis để:

```text
Online User Tracking

Last Seen

Presence
```

---

## Deliverables

✅ Online Status

✅ Last Seen

---

# PHASE 9

# ADVANCED FEATURES

## Message Features

* Reply Message
* Edit Message
* Delete Message
* Recall Message
* Pin Message
* Reaction

---

## Search

* Search User
* Search Conversation
* Search Message

---

## Voice & Video

* Agora
  hoặc
* WebRTC

---

## Deliverables

✅ Voice Call

✅ Video Call

✅ Screen Sharing

---

# Testing Requirements

Mỗi API phải có:

```text
Unit Test

Integration Test
```

Coverage tối thiểu:

```text
70%
```

---

# Definition Of Done

Một phase được xem hoàn thành khi:

✓ API hoạt động

✓ Validation đầy đủ

✓ Security đầy đủ

✓ Swagger cập nhật

✓ Test thành công

✓ Không có lỗi SonarLint nghiêm trọng

✓ Code Review hoàn tất
