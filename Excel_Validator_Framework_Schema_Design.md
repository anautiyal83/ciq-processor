# Excel Validator Framework — Schema Design

## Overview

The validation framework reads a YAML rules file and applies it to an Excel workbook (`.xlsx`).
Every aspect of validation — which sheets are required, which columns must be non-blank, what
values are allowed, how columns relate to each other — is declared in YAML.  Zero code changes
are needed to support a new node type or activity.

---

## Top-Level YAML Structure

```
version: "1.0"          ← informational; not validated

settings:               ← global read settings (can be overridden per sheet)

workbook_rules:         ← cross-sheet constraints evaluated after all rows are read

validators:             ← registry of custom Java validator plug-ins

sheets:                 ← per-sheet column definitions and row rules

report_output:          ← validation report naming and output formats

outputs:                ← post-validation aggregates (computed only on PASSED)

json_output:            ← MOP JSON structure template and segregation config
```

---

## 1. Global Settings (`settings:`)

Applied to every sheet unless overridden at the sheet level.

| Field | Default | Description |
|---|---|---|
| `headerRow` | `0` | 0-based row index of the header row |
| `dataStartRow` | `1` | 0-based row index where data rows begin |
| `trimCellValues` | `true` | Strip leading/trailing whitespace from each cell value |
| `ignoreBlankRows` | `true` | Skip rows where every cell is blank |
| `caseSensitiveHeaders` | `false` | Header matching is case-insensitive by default |
| `caseSensitiveValues` | `true` | Cell value comparisons are case-sensitive by default |

```yaml
settings:
  headerRow: 0
  dataStartRow: 1
  trimCellValues: true
  ignoreBlankRows: true
  caseSensitiveHeaders: false
  caseSensitiveValues: true
```

Sheet-level overrides:

```yaml
sheets:
  ANNOUNCEMENT_FILES:
    settings:
      ignoreBlankRows: false   # blank rows in the middle of the data are treated as errors
```

---

## 2. Custom Validator Registry (`validators:`)

Register Java classes that implement the `CellValidator` interface.  Once registered, reference
them from any column via `validator: <name>`.

```yaml
validators:
  cidrV4:
    class: "com.nokia.ciq.validator.custom.CidrV4Validator"
    description: "Validates IPv4 CIDR notation (e.g. 192.168.1.0/24)"

  neName:
    class: "com.nokia.ciq.validator.custom.NeNameValidator"
    description: "Validates NE names against the naming convention"
```

---

## 3. Sheet Definitions (`sheets:`)

```yaml
sheets:
  <SheetName>:
    required: true              # validation fails if this sheet is missing from the workbook
    aliases: [AltName, OTHER]   # alternative sheet names accepted in place of <SheetName>
    settings:                   # overrides global settings for this sheet only
      headerRow: 0
      ignoreBlankRows: false
    columns:
      <ColumnName>:
        ...                     # see Column Properties below
    rules:
      ...                       # see Row Rules below
```

### 3.1 Column Properties (common to all types)

| Property | Type | Default | Description |
|---|---|---|---|
| `required` | boolean | `false` | Cell must be non-blank |
| `type` | string | `string` | Column type — see Section 3.2 |
| `aliases` | list | `[]` | Alternative header names treated as equivalent |
| `unique` | boolean | `false` | All values in this column must be distinct within the sheet |
| `ignoreCase` | boolean | `false` | Case-insensitive comparison for `allowedValues` |
| `description` | string | — | Plain-language description shown in the `Column_Guide` sheet |
| `validator` | string | — | Name of a registered custom validator (see Section 2) |
| `messages` | map | — | Override default error messages (see Section 3.3) |

### 3.2 Column Types

#### `string` (default)

Free-form text with optional length, pattern, and value constraints.

```yaml
ColumnName:
  required: true
  type: string
  aliases: [AltHeader, ALTERNATE]
  unique: false
  ignoreCase: false
  minLength: 1
  maxLength: 255
  pattern: "^[A-Z]{2}\\d{4}$"          # Java regex — full match required
  patternMessage: "Must be two uppercase letters followed by four digits"
  allowedValues: [CREATE, DELETE, MODIFY]
  ref: OtherSheet.OtherColumn           # value must exist in OtherSheet.OtherColumn
  validator: cidrV4                     # custom validator name
  messages:
    required:  "This field is mandatory"
    minLength: "Must be at least 1 character"
    maxLength: "Cannot exceed 255 characters"
    pattern:   "Invalid format"
    type:      "Generic type error fallback"
```

> **`pattern`**: If the pattern is syntactically invalid, the engine reports a configuration
> error on every row rather than silently passing.

#### `integer`

Whole number.  Accepts optional range bounds and multi-range OR logic.

```yaml
ColumnName:
  required: true
  type: integer
  minValue: 1          # inclusive lower bound
  maxValue: 65535      # inclusive upper bound
  allowedRanges:       # value must fall within at least one band (OR logic)
    - { min: 1,    max: 1024  }
    - { min: 5001, max: 7048  }
  messages:
    min:  "Value is below the minimum"
    max:  "Value exceeds the maximum"
    type: "Must be a whole number"
```

#### `decimal`

Floating-point number with optional precision control.

```yaml
ColumnName:
  required: true
  type: decimal
  minDecimal: 0.00
  maxDecimal: 9999.99
  precision: 2         # max decimal places; e.g. 1.234 fails when precision: 2
  messages:
    precision: "At most 2 decimal places allowed"
    type:      "Must be a decimal number"
```

#### `datetime`

Date and/or time value validated with a `java.text.SimpleDateFormat` pattern.

```yaml
ColumnName:
  required: false
  type: datetime
  format: "yyyy-MM-dd'T'HH:mm:ss"   # default; also accepts "yyyy-MM-dd", "HH:mm:ss", etc.
  messages:
    format: "Expected format: yyyy-MM-dd'T'HH:mm:ss"
```

> Use `format: "yyyy-MM-dd"` for date-only columns and `format: "HH:mm:ss"` for time-only.

#### `email`

RFC 5322 `local@domain` format.  Supports comma-separated multiple addresses.

```yaml
ColumnName:
  required: true
  type: email
  multi: false         # true = comma-separated list of addresses is valid
  messages:
    type: "Must be a valid email address"
```

#### `ip`

IP address with version control.

```yaml
ColumnName:
  required: false
  type: ip
  accepts: both        # ipv4 (default) | ipv6 | both
  messages:
    type: "Must be a valid IP address"
```

#### `sheetRef`

Value must match an existing sheet name in the workbook.  Used in Index sheets where a column
holds data sheet names.

```yaml
Tables:
  required: true
  sheetRef: true
  sheetRefIgnoreCase: true   # "announcement_files" matches "ANNOUNCEMENT_FILES"
  messages:
    type: "Sheet name not found in workbook"
```

#### `custom`

Delegates validation entirely to a registered custom validator.

```yaml
ColumnName:
  required: false
  type: custom
  validator: neName
```

### 3.3 Custom Error Messages (`messages:`)

Every column type supports a `messages:` block to override default error text.  The keys
correspond to the check that failed:

| Key | Triggered by |
|---|---|
| `required` | Blank cell when `required: true` |
| `type` | Generic type/format failure |
| `minLength` / `maxLength` | String length out of range |
| `pattern` | Regex mismatch |
| `min` / `max` | Integer out of range |
| `precision` | Too many decimal places |
| `format` | Datetime format mismatch |

---

## 4. Row Rules (`rules:`)

Defined under `sheets.<SheetName>.rules:`.  Applied to every data row in the sheet.

### Column Reference Convention

Applies to **all** rule types — `require`, `forbid`, `when.column`, `compare`, `one_of`,
`only_one`, `all_or_none`, `sum`, and `equals`.

| Syntax | Meaning |
|---|---|
| `ColumnName` | Column in the **current** sheet |
| `SheetName.ColumnName` | Column in the **named** sheet |

### 4.1 `require`

Column (or cross-sheet value) must be non-blank.

```yaml
rules:
  # unconditional — same sheet
  - require: ActionKey

  # unconditional — cross-sheet: value must exist in Node_Details.Node_Name
  - require: Node_Details.Node_Name

  # conditional — apply only when Action equals MODIFY
  - require: ActionKey
    when:
      column: Action
      operator: equals
      value: MODIFY
```

### 4.2 `forbid`

Column (or cross-sheet value) must be blank.

```yaml
rules:
  # forbid same-sheet column when another is filled
  - forbid: RollbackId
    when:
      column: Action
      operator: notEquals
      value: DELETE

  # forbid cross-sheet value
  - forbid: Blacklist.NodeId
```

### 4.3 `compare`

Cross-column comparison.  Format: `"[Sheet.]ColA operator [Sheet.]ColB"`.

Supported operators: `equals`, `notEquals`, `greaterThan`, `greaterThanOrEquals`,
`lessThan`, `lessThanOrEquals` (symbol aliases `==`, `!=`, `>`, `>=`, `<`, `<=` also accepted).

```yaml
rules:
  - compare: "StartPort lessThanOrEquals EndPort"
  - compare: "Pricing.BasePrice lessThan Pricing.MaxPrice"
```

### 4.4 Group Presence

```yaml
rules:
  # at least one must be non-blank
  - one_of: [IPv4Address, IPv6Address]

  # exactly one must be non-blank
  - only_one: [StaticRoute, DynamicRoute]

  # all filled or all blank
  - all_or_none: [StartDate, EndDate, Duration]
```

### 4.5 `sum`

Sum of listed columns must equal the target column.

```yaml
rules:
  - sum: [VoiceSlots, DataSlots, SignallingSlots]
    equals: TotalSlots
```

### 4.6 `when:` Conditions

All row rules accept an optional `when:` block.  The rule is applied only when the condition
evaluates to true.

#### Simple condition

```yaml
- require: ActionKey
  when:
    column: Action         # ColumnName or SheetName.ColumnName
    operator: equals
    value: MODIFY
```

#### Supported operators

| Operator | Applies to | Description |
|---|---|---|
| `equals` | String / Number | Column value equals `value` |
| `notEquals` | String / Number | Column value does not equal `value` |
| `blank` | Any | Column is empty |
| `notBlank` | Any | Column is non-empty |
| `contains` | String | Column value contains `value` as a substring |
| `greaterThan` | Number | Column value > `value` |
| `greaterThanOrEquals` | Number | Column value >= `value` |
| `lessThan` | Number | Column value < `value` |
| `lessThanOrEquals` | Number | Column value <= `value` |
| `exists` | Cross-sheet | Current row's value is found in `sheet.column` |
| `notExists` | Cross-sheet | Current row's value is not found in `sheet.column` |

#### Cross-sheet existence condition

```yaml
- require: NIAM_NAME
  when:
    sheet: Node_Details        # look up current row's value in this sheet
    column: Node_Name
    operator: exists           # exists | notExists
```

#### Compound condition (AND / OR nesting)

```yaml
- require: OverrideValue
  when:
    all:
      - column: Action
        operator: equals
        value: MODIFY
      - any:
          - column: Flag
            operator: equals
            value: OVERRIDE
          - column: OverrideSrc
            operator: notBlank
```

---

## 5. Workbook-Level Rules (`workbook_rules:`)

Evaluated after all sheets are read.  Each rule checks a relationship between two columns
(possibly in different sheets).

### `subset`

Every value in `from` must exist in `to`.

```yaml
workbook_rules:
  - subset:
      from: ANNOUNCEMENT_FILES.GROUP
      to:   Index.GROUP
```

### `superset`

Every value in `to` must exist in `from`.

```yaml
  - superset:
      from: Index.CRGroup
      to:   USER_ID.CRGroup
```

### `match`

Bi-directional equality — the two columns must contain exactly the same set of values.

```yaml
  - match:
      from: Index.CRGroup
      to:   USER_ID.CRGroup
```

### `unique`

Values (or composite keys across multiple columns) must not appear more than once.

```yaml
  - unique:
      columns: [Index.Node, Index.Tables]
```

---

## 6. Validation Report Output (`report_output:`)

Controls the base file name and formats for the generated validation report files.

```yaml
report_output:
  formats:  [JSON, HTML, MSEXCEL]                          # one or more
  filename: "{nodeType}_{activity}_VALIDATION_REPORT"      # {nodeType} and {activity} are substituted
```

`{nodeType}` and `{activity}` are substituted from the `--node-type` and `--activity` CLI arguments.
When `report_output` is absent, all three formats are written and the filename defaults to
`{nodeType}_{activity}_VALIDATION_REPORT`.

> **HTML template:** The HTML report file is rendered with a built-in layout by default.  Passing
> `--report-template-name` (and optionally `--report-template-path`) on the CLI switches to an
> external template file.  Template location is always a CLI parameter — it is **not** part of
> `report_output` in the YAML.

---

## 7. Post-Validation Outputs (`outputs:`)

Computed **only when validation PASSES**.  Each entry is added to the validation report's
`parameters` map and printed to stdout as `KEY=VALUE`.

```yaml
outputs:
  <YAML_KEY>:
    name: <PARAM_NAME>     # optional — overrides YAML_KEY as the emitted parameter name
    sheet: <SheetName>     # required
    column: <ColumnName>   # required for distinct, sum, and group; optional for count
    aggregate: distinct    # distinct | count | sum | group
    separator: ","         # for distinct and group; default ","
    groupBy: <ColumnName>  # required for group aggregate — column whose values are collected per group
```

### Aggregates

| Aggregate | Column required | `groupBy` required | Description |
|---|---|---|---|
| `distinct` | Yes | No | Sorted, deduplicated non-blank values joined by `separator` |
| `count` | No | No | Count of non-blank values when `column` given; total row count otherwise |
| `sum` | Yes | No | Numeric sum of `column` values; non-numeric cells silently skipped |
| `group` | Yes | Yes | For each distinct value of `column`, emit `<KEY>_<value>=<groupBy values>` |

### `group` aggregate detail

For each distinct non-blank value of `column`, one parameter is emitted:

```
<YAML_KEY>_<columnValue>=<comma-separated groupBy values>
```

Example: `column: CRGroup`, `groupBy: Node` → emits `NODES_CR1=MRF1,MRF2` and `NODES_CR2=MRF3`.

### Examples

```yaml
outputs:
  # Distinct CR numbers → CR_LIST=CR1,CR2,CR3
  CR_LIST:
    sheet: Index
    column: CRGroup
    aggregate: distinct
    separator: ","

  # Count of Index rows → CR_COUNT=5
  CR_COUNT:
    sheet: Index
    aggregate: count

  # Count of non-blank GROUP values → GROUP_COUNT=6
  GROUP_COUNT:
    sheet: ANNOUNCEMENT_FILES
    column: GROUP
    aggregate: count

  # Numeric sum → TOTAL_SIZE_KB=2048
  TOTAL_SIZE_KB:
    sheet: ANNOUNCEMENT_FILES
    column: FILE_SIZE_KB
    aggregate: sum

  # 'name' overrides the YAML key → UNIQUE_NODES=MRF1,MRF2
  node_output:
    name: UNIQUE_NODES
    sheet: Index
    column: Node
    aggregate: distinct

  # Group aggregate → NODES_CR1=MRF1,MRF2   NODES_CR2=MRF3,MRF4
  NODES:
    sheet:     Index
    column:    CRGroup
    aggregate: group
    groupBy:   Node
    separator: ","
```

> `REPORT_FILENAME` is always emitted regardless of the `outputs:` section.
> All other `PASSED` parameters require an explicit `outputs:` entry.

---

## 8. JSON Output Template (`json_output:`)

Defines the structure of the MOP JSON file(s) written when validation passes and
`--mop-json-dir` is provided.

```yaml
json_output:
  output_mode: single          # single | individual

  segregate_by:                # required when output_mode: individual
    sheet:  Index
    column: CRGroup
    as:     $cr                # variable available in data template

  data:                        # free-form YAML template
    <key>: <value>
    <key>:
      _each: "<directive>"
      <key>: <value>
```

### `output_mode`

| Value | Output |
|---|---|
| `single` | One JSON file for the entire workbook: `{NODE_TYPE}_{ACTIVITY}.json` |
| `individual` | One folder per distinct `segregate_by` value, one JSON file per folder |

### `_each` directive

| Syntax | Meaning |
|---|---|
| `_each: "DISTINCT <Sheet>.<Column> AS $var"` | One array element per distinct column value |
| `_each: <SheetName>` | One array element per row of the sheet |
| `_each: "<Sheet> WHERE <Col> = <value>"` | One array element per matching row |

### Scalar value expressions

| Syntax | Meaning |
|---|---|
| `key: $var` | Inline variable (from enclosing `_each` or `segregate_by`) |
| `key: <Sheet>.<Column>` | First non-blank value from that sheet+column in the current scope |
| `key: "<Sheet>.<Col> WHERE <Sheet>.<Filter> = $var"` | First value where filter matches |

### Example

```yaml
json_output:
  output_mode: single
  data:
    nodeType: MRF
    activity: ANNOUNCEMENT_LOADING
    nodes:
      _each: "DISTINCT Index.Node AS $node"
      node:    $node
      crGroup: Index.CRGroup
      email:   "USER_ID.EMAIL WHERE USER_ID.CRGroup = Index.CRGroup"
      niamID:  "Node_Details.'NIAM NAME' WHERE Node_Details.Node_Name = $node"
      tableData:
        _each: "ANNOUNCEMENT_FILES WHERE GROUP = Index.GROUP"
        INPUT_FILE:           INPUT_FILE
        MRF_DESTINATION_PATH: MRF_DESTINATION_PATH
```

**Output:**

```json
{
  "nodeType": "MRF",
  "activity": "ANNOUNCEMENT_LOADING",
  "nodes": [
    {
      "node": "MRF1",
      "crGroup": "CR1",
      "email": "engineer@nokia.com",
      "niamID": "RJ-NOKIA-MRF-RJJVRMR01-CLI",
      "tableData": [
        { "INPUT_FILE": "audio.tar", "MRF_DESTINATION_PATH": "/var/opt/clips/" }
      ]
    }
  ]
}
```

---

## 9. HTML Report Template

The HTML report is rendered from an external template file when `--report-template-name` is
passed on the CLI.  The template is a plain HTML file with `{{placeholder}}` substitution.

### Scalar placeholders

| Placeholder | Value |
|---|---|
| `{{status}}` | `PASSED` or `FAILED` |
| `{{nodeType}}` | Node type |
| `{{activity}}` | Activity name |
| `{{totalErrors}}` | Total error count |
| `{{sheetsCount}}` | Number of sheets checked |
| `{{passedSheets}}` | Sheets with no errors |
| `{{failedSheets}}` | Sheets with errors |
| `{{totalRows}}` | Total data rows checked |
| `{{generatedAt}}` | Timestamp |
| `{{params.KEY}}` | Any parameter from the `outputs:` section |

### Section blocks

| Block | Repeats for |
|---|---|
| `{{#sheets}}...{{/sheets}}` | Each sheet result |
| `{{#errors}}...{{/errors}}` | Each error within a sheet |
| `{{#globalErrors}}...{{/globalErrors}}` | Global (Index-level) errors |
| `{{#parameters}}...{{/parameters}}` | Each output parameter |

### Per-sheet placeholders (inside `{{#sheets}}`)

| Placeholder | Value |
|---|---|
| `{{sheetName}}` | Sheet name |
| `{{sheetStatus}}` | `PASS` or `FAIL` |
| `{{rowsChecked}}` | Number of rows checked |
| `{{errorCount}}` | Number of errors |
| `{{sheetHeaderClass}}` | CSS class for the header (`pass-header` or `fail-header`) |
| `{{sheetBadgeClass}}` | CSS class for the badge (`badge-pass` or `badge-fail`) |
| `{{sheetPassMsg}}` | Message shown when sheet passes |
| `{{sheetErrorTableStyle}}` | `display:none` when sheet has no errors |

### Conditionals

| Block | Renders when |
|---|---|
| `{{#if_passed}}...{{/if_passed}}` | Status is PASSED |
| `{{#if_failed}}...{{/if_failed}}` | Status is FAILED |

A ready-to-use template with full styling is provided at
`src/main/resources/validation-report-template.html`.

---

## 10. Complete Annotated Example

```yaml
version: "1.0"

# ── Global read settings ────────────────────────────────────────────────────
settings:
  headerRow: 0
  dataStartRow: 1
  trimCellValues: true
  ignoreBlankRows: true
  caseSensitiveHeaders: false
  caseSensitiveValues: true

# ── Cross-sheet constraints ──────────────────────────────────────────────────
workbook_rules:
  # every CRGroup in Index must appear in USER_ID
  - match:
      from: Index.CRGroup
      to:   USER_ID.CRGroup

# ── Custom validators ────────────────────────────────────────────────────────
validators:
  cidrV4:
    class: "com.nokia.ciq.validator.custom.CidrV4Validator"
    description: "Validates IPv4 CIDR notation"

# ── Sheet definitions ────────────────────────────────────────────────────────
sheets:

  Index:
    required: true
    columns:
      Node:
        required: true
        type: string
        pattern: ".*MRF.*"
        patternMessage: "Node name must contain 'MRF'"
        description: "MRF hostname"
      CRGroup:
        required: true
        description: "Change request number"
      Tables:
        required: true
        sheetRef: true
        sheetRefIgnoreCase: true
        description: "Data sheet name — must match an actual sheet in the workbook"

  Node_Details:
    required: true
    columns:
      Node_Name:
        required: true
        description: "MRF hostname"
      "NIAM NAME":
        required: true
        description: "NIAM login ID for this node"

  ANNOUNCEMENT_FILES:
    required: true
    settings:
      ignoreBlankRows: false    # blank rows in the data are a validation error
    columns:
      GROUP:
        required: true
        description: "Logical group name"
      INPUT_FILE:
        required: true
        maxLength: 512
        description: "Source file name or TAR archive"
      MRF_DESTINATION_PATH:
        required: true
        type: string
        pattern: "^/.*"
        patternMessage: "Must be an absolute Unix path starting with /"
        maxLength: 1024
        description: "Absolute destination path on the MRF node"
    rules:
      # GROUP must match a group declared in the Index sheet
      - require: Index.NODE
        when:
          column: GROUP
          operator: notBlank

  USER_ID:
    required: true
    columns:
      CRGroup:
        required: true
        description: "Change request number"
      EMAIL:
        required: true
        type: email
        multi: true
        description: "Responsible engineer email (comma-separated for multiple)"

# ── Validation report output ──────────────────────────────────────────────────
report_output:
  formats:  [JSON, HTML, MSEXCEL]
  filename: "{nodeType}_{activity}_VALIDATION_REPORT"

# ── Post-validation outputs ───────────────────────────────────────────────────
outputs:
  CR_LIST:
    sheet: Index
    column: CRGroup
    aggregate: distinct
    separator: ","

  CR_COUNT:
    sheet: Index
    aggregate: count

# ── MOP JSON output template ──────────────────────────────────────────────────
json_output:
  output_mode: single
  data:
    nodeType: MRF
    activity: ANNOUNCEMENT_LOADING
    nodes:
      _each: "DISTINCT Index.Node AS $node"
      node:    $node
      crGroup: Index.CRGroup
      email:   "USER_ID.EMAIL WHERE USER_ID.CRGroup = Index.CRGroup"
      niamID:  "Node_Details.'NIAM NAME' WHERE Node_Details.Node_Name = $node"
      tableData:
        _each: "ANNOUNCEMENT_FILES WHERE GROUP = Index.GROUP"
        INPUT_FILE:           INPUT_FILE
        MRF_DESTINATION_PATH: MRF_DESTINATION_PATH
```

---

## 11. Validation Flow Summary

```
1. Load YAML rules file
2. Read Excel workbook into memory
   ├── Read Index sheet and all sheets declared in rules
   └── Read auxiliary sheets (Node_Details, USER_ID, etc.)
3. Validate each sheet
   ├── Sheet presence check (required: true)
   ├── Blank-sheet check (sheet has no data rows)
   ├── Per-row column validation (type, required, pattern, length, …)
   └── Per-row rule evaluation (require, forbid, compare, one_of, …)
4. Workbook-level rules (subset, superset, match, unique)
5. Produce ValidationReport
   ├── PASSED → compute outputs:, write reports (JSON/HTML/Excel per report_output), segregate MOP JSON per json_output
   └── FAILED → write report only (no MOP JSON written)
```

---

## 12. Error Message Customisation

Every column and rule type surfaces a `messages:` map where individual error keys can be
overridden.  Unspecified keys fall back to the engine default.

```yaml
columns:
  MRF_DESTINATION_PATH:
    required: true
    type: string
    pattern: "^/.*"
    patternMessage: "Must be an absolute Unix path starting with /"   # inline shorthand
    messages:
      required: "Destination path is mandatory"
      maxLength: "Path cannot exceed 1024 characters"
      # pattern key can also go here:
      # pattern: "Must be an absolute Unix path starting with /"
```

---

## 13. Design Constraints and Conventions

| Convention | Detail |
|---|---|
| File naming | `{NODE_TYPE}_{ACTIVITY}_validation-rules.yaml` |
| Sheet name matching | Case-insensitive by default (`caseSensitiveHeaders: false`) |
| Blank cells | Always skip type/pattern/length checks; only `required: true` fires |
| Cross-sheet references | `SheetName.ColumnName` syntax everywhere — columns, rules, outputs |
| Pattern validation | Full regex match (anchored) — same as `Pattern.matches()` in Java |
| Invalid regex | Treated as a configuration error and reported on every row rather than silently passing |
| Outputs | Computed only after all validation passes; none are emitted on FAILED |
| `REPORT_FILENAME` | Always emitted by the processor; no YAML entry needed |
