export interface WorkflowFieldRuleInput {
  field?: unknown;
  code?: unknown;
  required?: unknown;
  readOnly?: unknown;
  readonly?: unknown;
}

type FieldAuthMap = Record<string, Record<string, unknown>>;

export function getWorkflowFieldKey(field: WorkflowFieldRuleInput, fallback = ''): string {
  return String(field.field || field.code || fallback);
}

export function getWorkflowFieldAuth(
  fieldKey: string,
  fieldAuth: FieldAuthMap | null | undefined
): Record<string, unknown> {
  return fieldAuth?.[fieldKey] || {};
}

export function isWorkflowFieldHidden(
  fieldKey: string,
  fieldAuth: FieldAuthMap | null | undefined
): boolean {
  return Boolean(getWorkflowFieldAuth(fieldKey, fieldAuth).hidden);
}

export function resolveWorkflowFieldRequired(
  field: WorkflowFieldRuleInput,
  auth: Record<string, unknown>,
  globalRequired?: boolean
): boolean {
  if (auth.required !== undefined) {
    return Boolean(auth.required);
  }
  if (globalRequired !== undefined) {
    return Boolean(globalRequired);
  }
  return Boolean(field.required);
}

export function resolveWorkflowFieldReadonly(
  field: WorkflowFieldRuleInput,
  auth: Record<string, unknown>,
  globalReadonly?: boolean
): boolean {
  let readonly = Boolean(field.readOnly ?? field.readonly);
  if (globalReadonly !== undefined) {
    readonly = Boolean(globalReadonly);
  }
  if (auth.readonly !== undefined) {
    readonly = Boolean(auth.readonly);
  }
  if (auth.readOnly !== undefined) {
    readonly = Boolean(auth.readOnly);
  }
  return readonly;
}
