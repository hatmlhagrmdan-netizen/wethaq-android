from pathlib import Path

p = Path('app/src/main/java/com/wethaq/app/MainActivity.java')
s = p.read_text(encoding='utf-8')

# javac sourceCompatibility is Java 8, so regex backslashes must survive Java string parsing.
s = s.replace('n.split("\\s+")', 'n.split("\\\\s+")')
s = s.replace('y.matches("\\d{4}")', 'y.matches("\\\\d{4}")')
s = s.replace('code.matches("\\d{6,12}")', 'code.matches("\\\\d{6,12}")')

# Exact requested login heading; keep the security subtitle below it.
s = s.replace('private void login(){base("وَثاق");', 'private void login(){base("مدير ومؤسس وثاق حاتم حسين الحاج رمضان");', 1)

# Make the public administration directory reachable from every normal session.
needle = 'menu("⚠  الشكاوى والتواصل مع المؤسس",this::sendComplaint);'
replacement = needle + 'menu("👥  الإدارة",()->startActivity(new Intent(this,PublicAdministrationActivity.class)));'
if 'menu("👥  الإدارة"' not in s:
    if needle not in s:
        raise SystemExit('HOME_ADMIN_MENU_ANCHOR_NOT_FOUND')
    s = s.replace(needle, replacement, 1)

p.write_text(s, encoding='utf-8')
print('JAVA8_REGEX_AND_UI_FIXED')
