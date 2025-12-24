import models.Command;
import models.Host;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

import java.io.FileInputStream;
import java.util.ArrayList;
import java.util.List;

public class ExcelParser {

    // -------------------- HOSTS --------------------
    public static List<Host> parseHosts(String path) throws Exception {
        List<Host> hosts = new ArrayList<>();
        try (FileInputStream fis = new FileInputStream(path);
                Workbook wb = new XSSFWorkbook(fis)) {

            Sheet sheet = wb.getSheet("hosts_credentials");
            if (sheet == null)
                throw new Exception("Hosts sheet not found");

            FormulaEvaluator evaluator = wb.getCreationHelper().createFormulaEvaluator();

            // Get OS type from cell F2 (row 1, column 5)
            Row osRow = sheet.getRow(1);
            Cell cell = osRow.getCell(5); // F2
            String osType = getCellValue(cell, evaluator);
            System.out.println("Detected OS type: [" + osType + "]");

            // Start reading hosts from row index 4 (line 5 in Excel)
            for (int i = 4; i <= sheet.getLastRowNum(); i++) {
                Row row = sheet.getRow(i);
                if (row == null)
                    continue; // skip empty rows
                if (row.getCell(0) == null || getCellValue(row.getCell(0), evaluator).isEmpty())
                    continue; // skip rows with empty first cell

                String host = getCellValue(row.getCell(1), evaluator);
                String user = getCellValue(row.getCell(7), evaluator);
                String pass = getCellValue(row.getCell(12), evaluator);
                // Use OS type from F2 for all hosts
                hosts.add(new Host(host, user, pass, osType));

                System.out.println("Host: " + host + ", User: " + user + ", Type: " + osType);
            }
        }
        return hosts;
    }

    // -------------------- COMMANDS --------------------
    public static List<Command> parseCommands(String path) throws Exception {
        List<Command> commands = new ArrayList<>();
        try (FileInputStream fis = new FileInputStream(path);
                Workbook wb = new XSSFWorkbook(fis)) {

            Sheet sheet = wb.getSheet("commands");
            if (sheet == null)
                throw new Exception("Commands sheet not found");

            FormulaEvaluator evaluator = wb.getCreationHelper().createFormulaEvaluator();

            for (Row row : sheet) {
                if (row.getRowNum() == 0)
                    continue; // skip header
                if (row.getCell(0) == null || getCellValue(row.getCell(0), evaluator).isEmpty())
                    continue;

                String desc = getCellValue(row.getCell(0), evaluator);
                String cmd = getCellValue(row.getCell(1), evaluator);

                commands.add(new Command(desc, cmd));
            }
        }
        return commands;
    }

    // -------------------- HELPER --------------------
    private static String getCellValue(Cell cell, FormulaEvaluator evaluator) {
        if (cell == null)
            return "";

        CellType type = cell.getCellType();
        if (type == CellType.FORMULA) {
            // Evaluate the formula
            CellValue cv = evaluator.evaluate(cell);
            if (cv == null)
                return "";
            switch (cv.getCellType()) {
                case STRING:
                    return cv.getStringValue().trim();
                case NUMERIC:
                    double val = cv.getNumberValue();
                    return (val == (long) val) ? String.valueOf((long) val) : String.valueOf(val);
                case BOOLEAN:
                    return String.valueOf(cv.getBooleanValue());
                default:
                    return "";
            }
        } else if (type == CellType.STRING) {
            return cell.getStringCellValue().trim();
        } else if (type == CellType.NUMERIC) {
            double val = cell.getNumericCellValue();
            return (val == (long) val) ? String.valueOf((long) val) : String.valueOf(val);
        } else if (type == CellType.BOOLEAN) {
            return String.valueOf(cell.getBooleanCellValue());
        }
        return "";
    }
}
