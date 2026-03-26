package com.nokia.ciq.reader;

/**
 * CLI entry point for the CIQ Reader.
 *
 * Usage:
 *   java -jar ciq-reader.jar --ciq <file.xlsx> --output <dir> --node-type <SBC> --activity <FIXED_LINE_CONFIGURATION>
 */
public class CiqReaderMain {

    public static void main(String[] args) {
        System.exit(run(args));
    }

    static int run(String[] args) {
        String ciqFile   = null;
        String outputDir = null;
        String nodeType  = null;
        String activity  = null;

        for (int i = 0; i < args.length; i++) {
            switch (args[i]) {
                case "--ciq":        if (i + 1 < args.length) ciqFile   = args[++i]; break;
                case "--output":     if (i + 1 < args.length) outputDir = args[++i]; break;
                case "--node-type":  if (i + 1 < args.length) nodeType  = args[++i]; break;
                case "--activity":   if (i + 1 < args.length) activity  = args[++i]; break;
                case "--help": case "-h": printUsage(); return 0;
                default:
                    System.err.println("Unknown argument: " + args[i]);
                    printUsage();
                    return 1;
            }
        }

        if (ciqFile == null || outputDir == null || nodeType == null || activity == null) {
            System.err.println("Error: --ciq, --output, --node-type, and --activity are required.");
            printUsage();
            return 1;
        }

        CiqReadResult result = new ExcelCiqReader().read(ciqFile, outputDir, nodeType, activity);
        if (result.isSuccess()) {
            System.out.println("SUCCESS: " + result.getMessage());
            return 0;
        } else {
            System.err.println("FAILED: " + result.getError());
            return 1;
        }
    }

    private static void printUsage() {
        System.out.println("Usage: java -jar ciq-reader.jar --ciq <file.xlsx> --output <dir> --node-type <type> --activity <name>");
        System.out.println();
        System.out.println("  --ciq <file>         Path to the CIQ Excel file (.xlsx)");
        System.out.println("  --output <dir>       Directory to write JSON output files");
        System.out.println("  --node-type <type>   Node type, e.g. SBC");
        System.out.println("  --activity <name>    Activity name, e.g. FIXED_LINE_CONFIGURATION");
        System.out.println();
        System.out.println("Output files:");
        System.out.println("  {NODE_TYPE}_{ACTIVITY}_index.json          Index and NIAM mapping");
        System.out.println("  {NODE_TYPE}_{ACTIVITY}_{SheetName}.json    One file per table sheet");
    }
}
