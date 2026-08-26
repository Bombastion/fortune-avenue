import { type FormEvent, useState } from "react";
import { ApiError, api } from "../api/client";
import type { UserResponse } from "../api/types";
import { Alert, ErrorSummary } from "../components/Alert";
import { Field } from "../components/Field";
import { isBlank } from "../validation/rules";

export function UsersPage() {
  return (
    <div className="page">
      <h1>Users</h1>
      <div className="grid grid--2">
        <CreateUserForm />
        <LookupUserForm />
      </div>
    </div>
  );
}

function CreateUserForm() {
  const [username, setUsername] = useState("");
  const [errors, setErrors] = useState<string[]>([]);
  const [submitting, setSubmitting] = useState(false);
  const [created, setCreated] = useState<UserResponse | null>(null);

  function validate(): string[] {
    const problems: string[] = [];
    if (isBlank(username)) problems.push("Username is required.");
    return problems;
  }

  async function handleSubmit(event: FormEvent) {
    event.preventDefault();
    setCreated(null);

    const problems = validate();
    setErrors(problems);
    if (problems.length > 0) return;

    setSubmitting(true);
    try {
      const user = await api.createUser({ username: username.trim() });
      setCreated(user);
      setUsername("");
    } catch (err) {
      setErrors([err instanceof Error ? err.message : "Something went wrong."]);
    } finally {
      setSubmitting(false);
    }
  }

  return (
    <form className="card" onSubmit={handleSubmit} noValidate>
      <h2>Create a user</h2>
      <ErrorSummary errors={errors} />
      <Field label="Username" error={errors.length > 0 && isBlank(username) ? "Required" : undefined}>
        <input
          type="text"
          value={username}
          onChange={(event) => setUsername(event.target.value)}
          placeholder="e.g. xXcooluser22Xx"
        />
      </Field>
      <button type="submit" className="button" disabled={submitting}>
        {submitting ? "Creating…" : "Create user"}
      </button>
      {created && (
        <Alert kind="success">
          Created user <strong>{created.username}</strong> (id: <code>{created.id}</code>)
        </Alert>
      )}
    </form>
  );
}

function LookupUserForm() {
  const [id, setId] = useState("");
  const [error, setError] = useState<string | null>(null);
  const [loading, setLoading] = useState(false);
  const [user, setUser] = useState<UserResponse | null>(null);

  async function handleSubmit(event: FormEvent) {
    event.preventDefault();
    setUser(null);

    if (isBlank(id)) {
      setError("Enter a user id to look up.");
      return;
    }

    setError(null);
    setLoading(true);
    try {
      const found = await api.getUser(id.trim());
      setUser(found);
    } catch (err) {
      if (err instanceof ApiError && err.status === 404) {
        setError("No user found with that id.");
      } else {
        setError(err instanceof Error ? err.message : "Something went wrong.");
      }
    } finally {
      setLoading(false);
    }
  }

  return (
    <form className="card" onSubmit={handleSubmit} noValidate>
      <h2>Look up a user</h2>
      {error && <Alert kind="error">{error}</Alert>}
      <Field label="User id">
        <input
          type="text"
          value={id}
          onChange={(event) => setId(event.target.value)}
          placeholder="UUID"
        />
      </Field>
      <button type="submit" className="button" disabled={loading}>
        {loading ? "Looking up…" : "Look up"}
      </button>
      {user && (
        <Alert kind="success">
          <strong>{user.username}</strong> (id: <code>{user.id}</code>)
        </Alert>
      )}
    </form>
  );
}
