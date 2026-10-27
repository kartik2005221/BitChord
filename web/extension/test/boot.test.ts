import { readFileSync } from 'node:fs';
import { join } from 'node:path';
import vm from 'node:vm';
import { describe, expect, it } from 'vitest';

/**
 * Boots each built background bundle inside a process-less VM: a browser has
 * no `process` global, and a bare reference at module scope killed the whole
 * background once (ReferenceError: process is not defined) — no listener,
 * popup rows hanging on "Checking…", the site's detection timing out. Every
 * build must boot and answer a bridge ping under these conditions.
 */

const outputRoot = join(import.meta.dirname, '..', '.output');

const builds = [
  { browser: 'chromium', folder: 'chrome-mv3' },
  { browser: 'firefox', folder: 'firefox-mv3' },
] as const;

type OnMessageListener = (
  message: unknown,
  sender: { id: string; origin?: string },
  sendResponse: (response: unknown) => void,
) => boolean;

function boot(code: string, version: string) {
  const storage = new Map<string, unknown>();
  const installedRules: unknown[] = [];
  const messageListeners: OnMessageListener[] = [];
  const registered = { onInstalled: 0, onMessage: 0, onConnect: 0 };

  const browser = {
    runtime: {
      id: 'deck-test-runtime-id',
      getURL: (path: string) => `http://bit.chord/extension/${path}`,
      getManifest: () => ({ version, manifest_version: 3, name: 'BitChord' }),
      onInstalled: { addListener: () => void registered.onInstalled++ },
      onStartup: { addListener: () => undefined },
      onMessage: {
        addListener: (listener: OnMessageListener) => {
          messageListeners.push(listener);
          registered.onMessage++;
        },
      },
      onMessageExternal: { addListener: () => undefined },
      onConnect: { addListener: () => undefined },
      onConnectExternal: { addListener: () => undefined },
      sendMessage: () => Promise.reject(new Error('not expected in the boot test')),
    },
    storage: {
      local: {
        get: async (key: string | string[]) => {
          const keys = Array.isArray(key) ? key : key === null ? [...storage.keys()] : [key];
          return Object.fromEntries(keys.map((k) => [k, storage.get(k)]).filter(([, v]) => v !== undefined));
        },
        set: async (entries: Record<string, unknown>) => {
          for (const [k, v] of Object.entries(entries)) storage.set(k, v);
        },
      },
      onChanged: { addListener: () => undefined },
    },
    cookies: { get: async () => null, onChanged: { addListener: () => undefined } },
    declarativeNetRequest: {
      updateDynamicRules: async ({ removeRuleIds, addRules }: { removeRuleIds?: number[]; addRules?: unknown[] }) => {
        for (const id of removeRuleIds ?? []) {
          const index = installedRules.findIndex((rule) => (rule as { id: number }).id === id);
          if (index >= 0) installedRules.splice(index, 1);
        }
        installedRules.push(...(addRules ?? []));
      },
      getDynamicRules: async () => installedRules,
    },
    scripting: { registerContentScripts: async () => undefined, unregisterContentScripts: async () => undefined },
    contentScripts: undefined,
    tabs: { create: async () => ({}) },
  };

  const sandbox = vm.createContext({
    browser,
    console: { debug() {}, log() {}, warn() {}, error() {} },
    setTimeout,
    clearTimeout,
    URL,
    URLSearchParams,
    atob,
    btoa,
    performance,
    crypto: globalThis.crypto,
    fetch: () => Promise.reject(new Error('the boot test makes no network requests')),
  } satisfies Record<string, unknown>);
  vm.runInContext(code, sandbox, { filename: 'background.js' });

  if (messageListeners.length !== 1) {
    throw new Error(`expected one runtime.onMessage listener after boot, got ${messageListeners.length}`);
  }
  const [onMessage] = messageListeners;
  if (!onMessage) throw new Error('the boot test unchecked its own listener');
  return { browser, installedRules, messageListeners, onMessage, registered, storage };
}

/** A round trip through the compiled listener, exactly as a caller would. */
function call(
  listener: OnMessageListener,
  method: string,
  sendResponse: (response: unknown) => void,
  params: Record<string, unknown> = {},
): boolean {
  return listener(
    { channel: 'bitchord-bridge', protocol: 1, direction: 'to-extension', id: `boot-${method}`, method, params },
    { id: 'deck-test-runtime-id', origin: 'https://bitchord-web.pages.dev' },
    sendResponse,
  );
}

describe.each(builds)('$folder: the built background boots browserside', ({ folder }) => {
  const manifest = JSON.parse(readFileSync(join(outputRoot, folder, 'manifest.json'), 'utf8')) as { version: string };
  const code = readFileSync(join(outputRoot, folder, 'background.js'), 'utf8');

  it('carries no Node-only references', () => {
    expect(code.includes('process.env'), 'a browser bundle must not read process.env').toBe(false);
  });

  it('registers its listeners without a crash', () => {
    const boot0 = boot(code, manifest.version);
    expect(boot0.registered.onInstalled).toBe(1);
    expect(boot0.registered.onMessage).toBe(1);
  });

  it('answers a bridge ping after booting', async () => {
    const boot0 = boot(code, manifest.version);
    await new Promise((resolve) => setTimeout(resolve, 20)); // let the init chain settle

    const response = await new Promise<unknown>((resolve) => {
      expect(call(boot0.onMessage, 'ping', resolve)).toBe(true);
    });

    expect(response).toMatchObject({
      channel: 'bitchord-bridge',
      direction: 'to-page',
      id: 'boot-ping',
      ok: true,
      result: {
        version: manifest.version,
        protocol: 1,
        browser: folder === 'chrome-mv3' ? 'chromium' : 'firefox',
      },
    });
    expect((response as { result: { capabilities: string[] } }).result.capabilities).toContain('saavn');
  });

  it('refuses senders it doesn’t serve', async () => {
    const boot0 = boot(code, manifest.version);
    await new Promise((resolve) => setTimeout(resolve, 20));

    const response = await new Promise<unknown>((resolve) => {
      boot0.onMessage(
        { channel: 'bitchord-bridge', protocol: 1, direction: 'to-extension', id: 'boot-stranger', method: 'ping', params: {} },
        { id: 'deck-test-runtime-id', origin: 'https://totally-unrelated.example' },
        resolve,
      );
    });

    expect(response).toMatchObject({ ok: false, error: { code: 'forbidden' } });
  });
});
