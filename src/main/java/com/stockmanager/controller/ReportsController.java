package com.stockmanager.controller;

import com.stockmanager.db.DatabaseManager;
import javafx.concurrent.Task;
import javafx.fxml.FXML;
import javafx.scene.chart.*;
import javafx.scene.control.*;
import javafx.scene.layout.*;

import java.time.LocalDate;
import java.time.Month;
import java.time.format.TextStyle;
import java.util.*;
import java.util.stream.Collectors;

public class ReportsController {

    @FXML private TabPane tabPane;
    @FXML private BarChart<String, Number> weeklyChart, monthlyChart, annualChart, topProductsChart;
    @FXML private Label lblWeekTotal, lblMonthTotal, lblYearTotal;
    @FXML private Label lblWeekUsd, lblMonthUsd, lblYearUsd;
    @FXML private ComboBox<Integer> yearCombo;

    private MainController mainController;
    private int shopId;

    public void setMainController(MainController mc) { this.mainController = mc; }

    @FXML
    public void initialize() {
        int currentYear = LocalDate.now().getYear();
        for (int y = currentYear; y >= currentYear - 5; y--) yearCombo.getItems().add(y);
        yearCombo.setValue(currentYear);
    }

    public void refresh(int shopId) {
        this.shopId = shopId;
        loadAllAsync();
    }

    /** Runs ALL report queries on 2 background threads — UI stays fully responsive */
    private void loadAllAsync() {
        int year = yearCombo.getValue() != null ? yearCombo.getValue() : LocalDate.now().getYear();

        Task<ReportData> task = new Task<>() {
            @Override protected ReportData call() {
                DatabaseManager db = DatabaseManager.getInstance();
                ReportData r = new ReportData();
                r.usdRate = getUsdRate(db);
                LocalDate now = LocalDate.now();

                // ── Query 1: ONE fetch covers week + month + annual charts ──────
                // Pull last 5 years of sales; group client-side — no extra round trips
                int curYear = now.getYear();
                LocalDate fetchFrom = LocalDate.of(curYear - 4, 1, 1);
                LocalDate fetchTo   = LocalDate.of(year, 12, 31);
                // Use the wider of (fetchFrom, yearStart) so selected year is always covered
                LocalDate wideFrom = fetchFrom.isBefore(LocalDate.of(year, 1, 1)) ? fetchFrom : LocalDate.of(year, 1, 1);

                List<com.stockmanager.model.Sale> allSales = db.getSales(shopId, wideFrom, fetchTo);

                // ── Weekly: filter last 7 days, group by day ───────────────────
                LocalDate weekStart = now.minusDays(6);
                Map<LocalDate, Double> byDay = allSales.stream()
                    .filter(s -> !s.getSaleDate().isBefore(weekStart))
                    .collect(Collectors.groupingBy(
                        com.stockmanager.model.Sale::getSaleDate,
                        Collectors.summingDouble(com.stockmanager.model.Sale::getTotalAmount)));
                r.weekDays = new ArrayList<>(); r.weekValues = new ArrayList<>(); r.weekTotal = 0;
                for (int i = 6; i >= 0; i--) {
                    LocalDate d = now.minusDays(i);
                    double v = byDay.getOrDefault(d, 0.0);
                    r.weekDays.add(d.getDayOfWeek().getDisplayName(java.time.format.TextStyle.SHORT, Locale.getDefault()));
                    r.weekValues.add(v); r.weekTotal += v;
                }

                // ── Monthly: filter selected year, group by month ──────────────
                Map<Integer, Double> byMonth = allSales.stream()
                    .filter(s -> s.getSaleDate().getYear() == year)
                    .collect(Collectors.groupingBy(
                        s -> s.getSaleDate().getMonthValue(),
                        Collectors.summingDouble(com.stockmanager.model.Sale::getTotalAmount)));
                r.monthNames = new ArrayList<>(); r.monthValues = new ArrayList<>();
                r.yearTotal = 0; r.monthTotal = 0;
                int curMonth = now.getMonthValue();
                for (int m = 1; m <= 12; m++) {
                    double v = byMonth.getOrDefault(m, 0.0);
                    r.monthNames.add(java.time.Month.of(m).getDisplayName(java.time.format.TextStyle.SHORT, Locale.getDefault()));
                    r.monthValues.add(v); r.yearTotal += v;
                    if (m == curMonth && year == curYear) r.monthTotal = v;
                }

                // ── Annual: group by year ──────────────────────────────────────
                Map<Integer, Double> byYear = allSales.stream()
                    .collect(Collectors.groupingBy(
                        s -> s.getSaleDate().getYear(),
                        Collectors.summingDouble(com.stockmanager.model.Sale::getTotalAmount)));
                r.annualYears = new ArrayList<>(); r.annualValues = new ArrayList<>();
                for (int y2 = curYear - 4; y2 <= curYear; y2++) {
                    r.annualYears.add(String.valueOf(y2));
                    r.annualValues.add(byYear.getOrDefault(y2, 0.0));
                }

                // ── Query 2: top products (aggregated server-side) ─────────────
                r.topProducts = db.getTopProducts(shopId,
                    LocalDate.of(year, 1, 1), LocalDate.of(year, 12, 31), 8);

                return r;
            }
        };

        task.setOnSucceeded(e -> renderAll(task.getValue()));
        task.setOnFailed(e -> System.err.println("Reports failed: " + task.getException()));
        new Thread(task, "reports-loader").start();
    }


    private void renderAll(ReportData r) {
        // Weekly chart
        weeklyChart.getData().clear();
        XYChart.Series<String, Number> ws = new XYChart.Series<>();
        ws.setName("Daily Sales (RWF)");
        for (int i = 0; i < r.weekDays.size(); i++)
            ws.getData().add(new XYChart.Data<>(r.weekDays.get(i), r.weekValues.get(i)));
        weeklyChart.getData().add(ws);
        lblWeekTotal.setText(String.format("This Week: RWF %,.0f", r.weekTotal));
        lblWeekUsd.setText(String.format("≈ USD %.2f", r.weekTotal / r.usdRate));

        // Monthly chart
        monthlyChart.getData().clear();
        XYChart.Series<String, Number> ms = new XYChart.Series<>();
        ms.setName("Monthly Sales (RWF)");
        for (int i = 0; i < r.monthNames.size(); i++)
            ms.getData().add(new XYChart.Data<>(r.monthNames.get(i), r.monthValues.get(i)));
        monthlyChart.getData().add(ms);
        lblMonthTotal.setText(String.format("This Month: RWF %,.0f", r.monthTotal));
        lblMonthUsd.setText(String.format("≈ USD %.2f", r.monthTotal / r.usdRate));
        lblYearTotal.setText(String.format("Year %d Total: RWF %,.0f",
            yearCombo.getValue() != null ? yearCombo.getValue() : LocalDate.now().getYear(), r.yearTotal));
        lblYearUsd.setText(String.format("≈ USD %.2f", r.yearTotal / r.usdRate));

        // Annual chart
        annualChart.getData().clear();
        XYChart.Series<String, Number> as = new XYChart.Series<>();
        as.setName("Annual Sales (RWF)");
        for (int i = 0; i < r.annualYears.size(); i++)
            as.getData().add(new XYChart.Data<>(r.annualYears.get(i), r.annualValues.get(i)));
        annualChart.getData().add(as);

        // Top products chart
        topProductsChart.getData().clear();
        XYChart.Series<String, Number> ts = new XYChart.Series<>();
        ts.setName("Revenue (RWF)");
        for (Object[] row : r.topProducts) {
            String name = (String) row[0];
            String shortName = name.length() > 15 ? name.substring(0, 14) + "…" : name;
            ts.getData().add(new XYChart.Data<>(shortName, (Double) row[1]));
        }
        topProductsChart.getData().add(ts);
    }

    @FXML private void handleYearChange() { loadAllAsync(); }

    @FXML private void handleExportCsv() {
        int year = yearCombo.getValue() != null ? yearCombo.getValue() : LocalDate.now().getYear();
        List<com.stockmanager.model.Sale> sales = DatabaseManager.getInstance()
            .getSalesByShop(shopId, LocalDate.of(year, 1, 1), LocalDate.of(year, 12, 31));
        com.stockmanager.util.CsvExporter.exportSales(sales, shopId);
    }

    private double getUsdRate(DatabaseManager db) {
        String r = db.getSetting("usd_rate");
        return r != null ? Double.parseDouble(r) : 1380;
    }

    private static class ReportData {
        double usdRate, weekTotal, monthTotal, yearTotal;
        List<String> weekDays, monthNames, annualYears;
        List<Double> weekValues, monthValues, annualValues;
        List<Object[]> topProducts;
    }
}
