# Wethaq Message Reliability Phase

This marker records that the controlled migration for message idempotency and media endpoint rate limiting has been applied to the development branch.

Implemented server guarantees:
- `messages.client_id` persistence for client-generated message identity.
- Unique `(sender_id, client_id)` index for replay protection.
- Idempotent replay returns the existing message instead of creating a duplicate.
- Text, audio, and image message endpoints require a validated client ID.
- Audio and image message endpoints share a bounded per-user media rate limit.
