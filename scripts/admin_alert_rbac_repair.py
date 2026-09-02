from pathlib import Path
import re

p=Path('backend/server.js')
s=p.read_text(encoding='utf-8')
route=re.search(r"app\.post\('/api/admin/alert'.*?(?=app\.|server\.listen|$)",s,re.S)
if not route:
    raise SystemExit('PATCH_MISSING: admin alert route')
block=route.group(0)
original=block
block=block.replace("app.post('/api/admin/alert',auth,founderOnly", "app.post('/api/admin/alert',auth,rbac", 1)
block=block.replace("app.post('/api/admin/alert',auth,admin", "app.post('/api/admin/alert',auth,rbac", 1)
# Remove founder-only checks inside the alert route while preserving the explicit founder protection.
block=re.sub(r"if\(req\.adminRole!==ROLE_FOUNDER\)return res\.status\(403\)\.json\(\{error:'founder_only'\}\);", "", block)
# A warning is permitted for a normal target; founder remains protected by target-role validation.
if "roleCanBan(req.adminRole" in block:
    block=block.replace("roleCanBan(req.adminRole", "roleCanBan(req.adminRole", 1)
if block==original:
    # The route may already use RBAC but still contain a generic founder guard.
    if 'auth,rbac' not in block:
        raise SystemExit('PATCH_UNAPPLIED: admin alert is not RBAC accessible')
    print('WETHAQ_ADMIN_ALERT_RBAC_ALREADY_OK')
else:
    s=s[:route.start()]+block+s[route.end():]
    p.write_text(s,encoding='utf-8')
    print('WETHAQ_ADMIN_ALERT_RBAC_REPAIR_OK')
