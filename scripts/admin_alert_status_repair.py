from pathlib import Path

p = Path('backend/server.js')
s = p.read_text(encoding='utf-8')
old = "res.json({ok:true})})\napp.get('/api/admin/complaints/rbac'"
new = "res.status(201).json({ok:true})})\napp.get('/api/admin/complaints/rbac'"
if old in s:
    s = s.replace(old, new, 1)
    p.write_text(s, encoding='utf-8')
    print('WETHAQ_ADMIN_ALERT_STATUS_REPAIRED')
elif "res.status(201).json({ok:true})})\napp.get('/api/admin/complaints/rbac'" in s:
    print('WETHAQ_ADMIN_ALERT_STATUS_ALREADY_OK')
else:
    raise SystemExit('Could not locate canonical admin alert response contract')
