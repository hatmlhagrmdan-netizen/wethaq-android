const base = `http://127.0.0.1:${process.env.PORT || 3100}`;
const assert = (condition, message) => { if (!condition) throw new Error(message); };
const request = async (path, options = {}) => {
  const response = await fetch(base + path, {
    ...options,
    headers: { 'content-type': 'application/json', ...(options.headers || {}) }
  });
  const text = await response.text();
  let body = {};
  try { body = JSON.parse(text); } catch {}
  return { response, body };
};

const unauthenticated = await request('/api/me');
assert(unauthenticated.response.status === 401, 'unauthenticated API access was not rejected');

const invalidToken = await request('/api/me', {
  headers: { authorization: 'Bearer definitely-invalid-token' }
});
assert(invalidToken.response.status === 401, 'invalid JWT was not rejected');

const suffix = Date.now();
const personalCode = '731204';
const identity = await request('/api/identity', {
  method: 'POST',
  body: JSON.stringify({
    name: `أمان وثاق ${suffix}`,
    birthYear: 1995,
    personalCode,
    deviceKey: `aaaaaaaaaaaaaaaaaaaaaaaa${suffix}`
  })
});
assert(identity.response.status === 201 && identity.body.token && identity.body.user?.wethaq_id, 'security smoke identity failed');

const wrongCode = await request('/api/login', {
  method: 'POST',
  body: JSON.stringify({ name: `أمان وثاق ${suffix}`, birthYear: 1995, personalCode: '731205', deviceKey: `bbbbbbbbbbbbbbbbbbbbbbbb${suffix}` })
});
assert(wrongCode.response.status === 401 && wrongCode.body.error === 'invalid_personal_code', 'wrong personal code was accepted');

const token = identity.body.token;
const avatar = await request('/api/profile/avatar', {
  method: 'POST',
  headers: { authorization: `Bearer ${token}` },
  body: JSON.stringify({ imageBase64: 'aGVsbG8=', mimeType: 'image/png' })
});
assert(avatar.response.ok, 'avatar upload failed');
const publicSearch = await request(`/api/search?q=${encodeURIComponent(`أمان وثاق ${suffix}`)}`);
assert(publicSearch.response.ok, 'public search failed after avatar upload');
assert(publicSearch.body.users?.every(user => !Object.prototype.hasOwnProperty.call(user, 'avatar_data')), 'public search leaked avatar payload');
const invalidRecipient = await request('/api/messages', {
  method: 'POST',
  headers: { authorization: `Bearer ${token}` },
  body: JSON.stringify({ to: `Nonexistent_Wethaq_User_${suffix}`, body: 'invalid-recipient-should-fail' })
});
assert(invalidRecipient.response.status === 404, 'invalid message recipient was not rejected');

const oversizedMessage = await request('/api/messages', {
  method: 'POST',
  headers: { authorization: `Bearer ${token}` },
  body: JSON.stringify({ to: `Nonexistent_Wethaq_User_${suffix}`, body: 'x'.repeat(4001) })
});
assert(oversizedMessage.response.status === 400, 'oversized message was not rejected');

for (let i = 0; i < 8; i++) {
  await request('/api/login', {
    method: 'POST',
    body: JSON.stringify({ name: `أمان وثاق ${suffix}`, birthYear: 1995, personalCode: '731205', deviceKey: `brute-${suffix}-${i}` })
  });
}
const limitedLogin = await request('/api/login', {
  method: 'POST',
  body: JSON.stringify({ name: `أمان وثاق ${suffix}`, birthYear: 1995, personalCode: '731205', deviceKey: `brute-final-${suffix}` })
});
assert(limitedLogin.response.status === 429 && limitedLogin.body.error === 'too_many_attempts', 'identity login rate limit was not enforced');

console.log('WETHAQ_SECURITY_SMOKE_OK');
