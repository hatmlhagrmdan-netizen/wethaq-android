const base = `http://127.0.0.1:${process.env.PORT || 3100}`;
const assert = (condition, message) => { if (!condition) throw new Error(message); };
const request = async (path, options = {}) => {
  const controller = new AbortController();
  const timer = setTimeout(() => controller.abort(), 10000);
  try {
    const response = await fetch(base + path, { ...options, signal: controller.signal, headers: { 'content-type': 'application/json', ...(options.headers || {}) } });
    const text = await response.text();
    let body = {};
    try { body = JSON.parse(text); } catch {}
    return { response, body };
  } finally {
    clearTimeout(timer);
  }
};

const health = await request('/health');
assert(health.response.ok && health.body.ok === true, 'health failed');
assert(health.body.auth === 'name_birth_year_personal_code', 'health auth mode is not personal-code based');

const suffix = Date.now();
const deviceA = `aaaaaaaaaaaaaaaaaaaaaaaa${suffix}`;
const deviceB = `bbbbbbbbbbbbbbbbbbbbbbbb${suffix}`;
const personalCode = '583104';
const a = await request('/api/identity', { method: 'POST', body: JSON.stringify({ name: `اختبار وثاق ${suffix}`, birthYear: 1995, personalCode, deviceKey: deviceA }) });
assert(a.response.status === 201 && a.body.token && a.body.user?.wethaq_id, 'identity failed');
const b = await request('/api/identity', { method: 'POST', body: JSON.stringify({ name: `مستخدم وثاق ${suffix}`, birthYear: 1996, personalCode: '641205', deviceKey: deviceB }) });
assert(b.response.status === 201 && b.body.token && b.body.user?.wethaq_id, 'second identity failed');

const trustedLogin = await request('/api/login', { method: 'POST', body: JSON.stringify({ name: `اختبار وثاق ${suffix}`, birthYear: 1995, personalCode, deviceKey: deviceA }) });
assert(trustedLogin.response.ok && trustedLogin.body.token && trustedLogin.body.user?.wethaq_id === a.body.user.wethaq_id, 'personal-code login failed');

const wrongCode = await request('/api/login', { method: 'POST', body: JSON.stringify({ name: `اختبار وثاق ${suffix}`, birthYear: 1995, personalCode: '583105', deviceKey: deviceA }) });
assert(wrongCode.response.status === 401 && wrongCode.body.error === 'invalid_personal_code', 'wrong personal code was accepted');

const newDeviceLogin = await request('/api/login', { method: 'POST', body: JSON.stringify({ name: `اختبار وثاق ${suffix}`, birthYear: 1995, personalCode, deviceKey: `new-device-${suffix}` }) });
assert(newDeviceLogin.response.ok && newDeviceLogin.body.user?.wethaq_id === a.body.user.wethaq_id, 'cross-device identity login failed');

const search = await request(`/api/search?q=${encodeURIComponent(`اختبار وثاق ${suffix}`)}`);
assert(search.response.ok && search.body.users?.some(u => u.wethaq_id === a.body.user.wethaq_id), 'public search failed');
assert(search.body.users.every(u => !Object.prototype.hasOwnProperty.call(u, 'personal_code_hash')), 'private auth material leaked in search');
assert(search.body.users.every(u => !Object.prototype.hasOwnProperty.call(u, 'birth_year')), 'birth year leaked in public search');

const me = await request('/api/me', { headers: { authorization: `Bearer ${newDeviceLogin.body.token}` } });
assert(me.response.ok && me.body.user?.wethaq_id === a.body.user.wethaq_id, 'me failed');

const invalidToken = await request('/api/me', { headers: { authorization: 'Bearer invalid-token-for-smoke-test' } });
assert(invalidToken.response.status === 401, 'invalid JWT was accepted');

const unauthorizedMe = await request('/api/me');
assert(unauthorizedMe.response.status === 401, 'unauthenticated request was accepted');

const contact = await request('/api/contacts', { method: 'POST', headers: { authorization: `Bearer ${a.body.token}` }, body: JSON.stringify({ wethaqId: b.body.user.wethaq_id }) });
assert(contact.response.status === 201, 'contact failed');

const sent = await request('/api/messages', { method: 'POST', headers: { authorization: `Bearer ${a.body.token}` }, body: JSON.stringify({ to: b.body.user.wethaq_id, body: 'رسالة اختبار من وثاق' }) });
assert(sent.response.status === 201 && sent.body.message?.body === 'رسالة اختبار من وثاق', 'message send failed');

const history = await request(`/api/messages/${encodeURIComponent(b.body.user.wethaq_id)}`, { headers: { authorization: `Bearer ${a.body.token}` } });
assert(history.response.ok && history.body.messages?.some(m => m.id === sent.body.message.id), 'message history failed');

console.log('WETHAQ_SMOKE_TEST_OK');
