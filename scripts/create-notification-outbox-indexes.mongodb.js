// Version: 2026-08-03-notification-outbox-v1
// Take and verify a backup first. Dry-run is the default.
const applyMigration = process.env.NEXTALK_APPLY_NOTIFICATION_OUTBOX_INDEXES === "true";
const notifications = db.getCollection("notifications");
const messages = db.getCollection("messages");

const duplicateKeys = notifications.aggregate([
  { $match: { pushIdempotencyKey: { $type: "string" } } },
  { $group: { _id: "$pushIdempotencyKey", count: { $sum: 1 } } },
  { $match: { count: { $gt: 1 } } },
  { $count: "groups" },
]).toArray();
const duplicateGroupCount = duplicateKeys[0]?.groups ?? 0;

printjson({ applyMigration, duplicateGroupCount });
if (duplicateGroupCount > 0) {
  throw new Error("Duplicate outbox idempotency keys must be reviewed before creating indexes");
}

if (!applyMigration) {
  print("Dry run complete. Set NEXTALK_APPLY_NOTIFICATION_OUTBOX_INDEXES=true after backup and review.");
} else {
  notifications.createIndex(
    { pushIdempotencyKey: 1 },
    { name: "notification_push_idempotency_unique_v1", unique: true, sparse: true }
  );
  notifications.createIndex(
    { deliveryStatus: 1, nextDeliveryAttemptAt: 1, createdAt: 1 },
    { name: "notification_outbox_pending_v1" }
  );
  notifications.createIndex(
    { deliveryStatus: 1, deliveryLeaseUntil: 1 },
    { name: "notification_outbox_lease_v1" }
  );
  messages.createIndex(
    { notificationDispatchStatus: 1, notificationDispatchNextAttemptAt: 1, createdAt: 1 },
    { name: "message_notification_dispatch_v1" }
  );
  print("Notification outbox indexes created and verified.");
}
