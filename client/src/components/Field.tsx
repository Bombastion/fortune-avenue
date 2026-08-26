import type { ReactNode } from "react";

interface FieldProps {
  label: string;
  error?: string;
  hint?: ReactNode;
  children: ReactNode;
}

/**
 * Labeled wrapper for a single form control. Wrapping the control in the <label> itself (rather
 * than wiring up matching id/htmlFor attributes) keeps every call site simple, which matters here
 * since so many fields are generated dynamically (one per space, per path, per district, ...).
 */
export function Field({ label, error, hint, children }: FieldProps) {
  return (
    <label className="field">
      <span className="field__label">{label}</span>
      {children}
      {hint && !error && <span className="field__hint">{hint}</span>}
      {error && (
        <span className="field__error" role="alert">
          {error}
        </span>
      )}
    </label>
  );
}
