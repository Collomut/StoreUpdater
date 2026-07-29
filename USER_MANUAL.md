# 📘 Stock Manager — User Manual

**Version 1.0.1** | Last updated: 2026-07-29

---

## Table of Contents

1. [Getting Started](#1-getting-started)
2. [Logging In](#2-logging-in)
3. [Navigating the App](#3-navigating-the-app)
4. [Inventory Management](#4-inventory-management)
5. [Recording a Sale](#5-recording-a-sale)
6. [Sales History](#6-sales-history)
7. [Reports](#7-reports)
8. [Settings](#8-settings)
9. [Nyabugogo Shop — Special Guide](#9-nyabugogo-shop--special-guide)
10. [Downtown Shop — Bargain Pricing](#10-downtown-shop--bargain-pricing)
11. [Auto-Updates](#11-auto-updates)
12. [Troubleshooting](#12-troubleshooting)

---

## 1. Getting Started

### Launching the App

1. Double-click **`StockManager.exe`** in the StockManager folder
2. The login screen will appear
3. If an update is available, a prompt will appear after login — see [Section 11](#11-auto-updates)

### First-Time Setup

If this is a brand new installation, contact your administrator for your login credentials before proceeding.

---

## 2. Logging In

![Login Screen]

1. Enter your **Username** and **Password**
2. Click **Login**
3. If your credentials are correct, you will be taken to the main dashboard
4. If login fails, double-check spelling and ensure CAPS LOCK is off

> **Note:** Each staff member has their own login. Do not share passwords.

---

## 3. Navigating the App

The app has a **top navigation bar** with tabs for each section:

| Tab | Purpose |
|---|---|
| **Inventory** | View and manage all products for the selected shop |
| **Record Sale** | Add items to a basket and confirm a sale |
| **History** | View past sales transactions |
| **Reports** | View sales summaries by date range |
| **Settings** | Configure exchange rates and other options |

At the top-left you will see a **Shop Selector** dropdown. Always make sure the correct shop is selected before doing anything.

---

## 4. Inventory Management

### Viewing Inventory

- Click the **Inventory** tab
- All products for the selected shop are displayed in a table
- Columns: **Name**, **Color/Variant**, **Size/Spec**, **Quantity**, **Price**, **Status**

**Status indicators:**
| Status | Meaning |
|---|---|
| ✅ Active | Product is in stock and available for sale |
| ⚠️ Low Stock | Quantity is at or below the reorder level |
| 🔴 Out of Stock | Quantity is zero |
| ❌ Retired | Product is no longer sold (hidden from sales) |

---

### Adding a New Product

1. Click **Add Product**
2. Fill in the product details:
   - For **Nyabugogo shop**: use the cascading form (Category → Type → Color → Size) — see [Section 9](#9-nyabugogo-shop--special-guide)
   - For **other shops**: fill in Name, Category, Unit, Quantity, Price
3. Click **Save**

> **Tip:** The Quantity field is the current stock count. The price is the selling price in RWF.

---

### Editing a Product

1. Click on the product row in the inventory table to select it
2. Click **Edit Product**
3. Modify any field you need to update (e.g. correct a quantity, update the price)
4. Click **Save**

> **Restock:** To restock a product, simply edit it and increase the Quantity.

> **Auto-unretire:** If a product is Retired and you edit it to set Quantity > 0, it will automatically become Active again.

---

### Retiring a Product

Use this when a product is no longer sold but you want to keep its sales history.

1. Select the product in the table
2. Click **Retire Product**
3. Confirm the action
4. The product will be hidden from the sales screen but preserved in history

> **Note:** Retiring sets the quantity to 0 and marks the product as Retired. It does NOT delete the product or its sales records.

---

### Searching Inventory

Use the **search bar** at the top of the inventory table to filter products by name, colour, or type.

For **Nyabugogo shop**, use the **Size/Type filter** dropdown to filter by specific bead sizes or product types.

---

## 5. Recording a Sale

### Step-by-Step

1. Click the **Record Sale** tab
2. Make sure the correct **shop** is selected at the top
3. Use the **search bar** to find a product, or scroll the dropdown list
4. Select the product from the **Product** dropdown
   - The dropdown shows: `Product Name — Colour (Size)  |  RWF price  (stock: X)`
5. Enter the **Quantity** to sell
6. Click **Add to Basket**
7. Repeat steps 3–6 for additional items
8. Review the **basket** and the **Total** at the bottom
9. Click **Confirm Sale**

### Removing an Item from the Basket

- Click on the item in the basket table
- Click **Remove Selected**

### Clearing the Entire Basket

- Click **Clear Basket** to remove all items and start over

### Sale Date

- The date defaults to today
- You can change it using the **Date Picker** (for recording a past sale)

### Notes

- Use the **Notes** field to add any relevant information about the sale (optional)

---

## 6. Sales History

1. Click the **History** tab
2. A list of all past sales for the selected shop is shown
3. Each row shows: Date, Receipt Number, Items, Total Amount
4. Click on a sale to expand and view the individual items sold

### Filtering History

Use the **date filter** fields to view sales within a specific date range.

---

## 7. Reports

1. Click the **Reports** tab
2. Select a **date range** (start date and end date)
3. The report displays:
   - Total sales amount for the period
   - Number of transactions
   - Breakdown by product (top-selling items)
   - Daily summary

> **Tip:** Set both dates to today to see today's sales summary.

---

## 8. Settings

The Settings tab is typically for administrators. It includes:

| Setting | Description |
|---|---|
| **USD Exchange Rate** | Set the RWF/USD rate used for USD display in sales |
| *(future settings)* | More options may be added in future versions |

---

## 9. Nyabugogo Shop — Special Guide

The Nyabugogo shop has a **structured product catalogue** with a cascading form to ensure products are correctly categorized.

### Adding a Product (Nyabugogo)

When you click **Add Product** in Nyabugogo's inventory, the form works in steps:

**Step 1 — Select Category:**

| Category | Products it includes |
|---|---|
| Beads | Regular Beads, Bag Beads, Wood Beads, Rosary Beads, Diamond Beads, Alphabet Beads |
| Strings | Strong & Stretchy, Crystal Tec, Fishing Line, Fishing Twine |
| Rosary Bracelets | Rosary Bracelet (colour variants) |
| Hats | Hat (Original, Local) |
| Crosses | Cross (Brown, Metal) |

**Step 2 — Select Type:**
The Type dropdown changes based on the category selected.

**Step 3 — Select Color / Variant (if applicable):**
- A preset list appears for the selected type
- You can also **type a custom colour** not in the list

**Step 4 — Select Size (if applicable):**
- Shown only for products that have sizes
- For Fishing Twine, sizes change depending on the colour selected

**Step 5 — Enter Quantity and Price**

---

### Bead Size Reference

| Type | Sizes Available |
|---|---|
| Regular Beads | 1, 2, 3, 6, 8, 10, 18, 20 |
| Crystal Tec | 0.6mm, 0.7mm, 1.0mm |
| Fishing Line | 0.3mm, 0.4mm, 0.6mm, 0.7mm |
| Fishing Twine (White) | 2mm, 3mm |
| Fishing Twine (Brown) | 24mm, 36mm |

### Rosary Bead Colour Reference

| Type | Colours |
|---|---|
| Rosary Beads (With Cross) | Blue, Brown, White, White with Black Cross, White with Silver Cross, Multicolor |
| Rosary Beads (Without Cross) | Blue, Green, White, Brown, Red |

---

## 10. Downtown Shop — Bargain Pricing

The Downtown shop allows staff to **negotiate the sale price** per transaction.

When recording a sale at the Downtown shop:

1. Select a product — the **Guide Price** (RWF) is shown as a reference
2. An extra **Sale Price** field appears
3. Enter the **agreed bargain price** in the Sale Price field
4. Add to basket — the basket records the negotiated price, not the guide price

> **Guide price** is the default selling price set in inventory.  
> **Sale price** is what the customer actually pays.

---

## 11. Auto-Updates

The app automatically checks for updates every time it is launched.

### What happens when an update is available:

1. After login, a dialog appears:
   > **"Stock Manager v1.0.X is available"**  
   > *"You are running v1.0.X. Update now? The app will restart automatically."*

2. Click **Update Now** — the download begins with a progress bar
3. The app closes, replaces itself, and reopens with the new version
4. Or click **Skip** to continue using the current version (you'll be asked again next launch)

### What if there is no internet?

The app will open normally. The update check silently times out after 6 seconds and the app works as usual. No data is lost.

### What does an update replace?

Only the **application file** is replaced. Your database, sales history, and inventory are stored on the cloud and are **never affected** by updates.

---

## 12. Troubleshooting

### App won't open

- Make sure you are opening `StockManager.exe` from inside the `StockManager` folder — do not move the EXE out of the folder
- Do not delete any files from the `StockManager` folder

### Login fails

- Check that you are typing the correct username and password
- Check that CAPS LOCK is off
- Contact your administrator to reset your password

### "Update failed" message

- Check your internet connection
- Try launching the app again — the update will retry

### Product not showing in sales dropdown

- Check that the product is not **Retired** (check in Inventory)
- Make sure the correct **shop** is selected

### Sale price shows wrong amount

- For Downtown shop: check the **Sale Price** field — it overrides the default price
- For other shops: the default selling price from inventory is used

### Stock count is wrong after a sale

- The app deducts stock automatically when a sale is confirmed
- If the count seems incorrect, check the **Sales History** to verify recent transactions

### App is slow to start

- The app connects to the cloud database on startup — a brief delay (2–5 seconds) is normal
- If it takes more than 30 seconds, check your internet connection

---

*For technical support or to report an issue, contact your system administrator.*

---

**Stock Manager** | Version 1.0.1 | Built for Windows
