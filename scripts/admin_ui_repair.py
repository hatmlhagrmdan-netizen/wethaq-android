from pathlib import Path

p = Path('app/src/main/java/com/wethaq/app/AdminActivity.java')
s = p.read_text(encoding='utf-8')

# Least-privilege UI: admin members must not even be presented moderation controls.
s = s.replace('if("founder".equals(role))addFounderControls();else addModerationControls();',
              'if("founder".equals(role))addFounderControls();else if("deputy1".equals(role)||"deputy2".equals(role)||"deputy3".equals(role))addModerationControls();')

# Do not present moderation target/reason fields to a role that cannot moderate.
old = 'target=f("معرف وَثاق للمستخدم");minutes=f("مدة الحظر بالدقائق — 0 = نهائي");reason=f("السبب");body.addView(target,lp(-1,60,8));body.addView(minutes,lp(-1,60,8));body.addView(reason,lp(-1,60,8));'
new = 'target=f("معرف وَثاق للمستخدم");minutes=f("مدة الحظر بالدقائق — 0 = نهائي");reason=f("السبب");if("founder".equals(role)||"deputy1".equals(role)||"deputy2".equals(role)||"deputy3".equals(role)){body.addView(target,lp(-1,60,8));body.addView(minutes,lp(-1,60,8));body.addView(reason,lp(-1,60,8));}'
if old not in s:
    raise SystemExit('AdminActivity moderation-field anchor not found')
s = s.replace(old, new, 1)

# Prevent repeated administrative submissions while a request is in flight.
if 'private void setBusy(boolean busy)' not in s:
    anchor = 'private interface CB{void ok(String s);}'
    method = '''private void setBusy(boolean busy){if(root==null)return;setBusyView(root,busy);}\n private void setBusyView(View v,boolean busy){if(v instanceof Button)v.setEnabled(!busy);if(v instanceof ViewGroup){ViewGroup g=(ViewGroup)v;for(int i=0;i<g.getChildCount();i++)setBusyView(g.getChildAt(i),busy);}}\n '''
    if anchor not in s:
        raise SystemExit('AdminActivity request anchor not found')
    s = s.replace(anchor, method + anchor, 1)

old_req = 'private void req(String m,String p,String body,CB cb){new Thread(()->{try{'
new_req = 'private void req(String m,String p,String body,CB cb){runOnUiThread(()->setBusy(true));new Thread(()->{try{'
if old_req in s:
    s = s.replace(old_req, new_req, 1)
else:
    raise SystemExit('AdminActivity req start not found')

old_success = 'runOnUiThread(()->{if(code>=200&&code<300)cb.ok(s);else toast(error(code,s));});}catch(Exception e){runOnUiThread(()->toast("تعذر الاتصال بالخادم"));}}).start();}'
new_success = 'runOnUiThread(()->{setBusy(false);if(code>=200&&code<300)cb.ok(s);else toast(error(code,s));});}catch(Exception e){runOnUiThread(()->{setBusy(false);toast("تعذر الاتصال بالخادم");});}}).start();}'
if old_success not in s:
    raise SystemExit('AdminActivity req completion not found')
s = s.replace(old_success, new_success, 1)

p.write_text(s, encoding='utf-8')
print('WETHAQ_ADMIN_UI_REPAIR_OK')
