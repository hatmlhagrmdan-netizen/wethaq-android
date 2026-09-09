# Wethaq Administration Board Contract

This document records the intended production behavior of the administration boards.

## Founder board
- Shows the current authenticated administrative role and effective permissions.
- Provides a user search field using Wethaq ID or name.
- Provides a role selector limited to roles returned by the server `appoints` policy for the current role.
- Executes `/api/admin/assign` and displays the generated position secret and expiry.
- Shows user login history and position/appointment history.
- Position history displays name, Wethaq ID, role, code version, appointing administrator, appointment time, expiry time, and ended time when applicable.
- Personal login codes and administrative position secrets are never displayed as plaintext in public registries or audit history.

## Public administration board
- Loads the live structure from `/api/admin/structure`.
- Displays the current holder name, Wethaq ID, role, appointment date, and appointing administrator where available.
- Does not expose birth year, personal login code, or position secret.

## Backend record contract
- `user_login_log` records successful identity/personal/admin logins for new events.
- `admin_position_history` records appointments and closes the active record when a position is removed.
- Access to login and position history is founder-only.
- Server-side RBAC remains authoritative; hiding an action in the Android UI does not replace backend authorization.
