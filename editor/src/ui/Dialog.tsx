import { useEffect, useId, useRef, type ReactNode } from 'react';
import { IconClose } from './icons';

const FOCUSABLE =
  'button:not([disabled]), [href], input:not([disabled]):not([type="hidden"]), select:not([disabled]), textarea:not([disabled]), summary, [tabindex]:not([tabindex="-1"])';

export function focusableIn(root: HTMLElement): HTMLElement[] {
  return Array.from(root.querySelectorAll<HTMLElement>(FOCUSABLE)).filter((el) => el.getClientRects().length > 0);
}

/** Where Tab should go inside a focus trap, or null when the browser's default move stays inside. */
export function trapTarget(index: number, count: number, backwards: boolean): number | null {
  if (count === 0) return null;
  if (index < 0) return backwards ? count - 1 : 0;
  if (backwards && index === 0) return count - 1;
  if (!backwards && index === count - 1) return 0;
  return null;
}

/**
 * Modal dialog: native <dialog> (inert background), explicit focus trap, initial focus inside the body,
 * focus returned to the opener on close, Escape to dismiss.
 */
export function Dialog({
  open,
  onClose,
  title,
  subtitle,
  children,
  footer,
  size = 'md',
  dismissable = true,
}: {
  open: boolean;
  onClose: () => void;
  title: ReactNode;
  subtitle?: ReactNode;
  children: ReactNode;
  footer?: ReactNode;
  size?: 'sm' | 'md' | 'lg';
  /** When false, Esc and backdrop clicks do nothing (e.g. while writing). */
  dismissable?: boolean;
}) {
  const ref = useRef<HTMLDialogElement>(null);
  const opener = useRef<HTMLElement | null>(null);
  const titleId = useId();
  const bodyId = useId();

  useEffect(() => {
    const dialog = ref.current;
    if (!dialog) return;
    const restore = () => {
      const el = opener.current;
      opener.current = null;
      if (el && el.isConnected && typeof el.focus === 'function') el.focus();
    };
    if (open && !dialog.open) {
      opener.current = document.activeElement instanceof HTMLElement ? document.activeElement : null;
      dialog.showModal();
      requestAnimationFrame(() => {
        if (!dialog.open) return;
        const active = document.activeElement;
        const insideBody = active instanceof HTMLElement && dialog.querySelector('.dialog__body')?.contains(active);
        if (insideBody) return;
        const preferred =
          dialog.querySelector<HTMLElement>('[data-autofocus]') ??
          focusableIn(dialog.querySelector<HTMLElement>('.dialog__body') ?? dialog)[0] ??
          focusableIn(dialog)[0];
        preferred?.focus();
      });
    }
    if (!open && dialog.open) {
      dialog.close();
      restore();
    }
  }, [open]);

  // Unmounting while open (dialogs rendered conditionally) must also give focus back.
  useEffect(
    () => () => {
      const el = opener.current;
      if (el && el.isConnected) el.focus();
    },
    [],
  );

  return (
    <dialog
      ref={ref}
      className={`dialog dialog--${size}`}
      aria-labelledby={titleId}
      aria-describedby={subtitle ? `${titleId}-sub` : undefined}
      onCancel={(e) => {
        e.preventDefault();
        if (dismissable) onClose();
      }}
      onKeyDown={(e) => {
        if (e.key === 'Escape' && !e.defaultPrevented) {
          e.preventDefault();
          if (dismissable) onClose();
          return;
        }
        if (e.key === 'Tab' && ref.current) {
          const items = focusableIn(ref.current);
          const target = trapTarget(items.indexOf(document.activeElement as HTMLElement), items.length, e.shiftKey);
          if (target !== null) {
            e.preventDefault();
            items[target]?.focus();
          }
        }
      }}
      onMouseDown={(e) => {
        if (dismissable && e.target === ref.current) onClose();
      }}
    >
      {open && (
        <div className="dialog__panel">
          <header className="dialog__header">
            <div>
              <h2 id={titleId} className="dialog__title">
                {title}
              </h2>
              {subtitle && (
                <p id={`${titleId}-sub`} className="dialog__subtitle">
                  {subtitle}
                </p>
              )}
            </div>
            {dismissable && (
              <button type="button" className="btn btn--ghost btn--icon btn--sm" aria-label="Close dialog" onClick={onClose}>
                <IconClose />
              </button>
            )}
          </header>
          <div className="dialog__body" id={bodyId}>
            {children}
          </div>
          {footer && <footer className="dialog__footer">{footer}</footer>}
        </div>
      )}
    </dialog>
  );
}
