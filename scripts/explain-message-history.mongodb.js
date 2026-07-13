// Run in mongosh against the NexTalk database after the startup migration.
// Replace the sample values with real IDs from your database.
const conversationId = "REPLACE_CONVERSATION_ID";
const userId = "REPLACE_USER_ID";

print("Message history query plan:");
printjson(db.messages.find({
  conversationId,
  deletedByUsers: { $ne: userId }
}).sort({ createdAt: -1 }).limit(25).explain("executionStats"));

print("Seen-status query plan:");
printjson(db.message_statuses.find({
  conversationId,
  userId,
  status: { $in: ["SENT", "DELIVERED"] }
}).explain("executionStats"));

print("Relevant indexes:");
printjson(db.messages.getIndexes());
printjson(db.message_statuses.getIndexes());
