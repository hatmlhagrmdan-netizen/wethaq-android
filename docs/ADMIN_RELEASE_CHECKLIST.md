# Wethaq Admin/Identity Release Gate

- [ ] Normal login still uses name + birth year + personal identity verification.
- [ ] New identity receives one unique `wethaq_id` and duplicate names never overwrite an existing identity.
- [ ] Cross-device identity lookup resolves by unique Wethaq identity, not by name alone.
- [ ] Administrative login is a separate session and uses name + birth year + role secret.
- [ ] Admin secret is never returned by public structure endpoints.
- [ ] Admin secrets are stored hashed and are invalidated on removal/expiry.
- [ ] Server-side RBAC enforces every role's appointment and moderation scope.
- [ ] Founder cannot be banned or removed.
- [ ] Role capacities and annual fees match `ADMIN_HIERARCHY.md`.
- [ ] Public administration directory shows active names and `شاغر` seats without secrets.
- [ ] Appointment attribution is recorded in the audit log.
- [ ] Appointment card is delivered privately after successful assignment.
- [ ] Existing messaging, WebSocket, calls, notifications, media and security paths remain intact.
- [ ] Android manifest includes both public administration and admin access screens.
- [ ] Release build succeeds from the same commit being verified.
- [ ] APK artifact SHA-256 is recorded and corresponds to the verified commit.
