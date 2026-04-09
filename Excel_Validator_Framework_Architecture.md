# Excel Validator Framework — Architecture

## 1. Overview

The framework is a single-JAR command-line tool that:

1. **Generates** a blank CIQ Excel template from a YAML rules file.
2. **Validates** a filled CIQ workbook against those rules.
3. **Segregates** validated data into per-CR / per-group JSON folders for the MOP generator.

All three operations are driven by one external file: a YAML rules file named
`{NODE_TYPE}_{ACTIVITY}_validation-rules.yaml`.  No code changes are required to support a new
node type or activity.

---

## 2. Module / Package Structure

```
com.nokia.ciq.processor
├── CiqProcessorMain          CLI entry point
├── CiqProcessor              Processor interface
├── CiqProcessorImpl          Pipeline orchestrator
│
├── reader/
│   ├── InMemoryExcelReader   Excel → in-memory CIQ model (Apache POI)
│   └── InMemoryCiqDataStore  CiqDataStore implementation
│
├── model/
│   ├── CRGroupIndex          CRGROUP-mode segregation JSON model
│   └── GroupIndex            GROUP-mode segregation JSON model
│
└── template/
    ├── CiqTemplateGenerator  Blank .xlsx template generator
    └── CiqTemplateResult     Template generation result

com.nokia.ciq.reader
├── model/
│   ├── CiqIndex              Index sheet metadata (entries, NIAM mapping)
│   ├── CiqSheet              Single sheet (columns + rows)
│   ├── CiqRow                Single data row (column → value map)
│   └── NodeEntry             Index entry (Node, CRGroup, Tables list)
└── store/
    └── CiqDataStore          Read-only data access interface

com.nokia.ciq.validator
├── CiqValidationEngine       Validation orchestrator
│
├── config/
│   ├── ValidationRulesConfig Root YAML config object
│   ├── ValidationRulesLoader YAML → config object (SnakeYAML)
│   ├── SheetRules            Column rules + row rules for one sheet
│   ├── ColumnRule            Per-column validation specification
│   ├── SheetRowRule          Row-level rule (require/forbid/compare/…)
│   ├── RowCondition          Conditional predicate (when: clause)
│   ├── WorkbookRule          Cross-sheet workbook-level rule
│   ├── WorkbookSettings      Sheet reading settings
│   ├── OutputRule            Post-validation aggregate definition
│   ├── IntRange              Min/max range for allowedRanges
│   ├── ColumnMessages        Custom error messages per constraint
│   ├── ConditionalRequired   requiredWhen shorthand
│   ├── CrossRef              Cross-sheet value existence check
│   └── ValidatorDefinition   Custom validator class reference
│
├── model/
│   ├── ValidationReport      Top-level result (status, errors, parameters)
│   ├── SheetValidationResult Per-sheet result + error list
│   └── ValidationError       Single error (row, column, value, message)
│
├── report/
│   ├── HtmlReportWriter      HTML report generator
│   ├── ExcelReportWriter     Excel report generator
│   └── ReportFormat          Enum: JSON | HTML | MSEXCEL
│
└── validator/
    ├── CellValidator         Interface — all validators implement this
    ├── RequiredValidator     required / requiredWhen
    ├── StringValidator       minLength, maxLength, allowedValues, pattern (string type)
    ├── IntegerValidator      integer type — minValue, maxValue, allowedRanges
    ├── DecimalValidator      decimal type — minDecimal, maxDecimal, precision
    ├── TemporalValidator     datetime type — configurable SimpleDateFormat
    ├── EmailValidator        email type — RFC 5322, multi-address support
    ├── IpAddressValidator    ip type — IPv4 / IPv6 / both
    ├── PatternValidator      pattern: regex, built-in phone/mac patterns
    ├── CrossRefValidator     ref: — value must exist in another sheet column
    ├── SheetRefValidator     sheetRef: true — value must match a sheet name
    ├── ConditionalRowRuleValidator   require/forbid with when: conditions
    ├── CompareColumnsValidator       compare: "ColA op ColB"
    ├── GroupPresenceValidator        one_of / only_one / all_or_none
    ├── SumEqualsValidator            sum: [cols], equals: col
    └── WorkbookCrossRefValidator     subset / superset / match / unique
```

---

## 3. High-Level Data Flow

```
┌─────────────────────────────────────────────────────────────────────────┐
│                          CiqProcessorMain (CLI)                         │
│  --mode ciq-validate / ciq-generate                                     │
│  --ciq  --node-type  --activity  --rules  --output  --mop-json-dir      │
└───────────────────────────┬─────────────────────────────────────────────┘
                            │
                            ▼
┌─────────────────────────────────────────────────────────────────────────┐
│                        CiqProcessorImpl.process()                       │
│                                                                         │
│  ① ValidationRulesLoader.load(yaml)  →  ValidationRulesConfig          │
│                                                                         │
│  ② InMemoryExcelReader.read(xlsx)    →  CiqDataStore                   │
│       ├─ readNiamMapping()                                              │
│       ├─ readIndex()  (detects mode: NODE / GROUP / CRGROUP)            │
│       └─ readSheet()  per data table                                    │
│                                                                         │
│  ③ CiqValidationEngine.validate()   →  ValidationReport                │
│       ├─ per-sheet:  column checks + row rules                          │
│       ├─ workbook rules (cross-sheet)                                   │
│       └─ computeOutputs() [PASSED only]                                 │
│                                                                         │
│  ④ ReportWriters (JSON + HTML + Excel)                                  │
│                                                                         │
│  ⑤ Segregation [PASSED + --mop-json-dir]                               │
│       ├─ NODE mode    →  segregateByChildOrder()                        │
│       ├─ GROUP mode   →  segregateGroupBased()                          │
│       └─ CRGROUP mode →  segregateCRGroupBased()                       │
└─────────────────────────────────────────────────────────────────────────┘
```

---

## 4. Component Details

### 4.1 CLI — `CiqProcessorMain`

Parses command-line arguments and dispatches to either the validation pipeline or the template
generator.

```
--mode ciq-validate  →  CiqProcessorImpl.process()
--mode ciq-generate  →  CiqTemplateGenerator.generate()
```

Prints `KEY=VALUE` parameters to stdout on completion:
- Always: `STATUS`, `ERRORS`, `REPORT_FILENAME`
- On PASSED: parameters declared in the `outputs:` YAML section

Exit code: `0` = PASSED/SUCCESS, `1` = FAILED/ERROR.

---

### 4.2 YAML Loader — `ValidationRulesLoader`

Uses **SnakeYAML** with:
- Custom `TypeDescription` entries for all nested config classes.
- Property alias `workbook_rules` → `workbookRules` (maps YAML snake-case to Java camelCase).
- Unknown properties silently skipped so external YAML keys (e.g. comments, notes) do not
  cause parse failures.

Returns a fully populated `ValidationRulesConfig` object.

---

### 4.3 Excel Reader — `InMemoryExcelReader`

Reads the workbook once using **Apache POI** and populates an `InMemoryCiqDataStore`.

**Key responsibilities:**

| Task | Detail |
|---|---|
| NIAM mapping | Reads `Node_ID` / `Node_Details` sheet; builds `Map<Node, NIAM_ID>` |
| Index detection | Reads `Index` sheet; inspects columns to choose mode |
| Mode detection | See Section 5 |
| Sheet reading | `readSheet()` with optional `stripTrailingBlanks` |
| Header detection | Scans first 10 rows; falls back to lenient matching |
| 31-char name truncation | Matches long table names by prefix if exact match fails |
| Primary-area scanning | Ignores reference/lookup columns that repeat headers to the right |
| Blank row handling | Controlled by `ignoreBlankRows` setting; special sheets strip trailing blanks |

**`stripTrailingBlanks` flag:**
- `true` for structural sheets (Index, Node_ID) — prevents phantom Excel rows from appearing as
  required-field errors.
- `false` (default) for user data sheets — blank rows in the middle of data are intentional and
  must reach the validator.

---

### 4.4 Data Model

```
CiqDataStore (interface)
├── getIndex()                  → CiqIndex
├── getSheet(tableName)         → CiqSheet
└── getAvailableSheets()        → List<String>

InMemoryCiqDataStore
├── index                       CiqIndex
├── sheets                      Map<String, CiqSheet>
├── rawIndexSheet               CiqSheet (unfiltered Index, for column validation)
├── rawNodeIdSheet              CiqSheet (unfiltered Node_ID, for column validation)
└── allWorkbookSheetNames       Set<String> (all sheets, for sheetRef validation)

CiqIndex
├── entries                     List<NodeEntry>
├── niamMapping                 Map<Node, NIAM_ID>
├── nodeType, activity
└── getAllTables()               → unique table names

NodeEntry
├── node
├── crGroup
└── tables                      List<String>

CiqSheet
├── sheetName
├── columns                     List<String> (header names)
└── rows                        List<CiqRow>

CiqRow
├── rowNumber                   (1-based, for error messages)
├── data                        LinkedHashMap<String, String>
└── get(columnName)             case-insensitive, underscore-insensitive lookup
```

**Case/underscore-insensitive lookup in `CiqRow.get()`:**
`"CRGroup"`, `"CR_GROUP"`, `"crgroup"`, and `"cr_group"` all resolve to the same value.
This eliminates header-normalisation bugs across different CIQ file conventions.

---

### 4.5 Validation Engine — `CiqValidationEngine`

Orchestrates the full validation pass in this order:

```
1. For each sheet declared in the YAML rules:
   a. Check sheet presence (required: true)
   b. Check for blank sheet (0 rows or all-blank rows)
   c. Check for missing columns (declared in rules but not in sheet)
   d. For each data row:
      i.  For each column: run validator chain (see 4.6)
      ii. For each row rule: run row validator (see 4.7)

2. Validate special sheets (Index, Node_ID) using raw unfiltered sheets

3. Run workbook-level rules (subset, superset, match, unique)

4. finalise() → set status = PASSED | FAILED, totalErrors = sum of all errors

5. If PASSED: computeOutputs() → populate report.parameters
```

---

### 4.6 Cell Validator Chain

Validators are applied in a fixed order.  Each validator is responsible for one aspect.
Blank cells are **always** skipped by every validator except `RequiredValidator`.

```
RequiredValidator       → required / requiredWhen check
StringValidator         → minLength, maxLength, allowedValues, pattern (string type)
IntegerValidator        → integer type, minValue, maxValue, allowedRanges
DecimalValidator        → decimal type, minDecimal, maxDecimal, precision
TemporalValidator       → datetime type (configurable SimpleDateFormat)
EmailValidator          → email type (RFC 5322), multi-address
IpAddressValidator      → ip type (IPv4 / IPv6 / both)
PatternValidator        → pattern: regex, built-in phone / mac patterns
CrossRefValidator       → ref: SheetName.Column (value must exist)
SheetRefValidator       → sheetRef: true (value must match a workbook sheet name)
```

**Invalid regex protection:** `PatternValidator` wraps `Pattern.compile()` in a
`try/catch PatternSyntaxException`.  An invalid regex is reported as a configuration error on
every row rather than silently passing.

Custom validators are appended after `SheetRefValidator` when a `validator:` key is present.

---

### 4.7 Row Rule Validators

Applied per-row after all column validators.

| Rule | Validator class | Description |
|---|---|---|
| `require` / `forbid` | `ConditionalRowRuleValidator` | Column presence / absence; supports `when:` |
| `compare` | `CompareColumnsValidator` | Cross-column comparison (=, !=, >, >=, <, <=) |
| `one_of` | `GroupPresenceValidator` | At least one column non-blank |
| `only_one` | `GroupPresenceValidator` | Exactly one column non-blank |
| `all_or_none` | `GroupPresenceValidator` | All filled or all blank |
| `sum` + `equals` | `SumEqualsValidator` | Sum of columns must equal target |

All row rules support an optional `when:` condition evaluated before the rule fires.

---

### 4.8 Workbook-Level Validation

`WorkbookCrossRefValidator` runs after all sheets are validated.

| Rule | Description |
|---|---|
| `subset` | Every value in `from` column must appear in `to` column |
| `superset` | Every value in `to` column must appear in `from` column |
| `match` | Bi-directional; both columns must contain exactly the same value set |
| `unique` | Values (or composite keys) must not repeat across the listed columns |

---

### 4.9 Validation Report

```
ValidationReport
├── nodeType, activity
├── status                      PASSED | FAILED
├── totalErrors
├── globalErrors                List<String>  (Index-level issues)
├── sheets                      List<SheetValidationResult>
│   └── SheetValidationResult
│       ├── sheetName
│       ├── status
│       ├── rowsChecked
│       └── errors              List<ValidationError>
│           └── rowNumber, column, value, message
└── parameters                  Map<String, String>
    ├── REPORT_FILENAME         (always present)
    └── <outputs entries>       (PASSED only; from outputs: YAML section)
```

Reports are written in three formats:
- **JSON** — machine-readable; used by CI pipelines
- **HTML** — colour-coded; green/red sheet tabs; shared with users to fix errors
- **Excel** — tabular error listing; reviewed in spreadsheet tools

---

### 4.10 Post-Validation Outputs

Computed only when `status = PASSED`.  Reads aggregates directly from the in-memory
`CiqDataStore`:

| Aggregate | Logic |
|---|---|
| `distinct` | Collect non-blank values from column → sort → deduplicate → join with separator |
| `count` | Count non-blank values in column; or count total rows when column is omitted |
| `sum` | Parse each cell as double; accumulate; non-numeric cells are silently skipped |

Results are stored in `ValidationReport.parameters` and printed to stdout as `KEY=VALUE`.

---

### 4.11 MOP JSON Segregation

Runs only when `status = PASSED` and `--mop-json-dir` is provided.

#### Mode: NODE (e.g. SBC)

Index schema: `Node | CRGroup | Tables`

One folder per (Node, CRGroup) combination.

```
mop-json/
└── {NODE}_{CRGROUP}/
    ├── {TYPE}_{ACTIVITY}_index_{NODE}_{CRGROUP}.json     ← single NodeEntry + NIAM
    └── {TYPE}_{ACTIVITY}_{TABLE}_{NODE}_{CRGROUP}.json   ← rows filtered to this node
```

#### Mode: GROUP (e.g. MRF without CRGROUP)

Index schema: `GROUP | NODE` (no `TABLES` column)

One folder per GROUP.

```
mop-json/
└── {GROUP}/
    ├── {TYPE}_{ACTIVITY}_index_{GROUP}.json              ← GroupIndex JSON
    └── {TYPE}_{ACTIVITY}_{TABLE}_{GROUP}.json            ← rows filtered to this group
```

`GroupIndex` JSON:
```json
{
  "nodeType": "MRF",
  "activity": "ANNOUNCEMENT_LOADING",
  "group": "A",
  "nodes": ["MRF1", "MRF2"],
  "tables": ["ANNOUNCEMENT_FILES"],
  "niamMapping": { "MRF1": "...", "MRF2": "..." },
  "groupHomogeneous": true
}
```

#### Mode: CRGROUP (e.g. MRF with CR maintenance windows)

Index schema: `GROUP | CRGROUP | NODE`

One folder per CRGROUP.  Table data is **embedded** in the index file (no separate table files).

```
mop-json/
└── {CRGROUP}/
    └── {TYPE}_{ACTIVITY}_index_{CRGROUP}.json            ← CRGroupIndex JSON (self-contained)
```

`CRGroupIndex` JSON:
```json
{
  "nodeType": "MRF",
  "activity": "ANNOUNCEMENT_LOADING",
  "crGroup": "CR1",
  "email": "engineer@nokia.com",
  "groups": [
    {
      "group": "GRP1",
      "nodes": ["MRF1", "MRF2"],
      "niamMapping": { "MRF1": "...", "MRF2": "..." },
      "tableData": {
        "ANNOUNCEMENT_FILES": [
          { "GROUP": "GRP1", "INPUT_FILE": "audio.tar", "MRF_DESTINATION_PATH": "/var/opt/clips/" }
        ]
      }
    }
  ],
  "allNodes": ["MRF1", "MRF2"]
}
```

---

### 4.12 Template Generator — `CiqTemplateGenerator`

Generates a blank, pre-formatted Excel workbook from the YAML rules file.

**Output**: `{NODE_TYPE}_{ACTIVITY}_CIQ.xlsx`

**Per sheet:**
- Header row (Nokia dark blue fill, white bold text)
- Data validation dropdowns (rows 2–101) for columns with `allowedValues`
- All columns defined in the YAML rules

**Column_Guide sheet** (appended as last tab):

| Sheet | Column | Type | Required | Allowed Values | Constraints | Description |
|---|---|---|---|---|---|---|

Populated from column `description`, `type`, `required`, `allowedValues`, `minLength`/`maxLength`,
etc.  This sheet is informational and is not read or validated by the processor.

---

## 5. Index Mode Detection

`InMemoryExcelReader.readIndex()` inspects the column headers of the `Index` sheet to select
a processing mode:

| Columns present | Mode | Description |
|---|---|---|
| `Node` + `CRGroup` + `Tables` | **NODE** | Traditional per-node segregation |
| `GROUP` + `CRGROUP` + `NODE` | **CRGROUP** | Group-level CR maintenance windows |
| `GROUP` + `NODE` (no `TABLES`) | **GROUP** | Simple group-to-node mapping |
| None of the above | **No-Index fallback** | Index validation skipped |

Mode affects:
- How the Index sheet is parsed
- Which segregation method is called (`segregateByChildOrder`, `segregateGroupBased`,
  `segregateCRGroupBased`)
- Which JSON model class is used (`NodeEntry`, `GroupIndex`, `CRGroupIndex`)

---

## 6. Design Patterns

| Pattern | Where used | Purpose |
|---|---|---|
| **Chain of Responsibility** | `CiqValidationEngine.buildValidatorChain()` | Each `CellValidator` handles one rule type; passes to next |
| **Strategy** | `CellValidator` interface + implementations | Swap / extend validation rules without changing orchestration |
| **Template Method** | `CiqValidationEngine.validate()` | Fixed skeleton (per-sheet → per-row → workbook) with pluggable steps |
| **Repository** | `CiqDataStore` | Abstract read-only data access; decouples reader from engine |
| **Adapter** | `InMemoryExcelReader` / `InMemoryCiqDataStore` | POI `Workbook` → `CiqDataStore` interface |
| **Factory** | `buildValidatorChain()` | Constructs the ordered validator list in one place |
| **Registry** | `validators:` YAML section + reflection | Custom validators registered by name, instantiated on demand |

---

## 7. Key Technical Decisions

### Single-pass in-memory model
The Excel workbook is read once into memory.  All validation, cross-sheet rule evaluation, and
JSON segregation operate on the in-memory `CiqDataStore`.  This avoids multiple file I/O passes
and keeps the code simple.

### Zero-code extensibility via YAML
Every validation rule, sheet structure, and output definition lives in the YAML rules file.
Adding a new node type or activity requires only a new YAML file — no Java changes.

### Custom validators via reflection
When the built-in types do not cover a specific constraint, a `CellValidator` implementation can
be registered in the YAML `validators:` section.  The engine instantiates it by fully-qualified
class name at runtime.

### Blank-cell skip contract
Every cell validator (except `RequiredValidator`) skips null/blank values.  This makes
`required: false` columns truly optional — only the presence check fires on blank.

### `stripTrailingBlanks` separation
Excel often appends phantom blank rows at the end of a sheet.  Stripping these globally would
hide intentional blank rows in user data.  The flag is therefore applied only to structural
sheets (Index, Node_ID) where blank rows are never intentional.

### Case/underscore-insensitive column lookup
`CiqRow.get()` normalises column names before lookup.  This tolerates differences in
capitalisation and separator style (`CRGroup`, `CR_GROUP`, `crgroup`) across CIQ files from
different teams.

---

## 8. External Dependencies

| Dependency | Version | Usage |
|---|---|---|
| Apache POI | 5.x | Reading `.xlsx` Excel workbooks |
| SnakeYAML | 1.x | Parsing YAML rules files |
| Jackson | 2.x | Serialising `ValidationReport` and segregation JSON |
| SLF4J / Logback | — | Structured logging |
| JUnit 4 | — | Unit and integration tests |

---

## 9. File and Naming Conventions

| Artefact | Convention |
|---|---|
| Rules file | `{NODE_TYPE}_{ACTIVITY}_validation-rules.yaml` |
| CIQ template | `{NODE_TYPE}_{ACTIVITY}_CIQ.xlsx` |
| Validation report | `{NODE_TYPE}_{ACTIVITY}_VALIDATION_REPORT.{json\|html\|xlsx}` |
| NODE segregation folder | `{NODE}_{CRGROUP}/` |
| NODE index file | `{TYPE}_{ACTIVITY}_index_{NODE}_{CRGROUP}.json` |
| NODE table file | `{TYPE}_{ACTIVITY}_{TABLE}_{NODE}_{CRGROUP}.json` |
| GROUP segregation folder | `{GROUP}/` |
| GROUP index file | `{TYPE}_{ACTIVITY}_index_{GROUP}.json` |
| CRGROUP segregation folder | `{CRGROUP}/` |
| CRGROUP index file | `{TYPE}_{ACTIVITY}_index_{CRGROUP}.json` |

---

## 10. Component Interaction Diagram

```
                    ┌────────────────────────────────────────┐
                    │           CiqProcessorMain              │
                    │  (CLI argument parsing & dispatching)   │
                    └─────────────┬──────────────────────────┘
                                  │
                    ┌─────────────▼──────────────────────────┐
                    │          CiqProcessorImpl               │
                    │  (pipeline: load → read → validate →   │
                    │   report → segregate)                   │
                    └──┬──────────┬──────────────┬───────────┘
                       │          │              │
          ┌────────────▼──┐  ┌────▼────────┐  ┌─▼──────────────────┐
          │ValidationRules│  │InMemoryExcel│  │CiqValidationEngine  │
          │Loader         │  │Reader       │  │                     │
          │(SnakeYAML)    │  │(Apache POI) │  │  ┌──────────────┐   │
          └────────────┬──┘  └────┬────────┘  │  │ CellValidator│   │
                       │          │           │  │ Chain        │   │
          Validation   │          │ CiqData   │  └──────────────┘   │
          RulesConfig  │          │ Store     │  ┌──────────────┐   │
                       │          │           │  │RowRule       │   │
                       └──────────┼───────────►  │Validators    │   │
                                  │           │  └──────────────┘   │
                                  │           │  ┌──────────────┐   │
                                  │           │  │Workbook      │   │
                                  │           │  │CrossRef      │   │
                                  │           │  └──────────────┘   │
                                  │           └──────────┬──────────┘
                                  │                      │
                                  │           ┌──────────▼──────────┐
                                  │           │  ValidationReport    │
                                  │           └──────────┬──────────┘
                                  │                      │
                          ┌───────┼──────────────────────▼──────────┐
                          │       │      Report Writers               │
                          │       │  JSON / HTML / Excel              │
                          │       └───────────────────────────────────┘
                          │
                          └────────────────────────────────────────────►
                                       Segregation
                                  (NODE / GROUP / CRGROUP)
                                       mop-json/
```
