'use strict';
const test = require('node:test');
const assert = require('node:assert');
const { createRelay } = require('./relay');

function connect(port) {
  return new Promise((resolve, reject) => {
    const ws = new WebSocket(`ws://127.0.0.1:${port}`);
    const inbox = [];
    const waiters = [];
    ws.onmessage = (e) => {
      const m = JSON.parse(e.data);
      const w = waiters.shift();
      if (w) w(m); else inbox.push(m);
    };
    ws.onerror = reject;
    ws.onopen = () =>
      resolve({
        ws,
        send: (o) => ws.send(JSON.stringify(o)),
        next: () => new Promise((r) => { const m = inbox.shift(); if (m) r(m); else waiters.push(r); }),
        close: () => ws.close(),
      });
  });
}

async function withRelay(fn) {
  const { server, rooms } = createRelay();
  await new Promise((r) => server.listen(0, '127.0.0.1', r));
  try {
    await fn(server.address().port, rooms);
  } finally {
    server.closeAllConnections?.();
    await new Promise((r) => server.close(r));
  }
}

test('host creates a room, guest joins by code and messages flow both ways', () =>
  withRelay(async (port) => {
    const host = await connect(port);
    const guest = await connect(port);
    host.send({ t: 'host', name: 'Alice' });
    const hosted = await host.next();
    assert.strictEqual(hosted.t, 'hosted');
    assert.match(hosted.code, /^[A-Z2-9]{4}$/);

    guest.send({ t: 'join', code: hosted.code.toLowerCase(), name: 'Bob' });
    const joined = await guest.next();
    assert.strictEqual(joined.t, 'joined');
    assert.strictEqual(joined.id, 1);
    assert.strictEqual(joined.hostName, 'Alice');
    assert.deepStrictEqual(await host.next(), { t: 'peer', id: 1, name: 'Bob', join: true });

    guest.send({ t: 'm', m: { a: 1.5 } });
    assert.deepStrictEqual(await host.next(), { t: 'm', from: 1, m: { a: 1.5 } });
    host.send({ t: 'm', to: 1, m: { hello: true } });
    assert.deepStrictEqual(await guest.next(), { t: 'm', m: { hello: true } });
    host.send({ t: 'm', to: 'all', m: 'x' });
    assert.deepStrictEqual(await guest.next(), { t: 'm', m: 'x' });

    guest.close();
    assert.deepStrictEqual(await host.next(), { t: 'peer', id: 1, name: 'Bob', join: false });
    host.close();
  }));

test('unknown code gives an error and closing the host closes the room for guests', () =>
  withRelay(async (port, rooms) => {
    const guest = await connect(port);
    guest.send({ t: 'join', code: 'ZZZZ', name: 'Bob' });
    assert.strictEqual((await guest.next()).t, 'error');

    const host = await connect(port);
    host.send({ t: 'host', name: 'A' });
    const { code } = await host.next();
    guest.send({ t: 'join', code, name: 'Bob' });
    await guest.next();
    await host.next();
    host.close();
    assert.deepStrictEqual(await guest.next(), { t: 'closed' });
    assert.strictEqual(rooms.size, 0);
    guest.close();
  }));

test('room holds at most 8 players', () =>
  withRelay(async (port) => {
    const host = await connect(port);
    host.send({ t: 'host', name: 'H' });
    const { code } = await host.next();
    const guests = [];
    for (let i = 0; i < 7; i++) {
      const g = await connect(port);
      g.send({ t: 'join', code, name: 'G' + i });
      assert.strictEqual((await g.next()).t, 'joined');
      guests.push(g);
    }
    const extra = await connect(port);
    extra.send({ t: 'join', code, name: 'Late' });
    const err = await extra.next();
    assert.strictEqual(err.t, 'error');
    assert.match(err.msg, /full/i);
    [host, extra, ...guests].forEach((c) => c.close());
  }));

test('quick match creates a public room and the next player joins it', () =>
  withRelay(async (port) => {
    const a = await connect(port);
    a.send({ t: 'quick', name: 'A' });
    assert.strictEqual((await a.next()).t, 'hosted');
    const b = await connect(port);
    b.send({ t: 'quick', name: 'B' });
    assert.strictEqual((await b.next()).t, 'joined');
    // A private room is never handed out by quick match.
    const h = await connect(port);
    h.send({ t: 'host', name: 'Priv' });
    await h.next();
    [a, b, h].forEach((c) => c.close());
  }));

test('large messages (60 KB) survive framing', () =>
  withRelay(async (port) => {
    const host = await connect(port);
    host.send({ t: 'host', name: 'H' });
    const { code } = await host.next();
    const guest = await connect(port);
    guest.send({ t: 'join', code, name: 'G' });
    await guest.next();
    await host.next();
    const big = 'x'.repeat(60000);
    host.send({ t: 'm', to: 1, m: big });
    assert.strictEqual((await guest.next()).m, big);
    host.close();
    guest.close();
  }));
