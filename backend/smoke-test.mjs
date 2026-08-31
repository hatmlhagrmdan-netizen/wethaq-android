const base = `http://127.0.0.1:${process.env.PORT || 3100}`;
const assert = (condition, message) => { if (!condition) throw new Error(message); };
const request = async (path, options = {}) => {
  const response = await fetch(base + path, { ...options, headers: { 'content-type': 'application/json', ...(options.headers || {}) } });
  const text = await response.text();
  let body = {};
  try { body = JSON.parse(text); } catch {}
  return { response, body, text };
};

const health = await request('/health');
assert(health.response.ok && health.body.ok === true, `health failed: ${health.response.status} ${health.text}`);

const suffix = Date.now();
const deviceA = `aaaaaaaaaaaaaaaaaaaaaaaa${suffix}`;
const deviceB = `bbbbbbbbbbbbbbbbbbbbbbbb${suffix}`;
const a = await request('/api/identity', { method: 'POST', body: JSON.stringify({ name: `اختبار وثاق ${suffix}`, birthYear: 1995, deviceKey: deviceA }) });
assert(a.response.status === 201 && a.body.token && a.body.user?.wethaq_id, `identity failed: ${a.response.status} ${a.text}`);
const b = await request('/api/identity', { method: 'POST', body: JSON.stringify({ name: `مستخدم وثاق ${suffix}`, birthYear: 1996, deviceKey: deviceB }) });
assert(b.response.status === 201 && b.body.token && b.body.user?.wethaq_id, `second identity failed: ${b.response.status} ${b.text}`);

const wrongDeviceLogin = await request('/api/login', { method: 'POST', body: JSON.stringify({ name: `اختبار وثاق ${suffix}`, birthYear: 1995, deviceKey: `wrong-device-${suffix}` }) });
assert([401, 403, 409].includes(wrongDeviceLogin.response.status), `login accepted an untrusted device: ${wrongDeviceLogin.response.status}`);

const search = await request(`/api/search?q=${encodeURIComponent(`اختبار وثاق ${suffix}`)}`);
assert(search.response.ok && search.body.users?.some(u => u.wethaq_id === a.body.user.wethaq_id), 'public search failed');

const me = await request('/api/me', { headers: { authorization: `Bearer ${a.body.token}` } });
assert(me.response.ok && me.body.user?.wethaq_id === a.body.user.wethaq_id, 'me failed');

const unauthorizedMe = await request('/api/me');
assert(unauthorizedMe.response.status === 401, `unauthenticated request was accepted: ${unauthorizedMe.response.status}`);

const contact = await request('/api/contacts', { method: 'POST', headers: { authorization: `Bearer ${a.body.token}` }, body: JSON.stringify({ wethaqId: b.body.user.wethaq_id }) });
assert([200, 201].includes(contact.response.status), `contact failed: ${contact.response.status} ${contact.text}`);

const sent = await request('/api/messages', { method: 'POST', headers: { authorization: `Bearer ${a.body.token}` }, body: JSON.stringify({ to: b.body.user.wethaq_id, body: 'رسالة اختبار من وثاق' }) });
assert(sent.response.status === 201 && sent.body.message?.body === 'رسالة اختبار من وثاق', `message send failed: ${sent.response.status} ${sent.text}`);

const history = await request(`/api/messages/${encodeURIComponent(b.body.user.wethaq_id)}`, { headers: { authorization: `Bearer ${a.body.token}` } });
assert(history.response.ok && history.body.messages?.some(m => m.id === sent.body.message.id), 'message history failed');

console.log('WETHAQ_SMOKE_TEST_OK');
