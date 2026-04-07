# CIQ Processor

Validates a Nokia CIQ (Configuration Input Questionnaire) Excel workbook against YAML-defined rules, produces validation reports, and on success segregates the data into per-scope JSON files consumed by **mop-generator-utility**.

Also generates blank CIQ templates directly from the YAML rules file.

---

## Overview

```
YAML Rules File
      │
      ├──► ciq-generate ──► Blank CIQ Excel template (.xlsx)
      │                          Index / Data sheets / Node_ID / User_ID / Column_Guide
      │
CIQ Excel (.xlsx)
      │
      ▼
InMemoryExcelReader  ──  reads workbook into memory (no disk writes)
      │
      ▼
CiqValidationEngine  ──  validates every sheet/row against rules YAML
      │
      ├──►  Validation Report  (<NODE_TYPE>_<ACTIVITY>_VALIDATION_REPORT.json/html/xlsx)
      │
      │  validation PASSED + --mop-json-dir supplied?
      │             │ yes
      ▼
JSON segregation (mode determined by groupByColumnName in YAML)
  ┌───────────────┬──────────────┐
CRGROUP         GROUP           NODE
  │               │               │
  ▼               ▼               ▼
mop-json/      mop-json/      mop-json/
  CR1/             A/             SBC1_CR1/
  CR2/             B/             SBC2_CR1/
  │
  ▼
mop-generator-utility
```

---

## Build

```bash
cd ciq-processor
mvn package -DskipTests
# Produces: target/ciq-processor-1.0.0-cli.jar
```

> **Important:** Always use `ciq-processor-1.0.0-cli.jar` (the fat JAR). Never use the thin JAR (`ciq-processor-1.0.0.jar`) — it does not bundle dependencies and will fail with POI classpath errors on the server.

---

## CLI Usage

```
java -jar ciq-processor-1.0.0-cli.jar --mode <mode> [options]

Modes:
  ciq-validate  (default)  Validate a CIQ Excel file against rules
  ciq-generate             Generate a blank CIQ template from rules
```

---

### Mode: ciq-validate

```
Required:
  --ciq           <file>   CIQ Excel workbook (.xlsx)
  --node-type     <type>   Node type, e.g. SBC or MRF
  --activity      <name>   Activity name, e.g. ANNOUNCEMENT_LOADING
  --rules         <file>   YAML validation-rules file
  --output        <dir>    Output directory for validation reports

Optional:
  --format        <csv>    JSON,HTML,MSEXCEL (default: all three)
  --mop-json-dir  <dir>    JSON output directory for MOP generation (written on PASSED only)

Exit code: 0 = PASSED, 1 = FAILED or error
```

Report files are auto-named: **`<NODE_TYPE>_<ACTIVITY>_VALIDATION_REPORT.json/html/xlsx`**

**Example — MRF CRGROUP mode:**

```bash
java -jar ciq-processor-1.0.0-cli.jar \
  --mode         ciq-validate \
  --ciq          MRF_ANNOUNCEMENT_LOADING_CIQ.xlsx \
  --node-type    MRF \
  --activity     ANNOUNCEMENT_LOADING \
  --rules        MRF_ANNOUNCEMENT_LOADING_validation-rules.yaml \
  --output       target/reports \
  --format       JSON,HTML,MSEXCEL \
  --mop-json-dir target/mop-json
```

**Example — SBC NODE mode:**

```bash
java -jar ciq-processor-1.0.0-cli.jar \
  --ciq          SBC_FIXED_LINE_CONFIGURATION_CIQ.xlsx \
  --node-type    SBC \
  --activity     FIXED_LINE_CONFIGURATION \
  --rules        SBC_FIXED_LINE_CONFIGURATION_validation-rules.yaml \
  --output       target/reports \
  --mop-json-dir target/mop-json
```

**Console output — PASSED (CRGROUP mode):**

```
STATUS=PASSED
ERRORS=0
REPORT_FILENAME=target/reports/MRF_ANNOUNCEMENT_LOADING_VALIDATION_REPORT
CR_LIST=CR1,CR2
CR_COUNT=2
```

**Console output — FAILED:**

```
STATUS=FAILED
ERRORS=5
REPORT_FILENAME=target/reports/MRF_ANNOUNCEMENT_LOADING_VALIDATION_REPORT
```

**Console output — runtime error:**

```
STATUS=FAILED
ERROR=<exception message>
```

---

### Mode: ciq-generate

```
Required:
  --node-type     <type>   Node type, e.g. SBC or MRF
  --activity      <name>   Activity name, e.g. ANNOUNCEMENT_LOADING
  --rules         <file>   YAML validation-rules file

Optional:
  --output        <dir>    Output directory (default: current directory)

Exit code: 0 = success, 1 = error
```

CIQ file is auto-named: **`<NODE_TYPE>_<ACTIVITY>_CIQ.xlsx`**

**Example:**

```bash
java -jar ciq-processor-1.0.0-cli.jar \
  --mode      ciq-generate \
  --node-type MRF \
  --activity  ANNOUNCEMENT_LOADING \
  --rules     MRF_ANNOUNCEMENT_LOADING_validation-rules.yaml \
  --output    target/templates
```

**Console output — success:**

```
STATUS=SUCCESS
ERRORS=0
CIQ_FILENAME=MRF_ANNOUNCEMENT_LOADING_CIQ.xlsx
```

**Console output — failure:**

```
STATUS=FAILED
ERRORS=1
ERROR=<reason>
```

**Generated workbook sheets:**

| Sheet | Contents |
|---|---|
| `Index` | Header row with INDEX sheet columns from YAML |
| `<DataSheet>` | One sheet per entry in `sheets:` with column headers and dropdowns |
| `Node_Details` / `Node_ID` | Header row from `nodeIdSheet` config |
| `User_ID` | Header row from `userIdSheet` config (CRGROUP + EMAIL) |
| `Column_Guide` | Reference — column name, type, required, allowed values, constraints, description |

Columns with `allowedValues:` in the YAML get Excel **dropdown validation** on rows 2–101.

---

## Console Output Parameters — Full Reference

Every invocation prints machine-readable `KEY=VALUE` pairs to stdout.

### ciq-generate

| Parameter | When | Description |
|---|---|---|
| `STATUS` | Always | `SUCCESS` or `FAILED` |
| `ERRORS` | Always | `0` on success, `1` on failure |
| `CIQ_FILENAME` | SUCCESS | Generated file name (no path), e.g. `MRF_ANNOUNCEMENT_LOADING_CIQ.xlsx` |
| `ERROR` | FAILED | Error message |

### ciq-validate

| Parameter | When | Description |
|---|---|---|
| `STATUS` | Always | `PASSED` or `FAILED` |
| `ERRORS` | Always | Total number of validation errors |
| `REPORT_FILENAME` | Always | Full path to report files (without extension) |
| `ERROR` | Runtime error | Exception message |

Additional parameters on **STATUS=PASSED** depend on grouping mode:

**CRGROUP mode:**

| Parameter | Example |
|---|---|
| `CR_LIST` | `CR1,CR2` |
| `CR_COUNT` | `2` |

**NODE mode:**

| Parameter | Example |
|---|---|
| `NODE_1` | `SBC-1` |
| `NIAM_ID_1` | `sbc1-neid` |
| `TOTAL_NODES_COUNT` | `2` |
| `CHILD_ORDERS_COUNT` | `2` |

**GROUP mode:**

| Parameter | Example |
|---|---|
| `GROUP_1` | `A` |
| `GROUP_1_VALUES` | `MRF1,MRF2` |
| `TOTAL_GROUPS_COUNT` | `2` |
| `TOTAL_NODES_COUNT` | `3` |
| `CHILD_ORDERS_COUNT` | `2` |

---

## Programmatic API

### Generate a CIQ template

```java
import com.nokia.ciq.processor.template.CiqTemplateGenerator;
import com.nokia.ciq.processor.template.CiqTemplateResult;

// Rules are loaded internally; output filename is auto-generated as
// <NODE_TYPE>_<ACTIVITY>_CIQ.xlsx inside the specified output directory.
CiqTemplateResult result = new CiqTemplateGenerator().generate(
    "MRF",
    "ANNOUNCEMENT_LOADING",
    "MRF_ANNOUNCEMENT_LOADING_validation-rules.yaml",  // rulesFilePath
    "target/templates");                                // outputDir (null = CWD)

System.out.println(result.getStatus());           // "SUCCESS" or "FAILED"
System.out.println(result.getErrors());           // 0 or 1
System.out.println(result.isSuccess());           // true / false

// All other key/value pairs in a LinkedHashMap:
result.getParameters().forEach((k, v) -> System.out.println(k + "=" + v));
// On SUCCESS prints:
//   CIQ_FILENAME=MRF_ANNOUNCEMENT_LOADING_CIQ.xlsx
// On FAILED prints:
//   ERROR=<reason>
```

### Validate a CIQ

```java
import com.nokia.ciq.processor.CiqProcessorImpl;
import com.nokia.ciq.validator.model.ValidationReport;

// Report file name is auto-generated inside CiqProcessorImpl:
//   <NODE_TYPE>_<ACTIVITY>_VALIDATION_REPORT.json/html/xlsx
// Formats and baseName are resolved internally — pass a CSV string or null for all three.
ValidationReport report = new CiqProcessorImpl().process(
    "MRF_ANNOUNCEMENT_LOADING_CIQ.xlsx",               // ciqFilePath
    "MRF",                                              // nodeType
    "ANNOUNCEMENT_LOADING",                             // activity
    "MRF_ANNOUNCEMENT_LOADING_validation-rules.yaml",  // rulesFilePath
    "target/reports",                                   // outputDir
    "JSON,HTML,MSEXCEL",                                // formatCsv (null = all three)
    "target/mop-json"                                   // mopJsonOutputDir (null = skip)
);

System.out.println(report.getStatus());            // "PASSED" or "FAILED"
System.out.println(report.getTotalErrors());       // total error count

// Parameters map — always contains REPORT_FILENAME; additional params on PASSED:
report.getParameters().forEach((k, v) -> System.out.println(k + "=" + v));
// Always:        REPORT_FILENAME=target/reports/MRF_ANNOUNCEMENT_LOADING_VALIDATION_REPORT
// CRGROUP mode:  CR_LIST=CR1,CR2   CR_COUNT=2
// NODE mode:     NODE_1=SBC-1  NIAM_ID_1=sbc1-neid  TOTAL_NODES_COUNT=2 ...

// Per-sheet results:
for (SheetValidationResult sheet : report.getSheets()) {
    System.out.println(sheet.getSheetName() + ": " + sheet.getErrors().size() + " error(s)");
}
```

---

## Grouping Modes

The mode is set by **`groupByColumnName`** in the validation-rules YAML.

| `groupByColumnName` | INDEX sheet columns | JSON output layout |
|---|---|---|
| `CRGROUP` | `GROUP \| CRGROUP \| NODE` | `mop-json/<CRGROUP>/` — one JSON per CRGROUP |
| `GROUP` | `GROUP \| NODE` | `mop-json/<GROUP>/` — index + data files per GROUP |
| `NODE` | `Node \| CRGroup \| Tables` | `mop-json/<Node>_<CRGroup>/` — index + data per node |

---

### CRGROUP mode

**INDEX sheet:**

| GROUP | CRGROUP | NODE |
|---|---|---|
| GRP1 | CR1 | MRF1 |
| GRP1 | CR1 | MRF2 |
| GRP2 | CR1 | MRF3 |
| GRP2 | CR2 | MRF4 |

**User_ID sheet:**

| CRGROUP | EMAIL |
|---|---|
| CR1 | user1@nokia.com |
| CR2 | user2@nokia.com |

**JSON output:**

```
mop-json/
├── CR1/
│   └── MRF_ANNOUNCEMENT_LOADING_CR1.json
└── CR2/
    └── MRF_ANNOUNCEMENT_LOADING_CR2.json
```

**CRGroupIndex JSON:**

```json
{
  "nodeType": "MRF",
  "activity": "ANNOUNCEMENT_LOADING",
  "crGroup": "CR1",
  "email": "user1@nokia.com",
  "groups": [
    {
      "group": "GRP1",
      "nodes": ["MRF1", "MRF2"],
      "niamMapping": { "MRF1": "RJ-NOKIA-MRF-01-CLI", "MRF2": "RJ-NOKIA-MRF-02-CLI" },
      "tableData": {
        "ANNOUNCEMENT_FILES": [
          { "GROUP": "GRP1", "INPUT_FILE": "file1.tar", "MRF_DESTINATION_PATH": "/var/opt/clips/" }
        ]
      }
    }
  ],
  "allNodes": ["MRF1", "MRF2", "MRF3"]
}
```

---

### NODE mode

**INDEX sheet:**

| Node | CRGroup | Tables |
|---|---|---|
| SBC-1 | CR1 | CRFTargetList,ServiceProfileTable |
| SBC-2 | CR1 | CRFTargetList |

**JSON output:**

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

## Validation Reports

Written to `--output` with fixed base name `<NODE_TYPE>_<ACTIVITY>_VALIDATION_REPORT`:

| Format | File | Contents |
|---|---|---|
| `JSON` | `..._VALIDATION_REPORT.json` | Machine-readable; full error list per sheet/row/column |
| `HTML` | `..._VALIDATION_REPORT.html` | Human-readable; colour-coded per-sheet error tables |
| `MSEXCEL` | `..._VALIDATION_REPORT.xlsx` | Excel; one sheet per data sheet with errors highlighted |

---

## CIQ Excel Workbook Structure

| Sheet | Purpose | Key columns |
|---|---|---|
| `Index` | Maps groups/nodes | Mode-dependent (see Grouping Modes) |
| Data sheets | Configuration rows | `GROUP` or `Node` + `Action` + record fields |
| `Node_Details` / `Node_ID` | Node → NIAM ID mapping | Configured via `nodeIdSheet` in YAML |
| `User_ID` | CRGROUP → user email | `CRGROUP`, `EMAIL` (CRGROUP mode only) |

### Action column values

| Value | Meaning |
|---|---|
| `CREATE` | Create a new record |
| `DELETE` | Delete an existing record |
| `MODIFY` | Modify an existing record |

---

## Column_Guide Sheet

Every generated template includes a **`Column_Guide`** sheet (last tab) describing every column:

| Column | Description |
|---|---|
| **Sheet** | Which sheet this column belongs to |
| **Column** | Column name as it appears in the header row |
| **Type** | `Text`, `Integer`, `Email`, `Enum`, `Text (pattern)`, `Integer (ranges)` |
| **Required** | `Required`, `Optional`, or `Required when <COL> = <VALUE>` |
| **Allowed Values** | Comma-separated list for Enum columns |
| **Constraints** | Length limits, numeric ranges, pattern description, cross-references |
| **Description** | From `description:` field in the YAML rule |

---

## Validation Rules YAML

File naming convention: **`{NODE_TYPE}_{ACTIVITY}_validation-rules.yaml`**

### Top-level settings

```yaml
groupByColumnName: CRGROUP     # CRGROUP | GROUP | NODE
validateIndexSheets: false     # true for NODE mode only
validateNodeIds: true
```

### indexSheet

```yaml
indexSheet:
  columns:
    GROUP:   { required: true }
    CRGROUP: { required: true, description: "CR Number" }
    NODE:    { required: true, description: "Hostname" }
```

### nodeIdSheet

```yaml
nodeIdSheet:
  name:       Node_Details
  nodeColumn: Node_Name
  niamColumn: "NIAM NAME"
  columns:
    Node_Name:  { required: true, description: "Hostname of the MRF node" }
    "NIAM NAME": { required: true, description: "NIAM identifier for SSH/NETCONF access" }
```

### userIdSheet

```yaml
userIdSheet:
  name:          User_ID
  crGroupColumn: CRGROUP
  emailColumn:   EMAIL
  columns:
    CRGROUP: { required: true, description: "CR Number — must match Index sheet" }
    EMAIL:   { required: true, email: true, description: "Responsible user email(s), comma-separated" }
```

### sheets

```yaml
sheets:
  MyTable:
    columns:
      Action:
        required: true
        type: enum
        values: [CREATE, DELETE, MODIFY]
        description: "Operation to perform"
      ActionKey:
        requiredWhen: { column: Action, value: MODIFY }
      PORT:
        required: true
        type: integer
        minValue: 1
        maxValue: 65535
      NAME:
        minLength: 1
        maxLength: 19
      Record.VERSION:
        required: true
        pattern: "^\\d+\\.\\d+\\.\\d+\\.\\d+$"
        messages:
          pattern: "Must be X.X.X.X format"
      Record.TARGET_ID:
        type: integer
        allowedRanges:
          - { min: 1,    max: 1024 }
          - { min: 5001, max: 7048 }
      INPUT_FILE:
        required: true
        maxLength: 512
        description: "Announcement archive (.tar) filename"
      Tables:
        required: true
        sheetRef: true              # value must match an existing sheet name in the workbook
        sheetRefIgnoreCase: false

    rules:
      # require a column when a condition is met
      - require: ActionKey
        when:
          column: Action
          operator: equals
          value: MODIFY

      # forbid a column based on a condition
      - forbid: SubAction
        when:
          column: Action
          operator: equals
          value: CREATE

      # cross-sheet require — value must exist in another sheet's column
      - require: Node_Details.Node_Name
        when:
          column: Node_Name
          operator: notBlank

      # cross-sheet when condition — apply rule only if current row's value exists in another sheet
      - require: NIAM_NAME
        when:
          sheet: Node_Details
          column: Node_Name
          operator: exists          # exists | notExists

      # column comparison (string operators only)
      - compare: "StartPort lessThanOrEquals EndPort"

      # at least one of these columns must have a value
      - one_of: [IPv4_Address, IPv6_Address]

      # all or none must be filled
      - all_or_none: [SubAction, ActionKey]
```

### Column rule options — full reference

| Option | Type | Description |
|---|---|---|
| `type: <type>` | string | Column type — see type list below |
| `required: true` | boolean | Cell must not be blank |
| `requiredWhen: { column, value }` | object | Required only when named column equals given value |
| `values: [...]` | list | Closed vocabulary for `type: enum`, `type: protocol`, `type: urlScheme` |
| `allowedValues: [...]` | list | Allowed values for `type: string`; generates Excel dropdown |
| `allowedRanges: [{min, max}, ...]` | list | Numeric bands (OR logic); requires `type: integer` |
| `minValue` / `maxValue` | number | Numeric bounds (inclusive); requires `type: integer` |
| `minDecimal` / `maxDecimal` | number | Decimal bounds (inclusive); requires `type: decimal` |
| `minLength` / `maxLength` | number | String length bounds (inclusive) |
| `precision` | integer | Max decimal places; requires `type: decimal` |
| `format` | string | Date/time pattern (e.g. `yyyy-MM-dd`); requires `type: date/time/datetime` |
| `accepts` | string | IP version: `ipv4` \| `ipv6` \| `both`; requires `type: ip` |
| `accept` | string | Boolean values: `true/false` \| `yes/no` \| `1/0`; requires `type: boolean` |
| `pattern: "regex"` | string | Must match the Java regex (applies to any type) |
| `patternMessage: "..."` | string | Custom message when `pattern` fails (shorthand for `messages.pattern`) |
| `crossRef: { sheet, column }` | object | Value must exist in referenced sheet's column |
| `sheetRef: true` | boolean | Value must match an existing workbook sheet name |
| `sheetRefIgnoreCase: true` | boolean | Case-insensitive sheet name match (only when `sheetRef: true`) |
| `ignoreCase: true` | boolean | Case-insensitive comparison for `allowedValues` / `values` |
| `unique: true` | boolean | Values must be unique within the sheet |
| `multi: true` | boolean | Cell may contain multiple comma-separated values |
| `description: "..."` | string | Shown in Column_Guide sheet of generated template |
| `messages: { ... }` | object | Per-constraint custom messages (see below) |

**Supported `type` values:**

| Type | Validates | Key config |
|---|---|---|
| `string` | Free-form text | `minLength`, `maxLength`, `allowedValues`, `pattern` |
| `integer` | Whole number | `minValue`, `maxValue`, `allowedRanges` |
| `decimal` | Decimal number | `minDecimal`, `maxDecimal`, `precision` |
| `date` | Calendar date | `format` (default: `yyyy-MM-dd`) |
| `time` | Time of day | `format` (default: `HH:mm:ss`) |
| `datetime` | Date + time | `format` (default: `yyyy-MM-dd'T'HH:mm:ss`) |
| `boolean` | True/false | `accept` (`true/false` \| `yes/no` \| `1/0`) |
| `email` | E-mail address | `multi` (comma-separated list) |
| `phone` | Phone (E.164) | `pattern` override |
| `ip` | IP address | `accepts` (`ipv4` \| `ipv6` \| `both`) |
| `mac` | MAC address | `pattern` override |
| `cidr` | CIDR notation | — |
| `hostname` | RFC 1123 hostname | — |
| `fqdn` | Fully-qualified domain name | — |
| `enum` | Closed vocabulary | `values`, `ignoreCase` |
| `protocol` | Network protocol | `values` |
| `urlScheme` | URL with allowed scheme | `values` |

**`messages` block — per-constraint custom error messages:**

```yaml
messages:
  required:  "This field is mandatory"
  minLength: "Too short — minimum {minLength} characters"
  maxLength: "Too long — maximum {maxLength} characters"
  pattern:   "Must match format XYZ"
  min:       "Value is below the allowed minimum"
  max:       "Value exceeds the allowed maximum"
  format:    "Date must be in yyyy-MM-dd format"
  precision: "Too many decimal places"
  type:      "Invalid value for this column type"
```

**Column reference convention — applies to ALL rule types:**

> `ColumnName` → column in the current sheet (default)
> `SheetName.ColumnName` → column in the named sheet
>
> This notation is valid everywhere a column appears: `require`, `forbid`, `when.column`, `compare`, `one_of`, `only_one`, `all_or_none`, `sum`, `equals`.

**Row rules — full reference:**

| Rule | Syntax | Description |
|---|---|---|
| `require` | `require: [SheetName.]ColumnName` | Column must be non-blank; cross-sheet form checks value exists in that sheet |
| `forbid` | `forbid: [SheetName.]ColumnName` | Column must be blank; cross-sheet form checks value absent from that sheet |
| `compare` | `compare: "[S.]ColA operator [S.]ColB"` | Cross-column comparison using string operator |
| `one_of` | `one_of: [[S.]ColA, [S.]ColB, ...]` | At least one must be non-blank |
| `only_one` | `only_one: [[S.]ColA, [S.]ColB]` | Exactly one must be non-blank |
| `all_or_none` | `all_or_none: [[S.]ColA, [S.]ColB, ...]` | All filled or all blank |
| `sum` | `sum: [[S.]ColA, [S.]ColB]` + `equals: [S.]ColC` | Sum must equal target column |

**`when` condition operators:**

| Operator | Description |
|---|---|
| `equals` | Column value equals `value` (case-insensitive) |
| `notEquals` | Column value does not equal `value` |
| `blank` | Column value is null or empty |
| `notBlank` | Column value is not null/empty |
| `contains` | Column value contains `value` (case-insensitive) |
| `greaterThan` | Numeric or lexicographic `>` |
| `greaterThanOrEquals` | Numeric or lexicographic `>=` |
| `lessThan` | Numeric or lexicographic `<` |
| `lessThanOrEquals` | Numeric or lexicographic `<=` |
| `exists` | Cross-sheet via `sheet:` field: value exists in that sheet's column |
| `notExists` | Cross-sheet via `sheet:` field: value absent from that sheet's column |

```yaml
# require unconditional — same-sheet
- require: ActionKey

# require unconditional — cross-sheet (value must exist in Node_Details.Node_Name)
- require: Node_Details.Node_Name

# require conditional — same-sheet
- require: ActionKey
  when:
    column: Action
    operator: equals
    value: MODIFY

# require conditional — cross-sheet
- require: Node_Details.Node_Name
  when:
    column: Action
    operator: notEquals
    value: DELETE

# forbid unconditional — same-sheet
- forbid: SubAction

# forbid unconditional — cross-sheet (value must NOT exist in Blacklist.ID)
- forbid: Blacklist.ID

# forbid conditional — cross-sheet
- forbid: Blacklist.ID
  when:
    column: Action
    operator: notEquals
    value: DELETE

# when with sheet: field (existence check in another sheet)
- require: NIAM_NAME
  when:
    sheet: Node_Details             # sheet: + column: + exists/notExists
    column: Node_Name
    operator: exists

# compare — both columns may reference another sheet
- compare: "StartPort lessThanOrEquals EndPort"
- compare: "Ref.MinPrice greaterThan Pricing.BasePrice"

# one_of / sum with cross-sheet columns
- one_of: [IPv4_Address, Backup.IPv6_Address]
- sum: [Leg1, Leg2]
  equals: Summary.Total
```

`compare` operators: `equals`, `notEquals`, `greaterThan`, `greaterThanOrEquals`, `lessThan`, `lessThanOrEquals`.
Symbol aliases (`==`, `!=`, `>`, `>=`, `<`, `<=`) are accepted but string form is preferred.

---

## Project Layout

```
ciq-processor/
├── src/main/java/com/nokia/ciq/
│   ├── processor/
│   │   ├── CiqProcessor.java                  # API interface
│   │   ├── CiqProcessorImpl.java              # Validation + segregation pipeline
│   │   ├── CiqProcessorMain.java              # CLI entry point (ciq-validate / ciq-generate)
│   │   ├── model/
│   │   │   ├── CRGroupIndex.java              # CRGROUP mode JSON model
│   │   │   └── GroupIndex.java                # GROUP mode JSON model
│   │   ├── reader/
│   │   │   ├── InMemoryExcelReader.java       # Reads CIQ Excel; detects INDEX structure
│   │   │   └── InMemoryCiqDataStore.java      # In-memory data store
│   │   └── template/
│   │       ├── CiqTemplateGenerator.java      # Generates blank CIQ Excel from YAML rules
│   │       └── CiqTemplateResult.java         # Result object returned by generate()
│   └── validator/
│       ├── CiqValidationEngine.java           # Row-level validation + output parameters
│       ├── config/
│       │   ├── ValidationRulesConfig.java     # Top-level rules model
│       │   ├── ValidationRulesLoader.java     # Loads YAML rules file
│       │   ├── ColumnRule.java                # Per-column rule (type, required, pattern, sheetRef, etc.)
│       │   ├── ColumnMessages.java            # Per-column custom error messages
│       │   ├── SheetRules.java                # Per-sheet rule model
│       │   ├── SheetRowRule.java              # Row-level rule model (require/forbid/compare/etc.)
│       │   ├── RowCondition.java              # when: condition model (same-sheet + cross-sheet)
│       │   ├── WorkbookSettings.java          # Global/per-sheet settings model
│       │   ├── WorkbookRule.java              # workbook_rules entry model
│       │   ├── SubsetRule.java                # subset/superset rule model
│       │   ├── UniqueRule.java                # unique rule model
│       │   ├── ConditionalRequired.java       # requiredWhen model
│       │   ├── CrossRef.java                  # crossRef model
│       │   ├── IntRange.java                  # allowedRanges entry model
│       │   └── ValidatorDefinition.java       # Custom validator registry entry model
│       ├── model/
│       │   ├── ValidationReport.java          # Report model (status, errors, parameters)
│       │   ├── SheetValidationResult.java     # Per-sheet error list
│       │   └── ValidationError.java           # Single error (sheet, row, column, message)
│       ├── report/
│       │   ├── ReportFormat.java              # JSON | HTML | MSEXCEL enum
│       │   ├── HtmlReportWriter.java          # Writes HTML validation report
│       │   └── ExcelReportWriter.java         # Writes Excel validation report
│       └── validator/
│           ├── CellValidator.java             # Validator interface + msg() helper
│           ├── RowValidator.java              # Row-rule validator interface
│           ├── RequiredValidator.java         # required / requiredWhen
│           ├── StringValidator.java           # string: minLength/maxLength, allowedValues, boolean, urlScheme
│           ├── IntegerValidator.java          # integer: minValue/maxValue, allowedRanges
│           ├── DecimalValidator.java          # decimal: minDecimal/maxDecimal, precision
│           ├── TemporalValidator.java         # date / time / datetime
│           ├── EmailValidator.java            # email (multi-address support)
│           ├── IpAddressValidator.java        # ip / cidr
│           ├── HostnameValidator.java         # hostname / fqdn
│           ├── PatternValidator.java          # mac, phone (built-in patterns), custom pattern
│           ├── CrossRefValidator.java         # crossRef (cross-sheet column lookup)
│           ├── SheetRefValidator.java         # sheetRef (value must match a sheet name)
│           ├── CompareColumnsValidator.java   # compare row rule (ColA op ColB)
│           ├── ConditionalRowRuleValidator.java # require/forbid with when condition + cross-sheet
│           ├── WorkbookRuleValidator.java     # workbook_rules (subset/superset/unique/match)
│           ├── GroupPresenceValidator.java    # one_of / only_one / all_or_none row rules
│           ├── SumEqualsValidator.java        # sum: [...] equals: <col> row rule
│           └── Operator.java                 # canonical operator constants + normalize/evaluate
└── src/main/resources/
    ├── Schema.yaml                                          # Reference schema template
    ├── SBC_FIXED_LINE_CONFIGURATION_validation-rules.yaml  # NODE mode example
    └── MRF_ANNOUNCEMENT_LOADING_validation-rules.yaml      # CRGROUP mode example
```

---

## Adding a New Node Type / Activity

No Java changes needed — only a YAML file.

1. Create **`{NODE_TYPE}_{ACTIVITY}_validation-rules.yaml`**
2. Set `groupByColumnName`: `CRGROUP`, `GROUP`, or `NODE`
3. Set `validateIndexSheets: true` (NODE mode) or `false` (CRGROUP/GROUP)
4. Set `validateNodeIds: true`
5. Define `indexSheet.columns`, `nodeIdSheet`, `userIdSheet` (CRGROUP mode), and `sheets:`
6. Add `description:` to columns — appears in the generated Column_Guide sheet

**Verify the YAML by generating a template first:**

```bash
java -jar ciq-processor-1.0.0-cli.jar \
  --mode ciq-generate \
  --node-type <TYPE> --activity <ACTIVITY> \
  --rules <TYPE>_<ACTIVITY>_validation-rules.yaml \
  --output target/templates
```

Open the generated Excel and review the Column_Guide sheet before distributing to users.
