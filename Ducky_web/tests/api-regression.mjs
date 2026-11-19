import { test, afterEach } from 'node:test';
import { createRequire } from 'node:module';
import { fileURLToPath } from 'node:url';
const require = createRequire(import.meta.url);
const __dirname = fileURLToPath(new URL('.', import.meta.url));
import assert from 'node:assert/strict';
import fs from 'node:fs';
import path from 'node:path';
import vm from 'node:vm';
import ts from 'typescript';
import React from 'react';
import { renderToStaticMarkup } from 'react-dom/server';

// Load the application's TypeScript without adding a test runtime dependency.
const modules = new Map();
function load(relative) {
  const file = path.resolve(__dirname, '../src', relative);
  if (modules.has(file)) return modules.get(file).exports;
  const compiledModule = { exports: {} };
  modules.set(file, compiledModule);
  const code = ts.transpileModule(fs.readFileSync(file, 'utf8'), {
    compilerOptions: { module: ts.ModuleKind.CommonJS, jsx: ts.JsxEmit.ReactJSX },
  }).outputText;
  const localRequire = (name) => {
    if (name.startsWith('@/')) return load(`${name.slice(2)}.ts`);
    if (name.startsWith('.')) return load(path.relative(path.resolve(__dirname, '../src'), path.resolve(path.dirname(file), `${name}.ts`)));
    return require(name);
  };
  new Function('require', 'module', 'exports', code)(localRequire, compiledModule, compiledModule.exports);
  return compiledModule.exports;
}
load('lib/api/client.ts');
const docs = load('lib/api/documents.ts');
const chat = load('lib/api/chat.ts');
const { toChatMessage } = load('lib/chat/transformers.ts');
const originalFetch = global.fetch;
const originalTimeout = global.setTimeout;

test('chat distinguishes omitted and empty selection and restores response strategy', async () => {
  login();
  const bodies = [];
  global.fetch = async (_url, options) => {
    bodies.push(JSON.parse(options.body));
    return response({});
  };
  await chat.sendChatMessage({ conversationId: '1', message: 'question' });
  await chat.sendChatMessage({ conversationId: '1', message: 'question', documentIds: [] });
  await chat.sendChatMessage({ conversationId: '1', message: 'question', documentIds: [7] });
  assert.equal(Object.hasOwn(bodies[0], 'documentIds'), false);
  assert.deepEqual(bodies[1].documentIds, []);
  assert.deepEqual(bodies[2].documentIds, [7]);
  const restored = toChatMessage({ id: '1', role: 'assistant', content: 'answer', type: 'answer',
    createdAt: '2026-11-19', responseStrategy: 'DOCUMENT', responseMode: 'extractive', sources: [] });
  assert.equal(restored.responseStrategy, 'DOCUMENT');
});
let token;
function login(value = 'synthetic-account-a') {
  token = value;
  global.window = { localStorage: { getItem: () => token } };
}
function response(data) {
  return Response.json({ success: true, data });
}
afterEach(() => {
  global.fetch = originalFetch;
  global.setTimeout = originalTimeout;
  delete global.window;
});

test('upload uses authenticated multipart and preserves the server document ID', async () => {
  login();
  global.fetch = async (url, options) => {
    assert.ok(url.endsWith('/api/documents'));
    assert.equal(options.method, 'POST');
    assert.equal(options.headers.get('Authorization'), `Bearer ${token}`);
    assert.equal(options.headers.has('Content-Type'), false);
    assert.equal(options.body.get('file').name, 'synthetic.pdf');
    assert.equal(options.cache, 'no-store');
    return response({ id: '19', indexingStatus: 'pending' });
  };
  assert.equal((await docs.uploadDocument(new File(['synthetic'], 'synthetic.pdf'))).id, '19');
});

test('processing and processed continue polling until indexed; processed is never searchable', async () => {
  login();
  global.setTimeout = (fn) => originalTimeout(fn, 0);
  const states = ['processing', 'processed', 'indexed'];
  let calls = 0;
  global.fetch = async () => response({ id: '19', ragEnabled: true, indexingStatus: states[calls++] });
  const result = await docs.waitForDocument({ id: '19', indexingStatus: 'pending' });
  assert.equal(calls, 3);
  assert.equal(result.indexingStatus, 'indexed');
  assert.equal(docs.isDocumentSearchable({ indexingStatus: 'processed', ragEnabled: true }), false);
  assert.equal(docs.isDocumentSearchable({ indexingStatus: 'indexed', ragEnabled: false }), false);
});

test('failure/OCR/exclusion/timeout/cancellation do not return searchable documents', async () => {
  await assert.rejects(docs.waitForDocument({ indexingStatus: 'failed', processingError: 'OCR_REQUIRED' }), Error);
  await assert.rejects(docs.waitForDocument({ indexingStatus: 'excluded' }), Error);
  await assert.rejects(docs.waitForDocument({ indexingStatus: 'indexed', ragEnabled: false }), Error);
  const controller = new AbortController();
  controller.abort();
  await assert.rejects(docs.waitForDocument({ indexingStatus: 'processed' }, controller.signal), { name: 'AbortError' });
  const now = Date.now;
  let calls = 0;
  Date.now = () => calls++ === 0 ? 0 : 340_000;
  try { await assert.rejects(docs.waitForDocument({ indexingStatus: 'processed' }), Error); }
  finally { Date.now = now; }
});

test('retry keeps the current endpoint and reads fresh state', async () => {
  login();
  global.fetch = async (url, options) => {
    assert.ok(url.endsWith('/api/documents/19/retry'));
    assert.equal(options.method, 'POST');
    return response({ id: '19', indexingStatus: 'pending' });
  };
  assert.equal((await docs.retryDocument('19')).indexingStatus, 'pending');
});

test('conversation reload restores persisted sources including PDF page metadata', async () => {
  login();
  const sources = [{ documentId: '19', chunkId: '21', title: 'Synthetic', content: 'Evidence', pageNumber: 4, blockIds: ['b1'], contextChunkIds: ['21'] }];
  global.fetch = async () => response({ messages: [{ id: '2', role: 'assistant', type: 'answer', content: 'Answer', sources }] });
  const detail = await chat.getConversation('7');
  assert.deepEqual(toChatMessage(detail.messages[0]).sources, sources);
  assert.equal(toChatMessage({ id: 'old', role: 'assistant', type: 'answer' }).sources, undefined);
});

test('answer source renders the document and PDF page, with a fallback for legacy responses', () => {
  const { ChatBubble } = load('components/chat/ChatBubble.tsx');
  const source = { documentId: '19', chunkId: '21', title: 'Synthetic PDF', content: 'Evidence', pageNumber: 4 };
  const message = { id: '2', role: 'assistant', content: 'Answer', type: 'answer', sources: [source] };
  const html = renderToStaticMarkup(React.createElement(ChatBubble, { message }));
  assert.match(html, /PDF 4쪽/);
  assert.match(html, /문서 19/);
  const legacy = renderToStaticMarkup(React.createElement(ChatBubble, { message: { ...message, sources: [{ ...source, pageNumber: null }] } }));
  assert.match(legacy, /쪽 정보 없음/);
});

test('signed-out requests never fetch and late account-A responses are rejected after switching to B', async () => {
  login(null);
  global.fetch = () => assert.fail('must not fetch without credentials');
  await assert.rejects(docs.listDocuments(), Error);
  login();
  global.fetch = async () => { token = 'synthetic-account-b'; return response([{ id: 'private-a' }]); };
  await assert.rejects(docs.listDocuments(), Error);
});

test('service worker bypasses API and RSC while offline navigation uses only generic fallback', async () => {
  const handlers = {};
  const matches = [];
  const context = {
    self: { location: { origin: 'https://test.invalid' }, addEventListener: (event, handler) => { handlers[event] = handler; } },
    URL, fetch: async () => { throw new Error('offline'); },
    caches: { match: async (key) => { matches.push(key); return 'offline-page'; } },
  };
  vm.runInNewContext(fs.readFileSync(path.resolve(__dirname, '../public/sw.js'), 'utf8'), context);
  for (const url of ['/api/documents', '/settings?_rsc=private']) {
    handlers.fetch({ request: { url: `https://test.invalid${url}`, method: 'GET', headers: new Headers(), mode: 'cors' }, respondWith: () => assert.fail('private response intercepted') });
  }
  let result;
  handlers.fetch({ request: { url: 'https://test.invalid/chat', method: 'GET', headers: new Headers(), mode: 'navigate' }, respondWith: (promise) => { result = promise; } });
  assert.equal(await result, 'offline-page');
  assert.deepEqual(matches, ['/offline']);
});
