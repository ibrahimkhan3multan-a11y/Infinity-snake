# Infinity Snake relay server

Online rooms need one small server. **It does not run the game.** The game runs on the phone of
whoever creates the room (the *host*); this server only hands out room codes and passes messages
between the host and the guests. That keeps it tiny: zero dependencies, about 300 lines, free-tier friendly.

```
guest phone  <--->  relay (this folder)  <--->  host phone (runs the arena)
```

## Run it

Needs Node 18 or newer. No `npm install`.

```bash
cd server
node relay.js          # listens on port 8080 (or $PORT)
npm test               # protocol and room tests
```

Check it: open `http://<server>:8080/health`, it answers `{"ok":true,...}`.

## Use it from the game

Main menu > **ONLINE**, type the server address, then:

| Button | What it does |
| --- | --- |
| CREATE ROOM | Makes a private room and shows a 4-letter code in the game. Friends type it under JOIN. Optional bots keep it lively. |
| JOIN | Enter a friend's code. |
| QUICK MATCH | Joins any open public room, or starts one that others can find. |

Rooms hold up to 8 players. Everyone starts at the same size. If the host leaves, the room closes.

Address forms that work: `192.168.1.20:8080`, `my-relay.onrender.com`, `ws://host:8080`, `wss://host`.

## Easiest: Render from a phone (no computer needed)

1. In your phone's browser open render.com and sign up with your GitHub account.
2. Tap **New +**, then **Blueprint**, and choose the `Infinity-snake` repository (the `render.yaml` in the project root sets everything up).
3. Tap **Apply** / **Deploy** and wait a few minutes until the service says **Live**.
4. Copy the address shown at the top of the service page (like `infinity-snake-relay.onrender.com`).
5. In the game: ONLINE, paste it into **Server address**, then Create Room. Friends enter the same address and your 4-letter code.

The free plan sleeps when idle, so the first connection after a break may take about 30 seconds.

## Where to host it

Pick whichever is easiest; the relay is just a Node process that listens on `$PORT`.

* **Same Wi-Fi, no hosting (quickest test):** run `node relay.js` on a PC and enter that PC's LAN
  address, e.g. `192.168.1.20:8080`. All phones must be on that Wi-Fi.
* **Friends anywhere, no account:** run it on your PC and expose it with a tunnel such as
  `cloudflared tunnel --url http://localhost:8080` or `ngrok http 8080`. Use the `https://...` address it prints.
* **Always on:** deploy this `server/` folder to Render, Railway or Fly.io as a Node web service
  (start command `node relay.js`; a `Procfile` is included). Use the `https://` address they give you.
  Free tiers may sleep when idle, so the first connection after a break can take ~30 s.

## How it behaves

* Messages are small JSON text frames; one room is a host plus up to 7 guests.
* Phones are not trusted-checked: this is meant for friends and casual play, not tournaments.
  The host's phone is the referee, so a modified host could cheat.
* Traffic is plain `ws://` unless you put it behind HTTPS (hosting platforms and tunnels do that for you).
* The server keeps nothing on disk and logs nothing about players.
