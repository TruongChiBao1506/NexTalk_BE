// Run with mongosh against the NexTalk database before deploying friend suggestions.
// These reserved identities are application-owned accounts, not real people.
db.users.updateMany(
  {
    $or: [
      { email: "moderator@nextalk.local" },
      { username: { $in: ["NexTalk Moderator", "NexTalk AI"] } }
    ]
  },
  {
    $set: {
      systemAccount: true,
      friendSuggestionDiscoverable: false,
      isAccountLocked: true
    }
  }
);
