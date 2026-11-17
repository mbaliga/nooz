// Loads qml/js/HostBridge.js (a QML `.pragma library` file) into node.
import { readFileSync } from 'node:fs';
import vm from 'node:vm';

export function loadBridge() {
  const src = readFileSync(new URL('../qml/js/HostBridge.js', import.meta.url), 'utf8');
  if (!src.startsWith('.pragma library\n')) throw new Error('HostBridge.js must start with ".pragma library"');
  const ctx = vm.createContext({});
  return vm.runInContext(
    src.replace(/^\.pragma library\n/, '') +
      '\n({ parseUrl, isBlockedHost, checkTarget, parseConsole, replyScript, handle, createTracker, PREFIX, PAGE_MAX_BYTES, FEED_TIMEOUT_MS, PAGE_TIMEOUT_MS })',
    ctx,
  );
}
