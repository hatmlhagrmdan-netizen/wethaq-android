from pathlib import Path
import re

p = Path('app/src/main/java/com/wethaq/app/MainActivity.java')
s = p.read_text(encoding='utf-8')

patterns = [
    (r'n\.split\("\\s\+"\)', r'n.split("\\\\s+")'),
    (r'y\.matches\("\\d\{4\}"\)', r'y.matches("\\\\d{4}")'),
    (r'code\.matches\("\\d\{6,12\}"\)', r'code.matches("\\\\d{6,12}")'),
]
for pat, repl in patterns:
    s = re.sub(pat, repl, s)

s = s.replace('private void login(){base("وَثاق");', 'private void login(){base("مدير ومؤسس وثاق حاتم حسين الحاج رمضان");', 1)
needle = 'menu("⚠  الشكاوى والتواصل مع المؤسس",this::sendComplaint);'
if 'menu("👥  الإدارة"' not in s and needle in s:
    s = s.replace(needle, needle + 'menu("👥  الإدارة",()->startActivity(new Intent(this,PublicAdministrationActivity.class)));', 1)

if re.search(r'n\.split\("\\s\+"\)|y\.matches\("\\d\{4\}"\)|code\.matches\("\\d\{6,12\}"\)', s):
    raise SystemExit('JAVA8_REGEX_REPAIR_INCOMPLETE')
if 'base("مدير ومؤسس وثاق حاتم حسين الحاج رمضان")' not in s:
    raise SystemExit('LOGIN_TITLE_REPAIR_INCOMPLETE')
if 'menu("👥  الإدارة"' not in s:
    raise SystemExit('PUBLIC_ADMIN_MENU_REPAIR_INCOMPLETE')

p.write_text(s, encoding='utf-8')
print('JAVA8_REGEX_V2_OK')
