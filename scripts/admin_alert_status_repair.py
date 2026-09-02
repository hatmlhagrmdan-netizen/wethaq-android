from pathlib import Path

p = Path('backend/server.js')
s = p.read_text(encoding='utf-8')

marker = "app.post('/api/admin/alert'"
start = s.find(marker)
if start < 0:
    # Accept the alternate JSON-quoted form used by some generated revisions.
    marker = 'app.post("/api/admin/alert"'
    start = s.find(marker)
if start < 0:
    raise SystemExit('Could not locate /api/admin/alert route')

# The canonical route is immediately followed by the RBAC complaints route.
end_marker = "app.get('/api/admin/complaints/rbac'"
end = s.find(end_marker, start)
if end < 0:
    end_marker = 'app.get("/api/admin/complaints/rbac"'
    end = s.find(end_marker, start)
if end < 0:
    raise SystemExit('Could not locate end of /api/admin/alert route')

route = s[start:end]
if 'res.status(201).json({ok:true})' in route:
    print('WETHAQ_ADMIN_ALERT_STATUS_ALREADY_OK')
    raise SystemExit(0)

needle = 'res.json({ok:true})'
pos = route.rfind(needle)
if pos < 0:
    raise SystemExit('Could not locate success response in /api/admin/alert route')

route = route[:pos] + 'res.status(201).json({ok:true})' + route[pos + len(needle):]
s = s[:start] + route + s[end:]
p.write_text(s, encoding='utf-8')
print('WETHAQ_ADMIN_ALERT_STATUS_REPAIRED')
