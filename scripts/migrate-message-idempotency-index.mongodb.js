// Version: 2026-08-03-message-idempotency-v2
// Take a verified backup first. The script is dry-run unless the apply
// environment variable is explicitly set to "true".
const applyMigration = process.env.NEXTALK_APPLY_MESSAGE_IDEMPOTENCY_INDEX === "true";
const reconcileDuplicates = process.env.NEXTALK_RECONCILE_DUPLICATE_CLIENT_IDS === "true";
const messages = db.getCollection("messages");
const targetKeys = {
  conversationId: 1,
  senderId: 1,
  "metadata.clientMessageId": 1,
};
const targetIndexName = "msg_sender_client_id_unique_v2";

const findDuplicateGroups = () => messages.aggregate([
  { $match: { "metadata.clientMessageId": { $type: "string" } } },
  { $sort: { createdAt: 1, _id: 1 } },
  {
    $group: {
      _id: {
        conversationId: "$conversationId",
        senderId: "$senderId",
        clientMessageId: "$metadata.clientMessageId",
      },
      ids: { $push: "$_id" },
      count: { $sum: 1 },
    },
  },
  { $match: { count: { $gt: 1 } } },
]).toArray();

let duplicateGroups = findDuplicateGroups();
printjson({ applyMigration, reconcileDuplicates, duplicateGroupCount: duplicateGroups.length });

if (!applyMigration) {
  print("Dry run complete. Review the count and take a verified backup before applying.");
} else {
  if (duplicateGroups.length > 0 && !reconcileDuplicates) {
    throw new Error(
      "Duplicate groups exist. Review them, then explicitly set NEXTALK_RECONCILE_DUPLICATE_CLIENT_IDS=true to keep all messages while unsetting the retry key on later copies."
    );
  }

  let reconciledMessageCount = 0;
  for (const group of duplicateGroups) {
    const result = messages.updateMany(
      { _id: { $in: group.ids.slice(1) } },
      { $unset: { "metadata.clientMessageId": "" } }
    );
    reconciledMessageCount += result.modifiedCount;
  }
  duplicateGroups = findDuplicateGroups();
  if (duplicateGroups.length > 0) {
    throw new Error("Duplicate groups remain after reconciliation; the index was not changed");
  }

  for (const index of messages.getIndexes()) {
    if (tojson(index.key) === tojson(targetKeys) && index.name !== "_id_") {
      messages.dropIndex(index.name);
    }
  }
  messages.createIndex(targetKeys, {
    name: targetIndexName,
    unique: true,
    partialFilterExpression: {
      "metadata.clientMessageId": { $type: "string" },
    },
  });
  printjson({ reconciledMessageCount });
  printjson(messages.getIndexes().filter((index) => index.name === targetIndexName));
}
