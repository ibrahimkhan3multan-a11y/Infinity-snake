'use strict';
/**
 * INFINITY SNAKE relay server. Zero dependencies (Node 18+).
 *
 * The game itself runs on the room host's phone. This server only hands out room codes and passes
 * messages between the host and the guests of a room, so it stays tiny and cheap to host.
 *
 * Client -> server messages (JSON text frames):
 *   {t:"host", name, public?}        create a room            -> {t:"hosted", code}
 *   {t:"join", code, name}           join a room by code      -> {t:"joined", id, code, hostName}
 *   {t:"quick", name}                join/create a public room (hosted or joined reply)
 *   {t:"m", to?, m}                  relay payload m. Guests send to the host; the host sets
 *                                    to: <guest id> or to: "all".
 * Server -> client messages:
 *   {t:"m", from?, m}               relayed payload (host receives from: <guest id>)
 *   {t:"peer", id, name, join}      (host only) a guest joined or left
 *   {t:"closed"}                    (guests) the host left, room is gone
 *   {t:"error", msg}
 */
const http = require('http');
const crypto = require('crypto');

const MAX_PLAYERS = 8; // host + 7 guests
const MAX_MESSAGE = 256 * 1024;
const CODE_CHARS = 'ABCDEFGHJKLMNPQRSTUVWXYZ23456789';
const GUID = '258EAFA5-E914-47DA-95CA-C5AB0DC85B11';

// ---------------------------------------------------------------- minimal WebSocket framing

function encodeFrame(opcode, payload) {
  const len = payload.length;
  let header;
  if (len < 126) {
    header = Buffer.from([0x80 | opcode, len]);
  } else if (len < 65536) {
    header = Buffer.alloc(4);
    header[0] = 0x80 | opcode;
    header[1] = 126;
    header.writeUInt16BE(len, 2);
  } else {
    header = Buffer.alloc(10);
    header[0] = 0x80 | opcode;
    header[1] = 127;
    header.writeBigUInt64BE(BigInt(len), 2);
  }
  return Buffer.concat([header, payload]);
}

class Connection {
  constructor(socket, onMessage, onClose) {
    this.socket = socket;
    this.onMessage = onMessage;
    this.onClose = onClose;
    this.buf = Buffer.alloc(0);
    this.fragments = [];
    this.fragOpcode = 0;
    this.closed = false;
    this.alive = true;
    socket.on('data', (d) => this.onData(d));
    socket.on('close', () => this.finish());
    socket.on('error', () => this.finish());
  }

  finish() {
    if (this.closed) return;
    this.closed = true;
    this.onClose();
  }

  onData(chunk) {
    this.buf = this.buf.length ? Buffer.concat([this.buf, chunk]) : chunk;
    for (;;) {
      if (this.buf.length < 2) return;
      const b0 = this.buf[0];
      const b1 = this.buf[1];
      const fin = (b0 & 0x80) !== 0;
      const opcode = b0 & 0x0f;
      const masked = (b1 & 0x80) !== 0;
      let len = b1 & 0x7f;
      let off = 2;
      if (len === 126) {
        if (this.buf.length < 4) return;
        len = this.buf.readUInt16BE(2);
        off = 4;
      } else if (len === 127) {
        if (this.buf.length < 10) return;
        const big = this.buf.readBigUInt64BE(2);
        if (big > BigInt(MAX_MESSAGE)) return this.kill();
        len = Number(big);
        off = 10;
      }
      if (!masked || len > MAX_MESSAGE) return this.kill(); // clients must mask
      if (this.buf.length < off + 4 + len) return;
      const mask = this.buf.subarray(off, off + 4);
      const data = Buffer.from(this.buf.subarray(off + 4, off + 4 + len));
      for (let i = 0; i < data.length; i++) data[i] ^= mask[i & 3];
      this.buf = this.buf.subarray(off + 4 + len);

      if (opcode === 0x8) { // close
        this.send(0x8, data.subarray(0, 2));
        this.socket.end();
        return this.finish();
      } else if (opcode === 0x9) { // ping
        this.send(0xA, data);
      } else if (opcode === 0xA) { // pong
        this.alive = true;
      } else if (opcode === 0x1 || opcode === 0x2 || opcode === 0x0) {
        if (opcode !== 0x0) {
          this.fragments = [];
          this.fragOpcode = opcode;
        }
        this.fragments.push(data);
        if (this.fragments.reduce((a, f) => a + f.length, 0) > MAX_MESSAGE) return this.kill();
        if (fin) {
          const whole = Buffer.concat(this.fragments);
          this.fragments = [];
          if (this.fragOpcode === 0x1) this.onMessage(whole.toString('utf8'));
        }
      }
    }
  }

  send(opcode, payload) {
    if (this.closed || this.socket.destroyed) return;
    this.socket.write(encodeFrame(opcode, payload));
  }

  sendText(text) {
    this.send(0x1, Buffer.from(text, 'utf8'));
  }

  kill() {
    this.socket.destroy();
    this.finish();
  }
}

// ---------------------------------------------------------------- rooms

function createRelay() {
  const rooms = new Map(); // code -> { code, host, guests: Map<id, client>, nextId, isPublic }

  function newCode() {
    for (;;) {
      let c = '';
      for (let i = 0; i < 4; i++) c += CODE_CHARS[crypto.randomInt(CODE_CHARS.length)];
      if (!rooms.has(c)) return c;
    }
  }

  function cleanName(n) {
    return String(n || 'Player').replace(/[^\w \-.]/g, '').trim().slice(0, 14) || 'Player';
  }

  function send(client, obj) {
    client.conn.sendText(JSON.stringify(obj));
  }

  function leave(client) {
    const room = client.room;
    if (!room) return;
    client.room = null;
    if (room.host === client) {
      rooms.delete(room.code);
      for (const g of room.guests.values()) {
        g.room = null;
        send(g, { t: 'closed' });
      }
    } else {
      room.guests.delete(client.id);
      send(room.host, { t: 'peer', id: client.id, name: client.name, join: false });
    }
  }

  function createRoom(client, name, isPublic) {
    const room = { code: newCode(), host: client, guests: new Map(), nextId: 1, isPublic: !!isPublic };
    rooms.set(room.code, room);
    client.room = room;
    client.id = 0;
    client.name = name;
    send(client, { t: 'hosted', code: room.code });
  }

  function joinRoom(client, room, name) {
    if (room.guests.size + 1 >= MAX_PLAYERS) return send(client, { t: 'error', msg: 'Room is full' });
    client.room = room;
    client.id = room.nextId++;
    client.name = name;
    room.guests.set(client.id, client);
    send(client, { t: 'joined', id: client.id, code: room.code, hostName: room.host.name });
    send(room.host, { t: 'peer', id: client.id, name, join: true });
  }

  function handle(client, text) {
    let msg;
    try {
      msg = JSON.parse(text);
    } catch (e) {
      return;
    }
    if (!msg || typeof msg !== 'object') return;
    switch (msg.t) {
      case 'host':
        if (client.room) return send(client, { t: 'error', msg: 'Already in a room' });
        return createRoom(client, cleanName(msg.name), msg.public);
      case 'join': {
        if (client.room) return send(client, { t: 'error', msg: 'Already in a room' });
        const room = rooms.get(String(msg.code || '').toUpperCase().trim());
        if (!room) return send(client, { t: 'error', msg: 'No room with that code' });
        return joinRoom(client, room, cleanName(msg.name));
      }
      case 'quick': {
        if (client.room) return send(client, { t: 'error', msg: 'Already in a room' });
        const name = cleanName(msg.name);
        for (const room of rooms.values()) {
          if (room.isPublic && room.guests.size + 1 < MAX_PLAYERS) return joinRoom(client, room, name);
        }
        return createRoom(client, name, true);
      }
      case 'm': {
        const room = client.room;
        if (!room) return;
        if (room.host === client) {
          if (msg.to === 'all') {
            const out = JSON.stringify({ t: 'm', m: msg.m });
            for (const g of room.guests.values()) g.conn.sendText(out);
          } else {
            const g = room.guests.get(msg.to);
            if (g) send(g, { t: 'm', m: msg.m });
          }
        } else {
          send(room.host, { t: 'm', from: client.id, m: msg.m });
        }
        return;
      }
      default:
    }
  }

  const server = http.createServer((req, res) => {
    if (req.url === '/health') {
      res.writeHead(200, { 'Content-Type': 'application/json' });
      res.end(JSON.stringify({ ok: true, rooms: rooms.size }));
    } else {
      res.writeHead(200, { 'Content-Type': 'text/plain' });
      res.end('Infinity Snake relay is running.\n');
    }
  });

  const conns = new Set();
  server.on('upgrade', (req, socket) => {
    const key = req.headers['sec-websocket-key'];
    if (!key || String(req.headers.upgrade).toLowerCase() !== 'websocket') {
      socket.destroy();
      return;
    }
    const accept = crypto.createHash('sha1').update(key + GUID).digest('base64');
    socket.write(
      'HTTP/1.1 101 Switching Protocols\r\nUpgrade: websocket\r\nConnection: Upgrade\r\n' +
        `Sec-WebSocket-Accept: ${accept}\r\n\r\n`,
    );
    socket.setNoDelay(true);
    const client = { room: null, id: -1, name: '', conn: null };
    client.conn = new Connection(
      socket,
      (text) => handle(client, text),
      () => {
        conns.delete(client);
        leave(client);
      },
    );
    conns.add(client);
  });

  const heartbeat = setInterval(() => {
    for (const c of conns) {
      if (!c.conn.alive) {
        c.conn.kill();
        continue;
      }
      c.conn.alive = false;
      c.conn.send(0x9, Buffer.alloc(0));
    }
  }, 25000);
  heartbeat.unref();

  server.on('close', () => clearInterval(heartbeat));
  return { server, rooms };
}

module.exports = { createRelay, MAX_PLAYERS };

if (require.main === module) {
  const port = Number(process.env.PORT) || 8080;
  const { server } = createRelay();
  server.listen(port, () => console.log(`Infinity Snake relay listening on :${port}`));
}
