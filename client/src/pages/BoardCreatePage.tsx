import { type FormEvent, useState } from "react";
import { useNavigate } from "react-router-dom";
import { api } from "../api/client";
import { serializeRequestWithDecimals } from "../api/json";
import { SPACE_TYPES, type SpaceType } from "../api/types";
import { Collapsible, useOpenIds } from "../components/Collapsible";
import { Field } from "../components/Field";
import { Alert, ErrorSummary } from "../components/Alert";
import {
  type BoardFormState,
  type DistrictFormState,
  type PathFormState,
  type SpaceFormState,
  buildCreateBoardRequest,
  emptyBoardForm,
  newDistrict,
  newPath,
  newSpace,
  removeDistrictAt,
  removeSpaceAt,
  requiredProgressionLevels,
  spaceCountForDistrict,
  validateBoardForm,
} from "./BoardCreatePage.state";

export function BoardCreatePage() {
  const navigate = useNavigate();
  const [form, setForm] = useState<BoardFormState>(emptyBoardForm());
  const [errors, setErrors] = useState<string[]>([]);
  const [fieldErrors, setFieldErrors] = useState<Record<string, string>>({});
  const [submitting, setSubmitting] = useState(false);
  const [submitError, setSubmitError] = useState<string | null>(null);

  const openSpaces = useOpenIds();
  const openPaths = useOpenIds();
  const openDistricts = useOpenIds();

  function addSpace() {
    const space = newSpace();
    setForm((f) => ({ ...f, spaces: [...f.spaces, space] }));
    openSpaces.add(space.localId);
  }

  function updateSpace(index: number, patch: Partial<SpaceFormState>) {
    setForm((f) => ({
      ...f,
      spaces: f.spaces.map((s, i) => (i === index ? { ...s, ...patch } : s)),
    }));
  }

  function removeSpace(index: number) {
    const removedId = form.spaces[index]?.localId;
    setForm((f) => removeSpaceAt(f, index));
    if (removedId) openSpaces.remove(removedId);
  }

  function addPath() {
    const path = newPath();
    setForm((f) => ({ ...f, paths: [...f.paths, path] }));
    openPaths.add(path.localId);
  }

  function updatePath(index: number, patch: Partial<PathFormState>) {
    setForm((f) => ({
      ...f,
      paths: f.paths.map((p, i) => (i === index ? { ...p, ...patch } : p)),
    }));
  }

  function removePath(index: number) {
    const removedId = form.paths[index]?.localId;
    setForm((f) => ({ ...f, paths: f.paths.filter((_, i) => i !== index) }));
    if (removedId) openPaths.remove(removedId);
  }

  function addDistrict() {
    const district = newDistrict();
    setForm((f) => ({ ...f, districts: [...f.districts, district] }));
    openDistricts.add(district.localId);
  }

  function updateDistrict(index: number, patch: Partial<DistrictFormState>) {
    setForm((f) => ({
      ...f,
      districts: f.districts.map((d, i) => (i === index ? { ...d, ...patch } : d)),
    }));
  }

  function removeDistrict(index: number) {
    const removedId = form.districts[index]?.localId;
    setForm((f) => removeDistrictAt(f, index));
    if (removedId) openDistricts.remove(removedId);
  }

  function setProgressionValue(
    districtIndex: number,
    level: number,
    field: "existingShopBoostPercentage" | "newShopBoostPercentage",
    value: string,
  ) {
    setForm((f) => ({
      ...f,
      districts: f.districts.map((d, i) => {
        if (i !== districtIndex) return d;
        const existing = d.progressionValues[level] ?? {
          existingShopBoostPercentage: "",
          newShopBoostPercentage: "",
        };
        return {
          ...d,
          progressionValues: { ...d.progressionValues, [level]: { ...existing, [field]: value } },
        };
      }),
    }));
  }

  async function handleSubmit(event: FormEvent) {
    event.preventDefault();
    setSubmitError(null);

    const result = validateBoardForm(form);
    setErrors(result.errors);
    setFieldErrors(result.fieldErrors);
    if (result.errors.length > 0) {
      return;
    }

    setSubmitting(true);
    try {
      const request = buildCreateBoardRequest(form);
      const board = await api.createBoard(serializeRequestWithDecimals(request));
      navigate(`/boards/${board.id}`);
    } catch (err) {
      setSubmitError(err instanceof Error ? err.message : "Something went wrong.");
    } finally {
      setSubmitting(false);
    }
  }

  return (
    <div className="page">
      <h1>Create a board</h1>
      <form onSubmit={handleSubmit} noValidate>
        <ErrorSummary errors={errors} />
        {submitError && <Alert kind="error">{submitError}</Alert>}

        <section className="card">
          <h2>Basics</h2>
          <div className="grid grid--2">
            <Field label="Name" error={fieldErrors.name}>
              <input
                type="text"
                value={form.name}
                onChange={(e) => setForm((f) => ({ ...f, name: e.target.value }))}
                placeholder="e.g. Downtown Loop"
              />
            </Field>
            <Field label="Starting gold" error={fieldErrors.startingGold} hint="Positive whole number">
              <input
                type="text"
                inputMode="numeric"
                value={form.startingGold}
                onChange={(e) => setForm((f) => ({ ...f, startingGold: e.target.value }))}
              />
            </Field>
            <Field label="Base salary" error={fieldErrors.baseSalary} hint="Positive whole number">
              <input
                type="text"
                inputMode="numeric"
                value={form.baseSalary}
                onChange={(e) => setForm((f) => ({ ...f, baseSalary: e.target.value }))}
              />
            </Field>
            <Field
              label="Promotion bonus"
              error={fieldErrors.promotionBonus}
              hint="Zero or a positive whole number"
            >
              <input
                type="text"
                inputMode="numeric"
                value={form.promotionBonus}
                onChange={(e) => setForm((f) => ({ ...f, promotionBonus: e.target.value }))}
              />
            </Field>
          </div>
          <Field label="Start space" error={fieldErrors.startSpaceIndex}>
            <select
              value={form.startSpaceIndex === null ? "" : String(form.startSpaceIndex)}
              onChange={(e) =>
                setForm((f) => ({
                  ...f,
                  startSpaceIndex: e.target.value === "" ? null : Number(e.target.value),
                }))
              }
            >
              <option value="">— choose a space —</option>
              {form.spaces.map((space, index) => (
                <option key={space.localId} value={index}>
                  #{index} — {space.spaceType}
                </option>
              ))}
            </select>
          </Field>
        </section>

        <section className="card">
          <div className="section__header">
            <h2>Spaces ({form.spaces.length})</h2>
            <div className="section__actions">
              <button
                type="button"
                className="button button--small"
                onClick={() => openSpaces.expandAll(form.spaces.map((s) => s.localId))}
              >
                Expand all
              </button>
              <button type="button" className="button button--small" onClick={openSpaces.collapseAll}>
                Collapse all
              </button>
              <button type="button" className="button" onClick={addSpace}>
                Add space
              </button>
            </div>
          </div>
          {fieldErrors.spaces && <Alert kind="error">{fieldErrors.spaces}</Alert>}
          {fieldErrors.requiredSpaceTypes && <Alert kind="error">{fieldErrors.requiredSpaceTypes}</Alert>}

          {form.spaces.map((space, index) => (
            <SpaceItem
              key={space.localId}
              space={space}
              index={index}
              districts={form.districts}
              open={openSpaces.isOpen(space.localId)}
              onToggle={(open) => openSpaces.setOpen(space.localId, open)}
              onChange={(patch) => updateSpace(index, patch)}
              onRemove={() => removeSpace(index)}
              baseValueError={fieldErrors[`spaces.${index}.baseValue`]}
              basePricePercentageError={fieldErrors[`spaces.${index}.basePricePercentage`]}
            />
          ))}
        </section>

        <section className="card">
          <div className="section__header">
            <h2>Paths ({form.paths.length})</h2>
            <div className="section__actions">
              <button
                type="button"
                className="button button--small"
                onClick={() => openPaths.expandAll(form.paths.map((p) => p.localId))}
              >
                Expand all
              </button>
              <button type="button" className="button button--small" onClick={openPaths.collapseAll}>
                Collapse all
              </button>
              <button type="button" className="button" onClick={addPath} disabled={form.spaces.length === 0}>
                Add path
              </button>
            </div>
          </div>
          {form.spaces.length === 0 && <p className="hint">Add at least one space before adding paths.</p>}

          {form.paths.map((path, index) => (
            <PathItem
              key={path.localId}
              path={path}
              index={index}
              spaces={form.spaces}
              open={openPaths.isOpen(path.localId)}
              onToggle={(open) => openPaths.setOpen(path.localId, open)}
              onChange={(patch) => updatePath(index, patch)}
              onRemove={() => removePath(index)}
              fromError={fieldErrors[`paths.${index}.from`]}
              toError={fieldErrors[`paths.${index}.to`]}
              branchOrderError={fieldErrors[`paths.${index}.branchOrder`]}
            />
          ))}
        </section>

        <section className="card">
          <div className="section__header">
            <h2>Districts ({form.districts.length})</h2>
            <div className="section__actions">
              <button
                type="button"
                className="button button--small"
                onClick={() => openDistricts.expandAll(form.districts.map((d) => d.localId))}
              >
                Expand all
              </button>
              <button type="button" className="button button--small" onClick={openDistricts.collapseAll}>
                Collapse all
              </button>
              <button type="button" className="button" onClick={addDistrict}>
                Add district
              </button>
            </div>
          </div>
          <p className="hint">
            Districts are optional. Assign spaces to a district using the space's "District" field
            above.
          </p>

          {form.districts.map((district, index) => (
            <DistrictItem
              key={district.localId}
              district={district}
              index={index}
              spaceCount={spaceCountForDistrict(form.spaces, index)}
              open={openDistricts.isOpen(district.localId)}
              onToggle={(open) => openDistricts.setOpen(district.localId, open)}
              onChange={(patch) => updateDistrict(index, patch)}
              onRemove={() => removeDistrict(index)}
              onProgressionChange={(level, field, value) =>
                setProgressionValue(index, level, field, value)
              }
              nameError={fieldErrors[`districts.${index}.name`]}
              colorHexError={fieldErrors[`districts.${index}.colorHex`]}
              minimumStockPercentageError={fieldErrors[`districts.${index}.minimumStockPercentage`]}
              fieldErrors={fieldErrors}
            />
          ))}
        </section>

        <button type="submit" className="button button--primary" disabled={submitting}>
          {submitting ? "Creating…" : "Create board"}
        </button>
      </form>
    </div>
  );
}

// ---- Space item ----

interface SpaceItemProps {
  space: SpaceFormState;
  index: number;
  districts: DistrictFormState[];
  open: boolean;
  onToggle: (open: boolean) => void;
  onChange: (patch: Partial<SpaceFormState>) => void;
  onRemove: () => void;
  baseValueError?: string;
  basePricePercentageError?: string;
}

function SpaceItem({
  space,
  index,
  districts,
  open,
  onToggle,
  onChange,
  onRemove,
  baseValueError,
  basePricePercentageError,
}: SpaceItemProps) {
  const summaryParts: string[] = [space.spaceType];
  if (space.spaceType === "SHOP" && space.baseValue) summaryParts.push(`value ${space.baseValue}`);
  if (space.districtIndex !== null) {
    const district = districts[space.districtIndex];
    summaryParts.push(district?.name ? district.name : `district #${space.districtIndex}`);
  }

  return (
    <Collapsible
      title={`Space #${index}`}
      summary={summaryParts.join(" · ")}
      open={open}
      onToggle={onToggle}
      onRemove={onRemove}
      hasError={!!baseValueError || !!basePricePercentageError}
    >
      <div className="grid grid--2">
        <Field label="Space type">
          <select
            value={space.spaceType}
            onChange={(e) => {
              const spaceType = e.target.value as SpaceType;
              onChange(
                spaceType === "SHOP"
                  ? { spaceType }
                  : { spaceType, baseValue: "", basePricePercentage: "" },
              );
            }}
          >
            {SPACE_TYPES.map((type) => (
              <option key={type} value={type}>
                {type}
              </option>
            ))}
          </select>
        </Field>
        <Field label="District">
          <select
            value={space.districtIndex === null ? "" : String(space.districtIndex)}
            onChange={(e) =>
              onChange({ districtIndex: e.target.value === "" ? null : Number(e.target.value) })
            }
          >
            <option value="">— none —</option>
            {districts.map((district, districtIndex) => (
              <option key={district.localId} value={districtIndex}>
                #{districtIndex}
                {district.name ? `: ${district.name}` : ""}
              </option>
            ))}
          </select>
        </Field>
      </div>

      {space.spaceType === "SHOP" && (
        <div className="grid grid--2">
          <Field label="Base value" error={baseValueError} hint="Positive whole number">
            <input
              type="text"
              inputMode="numeric"
              value={space.baseValue}
              onChange={(e) => onChange({ baseValue: e.target.value })}
            />
          </Field>
          <Field
            label="Base price percentage"
            error={basePricePercentageError}
            hint="Strictly between 0 and 1, exactly 4 digits, e.g. 0.1234"
          >
            <input
              type="text"
              inputMode="decimal"
              value={space.basePricePercentage}
              onChange={(e) => onChange({ basePricePercentage: e.target.value })}
              placeholder="0.1234"
            />
          </Field>
        </div>
      )}
    </Collapsible>
  );
}

// ---- Path item ----

interface PathItemProps {
  path: PathFormState;
  index: number;
  spaces: SpaceFormState[];
  open: boolean;
  onToggle: (open: boolean) => void;
  onChange: (patch: Partial<PathFormState>) => void;
  onRemove: () => void;
  fromError?: string;
  toError?: string;
  branchOrderError?: string;
}

function PathItem({
  path,
  index,
  spaces,
  open,
  onToggle,
  onChange,
  onRemove,
  fromError,
  toError,
  branchOrderError,
}: PathItemProps) {
  const summary =
    path.from !== null && path.to !== null
      ? `#${path.from} → #${path.to} (branch ${path.branchOrder || "0"})`
      : "not set";

  return (
    <Collapsible
      title={`Path #${index}`}
      summary={summary}
      open={open}
      onToggle={onToggle}
      onRemove={onRemove}
      hasError={!!fromError || !!toError || !!branchOrderError}
    >
      <div className="grid grid--3">
        <Field label="From" error={fromError}>
          <select
            value={path.from === null ? "" : String(path.from)}
            onChange={(e) => onChange({ from: e.target.value === "" ? null : Number(e.target.value) })}
          >
            <option value="">— choose —</option>
            {spaces.map((space, spaceIndex) => (
              <option key={space.localId} value={spaceIndex}>
                #{spaceIndex} — {space.spaceType}
              </option>
            ))}
          </select>
        </Field>
        <Field label="To" error={toError}>
          <select
            value={path.to === null ? "" : String(path.to)}
            onChange={(e) => onChange({ to: e.target.value === "" ? null : Number(e.target.value) })}
          >
            <option value="">— choose —</option>
            {spaces.map((space, spaceIndex) => (
              <option key={space.localId} value={spaceIndex}>
                #{spaceIndex} — {space.spaceType}
              </option>
            ))}
          </select>
        </Field>
        <Field
          label="Branch order"
          error={branchOrderError}
          hint="Disambiguates forks (0, 1, 2, ...)"
        >
          <input
            type="text"
            inputMode="numeric"
            value={path.branchOrder}
            onChange={(e) => onChange({ branchOrder: e.target.value })}
          />
        </Field>
      </div>
    </Collapsible>
  );
}

// ---- District item ----

interface DistrictItemProps {
  district: DistrictFormState;
  index: number;
  spaceCount: number;
  open: boolean;
  onToggle: (open: boolean) => void;
  onChange: (patch: Partial<DistrictFormState>) => void;
  onRemove: () => void;
  onProgressionChange: (
    level: number,
    field: "existingShopBoostPercentage" | "newShopBoostPercentage",
    value: string,
  ) => void;
  nameError?: string;
  colorHexError?: string;
  minimumStockPercentageError?: string;
  fieldErrors: Record<string, string>;
}

function DistrictItem({
  district,
  index,
  spaceCount,
  open,
  onToggle,
  onChange,
  onRemove,
  onProgressionChange,
  nameError,
  colorHexError,
  minimumStockPercentageError,
  fieldErrors,
}: DistrictItemProps) {
  const levels = requiredProgressionLevels(spaceCount);
  const hasProgressionError = levels.some(
    (level) =>
      fieldErrors[`districts.${index}.progression.${level}.existing`] ||
      fieldErrors[`districts.${index}.progression.${level}.new`],
  );

  return (
    <Collapsible
      title={`District #${index}${district.name ? `: ${district.name}` : ""}`}
      summary={`${spaceCount} space${spaceCount === 1 ? "" : "s"}`}
      open={open}
      onToggle={onToggle}
      onRemove={onRemove}
      hasError={!!nameError || !!colorHexError || !!minimumStockPercentageError || hasProgressionError}
    >
      <div className="grid grid--3">
        <Field label="Name" error={nameError}>
          <input
            type="text"
            value={district.name}
            onChange={(e) => onChange({ name: e.target.value })}
          />
        </Field>
        <Field label="Color" error={colorHexError} hint="6 hex characters, e.g. 1E90FF">
          <div className="color-input">
            <span
              className="color-swatch"
              style={{ backgroundColor: /^[0-9A-Fa-f]{6}$/.test(district.colorHex) ? `#${district.colorHex}` : "transparent" }}
              aria-hidden="true"
            />
            <input
              type="text"
              value={district.colorHex}
              onChange={(e) => onChange({ colorHex: e.target.value })}
              placeholder="1E90FF"
            />
          </div>
        </Field>
        <Field
          label="Minimum stock percentage"
          error={minimumStockPercentageError}
          hint="Strictly between 0 and 1, exactly 4 digits"
        >
          <input
            type="text"
            inputMode="decimal"
            value={district.minimumStockPercentage}
            onChange={(e) => onChange({ minimumStockPercentage: e.target.value })}
            placeholder="0.5000"
          />
        </Field>
      </div>

      <div className="progressions">
        <h3>Shop value progression</h3>
        {levels.length === 0 ? (
          <p className="hint">
            Assign at least 2 spaces to this district (via each space's "District" field) to
            configure how shop values scale as a player accumulates more of them.
          </p>
        ) : (
          <table className="table">
            <thead>
              <tr>
                <th>Owned shop count</th>
                <th>Existing shop boost %</th>
                <th>New shop boost %</th>
              </tr>
            </thead>
            <tbody>
              {levels.map((level) => {
                const values = district.progressionValues[level] ?? {
                  existingShopBoostPercentage: "",
                  newShopBoostPercentage: "",
                };
                return (
                  <tr key={level}>
                    <td>{level}</td>
                    <td>
                      <input
                        type="text"
                        inputMode="decimal"
                        value={values.existingShopBoostPercentage}
                        placeholder="0.1000"
                        onChange={(e) =>
                          onProgressionChange(level, "existingShopBoostPercentage", e.target.value)
                        }
                      />
                      {fieldErrors[`districts.${index}.progression.${level}.existing`] && (
                        <span className="field__error" role="alert">
                          Required, positive, exactly 4 digits.
                        </span>
                      )}
                    </td>
                    <td>
                      <input
                        type="text"
                        inputMode="decimal"
                        value={values.newShopBoostPercentage}
                        placeholder="0.1000"
                        onChange={(e) =>
                          onProgressionChange(level, "newShopBoostPercentage", e.target.value)
                        }
                      />
                      {fieldErrors[`districts.${index}.progression.${level}.new`] && (
                        <span className="field__error" role="alert">
                          Required, positive, exactly 4 digits.
                        </span>
                      )}
                    </td>
                  </tr>
                );
              })}
            </tbody>
          </table>
        )}
      </div>
    </Collapsible>
  );
}
