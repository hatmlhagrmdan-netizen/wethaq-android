const base = `http://127.0.0.1:${process.env.PORT || 3100}`;
const assert = (condition, message) => { if (!condition) throw new Error(message); };
const request = async (path, options = {}) => {
  const response = await fetch(base + path, { ...options, headers: { 'content-type': 'application/json', ...(options.headers || {}) } });
  const text = await response.text();
  let body = {};
  try { body = JSON.parse(text); } catch {}
  return { response, body };
};

const health = await request('/health');
assert(health.response.ok && health.body.ok === true, 'health failed');

const suffix = Date.now();
const a = await request('/api/identity', { method: 'POST', body: JSON.stringify({ name: `اختبار وثاق ${suffix}`, birthYear: 1995, deviceKey: `aaaaaaaaaaaaaaaaaaaaaaaa${suffix}` }) });
assert(a.response.status === 201 && a.body.token && a.body.user?.wethaq_id, 'identity failed');
const b = await request('/api/identity', { method: 'POST', body: JSON.stringify({ name: `مستخدم وثاق ${suffix}`, birthYear: 1996, deviceKey: `bbbbbbbbbbbbbbbbbbbbbbbb${suffix}` }) });
assert(b.response.status === 201 && b.body.token && b.body.user?.wethaq_id, 'second identity failed');

const search = await request(`/api/search?q=${encodeURIComponent(`اختبار وثاق ${suffix}`)}`);
assert(search.response.ok && search.body.users?.some(u => u.wethaq_id === a.body.user.wethaq_id), 'public search failed');

const me = await request('/api/me', { headers: { authorization: `Bearer ${a.body.token}` } });
assert(me.response.ok && me.body.user?.wethaq_id === a.body.user.wethaq_id, 'me failed');

const contact = await request('/api/contacts', { method: 'POST', headers: { authorization: `Bearer ${a.body.token}` }, body: JSON.stringify({ wethaqId: b.body.user.wethaq_id }) });
assert(contact.response.status === 201, 'contact failed');

const sent = await request('/api/messages', { method: 'POST', headers: { authorization: `Bearer ${a.body.token}` }, body: JSON.stringify({ to: b.body.user.wethaq_id, body: 'رسالة اختبار من وثاق' }) });
assert(sent.response.status === 201 && sent.body.message?.body === 'رسالة اختبار من وثاق', 'message send failed');

const history = await request(`/api/messages/${encodeURIComponent(b.body.user.wethaq_id)}`, { headers: { authorization: `Bearer ${a.body.token}` } });
assert(history.response.ok && history.body.messages?.some(m => m.id === sent.body.message.id), 'message history failed');

console.log('WETHAQ_SMOKE_TEST_OK');
