import type { ReactNode } from "react";

interface AlertProps {
  kind: "error" | "success" | "info";
  children: ReactNode;
}

export function Alert({ kind, children }: AlertProps) {
  return (
    <div className={`alert alert--${kind}`} role={kind === "error" ? "alert" : "status"}>
      {children}
    </div>
  );
}

interface ErrorSummaryProps {
  errors: string[];
}

/** A bulleted list of every validation problem found before submit, shown at the top of a form. */
export function ErrorSummary({ errors }: ErrorSummaryProps) {
  if (errors.length === 0) return null;

  return (
    <div className="alert alert--error" role="alert">
      <p className="alert__title">
        {errors.length === 1
          ? "There is 1 problem with this form:"
          : `There are ${errors.length} problems with this form:`}
      </p>
      <ul className="alert__list">
        {errors.map((error, index) => (
          <li key={index}>{error}</li>
        ))}
      </ul>
    </div>
  );
}
