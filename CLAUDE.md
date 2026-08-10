# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

CIQ Processor is a Java 8 Maven project that validates Nokia CIQ (Configuration Input Questionnaire) Excel workbooks against YAML-defined rules, produces validation reports (JSON/HTML/Excel), and on success generates per-scope JSON files for **mop-generator-utility**. It also generates blank CIQ templates from YAML rules.

## Build & Test

```bash
# Build fat JAR (skip tests — integration tests require local CIQ files)
mvn package -DskipTests

# Run all tests (most are integration tests gated by Assume.assumeTrue on local file paths)
mvn test

# Run a single test class
mvn test -Dtest=CiqProcessorTest

# Run a single test method
mvn test -Dtest=CiqProcessorTest#testCiqGenerate

# Run the CLI
java -jar target/ciq-processor-1.0.2-cli.jar --mode ciq-validate --ciq <file> --node-type <TYPE> --activity <ACT> --rules <rules.yaml> --output <dir>
java -jar target/ciq-processor-1.0.2-cli.jar --mode ciq-generate --node-type <TYPE> --activity <ACT> --rules <rules.yaml> --output <dir>
```

Always use the `-cli.jar` (fat JAR), never the thin JAR.

## Architecture

The pipeline flows: **YAML rules -> Excel reader -> Validation engine -> Reports + JSON output**.

### Key packages (`com.nokia.ciq`)

- **`processor/`** — Entry point and orchestration
  - `CiqProcessorMain` — CLI entry point (`--mode ciq-validate | ciq-generate`)
  - `CiqProcessorImpl` — Main pipeline: load rules, read Excel, validate, write reports, generate JSON
  - `JsonTemplateEvaluator` — Evaluates `_each`/`_join`/WHERE directives in json-output YAML templates
  - `reader/InMemoryExcelReader` — Reads CIQ Excel into `InMemoryCiqDataStore` (no disk intermediates)
  - `template/CiqTemplateGenerator` — Generates blank CIQ Excel workbooks from YAML rules

- **`validator/`** — Validation engine
  - `CiqValidationEngine` — Orchestrates per-sheet row validation + workbook-level cross-sheet rules + output parameter computation
  - `config/` — YAML config model classes (`ValidationRulesConfig`, `ValidationRulesLoader`, `ColumnRule`, `SheetRules`, `WorkbookRule`, etc.)
  - `validator/` — Individual validators (`CellValidator` interface, `RequiredValidator`, `IntegerValidator`, `PatternValidator`, `CrossRefValidator`, `WorkbookRuleValidator`, etc.)
  - `report/` — Report writers (`HtmlReportWriter`, `HtmlTemplateReportWriter`, `ExcelReportWriter`)
  - `model/` — Result models (`ValidationReport`, `SheetValidationResult`, `ValidationError`)

- **`reader/model/`** — Data models for in-memory Excel representation (`CiqSheet`, `CiqRow`, `CiqIndex`)

### Configuration files (YAML-driven, no Java changes needed for new node types)

- **`{NODE_TYPE}_{ACTIVITY}_validation-rules.yaml`** — Defines sheets, column rules, row rules, workbook rules, report output, and output parameters
- **`{NODE_TYPE}_{ACTIVITY}_json-output.yaml`** — Defines JSON output structure with `_each`/`_join`/WHERE template directives and `output_mode` (single/individual)
- Example configs are in `src/main/resources/`

### JSON output modes

- `output_mode: single` — One JSON file for the whole workbook
- `output_mode: individual` — One JSON file per distinct value of `segregate_by` column, each in its own subfolder

## Key Conventions

- Java 8 source/target (no lambdas beyond what Java 8 supports)
- Tests are integration tests that use `Assume.assumeTrue` to skip when local CIQ Excel files aren't present
- Column matching is case-insensitive and underscore/space-insensitive throughout
- Report filenames are controlled by `report_output.filename` in the YAML rules with `{nodeType}` and `{activity}` placeholders
- Exit codes: 0 = PASSED/success, 1 = FAILED/error
- Console output is machine-readable `KEY=VALUE` pairs (parsed by upstream systems)
