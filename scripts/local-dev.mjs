import { spawn, spawnSync } from 'node:child_process';
import { existsSync, mkdirSync, openSync, closeSync, readFileSync, writeFileSync, unlinkSync } from 'node:fs';
import net from 'node:net';
import path from 'node:path';
import { setTimeout as delay } from 'node:timers/promises';
import { fileURLToPath } from 'node:url';

export const root = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '..');
const windows = process.platform === 'win32';
const python = path.join(root, '.venv-rag', windows ? 'Scripts/python.exe' : 'bin/python');
const setupLock = path.join(root, '.runtime/local-dev/setup.lock');
const java = process.env.JAVA_HOME
  ? path.join(process.env.JAVA_HOME, 'bin', windows ? 'java.exe' : 'java') : 'java';

export function portsFrom(args) {
  const ports = { api: 18080, worker: 18091, web: 13000 };
  for (let i = 0; i < args.length; i += 2) {
    const name = args[i]?.replace(/^--/, '').replace(/-port$/, '');
    const value = Number(args[i + 1]);
    if (!['api', 'worker', 'web'].includes(name) || args[i] !== `--${name}-port`
      || !Number.isInteger(value) || value < 1024 || value > 65535) {
      throw new Error('포트 지정: --api-port 18080 --worker-port 18091 --web-port 13000');
    }
    ports[name] = value;
  }
  if (new Set(Object.values(ports)).size !== 3) throw new Error('세 서비스는 서로 다른 포트가 필요합니다.');
  return ports;
}

function run(command, args, options = {}) {
  const result = spawnSync(command, args, { cwd: root, stdio: 'inherit', windowsHide: true, ...options });
  if (result.error || result.status !== 0) throw new Error(`${path.basename(command)} 실행 실패. 설치/버전과 위 오류를 확인하세요.`);
}

function checkRuntime() {
  if (Number(process.versions.node.split('.')[0]) < 24) throw new Error('Node.js 24 이상이 필요합니다.');
  run(java, ['-version']);
}

export function assertSetupIdle(lock = setupLock) {
  if (!existsSync(lock)) return;
  let pid;
  try { pid = JSON.parse(readFileSync(lock, 'utf8')).pid; } catch {
    throw new Error('초기 설치가 진행 중입니다. setup:local 완료 후 실행하세요.');
  }
  if (!Number.isInteger(pid) || pid <= 0) throw new Error(`설치 잠금 확인 필요: ${lock}`);
  let active = true;
  try { process.kill(pid, 0); } catch (error) { if (error.code === 'ESRCH') active = false; }
  if (active) throw new Error('초기 설치가 진행 중입니다. setup:local 완료 후 실행하세요.');
  unlinkSync(lock);
}

export async function assertPortsFree(ports) {
  for (const port of ports) {
    await new Promise((resolve, reject) => {
      const server = net.createServer();
      server.once('error', () => reject(new Error(`127.0.0.1:${port} 사용 중이거나 사용할 수 없습니다. 기존 프로세스는 종료하지 않습니다. 다른 --*-port를 지정하세요.`)));
      server.listen({ host: '127.0.0.1', port, exclusive: true }, () => server.close(resolve));
    });
  }
}

export function servicePlans(ports) {
  const origin = (port) => `http://127.0.0.1:${port}`;
  // Relative paths are resolved in Ducky_was; no shell parsing or machine-specific home paths.
  const data = `../.runtime/local-dev/${ports.api}`;
  const backendArgs = [
    '--spring.profiles.active=rag-local', '--server.address=127.0.0.1', `--server.port=${ports.api}`,
    '--document.mode=local', '--document.pipeline-enabled=true', '--document.worker-token=',
    `--document.worker-url=${origin(ports.worker)}`, `--document.storage-root=${data}/documents`,
    `--spring.datasource.url=jdbc:h2:file:${data}/db;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DEFAULT_NULL_ORDERING=HIGH`,
    '--spring.datasource.username=sa', '--spring.datasource.password=', '--spring.jpa.hibernate.ddl-auto=update',
    '--chat.response-mode=extractive', `--app.cors.allowed-origins=${origin(ports.web)}`,
  ];
  return [
    { name: 'worker', command: python, args: ['-m', 'ducky_rag.server', '--host', '127.0.0.1', '--port', String(ports.worker)],
      cwd: root, env: { PYTHONPATH: path.join(root, 'Ducky_rag'), RAG_WORKER_TOKEN: '', PYTHONUNBUFFERED: '1' },
      url: `${origin(ports.worker)}/health` },
    { name: 'backend', command: java,
      args: ['-classpath', path.join(root, 'Ducky_was/gradle/wrapper/gradle-wrapper.jar'), 'org.gradle.wrapper.GradleWrapperMain',
        '--no-daemon', 'bootRun', `--args=${backendArgs.join(' ')}`],
      cwd: path.join(root, 'Ducky_was'), env: { AWS_EC2_METADATA_DISABLED: 'true' },
      url: `${origin(ports.api)}/api/health` },
    { name: 'web', command: process.execPath,
      args: [path.join(root, 'Ducky_web/node_modules/next/dist/bin/next'), 'dev', '--hostname', '127.0.0.1', '--port', String(ports.web)],
      cwd: path.join(root, 'Ducky_web'), env: { NEXT_PUBLIC_API_BASE_URL: origin(ports.api) },
      url: origin(ports.web) },
  ];
}

export async function stopOwned(children) {
  for (const child of [...children].reverse()) {
    if (!child.pid) continue;
    if (windows) {
      // Only live processes spawned by this launcher; never search by port or process name.
      if (child.exitCode === null && child.signalCode === null) {
        const result = spawnSync('taskkill', ['/PID', String(child.pid), '/T', '/F'],
          { stdio: 'ignore', windowsHide: true, timeout: 5000 });
        if (result.status !== 0 && child.exitCode === null && child.signalCode === null) {
          // Restricted Windows sessions can reject taskkill's process-tree lookup.
          // The child handle still identifies exactly the process this launcher started.
          child.kill();
          console.warn(`프로세스 트리 종료를 확인하지 못했습니다. 재시작 전 서비스 포트를 확인하세요 (PID ${child.pid}).`);
        }
      }
    } else {
      try { process.kill(-child.pid, 'SIGTERM'); } catch (error) { if (error.code !== 'ESRCH') throw error; }
    }
  }
  if (!windows) {
    // A child process can exit before its own descendants. The process group remains ours.
    await delay(500);
    for (const child of children) {
      if (!child.pid) continue;
      try { process.kill(-child.pid, 'SIGKILL'); } catch (error) { if (error.code !== 'ESRCH') throw error; }
    }
  }
  await Promise.all(children.map((child) => child.exitCode !== null || child.signalCode !== null || !child.pid
    ? Promise.resolve() : new Promise((resolve) => child.once('exit', resolve))));
}

async function waitReady(url, timeoutMs, signal) {
  const until = Date.now() + timeoutMs;
  while (Date.now() < until) {
    signal.throwIfAborted();
    try {
      const response = await fetch(url, { signal: AbortSignal.any([signal, AbortSignal.timeout(2000)]), redirect: 'manual' });
      await response.body?.cancel();
      if (response.ok || response.status === 307 || response.status === 308) return;
    } catch { signal.throwIfAborted(); }
    await delay(300, undefined, { signal });
  }
  throw new Error(`준비 시간 초과: ${url}. 위 서비스 오류를 확인하세요.`);
}

export async function supervise(plans, { signal, timeoutMs = 300000, log = console.log, onReady = () => {} }) {
  const children = [];
  let stopping = false;
  let fail;
  const failed = new Promise((_, reject) => { fail = reject; });
  failed.catch(() => {});
  const aborted = new Promise((resolve) => signal.addEventListener('abort', resolve, { once: true }));
  try {
    signal.throwIfAborted();
    for (const plan of plans) {
      signal.throwIfAborted();
      log(`[${plan.name}] 시작: ${plan.url}`);
      const child = spawn(plan.command, plan.args, { cwd: plan.cwd,
        env: { ...process.env, ...plan.env }, detached: !windows, windowsHide: true, stdio: ['ignore', 'pipe', 'pipe'] });
      children.push(child);
      if (child.pid) log(`[${plan.name}] PID ${child.pid}`);
      child.stdout.on('data', (data) => log(`[${plan.name}] ${data.toString().trimEnd()}`));
      child.stderr.on('data', (data) => log(`[${plan.name}] ${data.toString().trimEnd()}`));
      child.once('error', (error) => fail(new Error(`${plan.name} 시작 실패: ${error.message}`)));
      child.once('exit', (code, reason) => {
        if (!stopping) fail(new Error(`${plan.name} 종료(${code ?? reason}). 함께 시작한 서비스만 정리합니다.`));
      });
      await Promise.race([waitReady(plan.url, timeoutMs, signal), failed]);
      log(`[${plan.name}] 준비 완료`);
    }
    await onReady();
    await Promise.race([failed, aborted]);
  } catch (error) {
    if (!signal.aborted) throw error;
  } finally {
    stopping = true;
    await stopOwned(children);
  }
}

async function setup() {
  assertSetupIdle();
  mkdirSync(path.dirname(setupLock), { recursive: true });
  const lock = openSync(setupLock, 'wx');
  writeFileSync(lock, JSON.stringify({ pid: process.pid }));
  closeSync(lock);
  try {
    checkRuntime();
    const pythonCommand = windows ? 'py' : 'python3.12';
    const prefix = windows ? ['-3.12'] : [];
    run(pythonCommand, [...prefix, '-c', 'import sys; assert sys.version_info[:2] == (3, 12), "Python 3.12 required"']);
    if (!existsSync(python)) run(pythonCommand, [...prefix, '-m', 'venv', path.join(root, '.venv-rag')]);
    run(python, ['-m', 'pip', 'install', '-r', 'Ducky_rag/requirements-test.txt']);
    const npm = process.env.npm_execpath;
    if (!npm || !existsSync(npm)) throw new Error('저장소 루트에서 npm run setup:local로 실행하세요.');
    // Invoke the npm JS entry point directly: no .cmd shell/string quoting on Windows.
    run(process.execPath, [npm, 'ci', '--no-audit', '--no-fund'], { cwd: path.join(root, 'Ducky_web') });
    console.log('설치 완료. 저장소 루트에서 npm run dev를 실행하세요.');
  } finally { unlinkSync(setupLock); }
}

async function main() {
  const command = process.argv[2];
  if (command === 'setup') return setup();
  if (command !== 'start') throw new Error('npm run setup:local 또는 npm run dev로 실행하세요.');
  assertSetupIdle();
  const ports = portsFrom(process.argv.slice(3));
  checkRuntime();
  if (!existsSync(python) || !existsSync(path.join(root, 'Ducky_web/node_modules/next/dist/bin/next'))
    || !existsSync(path.join(root, 'Ducky_web/node_modules/next/dist/server/require-hook.js'))) {
    throw new Error('의존성이 없습니다. 먼저 npm run setup:local을 실행하세요.');
  }
  await assertPortsFree(Object.values(ports));
  mkdirSync(path.join(root, '.runtime/local-dev', String(ports.api)), { recursive: true });
  const controller = new AbortController();
  const stop = () => controller.abort();
  process.on('SIGINT', stop);
  process.on('SIGTERM', stop);
  try {
    await supervise(servicePlans(ports), { signal: controller.signal, onReady: () => {
      console.log(`\n로컬 개발 준비 완료: http://127.0.0.1:${ports.web}\nAPI: http://127.0.0.1:${ports.api}\nCtrl+C로 함께 시작한 세 서비스를 종료합니다.\nAWS/LLM 호출 없는 발췌 모드입니다.\n`);
    } });
  } finally {
    controller.abort();
    process.removeListener('SIGINT', stop);
    process.removeListener('SIGTERM', stop);
  }
}

if (process.argv[1] && path.resolve(process.argv[1]) === fileURLToPath(import.meta.url)) {
  main().catch((error) => { console.error(`\n[local-dev] ${error.message}`); process.exitCode = 1; });
}
