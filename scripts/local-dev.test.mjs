import assert from 'node:assert/strict';
import net from 'node:net';
import { mkdtempSync, rmSync, writeFileSync } from 'node:fs';
import { tmpdir } from 'node:os';
import path from 'node:path';
import test from 'node:test';
import { assertPortsFree, assertSetupIdle, portsFrom, servicePlans, supervise } from './local-dev.mjs';

async function listen() {
  const server = net.createServer();
  await new Promise((resolve) => server.listen(0, '127.0.0.1', resolve));
  return server;
}
async function freePort() {
  const server = await listen();
  const port = server.address().port;
  await new Promise((resolve) => server.close(resolve));
  return port;
}
function fixture(port, code = '') {
  return { name: 'fixture', command: process.execPath,
    args: ['-e', code || `require('http').createServer((q,s)=>s.end('ok')).listen(${port},'127.0.0.1')`],
    url: `http://127.0.0.1:${port}` };
}

test('occupied ports fail without closing the existing listener', async () => {
  const server = await listen();
  try {
    await assert.rejects(assertPortsFree([server.address().port]), /사용 중/);
    assert.equal(server.listening, true);
  } finally { server.close(); }
});

test('an active install blocks startup before any service is launched', () => {
  const directory = mkdtempSync(path.join(tmpdir(), 'ducky-install-'));
  const lock = path.join(directory, 'setup.lock');
  try {
    writeFileSync(lock, JSON.stringify({ pid: process.pid }));
    assert.throws(() => assertSetupIdle(lock), /설치가 진행 중/);
  } finally {
    assert.equal(path.dirname(directory), path.resolve(tmpdir()));
    assert.ok(path.basename(directory).startsWith('ducky-install-'));
    rmSync(directory, { recursive: true });
  }
});

test('port overrides reject invalid and duplicate values; local plans override inherited cloud/DB', () => {
  assert.throws(() => portsFrom(['--api-port', 'abc']));
  assert.throws(() => portsFrom(['--web-port', '18080']));
  const plans = servicePlans(portsFrom(['--api-port', '18100']));
  assert.ok(plans[1].args.some((arg) => arg.includes('--document.mode=local') && arg.includes('jdbc:h2:file:../.runtime/local-dev/18100/db')));
  assert.equal(plans[2].env.NEXT_PUBLIC_API_BASE_URL, 'http://127.0.0.1:18100');
});

test('abort after readiness stops only owned children, preserving unrelated server', async () => {
  const unrelated = await listen();
  const port = await freePort();
  const controller = new AbortController();
  try {
    await supervise([fixture(port)], { signal: controller.signal, timeoutMs: 5000, log: () => {},
      onReady: async () => { assert.equal((await fetch(`http://127.0.0.1:${port}`)).status, 200); controller.abort(); } });
    await assertPortsFree([port]);
    assert.equal(unrelated.listening, true);
  } finally { controller.abort(); unrelated.close(); }
});

test('failure starting a later child cleans up an already ready child', async () => {
  const port = await freePort();
  const crashPort = await freePort();
  const controller = new AbortController();
  try {
    await assert.rejects(supervise([fixture(port), fixture(crashPort, 'process.exit(7)')],
      { signal: controller.signal, timeoutMs: 2000, log: () => {} }), /종료\(7\)/);
    await assertPortsFree([port]);
  } finally { controller.abort(); }
});

test('readiness timeout cleans up the process that never opened its port', async () => {
  const port = await freePort();
  const controller = new AbortController();
  try {
    await assert.rejects(supervise([fixture(port, 'setInterval(()=>{},1000)')],
      { signal: controller.signal, timeoutMs: 400, log: () => {} }), /준비 시간 초과/);
    await assertPortsFree([port]);
  } finally { controller.abort(); }
});
