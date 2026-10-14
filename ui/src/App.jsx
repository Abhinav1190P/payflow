import { useCallback, useEffect, useState } from 'react';
import { api, newKey, rupees } from './api.js';

function Login({ onToken }) {
  const [email, setEmail] = useState('');
  const [password, setPassword] = useState('');
  const [error, setError] = useState('');

  const submit = async (mode) => {
    setError('');
    try {
      const data = await api(`/api/v1/auth/${mode}`, { method: 'POST', body: { email, password } });
      onToken(data.accessToken, data.user);
    } catch (e) {
      setError(e.message);
    }
  };

  return (
    <div className="card narrow">
      <h2>Sign in</h2>
      <input placeholder="Email" value={email} onChange={(e) => setEmail(e.target.value)} />
      <input placeholder="Password (8+ chars)" type="password" value={password} onChange={(e) => setPassword(e.target.value)} />
      <div className="row">
        <button onClick={() => submit('login')}>Log in</button>
        <button className="secondary" onClick={() => submit('register')}>Create account</button>
      </div>
      {error && <p className="error">{error}</p>}
    </div>
  );
}

function Wallet({ token }) {
  const [wallet, setWallet] = useState(null);
  const [amount, setAmount] = useState(1000);
  const [error, setError] = useState('');

  const load = useCallback(() => api('/api/v1/wallets/me', { token }).then(setWallet).catch((e) => setError(e.message)), [token]);
  useEffect(() => { load(); }, [load]);

  const topUp = async () => {
    setError('');
    try {
      await api('/api/v1/wallets/me/top-ups', {
        token, method: 'POST', body: { amountMinor: Math.round(amount * 100) },
        headers: { 'Idempotency-Key': newKey('ui-topup') },
      });
      load();
    } catch (e) {
      setError(e.message);
    }
  };

  return (
    <div className="card">
      <h2>Wallet</h2>
      <p className="big">{wallet ? rupees(wallet.balanceMinor) : '…'}</p>
      <div className="row">
        <input type="number" min="1" value={amount} onChange={(e) => setAmount(Number(e.target.value))} />
        <button onClick={topUp}>Top up</button>
      </div>
      {error && <p className="error">{error}</p>}
    </div>
  );
}

function Shop({ token, onOrdered }) {
  const [products, setProducts] = useState([]);
  const [cart, setCart] = useState({});
  const [query, setQuery] = useState('');
  const [suggestions, setSuggestions] = useState([]);
  const [message, setMessage] = useState('');

  useEffect(() => { api('/api/v1/products?size=50').then((p) => setProducts(p.items)); }, []);
  useEffect(() => {
    if (!query.trim()) { setSuggestions([]); return; }
    const t = setTimeout(() => api(`/api/v1/products/autocomplete?q=${encodeURIComponent(query)}`).then(setSuggestions), 150);
    return () => clearTimeout(t);
  }, [query]);

  const add = (id) => setCart((c) => ({ ...c, [id]: (c[id] || 0) + 1 }));
  const items = Object.entries(cart).map(([productId, quantity]) => ({ productId, quantity }));
  const total = items.reduce((sum, i) => sum + i.quantity * (products.find((p) => p.id === i.productId)?.priceMinor || 0), 0);
  const visible = query.trim() ? products.filter((p) => suggestions.includes(p.name)) : products;

  const checkout = async () => {
    setMessage('');
    try {
      const order = await api('/api/v1/orders', {
        token, method: 'POST', body: { items }, headers: { 'Idempotency-Key': newKey('ui-order') },
      });
      setCart({});
      setMessage(`Order ${order.id.slice(0, 8)} placed — status ${order.status}`);
      onOrdered();
    } catch (e) {
      setMessage(e.message);
    }
  };

  return (
    <div className="card">
      <h2>Shop</h2>
      <input placeholder="Search products…" value={query} onChange={(e) => setQuery(e.target.value)} />
      <ul className="products">
        {visible.map((p) => (
          <li key={p.id}>
            <span>{p.name}</span>
            <span className="muted">{rupees(p.priceMinor)} · {p.stock} left</span>
            <button className="small" onClick={() => add(p.id)}>Add</button>
          </li>
        ))}
      </ul>
      <div className="row">
        <strong>Cart: {items.reduce((n, i) => n + i.quantity, 0)} items · {rupees(total)}</strong>
        <button disabled={!items.length} onClick={checkout}>Place order</button>
      </div>
      {message && <p>{message}</p>}
    </div>
  );
}

function Orders({ token, refresh }) {
  const [orders, setOrders] = useState([]);

  useEffect(() => {
    let alive = true;
    const load = () => api('/api/v1/orders?size=10', { token }).then((p) => alive && setOrders(p.items)).catch(() => {});
    load();
    const t = setInterval(load, 2000);
    return () => { alive = false; clearInterval(t); };
  }, [token, refresh]);

  return (
    <div className="card">
      <h2>My orders</h2>
      <table>
        <thead><tr><th>Order</th><th>Status</th><th>Total</th><th>Note</th></tr></thead>
        <tbody>
          {orders.map((o) => (
            <tr key={o.id}>
              <td>{o.id.slice(0, 8)}</td>
              <td><span className={`status ${o.status}`}>{o.status}</span></td>
              <td>{rupees(o.totalMinor)}</td>
              <td className="muted">{o.cancelReason || ''}</td>
            </tr>
          ))}
        </tbody>
      </table>
    </div>
  );
}

export default function App() {
  const [token, setToken] = useState(() => sessionStorage.getItem('payflow.token'));
  const [email, setEmail] = useState(() => sessionStorage.getItem('payflow.email'));
  const [refresh, setRefresh] = useState(0);

  const signIn = (t, user) => {
    sessionStorage.setItem('payflow.token', t);
    sessionStorage.setItem('payflow.email', user.email);
    setToken(t);
    setEmail(user.email);
  };
  const signOut = () => { sessionStorage.clear(); setToken(null); };

  return (
    <main>
      <header>
        <h1>PayFlow</h1>
        {token && <span className="muted">{email} · <button className="link" onClick={signOut}>Sign out</button></span>}
      </header>
      {!token ? <Login onToken={signIn} /> : (
        <div className="grid">
          <Wallet token={token} key={`w${refresh}`} />
          <Shop token={token} onOrdered={() => setRefresh((r) => r + 1)} />
          <Orders token={token} refresh={refresh} />
        </div>
      )}
    </main>
  );
}
