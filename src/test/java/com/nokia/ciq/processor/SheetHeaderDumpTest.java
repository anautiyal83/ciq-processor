package com.nokia.ciq.processor;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.Test;
import java.io.*;
public class SheetHeaderDumpTest {
    @Test
    public void dumpHeaders() throws Exception {
        String path = "D:/Nokia/documents/CR_Automation/MRF/MRF_ANNOUNCEMENT_LOADING_TEST_CIQ.xlsx";
        if (!new File(path).exists()) { System.out.println("File not found"); return; }
        try (FileInputStream fis = new FileInputStream(path);
             Workbook wb = new XSSFWorkbook(fis)) {
            for (int i = 0; i < wb.getNumberOfSheets(); i++) {
                Sheet sheet = wb.getSheetAt(i);
                System.out.println("=== Sheet: " + sheet.getSheetName() + " ===");
                for (int r = 0; r <= Math.min(2, sheet.getLastRowNum()); r++) {
                    Row row = sheet.getRow(r); if (row == null) continue;
                    System.out.print("  Row " + r + ": ");
                    for (Cell cell : row) {
                        String v = "";
                        if (cell.getCellType() == CellType.STRING) v = cell.getStringCellValue();
                        else if (cell.getCellType() == CellType.NUMERIC) v = String.valueOf((long)cell.getNumericCellValue());
                        if (!v.trim().isEmpty()) System.out.print("[col" + cell.getColumnIndex() + "=" + v + "] ");
                    }
                    System.out.println();
                }
            }
        }
    }
}
