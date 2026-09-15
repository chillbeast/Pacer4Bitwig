import { useUi } from '../store/ui';

/**
 * Register the build-generated service worker (production builds only; never in `npm run dev`).
 * Works under any base path: the worker URL and scope are resolved against the page.
 */
export function registerServiceWorker(): void {
  if (!import.meta.env.PROD || typeof navigator === 'undefined' || !('serviceWorker' in navigator)) return;
  const secure = location.protocol === 'https:' || location.hostname === 'localhost' || location.hostname === '127.0.0.1';
  if (!secure) return;

  window.addEventListener('load', () => {
    const hadController = navigator.serviceWorker.controller !== null;
    navigator.serviceWorker
      .register(new URL('sw.js', document.baseURI), { scope: './' })
      .catch(() => {
        // offline support is optional
      });
    navigator.serviceWorker.addEventListener('controllerchange', () => {
      if (!hadController) return; // first install: nothing to reload
      useUi.getState().toast(
        {
          tone: 'info',
          title: 'Pacer Studio was updated',
          detail: 'Reload to use the new version (unsent edits are lost on reload).',
          action: { label: 'Reload', run: () => location.reload() },
        },
        0,
      );
    });
  });
}
