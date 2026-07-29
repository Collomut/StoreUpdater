package com.stockmanager.util;

import com.stockmanager.model.*;
import javafx.scene.control.Alert;
import javafx.scene.control.ButtonType;
import javafx.stage.FileChooser;

import java.io.*;
import java.time.LocalDate;
import java.util.List;

public class CsvExporter {

    public static void exportProducts(List<Product> products, int shopId) {
        FileChooser fc = new FileChooser();
        fc.setTitle("Export Inventory to CSV");
        fc.setInitialFileName("inventory_shop" + shopId + "_" + LocalDate.now() + ".csv");
        fc.getExtensionFilters().add(new FileChooser.ExtensionFilter("CSV Files", "*.csv"));
        File file = fc.showSaveDialog(null);
        if (file == null) return;

        try (PrintWriter pw = new PrintWriter(new FileWriter(file))) {
            pw.println("Name,Category,SKU,Unit,Quantity,ReorderLevel,CostPrice(RWF),SellingPrice(RWF),Status");
            for (Product p : products) {
                pw.printf("\"%s\",\"%s\",\"%s\",\"%s\",%d,%d,%.0f,%.0f,\"%s\"%n",
                    p.getName(), p.getCategory(), p.getSku(), p.getUnit(),
                    p.getQuantity(), p.getReorderLevel(),
                    p.getCostPrice(), p.getSellingPrice(),
                    p.isLowStock() ? "LOW STOCK" : "OK");
            }
            showSuccess("Inventory exported to:\n" + file.getAbsolutePath());
        } catch (IOException e) {
            showError("Export failed: " + e.getMessage());
        }
    }

    public static void exportSales(List<Sale> sales, int shopId) {
        FileChooser fc = new FileChooser();
        fc.setTitle("Export Sales to CSV");
        fc.setInitialFileName("sales_shop" + shopId + "_" + LocalDate.now() + ".csv");
        fc.getExtensionFilters().add(new FileChooser.ExtensionFilter("CSV Files", "*.csv"));
        File file = fc.showSaveDialog(null);
        if (file == null) return;

        try (PrintWriter pw = new PrintWriter(new FileWriter(file))) {
            pw.println("Date,Receipt,Total(RWF),Notes");
            for (Sale s : sales) {
                pw.printf("\"%s\",\"%s\",%.0f,\"%s\"%n",
                    s.getSaleDate(), s.getReceiptNumber(),
                    s.getTotalAmount(), s.getNotes() != null ? s.getNotes() : "");
            }
            showSuccess("Sales exported to:\n" + file.getAbsolutePath());
        } catch (IOException e) {
            showError("Export failed: " + e.getMessage());
        }
    }

    private static void showSuccess(String msg) {
        new Alert(Alert.AlertType.INFORMATION, msg, ButtonType.OK).showAndWait();
    }

    private static void showError(String msg) {
        new Alert(Alert.AlertType.ERROR, msg, ButtonType.OK).showAndWait();
    }
}
