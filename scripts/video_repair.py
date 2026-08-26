from pathlib import Path
p=Path('app/src/main/java/com/wethaq/app/VideoCallActivity.java')
s=p.read_text(encoding='utf-8')
s=s.replace('if("offer".equals(type)&&!isInitiator()){','if("offer".equals(type)&&!isInitiator()&&peer!=null&&!remoteDescriptionSet){')
s=s.replace('}else if("answer".equals(type)&&isInitiator()){','}else if("answer".equals(type)&&isInitiator()&&peer!=null&&!remoteDescriptionSet){')
p.write_text(s,encoding='utf-8')
print('OK')
