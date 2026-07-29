package com.stockmanager.util;

import com.stockmanager.model.*;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.scene.text.*;
import javafx.print.*;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;

public class ReceiptPrinter {

    public static void print(Sale sale, String shopName) {
        // Build receipt text as a dialog (printable)
        StringBuilder sb = new StringBuilder();
        sb.append("═══════════════════════════════\n");
        sb.append("        ").append(shopName).append("\n");
        sb.append("═══════════════════════════════\n");
        sb.append("Receipt #: ").append(sale.getReceiptNumber()).append("\n");
        sb.append("Date:      ").append(sale.getSaleDate().format(DateTimeFormatter.ofPattern("dd MMM yyyy"))).append("\n");
        sb.append("───────────────────────────────\n");
        for (SaleItem item : sale.getItems()) {
            sb.append(String.format("%-20s\n", item.getProductName()));
            sb.append(String.format("  %3d x RWF %,8.0f = RWF %,10.0f\n",
                item.getQuantitySold(), item.getUnitPrice(), item.getSubtotal()));
        }
        sb.append("───────────────────────────────\n");
        sb.append(String.format("TOTAL:           RWF %,10.0f\n", sale.getTotalAmount()));
        sb.append("═══════════════════════════════\n");
        if (sale.getNotes() != null && !sale.getNotes().isBlank()) {
            sb.append("Note: ").append(sale.getNotes()).append("\n");
        }
        sb.append("\n   Thank you for your purchase!\n");
        sb.append("═══════════════════════════════\n");

        // Show receipt preview dialog
        Dialog<ButtonType> dialog = new Dialog<>();
        dialog.setTitle("Receipt — " + sale.getReceiptNumber());
        dialog.setHeaderText(null);

        TextArea ta = new TextArea(sb.toString());
        ta.setEditable(false);
        ta.setFont(Font.font("Courier New", 12));
        ta.setPrefWidth(380); ta.setPrefHeight(420);
        ta.setStyle("-fx-control-inner-background: #1a1a2e; -fx-text-fill: #e0e0e0;");

        dialog.getDialogPane().setContent(ta);
        dialog.getDialogPane().getStylesheets().add(
            ReceiptPrinter.class.getResource("/css/styles.css").toExternalForm());

        ButtonType printBtn = new ButtonType("🖨 Print", ButtonBar.ButtonData.OK_DONE);
        ButtonType closeBtn = new ButtonType("Close", ButtonBar.ButtonData.CANCEL_CLOSE);
        dialog.getDialogPane().getButtonTypes().addAll(printBtn, closeBtn);

        dialog.showAndWait().ifPresent(result -> {
            if (result == printBtn) {
                doPrint(ta);
            }
        });
    }

    private static void doPrint(TextArea ta) {
        PrinterJob job = PrinterJob.createPrinterJob();
        if (job != null && job.showPrintDialog(ta.getScene().getWindow())) {
            boolean printed = job.printPage(ta);
            if (printed) job.endJob();
        }
    }
}
