const BASE = import.meta.env.VITE_API_URL || 'http://localhost:8080';

export function newKey(prefix) {
  return `${prefix}-${crypto.randomUUID()}`;
}

export async function api(path, { token, method = 'GET', body, headers = {} } = {}) {
  const res = await fetch(`${BASE}${path}`, {
    method,
    headers: {
      'Content-Type': 'application/json',
      ...(token ? { Authorization: `Bearer ${token}` } : {}),
      ...headers,
    },
    body: body ? JSON.stringify(body) : undefined,
  });
  const text = await res.text();
  const json = text ? JSON.parse(text) : null;
  if (!res.ok) {
    throw new Error(json?.message || `Request failed (${res.status})`);
  }
  return json?.data;
}

export const rupees = (minor) => `₹${(minor / 100).toFixed(2)}`;
