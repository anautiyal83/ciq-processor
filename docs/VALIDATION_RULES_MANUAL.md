# CIQ Validator — Validation Rules Configuration Manual

## Overview

Validation rules are defined in a YAML file, one file per `{NODE_TYPE}_{ACTIVITY}` combination.
No code changes are ever needed — drop a new YAML file to support a new use case.

**File naming convention:**
```
{NODE_TYPE}_{ACTIVITY}_validation-rules.yaml
```
Example: `SBC_FIXED_LINE_CONFIGURATION_validation-rules.yaml`

---

## Top-Level Structure

```yaml
validateIndexSheets: true        # optional, default: false
validateNodeIds: true             # optional, default: false

sheets:
  <SheetName>:
    columns:
      <ColumnName>:
        <rule>: <value>
        ...
      <AnotherColumn>:
        ...
  <AnotherSheet>:
    ...
```

---

## Top-Level Flags

### `validateIndexSheets`

| Type | Default |
|---|---|
| `boolean` | `false` |

When `true`, checks that every table listed in the Index sheet has a corresponding JSON file, and
that every JSON file present is listed in the Index. Reports count mismatches as global errors.

```yaml
validateIndexSheets: true
```

---

### `validateNodeIds`

| Type | Default |
|---|---|
| `boolean` | `false` |

When `true`, every `Node` column value in every sheet is validated against the set of node names
in the `Node_ID` sheet. Invalid nodes are reported as per-row errors. No YAML column rule needed.

```yaml
validateNodeIds: true
```

---

## Sheet Configuration

Each entry under `sheets:` maps a sheet (table) name to its column rules.
Sheets not listed here receive no column-level validation (global checks still apply).

```yaml
sheets:
  CRFTargetList:
    columns:
      ...
  PeeringSDPProfileTable:
    columns:
      ...
```

Column names must match the header row values in the CIQ Excel exactly (case-sensitive).
For nested fields use dot notation: `Record.FIELD_NAME` or `Record.SubTable.FIELD_NAME`.

---

## Column Rules

Each column entry supports any combination of the following rules.
All rules are optional — omit a rule to skip that check for the column.

---

### `required`

| Type | Default |
|---|---|
| `boolean` | `false` |

The column must have a non-null, non-blank value in every row. Validation stops for that cell
if this check fails (no further rules are evaluated on a blank required field).

```yaml
Node:
  required: true
```

**Error message:** `Column 'Node' is required but is empty`

---

### `requiredWhen`

| Type | Default |
|---|---|
| `object` | — |

The column is required only when another column in the same row has a specific value.
Comparison is case-insensitive. Blank values pass if the trigger condition is not met.

```yaml
ActionKey:
  requiredWhen:
    column: Action
    value: MODIFY
```

**Sub-fields:**

| Field | Description |
|---|---|
| `column` | Name of the trigger column |
| `value` | Value that triggers the requirement (case-insensitive match) |

**Error message:** `Column 'ActionKey' is required when Action=MODIFY but is empty`

---

### `allowedValues`

| Type | Default |
|---|---|
| `list of strings` | — |

The value must match one of the listed strings. Comparison is **case-insensitive**.
Blank values always pass (combine with `required` or `requiredWhen` to reject blanks).

---

### `dropdownDisabled`

| Type | Default |
|---|---|
| `boolean` | `false` |

When `true`, the CIQ generator **suppresses the Excel dropdown** for this column even when
`allowedValues` is defined. Use this for columns whose value list is too long for Excel's
255-character inline-list limit (many values or individually long strings).

> **Validation is not affected** — `allowedValues` is still enforced at submission time.
> Only the UI dropdown is omitted from the generated CIQ template.

```yaml
Record.CodecBandwidth.CODEC:
  dropdownDisabled: true        # 34 values — exceeds Excel 255-char dropdown limit
  allowedValues: [PCMU, PCMA, G723, G729, AMR, EVRC0, EVRCB0, "G726-16", ...]
```

**When to use:**
- The joined allowedValues string (comma-separated) exceeds 255 characters
- A quick check: count characters in `value1,value2,...,valueN` — if > 255, set this flag

**Error message (validation):** unchanged — `Value 'X' not in allowed values: [...]`

```yaml
Action:
  required: true
  allowedValues: [CREATE, DELETE, MODIFY]
```

```yaml
Record.TARGET_TYPE:
  allowedValues: ["Type 1", "Type 2", "IBCF Trunk Group"]
```

**Error message:** `Value 'CREATEE' not in allowed values: [CREATE, DELETE, MODIFY]`

> **YAML quoting requirement:** The following words have special meaning in YAML 1.1 and
> **must always be quoted** when used as `allowedValues` entries, otherwise SnakeYAML will
> parse them as booleans or null instead of strings:
>
> | Unquoted (wrong) | Parsed as | Quoted (correct) |
> |---|---|---|
> | `Yes`, `YES`, `yes` | boolean `true` | `"Yes"` |
> | `No`, `NO`, `no` | boolean `false` | `"No"` |
> | `True`, `TRUE`, `true` | boolean `true` | `"True"` |
> | `False`, `FALSE`, `false` | boolean `false` | `"False"` |
> | `NULL`, `Null`, `null`, `~` | null | `"NULL"` |
> | `On`, `ON`, `on` | boolean `true` | `"On"` |
> | `Off`, `OFF`, `off` | boolean `false` | `"Off"` |
>
> Bare numeric values (e.g. `1`, `2`, `0`) should also be quoted when they represent
> string cell values: `"1"`, `"2"`, `"0"`.
>
> ```yaml
> # Wrong — Yes/No/NULL parsed as boolean/null by SnakeYAML
> Record.FIELD:
>   allowedValues: [Yes, No, NULL]
>
> # Correct
> Record.FIELD:
>   allowedValues: ["Yes", "No", "NULL"]
> ```

---

### `minLength` / `maxLength`

| Type | Default |
|---|---|
| `integer` | — |

Validates the **string length** of the cell value (number of characters).
Both bounds are inclusive. Blank values always pass.

```yaml
NAME:
  required: true
  minLength: 1
  maxLength: 19
```

**Error messages:**
- `Value length 0 is below minimum length 1`
- `Value length 25 exceeds maximum length 19`

---

### `integer`

| Type | Default |
|---|---|
| `boolean` | `false` |

The value must be parseable as a whole number (long). Leading/trailing whitespace is trimmed
before parsing. When `true`, also enables `minValue` and `maxValue` checks.

```yaml
ID:
  integer: true
```

**Error message:** `Value 'abc' is not a valid integer`

---

### `minValue` / `maxValue`

| Type | Default |
|---|---|
| `long` | — |

Validates the **numeric value** against inclusive lower and upper bounds.
Only evaluated when `integer: true` is also set and the value parses successfully.

```yaml
ID:
  required: true
  integer: true
  minValue: 1
  maxValue: 6000
```

**Error messages:**
- `Value 0 is below minimum 1`
- `Value 7000 exceeds maximum 6000`

---

### `allowedRanges`

| Type | Default |
|---|---|
| `list of range objects` | — |

The value must be an integer that falls within **at least one** of the listed ranges (union).
Each range has `min` and `max` (both inclusive). Use a single-point range (`min == max`) to
allow an exact value.

```yaml
Record.CRFTargetListEntry.TARGET_ID:
  allowedRanges:
    - min: 1
      max: 1024
    - min: 5001
      max: 7048
    - min: 0
      max: 0       # also allows exactly 0
```

**Error message:** `Value 2000 is not within any allowed range: [1..1024, 5001..7048, 0]`

> **Note:** `allowedRanges` and `integer` are independent. `allowedRanges` always parses the
> value as a long internally; you do not need to also set `integer: true`.

---

### `pattern`

| Type | Default |
|---|---|
| `string` (Java regex) | — |

The value must match the given regular expression (full match via `Pattern.matches()`).
Blank values always pass. Invalid regex patterns are logged as warnings and skipped.

```yaml
Record.IP_ADDRESS:
  pattern: "\\d{1,3}\\.\\d{1,3}\\.\\d{1,3}\\.\\d{1,3}"
```

**Error message:** `Value '999.x.1.1' does not match pattern: \d{1,3}\.\d{1,3}\.\d{1,3}\.\d{1,3}`

---

### `allowedValuesWhen`

| Type | Default |
|---|---|
| `list of condition objects` | — |

Defines conditional constraints: when a trigger column has a specific value, this column's value
is checked against a set of allowed values. Multiple entries are evaluated independently.

Each entry has three fields:

| Field | Description |
|---|---|
| `column` | The trigger column in the same row |
| `value` | The trigger value (case-insensitive match) |
| `allowedValues` | Allowed values when condition is met. **Empty list = must be blank.** |

**Use case 1 — Column must be blank when Action=MODIFY** (non-modifiable field):

```yaml
Record.IMMUTABLE_FIELD:
  allowedValuesWhen:
    - column: Action
      value: MODIFY
      allowedValues: []     # empty list → must be blank when Action=MODIFY
```

**Error message:** `Column 'Record.IMMUTABLE_FIELD' must be blank when Action=MODIFY but found 'some-value'`

**Use case 2 — Column Y value depends on Column X value**:

```yaml
ColumnY:
  allowedValuesWhen:
    - column: ColumnX
      value: A
      allowedValues: [B]
    - column: ColumnX
      value: C
      allowedValues: [D, E]
```

**Error message:** `Value 'X' is not allowed when ColumnX=A. Allowed values: [B]`

> **Notes:**
> - A blank cell always passes the non-empty `allowedValues` check (combine with `required` or `requiredWhen` to also enforce presence).
> - For the empty-list (must-be-blank) case, a blank cell passes — the rule only fires when the field has a value.
> - Multiple conditions in the same list are all evaluated; a cell can trigger more than one entry if multiple conditions match.

---

### `crossRef`

| Type | Default |
|---|---|
| `object` | — |

The value must exist in a specified column of another (or the same) sheet.
Results are cached per sheet+column pair.

```yaml
Node:
  required: true
  crossRef:
    sheet: "_index"
    column: node
```

**Sub-fields:**

| Field | Description |
|---|---|
| `sheet` | Sheet name to look up, or `_index` for the index node list |
| `column` | Column name within that sheet whose values form the valid set |

**Special value `_index`:** References the set of node names from the `Node_ID` sheet
(the `niamMapping` keys). Use `column: node` with this special sheet name.

**Error message:** `Value 'NODE_X' not found in _index.node. Valid values: [NODE_A, NODE_B]`

---

## Rule Evaluation Order

For each cell, rules are evaluated in this order:

1. `required` — if blank and required, report error and **stop**
2. `requiredWhen` — if blank and trigger condition met, report error and **stop**
3. *(blank values skip all remaining checks except `allowedValuesWhen` with empty list)*
4. `allowedValues`
5. `minLength`
6. `maxLength`
7. `integer` + `minValue` + `maxValue`
8. `allowedRanges`
9. `pattern`
10. `crossRef`
11. `allowedValuesWhen` — all matching conditions evaluated independently

All non-blank checks (4–11) are independent — all violations in a cell are reported.

---

## Complete Example

```yaml
# Validation rules for: SBC - FIXED_LINE_CONFIGURATION
validateIndexSheets: true
validateNodeIds: true

sheets:
  CRFTargetList:
    columns:
      Node:
        required: true
        crossRef:
          sheet: "_index"
          column: node
      Action:
        required: true
        allowedValues: [CREATE, DELETE, MODIFY]
      ActionKey:
        requiredWhen:
          column: Action
          value: MODIFY
      SubAction:
        requiredWhen:
          column: Action
          value: MODIFY
        allowedValues: [ADD, DEL, MOD]
      ID:
        required: true
        integer: true
        minValue: 1
        maxValue: 6000
      NAME:
        required: true
        minLength: 1
        maxLength: 19
      Record.TARGET_LIST_ID:
        requiredWhen:
          column: Action
          value: CREATE
        integer: true
        minValue: 1
        maxValue: 100000
      Record.DESCRIPTION:
        maxLength: 32
      Record.LOCATION:
        allowedValues: [Access, Core]
      Record.TARGET_TYPE:
        allowedValues: ["Type 1", "Type 2", "IBCF Trunk Group"]
      Record.IMMUTABLE_CODE:
        allowedValuesWhen:
          - column: Action
            value: MODIFY
            allowedValues: []   # must be blank when Action=MODIFY
      Record.CRFTargetListEntry.TARGET_ID:
        allowedRanges:
          - min: 1
            max: 1024
          - min: 5001
            max: 7048
          - min: 0
            max: 0
      Record.CRFTargetListEntry.TRANSPORT_TYPE:
        allowedValues: [UDP, TCP, SCTP, TLS, ANY]
      Record.CRFTargetListEntry.WEIGHT:
        integer: true
        minValue: 0
        maxValue: 100

  PeeringSDPProfileTable:
    columns:
      Node:
        required: true
        crossRef:
          sheet: "_index"
          column: node
      Action:
        required: true
        allowedValues: [CREATE, DELETE, MODIFY]
      ID:
        required: true
        integer: true
        minValue: 1
```

---

## Quick Reference

| Rule | Applies to | Blank passes? | Case-sensitive? |
|---|---|---|---|
| `required` | Any | No | — |
| `requiredWhen` | Any | Conditional | No (trigger match) |
| `allowedValues` | Any | Yes | No |
| `dropdownDisabled` | Any (generator only) | — | — |
| `minLength` / `maxLength` | Any | Yes | — |
| `integer` | Numeric | Yes | — |
| `minValue` / `maxValue` | Numeric (needs `integer: true`) | Yes | — |
| `allowedRanges` | Numeric | Yes | — |
| `pattern` | Any | Yes | Depends on regex |
| `crossRef` | Any | Yes | Yes (exact match) |
| `allowedValuesWhen` | Any | Yes (non-blank check) / No (must-be-blank check) | No (trigger + values) |
