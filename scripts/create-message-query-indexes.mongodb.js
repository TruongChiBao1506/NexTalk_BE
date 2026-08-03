// Version: message-query-indexes-v1
// Dry-run is the default. Set NEXTALK_APPLY_MESSAGE_QUERY_INDEXES=true only
// after verifying a database backup and reviewing this output.

const apply = process.env.NEXTALK_APPLY_MESSAGE_QUERY_INDEXES === 'true';
const collection = db.getCollection('messages');
const requiredIndexes = [
  {
    name: 'message_history_cursor_v1',
    keys: { conversationId: 1, createdAt: -1, _id: -1 },
    options: {},
  },
  {
    name: 'message_pinned_cursor_v1',
    keys: { conversationId: 1, isPinned: 1, createdAt: -1, _id: -1 },
    options: {},
  },
  {
    name: 'message_search_filter_cursor_v1',
    keys: {
      conversationId: 1,
      isRecalled: 1,
      messageType: 1,
      senderId: 1,
      createdAt: -1,
      _id: -1,
    },
    options: {},
  },
  {
    name: 'message_content_text_v1',
    keys: { content: 'text' },
    options: { default_language: 'none' },
  },
];

print(`Message query index migration mode: ${apply ? 'APPLY' : 'DRY-RUN'}`);
print(`Message documents: ${collection.estimatedDocumentCount()}`);

const currentIndexes = collection.getIndexes();
const sameKeys = (left, right) => JSON.stringify(left) === JSON.stringify(right);
const isTextSpec = (spec) => Object.values(spec.keys).includes('text');
const isReady = (spec) => currentIndexes.some((index) => (
  isTextSpec(spec)
    ? index.weights && Object.prototype.hasOwnProperty.call(index.weights, 'content')
    : sameKeys(index.key, spec.keys)
));

for (const spec of requiredIndexes) {
  print(`${isReady(spec) ? 'READY' : 'MISSING'} ${spec.name}`);
}

if (!apply) {
  print('No indexes changed. Verify backup, then set NEXTALK_APPLY_MESSAGE_QUERY_INDEXES=true.');
} else {
  const conflictingTextIndex = currentIndexes.find((index) => (
    index.key && index.key._fts === 'text'
    && !(index.weights && Object.prototype.hasOwnProperty.call(index.weights, 'content'))
  ));
  if (conflictingTextIndex) {
    throw new Error('A different text index already exists. Review and replace it manually before applying this migration.');
  }
  for (const spec of requiredIndexes) {
    if (!isReady(spec)) {
      collection.createIndex(spec.keys, { name: spec.name, ...spec.options });
    }
  }
  print('Message query indexes applied. Validate with db.messages.getIndexes() and explain("executionStats").');
}
