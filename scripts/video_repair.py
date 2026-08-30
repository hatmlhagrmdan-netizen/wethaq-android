from pathlib import Path

VIDEO = Path('app/src/main/java/com/wethaq/app/VideoCallActivity.java')
MAIN = Path('app/src/main/java/com/wethaq/app/MainActivity.java')

# Release CI must validate the production WebRTC source, not rewrite it.
# Rewriting generated Java here previously corrupted the video capturer.
for required in (VIDEO, MAIN):
    if not required.is_file():
        raise SystemExit(f'missing required source: {required}')

video = VIDEO.read_text(encoding='utf-8')
main = MAIN.read_text(encoding='utf-8')
for marker in ('incomingOffer', 'createOffer', 'createAnswer', 'onIceCandidate', 'addTrack', 'startCapture'):
    if marker not in video:
        raise SystemExit(f'WebRTC validation failed: {marker}')
for marker in ('startAudioCall', 'startVideoCall', 'audioOnly'):
    if marker not in main:
        raise SystemExit(f'call wiring validation failed: {marker}')
print('WebRTC production source validation: OK')
