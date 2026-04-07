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
  --rules        MRF_ANNOUNCEMENT_LOADING_validation-rules.yaml \
  --output       reports/ \
  --mop-json-dir mop-json/
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

When validation **PASSES**: JSON data is segregated into `mop-json/` for the MOP generator.

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
| `--mop-json-dir <dir>` | No | Write per-CR/group JSON here on PASSED |

Report file name is auto-generated: **`<NODE_TYPE>_<ACTIVITY>_VALIDATION_REPORT`**

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

### CRGROUP mode (e.g. MRF — Announcement Loading)

Used when nodes are organized into change requests (CRs). Each CR represents a maintenance window.

#### Index sheet

| GROUP | CRGROUP | NODE |
|---|---|---|
| GRP1 | CR1 | MRF1 |
| GRP1 | CR1 | MRF2 |
| GRP2 | CR1 | MRF3 |
| GRP2 | CR2 | MRF4 |

- **GROUP** — logical group name. All nodes in a GROUP receive the same configuration rows from data sheets.
- **CRGROUP** — CR number / change window. Nodes in the same CRGROUP are executed together.
- A GROUP can appear under multiple CRGROUPs.

#### Data sheets

Use a `GROUP` column (not `NODE`) to associate rows with a group:

| GROUP | Action | INPUT_FILE | MRF_DESTINATION_PATH |
|---|---|---|---|
| GRP1 | CREATE | audio1.tar | /var/opt/clips/raj/ |
| GRP2 | CREATE | audio3.tar | /var/opt/clips/mum/ |

#### Node_Details sheet

| Node_Name | NIAM NAME |
|---|---|
| MRF1 | RJ-NOKIA-MRF-RJJVRMR01-CLI |
| MRF2 | RJ-NOKIA-MRF-RJJVRMR02-CLI |

#### User_ID sheet

| CRGROUP | EMAIL |
|---|---|
| CR1 | engineer1@nokia.com |
| CR2 | engineer2@nokia.com |

- One row per CR.
- EMAIL supports comma-separated multiple addresses: `engineer1@nokia.com,manager@nokia.com`
- The email is embedded in the output JSON for notification by the MOP generator.

---

### NODE mode (e.g. SBC — Fixed Line Configuration)

Used when each node has its own individual configuration rows.

#### Index sheet

| Node | CRGroup | Tables |
|---|---|---|
| SBC-1 | CR1 | CRFTargetList,ServiceProfileTable |
| SBC-2 | CR1 | CRFTargetList |

#### Data sheets

Use a `Node` column to associate rows with a specific node:

| Node | Action | ID | NAME |
|---|---|---|---|
| SBC-1 | CREATE | 1 | Target-A |
| SBC-2 | CREATE | 1 | Target-B |

#### Node_ID sheet

| Node | NIAM_ID |
|---|---|
| SBC-1 | sbc1-neid |
| SBC-2 | sbc2-neid |

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

After running `ciq-validate`, report files are written to `--output` with the fixed base name:

**`<NODE_TYPE>_<ACTIVITY>_VALIDATION_REPORT`**

| Format | File | Best for |
|---|---|---|
| HTML | `..._VALIDATION_REPORT.html` | Sharing with users — colour-coded, readable in any browser |
| Excel | `..._VALIDATION_REPORT.xlsx` | Reviewing errors in a spreadsheet |
| JSON | `..._VALIDATION_REPORT.json` | Automated processing / CI pipelines |

### Reading the HTML report

- **Green** sheet tabs — no errors found.
- **Red** sheet tabs — one or more errors; each error shows: row number, column name, invalid value, and reason it failed.

---

## MOP JSON Output

When `--mop-json-dir` is provided and validation **PASSES**, data is written into subfolders for the MOP generator.

### CRGROUP mode — one folder per CR, one JSON file per folder

```
mop-json/
├── CR1/
│   └── MRF_ANNOUNCEMENT_LOADING_CR1.json
└── CR2/
    └── MRF_ANNOUNCEMENT_LOADING_CR2.json
```

Each JSON file contains:
- CR number and responsible user email
- For each GROUP: node list, NIAM ID mapping, and all configuration rows from every data sheet

```json
{
  "nodeType": "MRF",
  "activity": "ANNOUNCEMENT_LOADING",
  "crGroup": "CR1",
  "email": "engineer1@nokia.com",
  "groups": [
    {
      "group": "GRP1",
      "nodes": ["MRF1", "MRF2"],
      "niamMapping": {
        "MRF1": "RJ-NOKIA-MRF-RJJVRMR01-CLI",
        "MRF2": "RJ-NOKIA-MRF-RJJVRMR02-CLI"
      },
      "tableData": {
        "ANNOUNCEMENT_FILES": [
          { "GROUP": "GRP1", "INPUT_FILE": "audio1.tar", "MRF_DESTINATION_PATH": "/var/opt/clips/raj/" }
        ]
      }
    }
  ],
  "allNodes": ["MRF1", "MRF2"]
}
```

### NODE mode — one folder per node+CR combination

```
mop-json/
├── SBC-1_CR1/
│   ├── SBC_FIXED_LINE_CONFIGURATION_index_SBC-1_CR1.json
│   └── SBC_FIXED_LINE_CONFIGURATION_CRFTargetList_SBC-1_CR1.json
└── SBC-2_CR1/
    ├── SBC_FIXED_LINE_CONFIGURATION_index_SBC-2_CR1.json
    └── SBC_FIXED_LINE_CONFIGURATION_CRFTargetList_SBC-2_CR1.json
```

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
| Validation PASSED but `mop-json/` is empty | `--mop-json-dir` not specified | Add `--mop-json-dir <dir>` to the command |

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
```

> **Note:** `REPORT_FILENAME` is always emitted regardless of the `outputs:` section. All other parameters on PASSED require an explicit `outputs:` entry.

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
