# CIQ Processor — User Manual

## What is CIQ Processor?

CIQ Processor is a command-line tool that:

1. **Generates** a blank CIQ Excel template from a YAML rules file — ready to distribute to the user for data entry.
2. **Validates** a filled-in CIQ Excel workbook against the same YAML rules.
3. **Segregates** validated data into per-CR or per-group JSON folders consumed by the MOP generator.

No separate `ciq-reader` or `ciq-validator` steps are needed — this tool replaces both.

---

## Prerequisites

| Requirement | Details |
|---|---|
| Java | 8 or higher |
| CIQ Excel workbook | `.xlsx` format; structure described in this manual |
| Validation rules YAML | One file per node type + activity combination |

> **Important:** Always use `ciq-processor-1.0.0-cli.jar` (the fat JAR). Never use `ciq-processor-1.0.0.jar` (the thin JAR) — it does not bundle its dependencies and will fail with a POI classpath error on the server.

---

## Quick Start

### Step 1 — Generate a blank CIQ template

If the user does not have a CIQ yet, generate a blank template from the rules file:

```bash
java -jar ciq-processor-1.0.0-cli.jar \
  --mode      ciq-generate \
  --node-type MRF \
  --activity  ANNOUNCEMENT_LOADING \
  --rules     MRF_ANNOUNCEMENT_LOADING_validation-rules.yaml \
  --output    templates/
```

**Console output:**
```
STATUS=SUCCESS
ERRORS=0
CIQ_FILENAME=MRF_ANNOUNCEMENT_LOADING_CIQ.xlsx
```

The generated file contains:
- All required sheets with column headers pre-populated
- Dropdown lists on columns that have allowed values
- A **Column_Guide** sheet describing every column

Distribute this file to the user. They fill in data rows and return it.

---

### Step 2 — Validate the filled CIQ

```bash
java -jar ciq-processor-1.0.0-cli.jar \
  --mode         ciq-validate \
  --ciq          MRF_ANNOUNCEMENT_LOADING_CIQ.xlsx \
  --node-type    MRF \
  --activity     ANNOUNCEMENT_LOADING \
  --rules                   MRF_ANNOUNCEMENT_LOADING_validation-rules.yaml \
  --output                  reports/ \
  --json-output-dir         mop-json/ \
  --json-output-config-file MRF_ANNOUNCEMENT_LOADING_json-output.yaml
```

**Console output on PASSED:**
```
STATUS=PASSED
ERRORS=0
REPORT_FILENAME=reports/MRF_ANNOUNCEMENT_LOADING_VALIDATION_REPORT
CR_LIST=CR1,CR2
CR_COUNT=2
```

**Console output on FAILED:**
```
STATUS=FAILED
ERRORS=5
REPORT_FILENAME=reports/MRF_ANNOUNCEMENT_LOADING_VALIDATION_REPORT
```

When validation **FAILS**: share `REPORT_FILENAME.html` with the user to fix the errors, then re-validate.

When validation **PASSES**: JSON data is written to `mop-json/` according to the JSON output config file.

---

## CLI Reference

```
java -jar ciq-processor-1.0.0-cli.jar --mode <mode> [options]
```

### Mode: ciq-validate (default)

| Argument | Required | Description |
|---|---|---|
| `--ciq <file>` | Yes | Path to the CIQ Excel workbook (.xlsx) |
| `--node-type <type>` | Yes | Node type, e.g. `MRF` or `SBC` |
| `--activity <name>` | Yes | Activity name, e.g. `ANNOUNCEMENT_LOADING` |
| `--rules <file>` | Yes | Path to the YAML validation rules file |
| `--output <dir>` | Yes | Directory for validation report files |
| `--format <csv>` | No | `JSON,HTML,MSEXCEL` — default: all three |
| `--json-output-dir <dir>` | No | Output directory for JSON files (written on PASSED only) |
| `--json-output-config-file <file>` | No | JSON output config file (`*_json-output.yaml`) |
| `--report-template-name <file>` | No | HTML report template filename (enables template-based HTML) |
| `--report-template-path <dir>` | No | Directory containing the HTML report template |

Report file name is configured in `report_output.filename` in the rules YAML
(default: **`<NODE_TYPE>_<ACTIVITY>_VALIDATION_REPORT`**)

### Mode: ciq-generate

| Argument | Required | Description |
|---|---|---|
| `--node-type <type>` | Yes | Node type, e.g. `MRF` or `SBC` |
| `--activity <name>` | Yes | Activity name, e.g. `ANNOUNCEMENT_LOADING` |
| `--rules <file>` | Yes | Path to the YAML validation rules file |
| `--output <dir>` | No | Output directory (default: current directory) |

CIQ file name is auto-generated: **`<NODE_TYPE>_<ACTIVITY>_CIQ.xlsx`**

### Exit codes

| Code | Meaning |
|---|---|
| `0` | Success / validation PASSED |
| `1` | Error / validation FAILED |

---

## Console Output Parameters

Every invocation writes machine-readable `KEY=VALUE` pairs to stdout that can be parsed by the calling script or pipeline.

### ciq-generate output

| Parameter | When | Description |
|---|---|---|
| `STATUS` | Always | `SUCCESS` or `FAILED` |
| `ERRORS` | Always | `0` on success, `1` on failure |
| `CIQ_FILENAME` | SUCCESS | File name only, e.g. `MRF_ANNOUNCEMENT_LOADING_CIQ.xlsx` |
| `ERROR` | FAILED | Reason for failure |

### ciq-validate output

| Parameter | When | Description |
|---|---|---|
| `STATUS` | Always | `PASSED` or `FAILED` |
| `ERRORS` | Always | Total number of validation errors found |
| `REPORT_FILENAME` | Always | Full path to report files (without extension); append `.json`, `.html`, or `.xlsx` |
| `ERROR` | Runtime error | Exception message |

Additional parameters printed only when **STATUS=PASSED**: any parameters declared in the `outputs:` section of the YAML rules file (see [Post-Validation Outputs](#post-validation-outputs)).

**Example (MRF rules file defines `CR_LIST` and `CR_COUNT`):**

```
STATUS=PASSED
ERRORS=0
REPORT_FILENAME=reports/MRF_ANNOUNCEMENT_LOADING_VALIDATION_REPORT
CR_LIST=CR1,CR2
CR_COUNT=2
```

---

## CIQ Excel Workbook Structure

### Sheet overview

| Sheet | Purpose | Mode |
|---|---|---|
| `Index` | Maps groups/nodes to CRs | All modes |
| Data sheets | Configuration rows (one per table) | All modes |
| `Node_Details` / `Node_ID` | Node name → NIAM ID mapping | All modes |
| `User_ID` | CR number → responsible user email | CRGROUP mode |
| `Column_Guide` | Reference — describes every column | Generated template only |

`Column_Guide` is informational — generated in the template but not required for validation submission.

---

### CIQ sheet structure

The exact sheet and column layout depends on the YAML rules file for the node type and activity.
The processor does not enforce a fixed set of sheet names — all sheet names, column names, and
their relationships are declared in the YAML.

#### Example — Index sheet (MRF — mapped by CRGroup)

| Node | CRGroup | Tables |
|---|---|---|
| MRF1 | CR1 | ANNOUNCEMENT_FILES |
| MRF2 | CR1 | ANNOUNCEMENT_FILES |
| MRF3 | CR2 | ANNOUNCEMENT_FILES |

#### Example — data sheet (ANNOUNCEMENT_FILES)

| GROUP | INPUT_FILE | MRF_DESTINATION_PATH |
|---|---|---|
| GRP1 | audio1.tar | /var/opt/clips/raj/ |
| GRP2 | audio3.tar | /var/opt/clips/mum/ |

#### Example — Node_Details sheet

| Node_Name | NIAM NAME |
|---|---|
| MRF1 | RJ-NOKIA-MRF-RJJVRMR01-CLI |
| MRF2 | RJ-NOKIA-MRF-RJJVRMR02-CLI |

#### Example — User_ID sheet

| CRGroup | EMAIL |
|---|---|
| CR1 | engineer1@nokia.com |
| CR2 | engineer2@nokia.com |

- EMAIL supports comma-separated multiple addresses: `engineer1@nokia.com,manager@nokia.com`

---

### Action column values

| Value | Meaning |
|---|---|
| `CREATE` | Create a new record on the node |
| `DELETE` | Delete an existing record from the node |
| `MODIFY` | Modify an existing record on the node |

---

## Column_Guide Sheet

Every generated template includes a **`Column_Guide`** sheet as the last tab. Use it when filling in the CIQ to understand what each column expects.

| Column | What it shows |
|---|---|
| **Sheet** | Which sheet the column belongs to |
| **Column** | Column header name |
| **Type** | `Text`, `Integer`, `Email`, `Enum`, `Text (pattern)`, `Integer (ranges)` |
| **Required** | `Required`, `Optional`, or `Required when <COL> = <VALUE>` |
| **Allowed Values** | Valid options for Enum columns — same as the dropdown |
| **Constraints** | Length limits, numeric ranges, format requirements |
| **Description** | Plain-language description of what to enter |

---

## Validation Reports

After running `ciq-validate`, report files are written to `--output` with base name controlled
by `report_output.filename` in the rules YAML (default: **`<NODE_TYPE>_<ACTIVITY>_VALIDATION_REPORT`**):

| Format | File | Best for |
|---|---|---|
| HTML | `..._VALIDATION_REPORT.html` | Sharing with users — colour-coded, readable in any browser |
| Excel | `..._VALIDATION_REPORT.xlsx` | Reviewing errors in a spreadsheet |
| JSON | `..._VALIDATION_REPORT.json` | Automated processing / CI pipelines |

### HTML report template

By default the HTML report uses a built-in layout.  To use an external template, pass:

```bash
--report-template-name  validation-report-template.html \
--report-template-path  src/main/resources
```

The template uses `{{placeholder}}` substitution and `{{#section}}...{{/section}}` loops.
A ready-to-use template is provided at `src/main/resources/validation-report-template.html`.

If only `--report-template-name` is given (no path), the name is treated as a full file path.

### Reading the HTML report

- **Green** sheet tabs — no errors found.
- **Red** sheet tabs — one or more errors; each error shows: row number, column name, invalid value, and reason it failed.

---

## MOP JSON Output

When **both** `--json-output-dir` and `--json-output-config-file` are provided and validation
**PASSES**, data is written according to the standalone JSON output config file. The layout is
fully YAML-driven — there are no hardcoded modes.

The config file is independent of the validation-rules YAML and is shared by `ciq-processor`,
`mop-generator-utility`, and `network-command-executor-utility`.

File naming convention: **`{NODE_TYPE}_{ACTIVITY}_json-output.yaml`**

### JSON output config file

```yaml
output_mode: single          # single | individual
segregate_by:                # required for output_mode: individual
  sheet:  Index
  column: CRGroup
  as:     $cr
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

### `output_mode: single` — one JSON file for the entire workbook

```
mop-json/
└── MRF_ANNOUNCEMENT_LOADING.json
```

### `output_mode: individual` — one folder per segregation value

Segregation key is the column specified in `segregate_by`.  For example, segregating by
`Index.CRGroup`:

```
mop-json/
├── CR1/
│   └── MRF_ANNOUNCEMENT_LOADING_CR1.json
└── CR2/
    └── MRF_ANNOUNCEMENT_LOADING_CR2.json
```

The JSON structure in each file is exactly what the `data:` template produces for that
segregation value.

### `_each` directive — iterating rows

| Syntax | What it produces |
|---|---|
| `_each: "DISTINCT <Sheet>.<Column> AS $var"` | One element per distinct column value |
| `_each: <SheetName>` | One element per row of the sheet |
| `_each: "<Sheet> WHERE <Col> = <value>"` | One element per matching row |

### Relational lookups

| Syntax | What it produces |
|---|---|
| `key: "<Sheet>.<Col> WHERE <Sheet>.<Filter> = $var"` | First value where filter matches |
| `key: <Sheet>.<Column>` | First non-blank value scoped to current iteration |

---

## Troubleshooting

| Symptom | Likely cause | Fix |
|---|---|---|
| `General error: org.apache.poi.util.IOUtils.toByteArray` | Running the thin JAR instead of the fat JAR | Use `ciq-processor-1.0.0-cli.jar`, not `ciq-processor-1.0.0.jar` |
| `--rules file not found` | Wrong path to YAML file | Naming convention: `{NODE_TYPE}_{ACTIVITY}_validation-rules.yaml` |
| `Header row not found in 'Node_Details' sheet` | Column names in sheet don't match YAML `nodeColumn`/`niamColumn` | Verify exact column names match on both sides |
| `Column 'X' is required but is empty` | Mandatory cell left blank | Fill in the missing value |
| `Value 'X' not in allowed values` | Cell value does not match permitted options | Use the dropdown or check the Column_Guide sheet |
| `'X' is not a valid email address` | Incorrectly formatted email in User_ID sheet | Fix the format; multiple emails must be comma-separated without spaces |
| `Value 'X' is not a valid integer` | Numeric column contains text or a decimal | Enter a whole number |
| `Node 'X' not found in Node_ID sheet` | Node name mismatch between sheets | Check for spelling differences or trailing spaces |
| Validation PASSED but `mop-json/` is empty | `--json-output-dir` or `--json-output-config-file` not specified | Add both `--json-output-dir <dir>` and `--json-output-config-file <file>` to the command |

---

## Validation Rules YAML

Naming convention: `{NODE_TYPE}_{ACTIVITY}_validation-rules.yaml`

### Column types

| `type` | What it validates |
|---|---|
| `string` | Free-form text (`minLength`, `maxLength`, `allowedValues`, `pattern`) |
| `integer` | Whole number (`minValue`, `maxValue`, `allowedRanges`) |
| `decimal` | Decimal number (`minDecimal`, `maxDecimal`, `precision`) |
| `datetime` | Date / time (`format`, default: `yyyy-MM-dd'T'HH:mm:ss`) |
| `email` | E-mail address (`multi: true` = comma-separated list) |
| `ip` | IP address (`accepts`: `ipv4` \| `ipv6` \| `both`) |

### Row rules

Row rules are defined under `sheets.<SheetName>.rules:` and apply to every data row.

**Column reference convention:** `ColumnName` means the current sheet. `SheetName.ColumnName` means a column in another sheet. This applies to **all** rule types — `require`, `forbid`, `when.column`, `compare`, `one_of`, `only_one`, `all_or_none`, `sum`, and `equals`.

| Rule | Description |
|---|---|
| `require: [Sheet.]Column` | Column must be non-blank; cross-sheet form checks value exists in that sheet |
| `forbid: [Sheet.]Column` | Column must be blank; cross-sheet form checks value absent from that sheet |
| `compare: "[S.]ColA operator [S.]ColB"` | Cross-column comparison (string operators) |
| `one_of: [[S.]ColA, [S.]ColB, ...]` | At least one must be non-blank |
| `only_one: [[S.]ColA, [S.]ColB]` | Exactly one must be non-blank |
| `all_or_none: [[S.]ColA, [S.]ColB, ...]` | All filled or all blank |
| `sum: [[S.]ColA, [S.]ColB]` + `equals: [S.]ColC` | Sum must equal target column |

All rules support an optional `when:` condition:

```yaml
rules:
  # require unconditional — same-sheet
  - require: ActionKey

  # require unconditional — cross-sheet (value must exist in Node_Details.Node_Name)
  - require: Node_Details.Node_Name

  # require conditional — same-sheet
  - require: ActionKey
    when:
      column: Action                  # ColumnName = same sheet
      operator: equals                # equals | notEquals | blank | notBlank | contains
      value: MODIFY                   # greaterThan | greaterThanOrEquals | lessThan | lessThanOrEquals

  # require conditional — cross-sheet
  - require: Node_Details.Node_Name
    when:
      column: Action
      operator: notEquals
      value: DELETE

  # when with sheet: field — existence check in another sheet
  - require: NIAM_NAME
    when:
      sheet: Node_Details             # sheet: + column: + exists/notExists
      column: Node_Name
      operator: exists

  # forbid unconditional — cross-sheet (value must NOT exist in Blacklist.ID)
  - forbid: Blacklist.ID

  # compare with optional cross-sheet columns
  - compare: "StartPort lessThanOrEquals EndPort"
  - compare: "Ref.MinPrice greaterThan Pricing.BasePrice"
```

### Post-Validation Outputs

The `outputs:` section declares values to extract from the validated data and emit as `KEY=VALUE` console parameters when validation **PASSES**. Each entry is also written into the JSON/HTML/Excel report under `parameters`.

#### Fields

| Field | Required | Description |
|---|---|---|
| `sheet` | Yes | Sheet to read from (any sheet accessible to the validator, including `Index`) |
| `aggregate` | Yes | Computation to perform — `distinct`, `count`, or `sum` |
| `column` | Depends | Column to aggregate. Required for `distinct` and `sum`; optional for `count` (omit to count total rows) |
| `separator` | No | Join character for `distinct` — default `,` |
| `name` | No | Override the parameter name emitted to stdout and the report. When absent the YAML key is used |

#### Aggregates

| Aggregate | Description | Example output |
|---|---|---|
| `distinct` | Sorted, deduplicated non-blank values joined by `separator` | `CR1,CR2,CR3` |
| `count` | Count of non-blank values in `column`; or total row count when `column` is omitted | `6` |
| `sum` | Numeric sum of `column` values; non-numeric cells are ignored | `1024` |
| `group` | For each distinct value of `column`, emit a separate `<PARAM>_<value>` parameter listing values from `groupBy` | `NODES_CR1=MRF1,MRF2` |

For the `group` aggregate, the `groupBy` field names the column whose values are collected
per group:

```yaml
outputs:
  NODES:
    sheet:     Index
    column:    CRGroup      # one parameter per distinct CRGroup
    aggregate: group
    groupBy:   Node         # collect Node values for each CRGroup
    separator: ","
# Emits: NODES_CR1=MRF1,MRF2   NODES_CR2=MRF3,MRF4
```

#### Example

```yaml
outputs:
  # Distinct CR numbers from the Index sheet → emitted as CR_LIST=CR1,CR2
  CR_LIST:
    sheet: Index
    column: CRGroup
    aggregate: distinct
    separator: ","

  # Total rows in ANNOUNCEMENT_FILES → emitted as ANNOUNCEMENT_COUNT=6
  ANNOUNCEMENT_COUNT:
    sheet: ANNOUNCEMENT_FILES
    aggregate: count

  # Count of non-blank GROUP values → emitted as GROUP_COUNT=6
  GROUP_COUNT:
    sheet: ANNOUNCEMENT_FILES
    column: GROUP
    aggregate: count

  # Numeric sum of a column → emitted as TOTAL_SIZE_KB=2048
  TOTAL_SIZE_KB:
    sheet: ANNOUNCEMENT_FILES
    column: FILE_SIZE_KB
    aggregate: sum

  # 'name' overrides the YAML key → emitted as UNIQUE_NODES=MRF1,MRF2
  node_output:
    name: UNIQUE_NODES
    sheet: Index
    column: Node
    aggregate: distinct

  # Group aggregate → emitted as NODES_CR1=MRF1,MRF2  NODES_CR2=MRF3
  NODES:
    sheet:     Index
    column:    CRGroup
    aggregate: group
    groupBy:   Node
    separator: ","
```

> **Note:** `REPORT_FILENAME` is always emitted regardless of the `outputs:` section. All other parameters on PASSED require an explicit `outputs:` entry.

---

### JSON Output Config File (`*_json-output.yaml`)

The JSON output config file defines the complete shape of the output JSON using a free-form YAML
template. The template is evaluated by `JsonTemplateEvaluator` which supports iteration,
relational lookups, and variable substitution.

#### Top-level fields

| Field | Description |
|---|---|
| `output_mode` | `single` — one JSON file for the workbook; `individual` — one file per segregation value |
| `segregate_by` | Required for `individual` mode — specifies sheet, column, and variable name |
| `data` | Free-form YAML template defining the JSON output shape |

#### `segregate_by` fields

| Field | Description |
|---|---|
| `sheet` | Sheet containing the segregation column |
| `column` | Column whose distinct values determine the segregation units |
| `as` | Variable name (e.g. `$cr`) used to reference the current value in `data` |

#### `data` template directives

| Directive | Example | Meaning |
|---|---|---|
| `_each: "DISTINCT S.Col AS $var"` | `_each: "DISTINCT Index.Node AS $node"` | Iterate distinct column values |
| `_each: SheetName` | `_each: ANNOUNCEMENT_FILES` | Iterate all rows of a sheet |
| `_each: "Sheet WHERE Col = value"` | `_each: "ANNOUNCEMENT_FILES WHERE GROUP = A"` | Iterate filtered rows |
| `key: S.Col WHERE S.Filter = $var` | `email: "USER_ID.EMAIL WHERE USER_ID.CRGroup = $cr"` | Relational scalar lookup |
| `key: S.Col` | `crGroup: Index.CRGroup` | First non-blank value from sheet+column |
| `key: $var` | `node: $node` | Inline variable reference |

#### Example (`MRF_ANNOUNCEMENT_LOADING_json-output.yaml`)

```yaml
output_mode: individual
segregate_by:
  sheet:  Index
  column: CRGroup
  as:     $cr
data:
  nodeType: MRF
  activity: ANNOUNCEMENT_LOADING
  crGroup:  $cr
  email:    "USER_ID.EMAIL WHERE USER_ID.CRGroup = $cr"
  nodes:
    _each: "DISTINCT Index.Node AS $node"
    node:   $node
    niamID: "Node_Details.'NIAM NAME' WHERE Node_Details.Node_Name = $node"
    files:
      _each: "ANNOUNCEMENT_FILES WHERE GROUP = Index.GROUP"
      INPUT_FILE:           INPUT_FILE
      MRF_DESTINATION_PATH: MRF_DESTINATION_PATH
```

**Output JSON** (for CR1):

```json
{
  "nodeType": "MRF",
  "activity": "ANNOUNCEMENT_LOADING",
  "crGroup": "CR1",
  "email": "engineer@nokia.com",
  "nodes": [
    {
      "node": "MRF1",
      "niamID": "RJ-NOKIA-MRF-RJJVRMR01-CLI",
      "files": [
        { "INPUT_FILE": "audio.tar", "MRF_DESTINATION_PATH": "/var/opt/clips/raj/" }
      ]
    }
  ]
}
```

#### Output file name

**`output_mode: single`** → `{NODE_TYPE}_{ACTIVITY}.json`

**`output_mode: individual`** → `{NODE_TYPE}_{ACTIVITY}_{SEGREGATION_VALUE}.json`
(inside folder `mop-json/{SEGREGATION_VALUE}/`)

---

### `when:` operators

| Operator | Applies to | Description |
|---|---|---|
| `equals` | String / Number | Condition column value equals `value` |
| `notEquals` | String / Number | Condition column value does not equal `value` |
| `blank` | Any | Condition column is empty |
| `notBlank` | Any | Condition column is non-empty |
| `contains` | String | Condition column value contains `value` as a substring |
| `greaterThan` | Number | Condition column value > `value` |
| `greaterThanOrEquals` | Number | Condition column value >= `value` |
| `lessThan` | Number | Condition column value < `value` |
| `lessThanOrEquals` | Number | Condition column value <= `value` |
| `exists` | Cross-sheet | Current row's value is found in `sheet.column` (used with `sheet:` field) |
| `notExists` | Cross-sheet | Current row's value is not found in `sheet.column` (used with `sheet:` field) |
