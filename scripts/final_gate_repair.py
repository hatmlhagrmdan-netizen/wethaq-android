from pathlib import Path
import re


def read(path: str) -> str:
    return Path(path).read_text(encoding="utf-8")


def write(path: str, value: str) -> None:
    Path(path).write_text(value, encoding="utf-8")


def replace_once(text: str, old: str, new: str, label: str) -> str:
    if new in text:
        return text
    if old not in text:
        raise SystemExit(f"PATCH_MISSING:{label}")
    return text.replace(old, new, 1)


def patch_backend() -> None:
    p = "backend/server.js"
    s = read(p)
    s = replace_once(
        s,
        "CREATE TABLE IF NOT EXISTS admin_roles(id INTEGER PRIMARY KEY AUTOINCREMENT,user_id INTEGER NOT NULL UNIQUE,role TEXT NOT NULL,admin_code_hash TEXT,created_by INTEGER,created_at TEXT NOT NULL DEFAULT CURRENT_TIMESTAMP,FOREIGN KEY(user_id) REFERENCES users(id) ON DELETE CASCADE);CREATE INDEX IF NOT EXISTS idx_admin_roles_role ON admin_roles(role);",
        "CREATE TABLE IF NOT EXISTS admin_roles(id INTEGER PRIMARY KEY AUTOINCREMENT,user_id INTEGER NOT NULL UNIQUE,role TEXT NOT NULL,admin_code_hash TEXT,created_by INTEGER,created_at TEXT NOT NULL DEFAULT CURRENT_TIMESTAMP,expires_at TEXT,FOREIGN KEY(user_id) REFERENCES users(id) ON DELETE CASCADE);CREATE INDEX IF NOT EXISTS idx_admin_roles_role ON admin_roles(role);",
        "admin_roles expiry schema",
    )
    if "ensureColumn('admin_roles','expires_at','TEXT')" not in s:
        s = replace_once(
            s,
            "CREATE INDEX IF NOT EXISTS idx_admin_roles_role ON admin_roles(role);",
            "CREATE INDEX IF NOT EXISTS idx_admin_roles_role ON admin_roles(role);ensureColumn('admin_roles','expires_at','TEXT');",
            "admin_roles expiry migration",
        )
    s = replace_once(
        s,
        "function roleOf(userId){if(!userId)return null;const owner=db.prepare('SELECT id FROM users WHERE wethaq_id=?').get(OWNER_WETHAQ_ID);if(owner&&Number(userId)===Number(owner.id))return ROLE_FOUNDER;return db.prepare('SELECT role FROM admin_roles WHERE user_id=?').get(Number(userId))?.role||null}",
        "function roleOf(userId){if(!userId)return null;const owner=db.prepare('SELECT id FROM users WHERE wethaq_id=?').get(OWNER_WETHAQ_ID);if(owner&&Number(userId)===Number(owner.id))return ROLE_FOUNDER;const row=db.prepare('SELECT role,expires_at FROM admin_roles WHERE user_id=?').get(Number(userId));if(!row)return null;if(row.expires_at&&new Date(row.expires_at)<=new Date())return null;return row.role||null}",
        "role expiry enforcement",
    )
    if "function resolveUser(value)" not in s:
        s = replace_once(
            s,
            "function findUser(id){return db.prepare('SELECT id,wethaq_id,name,birth_year FROM users WHERE wethaq_id=?').get(String(id||'').trim())}",
            "function findUser(id){return db.prepare('SELECT id,wethaq_id,name,birth_year FROM users WHERE wethaq_id=?').get(String(id||'').trim())}\nfunction resolveUser(value){const q=String(value||'').trim();if(!q)return null;return findUser(q)||db.prepare('SELECT id,wethaq_id,name,birth_year FROM users WHERE name=? COLLATE NOCASE').get(q)}",
            "admin unified user lookup",
        )
    s = replace_once(
        s,
        "const adminCredential=db.prepare('SELECT role,admin_code_hash FROM admin_roles WHERE user_id=?').get(u.id);const personalMatches=!!u.personal_code_hash&&u.personal_code_hash===personalCodeHash;const adminMatches=!!adminCredential?.admin_code_hash&&adminCredential.admin_code_hash===personalCodeHash;",
        "const adminCredential=db.prepare('SELECT role,admin_code_hash,expires_at FROM admin_roles WHERE user_id=?').get(u.id);const adminActive=!!adminCredential&&(!adminCredential.expires_at||new Date(adminCredential.expires_at)>new Date());const personalMatches=!!u.personal_code_hash&&u.personal_code_hash===personalCodeHash;const adminMatches=adminActive&&!!adminCredential?.admin_code_hash&&adminCredential.admin_code_hash===personalCodeHash;",
        "expired admin credential login",
    )
    s = replace_once(
        s,
        "const rows=db.prepare('SELECT ar.role,u.id,u.wethaq_id,u.name,u.birth_year FROM admin_roles ar JOIN users u ON u.id=ar.user_id ORDER BY ar.id').all();",
        "const rows=db.prepare('SELECT ar.role,ar.expires_at,u.id,u.wethaq_id,u.name,u.birth_year FROM admin_roles ar JOIN users u ON u.id=ar.user_id ORDER BY ar.id').all().filter(x=>!x.expires_at||new Date(x.expires_at)>new Date());",
        "admin structure expiry",
    )
    s = replace_once(
        s,
        "const code=newAdminCode();db.prepare('INSERT INTO admin_roles(user_id,role,admin_code_hash,created_by) VALUES(?,?,?,?)').run(target.id,role,hashCode(code),req.user.sub);",
        "const code=newAdminCode(),expiresAt=new Date(Date.now()+365*24*60*60*1000).toISOString();db.prepare('INSERT INTO admin_roles(user_id,role,admin_code_hash,created_by,expires_at) VALUES(?,?,?,?,?)').run(target.id,role,hashCode(code),req.user.sub,expiresAt);",
        "admin role expiry assignment",
    )
    s = replace_once(
        s,
        "res.status(201).json({ok:true,user:target,role,role_label:roleLabel(role),admin_code:code})",
        "res.status(201).json({ok:true,user:target,role,role_label:roleLabel(role),admin_code:code,expires_at:expiresAt,period:'سنة واحدة',price_usd:role==='deputy1'?20:role==='deputy2'?17:role==='deputy3'?12:8})",
        "admin role response",
    )
    s = replace_once(
        s,
        "const code=String(req.body?.code||'').trim().toUpperCase(),row=db.prepare('SELECT role,admin_code_hash FROM admin_roles WHERE user_id=?').get(req.user.sub);if(!row||!code||hashCode(code)!==row.admin_code_hash)return res.status(403).json({error:'invalid_admin_code'});res.json({ok:true,role:row.role,role_label:roleLabel(row.role)})",
        "const code=String(req.body?.code||'').trim().toUpperCase(),row=db.prepare('SELECT role,admin_code_hash,expires_at FROM admin_roles WHERE user_id=?').get(req.user.sub);if(!row||!roleOf(req.user.sub)||!code||hashCode(code)!==row.admin_code_hash)return res.status(403).json({error:'invalid_admin_code'});res.json({ok:true,role:row.role,role_label:roleLabel(row.role),expires_at:row.expires_at||null})",
        "admin code expiry verification",
    )
    # The administration console search accepts either name or Wethaq ID.
    s = s.replace("const q=safeName(req.query.q||'');const rows=q?db.prepare('SELECT id,wethaq_id,name,birth_year,last_seen FROM users WHERE wethaq_id LIKE ? OR name LIKE ? ORDER BY id DESC LIMIT 50').all(`%${q}%`,`%${q}%`):", "const q=safeName(req.query.q||'');const rows=q?db.prepare('SELECT id,wethaq_id,name,birth_year,last_seen FROM users WHERE wethaq_id LIKE ? OR name LIKE ? ORDER BY id DESC LIMIT 50').all(`%${q}%`,`%${q}%`):", 1)
    write(p, s)


def patch_gradle() -> None:
    p = "app/build.gradle"
    s = read(p)
    old = """buildTypes {\n        release {\n            minifyEnabled false\n            shrinkResources false\n            signingConfig signingConfigs.debug\n        }\n    }"""
    new = """signingConfigs {\n        release {\n            def ks = System.getenv('WETHAQ_KEYSTORE_PATH')\n            if (ks != null && new File(ks).exists()) {\n                storeFile file(ks)\n                storePassword System.getenv('WETHAQ_KEYSTORE_PASSWORD')\n                keyAlias System.getenv('WETHAQ_KEY_ALIAS')\n                keyPassword System.getenv('WETHAQ_KEY_PASSWORD')\n            }\n        }\n    }\n\n    buildTypes {\n        release {\n            minifyEnabled false\n            shrinkResources false\n            if (System.getenv('WETHAQ_KEYSTORE_PATH') != null) {\n                signingConfig signingConfigs.release\n            }\n        }\n    }"""
    s = replace_once(s, old, new, "remove debug signing from release")
    write(p, s)


def patch_service() -> None:
    p = "app/src/main/java/com/wethaq/app/WethaqMessageService.java"
    s = read(p)
    s = replace_once(
        s,
        "private boolean stopping;",
        "private boolean stopping;private final Handler reconnectHandler=new Handler(Looper.getMainLooper());private long reconnectDelayMs=3000;",
        "service reconnect state",
    )
    old = "private void scheduleReconnect(){if(stopping)return;new Handler(Looper.getMainLooper()).postDelayed(()->{if(!stopping&&socket==null)connect();},3000);}"
    new = "private void scheduleReconnect(){if(stopping)return;reconnectHandler.removeCallbacksAndMessages(null);long delay=reconnectDelayMs;reconnectDelayMs=Math.min(reconnectDelayMs*2,60000);reconnectHandler.postDelayed(()->{if(!stopping&&socket==null)connect();},delay);}"
    s = replace_once(s, old, new, "bounded websocket reconnect")
    s = replace_once(s, "@Override public void onOpen(WebSocket w,Response x){update(\"متصل — استقبال الرسائل والمكالمات فعال\");}", "@Override public void onOpen(WebSocket w,Response x){reconnectDelayMs=3000;update(\"متصل — استقبال الرسائل والمكالمات فعال\");}", "reset reconnect backoff")
    s = replace_once(s, "@Override public void onDestroy(){stopping=true;if(socket!=null){socket.close(1000,\"service stopped\");socket=null;}if(client!=null)client.dispatcher().executorService().shutdown();super.onDestroy();}", "@Override public void onDestroy(){stopping=true;reconnectHandler.removeCallbacksAndMessages(null);if(socket!=null){socket.close(1000,\"service stopped\");socket=null;}if(client!=null)client.dispatcher().executorService().shutdown();super.onDestroy();}", "cancel reconnect callbacks")
    write(p, s)


if __name__ == "__main__":
    patch_backend()
    patch_gradle()
    patch_service()
    print("FINAL_GATE_REPAIR_OK")
