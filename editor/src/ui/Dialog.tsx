import { useEffect, useId, useRef, type ReactNode } from 'react';
import { IconClose } from './icons';

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
  const titleId = useId();

  useEffect(() => {
    const dialog = ref.current;
    if (!dialog) return;
    if (open && !dialog.open) dialog.showModal();
    if (!open && dialog.open) dialog.close();
  }, [open]);

  return (
    <dialog
      ref={ref}
      className={`dialog dialog--${size}`}
      aria-labelledby={titleId}
      onCancel={(e) => {
        e.preventDefault();
        if (dismissable) onClose();
      }}
      onKeyDown={(e) => {
        // Handle Escape directly: the native "cancel" event is not delivered reliably in every host.
        if (e.key === 'Escape' && !e.defaultPrevented) {
          e.preventDefault();
          if (dismissable) onClose();
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
              {subtitle && <p className="dialog__subtitle">{subtitle}</p>}
            </div>
            {dismissable && (
              <button type="button" className="btn btn--ghost btn--icon btn--sm" aria-label="Close" onClick={onClose}>
                <IconClose />
              </button>
            )}
          </header>
          <div className="dialog__body">{children}</div>
          {footer && <footer className="dialog__footer">{footer}</footer>}
        </div>
      )}
    </dialog>
  );
}
