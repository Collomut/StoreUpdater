package com.stockmanager.controller;

import com.stockmanager.db.DatabaseManager;
import com.stockmanager.model.Expense;
import com.stockmanager.util.Session;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.concurrent.Task;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.layout.HBox;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public class ExpensesController {

    @FXML private DatePicker dpFrom, dpTo, dpExpenseDate;
    @FXML private TextField fldAmount;
    @FXML private ComboBox<String> cmbCategory;
    @FXML private ComboBox<String> cmbPaymentMethod;
    @FXML private TextArea txtNotes;
    @FXML private Label lblStatus;
    @FXML private Label lblTotalExpenses, lblCashExpenses, lblPhoneExpenses;

    @FXML private TableView<Expense> expenseTable;
    @FXML private TableColumn<Expense, String> colDate, colCategory, colNotes, colPayment, colAmount, colUser, colActions;

    private MainController mainController;
    private DatabaseManager db = DatabaseManager.getInstance();
    private int shopId;

    public void setMainController(MainController mc) {
        this.mainController = mc;
    }

    @FXML
    public void initialize() {
        // Date Pickers default to Today
        LocalDate today = LocalDate.now();
        dpExpenseDate.setValue(today);
        dpFrom.setValue(today.withDayOfMonth(1));
        dpTo.setValue(today);

        // Categories
        cmbCategory.setItems(FXCollections.observableArrayList(
            "Transport", "Meals", "Supplies", "Utilities", "Maintenance", "Rent", "Other"
        ));
        cmbCategory.setValue("Transport");

        // Payment Methods
        cmbPaymentMethod.setItems(FXCollections.observableArrayList("Cash", "Phone"));
        cmbPaymentMethod.setValue("Cash");

        // Table Columns
        colDate.setCellValueFactory(d -> new SimpleStringProperty(d.getValue().getExpenseDate().toString()));
        colCategory.setCellValueFactory(d -> new SimpleStringProperty(d.getValue().getCategory()));
        colNotes.setCellValueFactory(d -> new SimpleStringProperty(d.getValue().getNotes()));
        colPayment.setCellValueFactory(d -> new SimpleStringProperty("PHONE".equals(d.getValue().getPaymentMethod()) ? "Phone" : "Cash"));
        colAmount.setCellValueFactory(d -> new SimpleStringProperty(String.format("RWF %,.0f", d.getValue().getAmount())));
        colUser.setCellValueFactory(d -> new SimpleStringProperty(d.getValue().getUsername()));

        colActions.setCellFactory(col -> new TableCell<>() {
            private final Button btnDelete = new Button("Delete");
            {
                btnDelete.setStyle("-fx-background-color:#FFF0F0;-fx-text-fill:#CC0000;-fx-background-radius:4;-fx-border-color:#CC0000;-fx-border-radius:4;-fx-border-width:1;-fx-cursor:hand;");
                btnDelete.setOnAction(e -> {
                    Expense exp = getTableView().getItems().get(getIndex());
                    Alert confirm = new Alert(Alert.AlertType.CONFIRMATION,
                        "Delete expense of RWF " + String.format("%,.0f", exp.getAmount()) + " (" + exp.getCategory() + ")?",
                        ButtonType.YES, ButtonType.NO);
                    confirm.setHeaderText(null);
                    confirm.showAndWait().ifPresent(bt -> {
                        if (bt == ButtonType.YES) {
                            try {
                                if (db.deleteExpense(exp.getId())) {
                                    showStatus("Expense deleted.");
                                    loadExpensesAsync();
                                }
                            } catch (Exception ex) {
                                showStatus(ex.getMessage());
                            }
                        }
                    });
                });
            }
            @Override protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                setGraphic(empty ? null : btnDelete);
            }
        });
    }

    public void refresh(int shopId, String shopName) {
        this.shopId = shopId;
        loadExpensesAsync();
    }

    @FXML
    private void handleFilter() {
        loadExpensesAsync();
    }

    @FXML
    private void handleRefresh() {
        loadExpensesAsync();
    }

    private void loadExpensesAsync() {
        LocalDate from = dpFrom.getValue();
        LocalDate to   = dpTo.getValue();

        Task<List<Expense>> task = new Task<>() {
            @Override protected List<Expense> call() {
                return db.getExpenses(shopId, from, to);
            }
        };

        task.setOnSucceeded(e -> {
            List<Expense> list = task.getValue();
            expenseTable.getItems().setAll(list);

            BigDecimal total = BigDecimal.ZERO;
            BigDecimal cash  = BigDecimal.ZERO;
            BigDecimal phone = BigDecimal.ZERO;

            for (Expense exp : list) {
                total = total.add(exp.getAmount());
                if ("PHONE".equals(exp.getPaymentMethod())) {
                    phone = phone.add(exp.getAmount());
                } else {
                    cash = cash.add(exp.getAmount());
                }
            }

            lblTotalExpenses.setText(String.format("RWF %,.0f", total));
            lblCashExpenses.setText(String.format("Cash: RWF %,.0f", cash));
            lblPhoneExpenses.setText(String.format("Phone: RWF %,.0f", phone));
        });

        task.setOnFailed(e -> showStatus("Error loading expenses: " + task.getException().getMessage()));

        new Thread(task, "expenses-loader").start();
    }

    @FXML
    private void handleRecordExpense() {
        String amtStr  = fldAmount.getText().trim();
        String cat     = cmbCategory.getValue();
        String method  = "Phone".equals(cmbPaymentMethod.getValue()) ? "PHONE" : "CASH";
        String notes   = txtNotes.getText().trim();
        LocalDate date = dpExpenseDate.getValue();

        if (amtStr.isEmpty()) {
            showStatus("Please enter an amount."); return;
        }

        BigDecimal amount;
        try {
            amount = new BigDecimal(amtStr.replaceAll(",", ""));
            if (amount.compareTo(BigDecimal.ZERO) <= 0) {
                showStatus("Amount must be greater than 0."); return;
            }
        } catch (NumberFormatException ex) {
            showStatus("Invalid amount entered."); return;
        }

        try {
            if (db.addExpense(shopId, date, amount, cat, method, notes)) {
                showStatus("Expense recorded: RWF " + String.format("%,.0f", amount) + " (" + cat + ")");
                fldAmount.clear();
                txtNotes.clear();
                loadExpensesAsync();
            } else {
                showStatus("Failed to record expense.");
            }
        } catch (Exception ex) {
            showStatus(ex.getMessage());
        }
    }

    private void showStatus(String msg) {
        lblStatus.setText(msg);
    }
}
