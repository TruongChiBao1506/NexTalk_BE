// Run with mongosh against the NexTalk database before deploying friend suggestions.
// The commands are idempotent when the existing indexes use these names and options.
db.friend_suggestion_dismissals.createIndex(
  { userId: 1, candidateUserId: 1 },
  {
    name: "friend_suggestion_dismissal_user_candidate_idx",
    unique: true
  }
);

db.friend_suggestion_dismissals.createIndex(
  { expiresAt: 1 },
  {
    name: "expiresAt",
    expireAfterSeconds: 0
  }
);
