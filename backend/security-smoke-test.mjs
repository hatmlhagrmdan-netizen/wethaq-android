const base = `http://127.0.0.1:${process.env.PORT || 3100}`;
const assert = (condition, message) => { if (!condition) throw new Error(message); };
const request = async (path, options = {}) => {
  const response = await fetch(base + path, { ...options, headers: { 'content-type': 'application/json', ...(options.headers || {}) } });
  const text = await response.text();
  let body = {};
  try { body = JSON.parse(text); } catch {}
  return { response, body };
};

const unauthenticated = await request('/api/me');
assert(unauthenticated.response.status === 401, 'unauthenticated API access was not rejected');

const invalidToken = await request('/api/me', { headers: { authorization: 'Bearer definitely-invalid-token' } });
assert(invalidToken.response.status === 401, 'invalid JWT was not rejected');

const suffix = Date.now();
const a = await request('/api/identity', { method: 'POST', body: JSON.stringify({ name: `أمان وثاق ${suffix}`, birthYear: 1995, deviceKey: `aaaaaaaaaaaaaaaaaaaaaaaa${suffix}` }) });
assert(a.response.status === 201 && a.body.token && a.body.user?.wethaq_id, 'security smoke identity failed');
const token = a.body.token;
const id = a.body.user.wethaq_id;

const selfMessage = await request('/api/messages', {
  method: 'POST',
  headers: { authorization: `Bearer ${token}` },
  body: JSON.stringify({ to: id, body: 'self-message-should-fail' })
});
assert(selfMessage.response.status === 400, 'self-message was not rejected');

const invalidIdentity = await request('/api/identity', {
  method: 'POST',
  body: JSON.stringify({ name: 'اسم', birthYear: 1995, deviceKey: 'short' })
});
assert(invalidIdentity.response.status === 400, 'invalid identity input was not rejected');

const oversizedMessage = await request('/api/messages', {
  method: 'POST',
  headers: { authorization: `Bearer ${token}` },
  body: JSON.stringify({ to: id, body: 'x'.repeat(4001) })
});
assert(oversizedMessage.response.status === 400, 'oversized message was not rejected');

console.log('WETHAQ_SECURITY_SMOKE_OK');
