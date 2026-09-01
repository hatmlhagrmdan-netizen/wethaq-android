from pathlib import Path

MAIN = Path('app/src/main/java/com/wethaq/app/MainActivity.java')
VIDEO = Path('app/src/main/java/com/wethaq/app/VideoCallActivity.java')
ADMIN = Path('app/src/main/java/com/wethaq/app/AdminActivity.java')

# Repair only deterministic build-breaking artifacts introduced by layered repairs.
s = MAIN.read_text(encoding='utf-8')
s = s.replace(
    'auth(n.getText().toString().trim(),y.getText().toString().trim(),false);',
    'auth(n.getText().toString().trim(),y.getText().toString().trim(),false,pc.getText().toString().trim());'
)
s = s.replace(
    'auth(n.getText().toString().trim(),y.getText().toString().trim(),true);',
    'auth(n.getText().toString().trim(),y.getText().toString().trim(),true,code);'
)
MAIN.write_text(s, encoding='utf-8')

s = VIDEO.read_text(encoding='utf-8')
s = s.replace('}).start();}', '});}')
VIDEO.write_text(s, encoding='utf-8')

s = ADMIN.read_text(encoding='utf-8')
double = 'private TextView connectionStatus;private TextView connectionStatus;'
s = s.replace(double, 'private TextView connectionStatus;')
ADMIN.write_text(s, encoding='utf-8')

# These invariants must hold before javac starts.
main = MAIN.read_text(encoding='utf-8')
video = VIDEO.read_text(encoding='utf-8')
admin = ADMIN.read_text(encoding='utf-8')
assert 'auth(n.getText().toString().trim(),y.getText().toString().trim(),false);' not in main
assert 'auth(n.getText().toString().trim(),y.getText().toString().trim(),true);' not in main
assert 'private TextView connectionStatus;private TextView connectionStatus;' not in admin
assert '}).start();}' not in video
print('WETHAQ_COMPILE_COMPAT_REPAIR_OK')
