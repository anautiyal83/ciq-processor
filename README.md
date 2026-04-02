# CIQ Processor

Validates a Nokia CIQ (Configuration Input Questionnaire) Excel workbook against YAML-defined rules, produces validation reports, and on success segregates the data into per-scope JSON files consumed by **mop-generator-utility**.

---

## Overview

```
CIQ Excel (.xlsx)
       │
       ▼
InMemoryExcelReader  ──  reads workbook into memory (no disk writes)
       │
       ▼
CiqValidationEngine  ──  validates every sheet/row against rules YAML
       │
       ├──►  Validation Report  (JSON / HTML / Excel)
       │
       │  validation PASSED + --mop-json-dir supplied?
       │             │ yes
       ▼
Child-order JSON segregation
  ┌────┴────────────────┐
CRGROUP              GROUP            NODE
  │                    │               │
  ▼                    ▼               ▼
mop-json/           mop-json/       mop-json/
  CR-001/              A/            SBC-1_CR1/
  CR-002/              B/            SBC-2_CR1/
       │
       ▼
mop-generator-utility
```

---

## CLI Usage

```
java -jar ciq-processor-1.0.0-cli.jar [options]

Required:
  --ciq           <file>   CIQ Excel workbook (.xlsx)
  --node-type     <type>   Node type, e.g. SBC or MRF
  --activity      <name>   Activity name, e.g. FIXED_LINE_CONFIGURATION
  --rules         <file>   YAML validation-rules file
                           Naming convention: <NODE_TYPE>_<ACTIVITY>_validation-rules.yaml
  --output        <dir>    Output directory for validation reports

Optional:
  --format        <csv>    Report formats: JSON,HTML,MSEXCEL (default: all three)
  --mop-json-dir  <dir>    JSON output for MOP generation (only written on PASSED)
  --report-name   <name>   Base file name for reports (no extension)
                           Default: <node-type>_<activity>_validation-report

Exit code: 0 = PASSED, 1 = FAILED or error
```

### Example — SBC NODE mode

```
java -jar ciq-processor-1.0.0-cli.jar \
  --ciq         SBC_FIXED_LINE_CONFIGURATION_CIQ.xlsx \
  --node-type   SBC                                   \
  --activity    FIXED_LINE_CONFIGURATION              \
  --rules       SBC_FIXED_LINE_CONFIGURATION_validation-rules.yaml \
  --output      target/processor-output               \
  --format      JSON,HTML                             \
  --mop-json-dir target/mop-json
```

### Example — MRF CRGROUP mode

```
java -jar ciq-processor-1.0.0-cli.jar \
  --ciq         MRF_ANNOUNCEMENT_LOADING_CIQ.xlsx \
  --node-type   MRF                               \
  --activity    ANNOUNCEMENT_LOADING              \
  --rules       MRF_ANNOUNCEMENT_LOADING_validation-rules.yaml \
  --output      target/processor-output           \
  --mop-json-dir target/mop-json
```

---

## Grouping Modes

The mode is controlled by **`groupByColumnName`** in the validation-rules YAML. It determines the INDEX sheet column structure, the JSON output layout, and which `--` parameter mop-generator-utility expects.

| `groupByColumnName` | INDEX sheet columns | JSON output layout | mop-generator-utility |
|---|---|---|---|
| `CRGROUP` | `GROUP \| CRGROUP \| NODE` | `mop-json/<CRGROUP>/` | `--crgroup <id>` |
| `GROUP` | `GROUP \| NODE` | `mop-json/<GROUP>/` | `--group <name>` |
| `NODE` | `Node \| CRGroup \| Tables` | `mop-json/<Node>_<CRGroup>/` | `--host <node>` |

---

### CRGROUP mode

Triggered by `groupByColumnName: CRGROUP`. Used when nodes are grouped into change requests, each spanning one or more GROUPs.

**CIQ INDEX sheet structure:**

| GROUP | CRGROUP | NODE |
|---|---|---|
| A | CR-001 | MRF1 |
| A | CR-001 | MRF2 |
| B | CR-001 | MRF3 |
| B | CR-002 | MRF4 |

- **CRGROUP** = change window (e.g. `CR-001`). Nodes in the same CRGROUP are executed in the same maintenance window.
- **GROUP** = MOP content scope (e.g. `A`, `B`). All nodes in a GROUP receive identical configuration rows.
- A GROUP can appear in multiple CRGROUPs (data files are shared).

**JSON output:**

```
mop-json/
├── CR-001/
│   ├── MRF_ANNOUNCEMENT_LOADING_CRGroupIndex_CR-001.json
│   ├── MRF_ANNOUNCEMENT_LOADING_ANNOUNCEMENT_FILES_A.json
│   └── MRF_ANNOUNCEMENT_LOADING_ANNOUNCEMENT_FILES_B.json
└── CR-002/
    ├── MRF_ANNOUNCEMENT_LOADING_CRGroupIndex_CR-002.json
    └── MRF_ANNOUNCEMENT_LOADING_ANNOUNCEMENT_FILES_B.json
```

**`CRGroupIndex` JSON structure:**

```json
{
  "nodeType": "MRF",
  "activity": "ANNOUNCEMENT_LOADING",
  "crGroup": "CR-001",
  "groups": [
    {
      "group": "A",
      "nodes": ["MRF1", "MRF2"],
      "niamMapping": { "MRF1": "RJ-NOKIA-MRF-01-CLI", "MRF2": "RJ-NOKIA-MRF-02-CLI" }
    },
    {
      "group": "B",
      "nodes": ["MRF3"],
      "niamMapping": { "MRF3": "RJ-NOKIA-MRF-03-CLI" }
    }
  ]
}
```

---

### GROUP mode

Triggered by `groupByColumnName: GROUP`. Used when all nodes in a GROUP receive identical configuration (homogeneous MOP), with no CRGROUP-level change window concept.

**CIQ INDEX sheet structure:**

| GROUP | NODE |
|---|---|
| A | MRF1 |
| A | MRF2 |
| B | MRF3 |

**JSON output:**

```
mop-json/
├── A/
│   ├── MRF_ANNOUNCEMENT_LOADING_index_A.json
│   └── MRF_ANNOUNCEMENT_LOADING_ANNOUNCEMENT_FILES_A.json
└── B/
    ├── MRF_ANNOUNCEMENT_LOADING_index_B.json
    └── MRF_ANNOUNCEMENT_LOADING_ANNOUNCEMENT_FILES_B.json
```

**`GroupIndex` JSON structure:**

```json
{
  "nodeType": "MRF",
  "activity": "ANNOUNCEMENT_LOADING",
  "group": "A",
  "nodes": ["MRF1", "MRF2"],
  "tables": ["ANNOUNCEMENT_FILES"],
  "niamMapping": { "MRF1": "RJ-NOKIA-MRF-01-CLI", "MRF2": "RJ-NOKIA-MRF-02-CLI" }
}
```

---

### NODE mode

Triggered by `groupByColumnName: NODE`. Used when each node has its own individual configuration rows (heterogeneous MOP).

**CIQ INDEX sheet structure:**

| Node | CRGroup | Tables |
|---|---|---|
| SBC-1 | CR1 | CRFTargetList,ServiceProfileTable |
| SBC-2 | CR1 | CRFTargetList |

- **Child order** = `<Node>_<CRGroup>` (e.g. `SBC-1_CR1`)
- Each data sheet must have a `Node` column; rows are filtered per node.

**JSON output:**

```
mop-json/
├── SBC-1_CR1/
│   ├── SBC_FIXED_LINE_CONFIGURATION_index_SBC-1_CR1.json
│   ├── SBC_FIXED_LINE_CONFIGURATION_CRFTargetList_SBC-1_CR1.json
│   └── SBC_FIXED_LINE_CONFIGURATION_ServiceProfileTable_SBC-1_CR1.json
└── SBC-2_CR1/
    ├── SBC_FIXED_LINE_CONFIGURATION_index_SBC-2_CR1.json
    └── SBC_FIXED_LINE_CONFIGURATION_CRFTargetList_SBC-2_CR1.json
```

**`CiqIndex` JSON structure:**

```json
{
  "nodeType": "SBC",
  "activity": "FIXED_LINE_CONFIGURATION",
  "entries": [
    { "node": "SBC-1", "crGroup": "CR1", "tables": ["CRFTargetList", "ServiceProfileTable"] }
  ],
  "niamMapping": { "SBC-1": "sbc1-neid" }
}
```

---

## Output Parameters

When validation **PASSES**, the following parameters are printed to stdout and returned in `ValidationReport.getParameters()`.

### NODE mode

| Parameter | Example |
|---|---|
| `NODE_1` | `SBC-1` |
| `NIAM_ID_1` | `sbc1-neid` |
| `NODE_2` | `SBC-2` |
| `NIAM_ID_2` | `sbc2-neid` |
| `TOTAL_NODES_COUNT` | `2` |
| `CHILD_ORDERS_COUNT` | `2` |

### GROUP mode

| Parameter | Example |
|---|---|
| `GROUP_1` | `A` |
| `GROUP_1_VALUES` | `MRF1,MRF2` |
| `GROUP_2` | `B` |
| `GROUP_2_VALUES` | `MRF3,MRF4` |
| `TOTAL_GROUPS_COUNT` | `2` |
| `NODE_1` | `MRF1` |
| `NIAM_ID_1` | `RJ-NOKIA-MRF-01-CLI` |
| `TOTAL_NODES_COUNT` | `4` |
| `CHILD_ORDERS_COUNT` | `2` |

### CRGROUP mode

| Parameter | Example |
|---|---|
| `CRGROUP_1` | `CR-001` |
| `CRGROUP_1_GROUPS` | `A,B` |
| `CRGROUP_1_NODES` | `MRF1,MRF2,MRF3` |
| `CRGROUP_1_NODES_COUNT` | `3` |
| `TOTAL_CRGROUPS_COUNT` | `2` |
| `CHILD_ORDERS_COUNT` | `2` (= `TOTAL_CRGROUPS_COUNT`) |
| `GROUP_1` | `A` |
| `GROUP_1_VALUES` | `MRF1,MRF2` |
| `TOTAL_GROUPS_COUNT` | `2` |
| `NODE_1` | `MRF1` |
| `NIAM_ID_1` | `RJ-NOKIA-MRF-01-CLI` |
| `TOTAL_NODES_COUNT` | `3` |

---

## Validation Reports

Three formats are available, all written to `--output`:

| Format | File | Contents |
|---|---|---|
| `JSON` | `<name>.json` | Machine-readable; full error list with sheet, row, column, message |
| `HTML` | `<name>.html` | Human-readable; colour-coded per-sheet error tables |
| `MSEXCEL` | `<name>.xlsx` | Excel report; one sheet per data sheet with errors highlighted |

Default base name: `<node-type>_<activity>_validation-report`

---

## CIQ Excel Workbook Structure

| Sheet | Purpose | Required columns |
|---|---|---|
| `Index` | Maps nodes → groups → tables | Depends on mode (see Grouping Modes above) |
| `NODE_ID` | NIAM (NE ID) mapping | `Node`/`NODE` + `NIAM`/`NIAM_ID` |
| `<TableName>` | Configuration data | `Node` (NODE mode) or `GROUP` (GROUP/CRGROUP mode), `Action`, record fields |

### Action column values

| Value | Meaning |
|---|---|
| `CREATE` | Create a new record — generates `xc:operation="create"` XML |
| `DELETE` | Delete an existing record — generates `xc:operation="delete"` XML |
| `MODIFY` | Modify an existing record — generates `xc:operation="merge"` XML |

For `MODIFY` rows, `ActionKey` (which field to match) and `SubAction` (`ADD`/`DEL`/`MOD` for sub-table entries) may also be required depending on the rules.

### CIQ columns as MOP template variables

Data sheet columns are available as `${COLUMN_NAME}` variables in the mop-generator-utility YAML template commands. The column name is used as-is; `Record.`-prefixed columns (e.g. `Record.INPUT_FILE`) are referenced without the prefix (`${INPUT_FILE}`).

| Column characteristic | How resolved in MOP |
|---|---|
| Same value across all rows (e.g. `CIRCLE`, `VERSION`) | Group-level variable — substituted once |
| Different value per row (e.g. `INPUT_FILE`, `MRF_DESTINATION_PATH`) | Per-row expansion — command written once per CIQ row |

Built-in variables always available: `${NODE}` (node name) and `${NEID}` (NE identifier from NIAM mapping).

---

## Validation Rules YAML

File naming convention: **`{NODE_TYPE}_{ACTIVITY}_validation-rules.yaml`**

### Top-level settings

```yaml
# Controls JSON segregation mode and INDEX sheet interpretation
groupByColumnName: CRGROUP     # CRGROUP | GROUP | NODE

# Validate that every table listed in INDEX has a matching data sheet
validateIndexSheets: false     # true for NODE mode; false for GROUP/CRGROUP mode

# Validate that every node in the INDEX exists in the NODE_ID sheet
validateNodeIds: true
```

### INDEX sheet column rules

```yaml
indexSheet:
  columns:
    # CRGROUP mode:
    GROUP:   { required: true }
    CRGROUP: { required: true }
    NODE:    { required: true }

    # GROUP mode:
    # GROUP: { required: true }
    # NODE:  { required: true }

    # NODE mode:
    # Node:    { required: true }
    # CRGroup: { required: true }
    # Tables:  { required: true }
```

### NODE_ID sheet column rules

```yaml
nodeIdSheet:
  columns:
    NODE:    { required: true }   # or "Node" for NODE mode
    NIAM_ID: { required: true }   # or "NIAM" for NODE mode
```

### Data sheet rules

```yaml
sheets:

  MyTable:
    columns:
      Node:
        required: true
        crossRef:             # Value must appear in another sheet's column
          sheet: "_index"
          column: node

      Action:
        required: true
        allowedValues: [CREATE, DELETE, MODIFY]

      ActionKey:
        requiredWhen:         # Required only when another column has a specific value
          column: Action
          value:  MODIFY

      SubAction:
        requiredWhen:
          column: Action
          value:  MODIFY
        allowedValues: [ADD, DEL, MOD]

      ID:
        required: true
        integer:  true
        minValue: 1
        maxValue: 6000

      NAME:
        minLength: 1
        maxLength: 19

      Record.VERSION:
        required: true
        pattern: "^\\d+\\.\\d+\\.\\d+\\.\\d+$"
        patternMessage: "VERSION must be in format X.X.X.X (e.g. 17.0.3.7)"

      Record.SOME_RANGE_FIELD:
        allowedRanges:        # Multiple allowed numeric bands
          - { min: 0,   max: 0   }
          - { min: 500, max: 500 }
          - { min: 1024, max: 65535 }
```

### Column validation options — full reference

| Option | Type | Description |
|---|---|---|
| `required: true` | boolean | Cell must not be blank |
| `requiredWhen: { column, value }` | object | Required only when the named column equals the given value |
| `allowedValues: [...]` | list | Cell must be one of the listed values (case-sensitive) |
| `allowedRanges: [{min, max}, ...]` | list | Numeric value must fall within at least one of the bands |
| `integer: true` | boolean | Cell must parse as a whole number |
| `minValue` / `maxValue` | number | Numeric lower / upper bound (inclusive) |
| `minLength` / `maxLength` | number | String length bounds (inclusive) |
| `pattern: "regex"` | string | Value must match the Java regex |
| `patternMessage: "..."` | string | Custom error message when `pattern` fails |
| `crossRef: { sheet, column }` | object | Value must exist in the referenced sheet's column (`_index` = INDEX sheet) |

---

## Programmatic API

```java
ValidationReport report = new CiqProcessorImpl().process(
    "/path/to/CIQ.xlsx",                        // ciqFilePath
    "MRF",                                      // nodeType
    "ANNOUNCEMENT_LOADING",                     // activity
    "/path/to/MRF_ANNOUNCEMENT_LOADING_validation-rules.yaml", // rulesFilePath
    "/path/to/output",                          // outputDir
    List.of(ReportFormat.JSON, ReportFormat.HTML), // formats
    "/path/to/mop-json",                        // mopJsonOutputDir (null = skip)
    null                                        // reportFileName (null = default)
);

System.out.println(report.getStatus());         // "PASSED" or "FAILED"
System.out.println(report.getTotalErrors());    // total error count
report.getParameters().forEach((k, v) -> ...); // output parameters (on PASSED)
```

---

## Project Layout

```
ciq-processor/
├── src/main/java/com/nokia/ciq/
│   ├── processor/
│   │   ├── CiqProcessor.java                  # API interface
│   │   ├── CiqProcessorImpl.java              # Validation + segregation pipeline
│   │   ├── CiqProcessorMain.java              # CLI entry point
│   │   ├── model/
│   │   │   ├── CRGroupIndex.java              # CRGROUP mode index model (written per CRGROUP)
│   │   │   └── GroupIndex.java                # GROUP mode index model (written per GROUP)
│   │   └── reader/
│   │       ├── InMemoryExcelReader.java       # Reads CIQ Excel; detects INDEX structure
│   │       └── InMemoryCiqDataStore.java      # In-memory data store (sheets + index)
│   └── validator/
│       ├── CiqValidationEngine.java           # Row-level validation + output parameters
│       ├── config/
│       │   ├── ValidationRulesConfig.java     # Top-level rules model
│       │   ├── ValidationRulesLoader.java     # Loads YAML rules file
│       │   ├── ColumnRule.java                # Per-column rule model
│       │   ├── SheetRules.java                # Per-sheet rule model
│       │   ├── ConditionalRequired.java       # requiredWhen model
│       │   ├── CrossRef.java                  # crossRef model
│       │   └── IntRange.java                  # allowedRanges entry model
│       ├── model/
│       │   ├── ValidationReport.java          # Report model (status, errors, parameters)
│       │   ├── SheetValidationResult.java     # Per-sheet error list
│       │   └── ValidationError.java           # Single error (sheet, row, column, message)
│       ├── report/
│       │   ├── ReportFormat.java              # JSON | HTML | MSEXCEL enum
│       │   ├── HtmlReportWriter.java          # Writes HTML validation report
│       │   └── ExcelReportWriter.java         # Writes Excel validation report
│       └── validator/
│           ├── CellValidator.java             # Dispatcher: runs all applicable validators
│           ├── RequiredValidator.java
│           ├── AllowedValuesValidator.java
│           ├── AllowedRangesValidator.java
│           ├── IntegerValidator.java
│           ├── LengthValidator.java
│           ├── PatternValidator.java
│           └── CrossRefValidator.java
└── src/main/resources/
    ├── SBC_FIXED_LINE_CONFIGURATION_validation-rules.yaml   # NODE mode example
    └── MRF_ANNOUNCEMENT_LOADING_validation-rules.yaml       # CRGROUP mode example
```

---

## Adding a New Node Type / Activity

1. Create **`{NODE_TYPE}_{ACTIVITY}_validation-rules.yaml`** — no Java changes needed.
2. Set `groupByColumnName` to match the CIQ INDEX sheet column structure.
3. Set `validateIndexSheets` and `validateNodeIds` for the mode.
4. Define `indexSheet.columns` and `nodeIdSheet.columns` for the workbook layout.
5. Define `sheets:` with column-level rules for each data sheet.
6. Pass the YAML path via `--rules` on the CLI, or as `rulesFilePath` in the API.
