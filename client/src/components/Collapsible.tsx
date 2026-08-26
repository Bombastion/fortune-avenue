import { type ReactNode, useState } from "react";

interface CollapsibleProps {
  title: string;
  summary?: string;
  open: boolean;
  onToggle: (open: boolean) => void;
  onRemove?: () => void;
  removeLabel?: string;
  hasError?: boolean;
  children: ReactNode;
}

/**
 * Expand/collapse wrapper used for every dynamically-added item (a space, a path, a district, ...)
 * so a board with dozens of entries stays navigable: collapse the ones you're done with, leave the
 * one you're editing open. Open/closed state is owned by the caller (via useOpenIds below) rather
 * than local state, so a "collapse all" / "expand all" control can drive every item at once.
 */
export function Collapsible({
  title,
  summary,
  open,
  onToggle,
  onRemove,
  removeLabel = "Remove",
  hasError = false,
  children,
}: CollapsibleProps) {
  return (
    <div className={`collapsible${hasError ? " collapsible--error" : ""}`}>
      <div className="collapsible__header">
        <button
          type="button"
          className="collapsible__toggle"
          aria-expanded={open}
          onClick={() => onToggle(!open)}
        >
          <span className="collapsible__chevron" aria-hidden="true">
            {open ? "▾" : "▸"}
          </span>
          <span className="collapsible__title">{title}</span>
          {!open && summary && <span className="collapsible__summary">{summary}</span>}
        </button>
        {onRemove && (
          <button
            type="button"
            className="button button--danger button--small"
            onClick={onRemove}
          >
            {removeLabel}
          </button>
        )}
      </div>
      {open && <div className="collapsible__body">{children}</div>}
    </div>
  );
}

/**
 * Tracks which items in a dynamic list are expanded, keyed by a stable per-item id. Deliberately
 * simple (no effects syncing it to the list of ids) -- callers explicitly `add` an id when they
 * add the item (so it starts expanded) and `remove` it when they remove the item, which keeps the
 * open/closed state exactly as predictable as the list itself.
 */
export function useOpenIds(initialIds: string[] = []) {
  const [openIds, setOpenIds] = useState<Set<string>>(() => new Set(initialIds));

  return {
    isOpen: (id: string) => openIds.has(id),
    setOpen: (id: string, open: boolean) => {
      setOpenIds((previous) => {
        const next = new Set(previous);
        if (open) next.add(id);
        else next.delete(id);
        return next;
      });
    },
    add: (id: string) => setOpenIds((previous) => new Set(previous).add(id)),
    remove: (id: string) => {
      setOpenIds((previous) => {
        const next = new Set(previous);
        next.delete(id);
        return next;
      });
    },
    expandAll: (ids: string[]) => setOpenIds(new Set(ids)),
    collapseAll: () => setOpenIds(new Set()),
  };
}
