from pathlib import Path
import re

p = Path('backend/server.js')
s = p.read_text(encoding='utf-8')

# The backend is currently minified, so do not depend on a literal newline or on
# the exact route that happens to follow /api/admin/alert. Restrict the change to
# the admin alert route and promote only its final success response to HTTP 201.
route = re.search(r"app\.post\('/api/admin/alert'(?P<body>.*?)\)\)\);?app\.", s)
if not route:
    route = re.search(r"app\.post\('/api/admin/alert'(?P<body>.*?)(?=app\.)", s)
if not route:
    raise SystemExit('Could not locate /api/admin/alert route')

body = route.group('body')
body_new = body.replace('res.status(201).json({ok:true})', 'res.json({ok:true})')
# Exactly one final success response is expected in this route. Replace the last
# plain JSON success response, preserving all error responses and RBAC logic.
pos = body_new.rfind('res.json({ok:true})')
if pos < 0:
    if 'res.status(201).json({ok:true})' in body:
        print('WETHAQ_ADMIN_ALERT_STATUS_ALREADY_OK')
        raise SystemExit(0)
    raise SystemExit('Could not locate success response in /api/admin/alert route')

body_fixed = body_new[:pos] + 'res.status(201).json({ok:true})' + body_new[pos + len('res.json({ok:true})'):]
s = s[:route.start('body')] + body_fixed + s[route.end('body'):]
p.write_text(s, encoding='utf-8')
print('WETHAQ_ADMIN_ALERT_STATUS_REPAIRED')
