package com.mohasibi.pro.data

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import java.time.Instant
import java.util.UUID

/**
 * Offline-first accounting store.
 *
 * Monetary amounts are stored as integer YER (never Float/Double) to avoid
 * rounding errors. A completed sale, stock decrement and its balanced journal
 * are committed in one SQLite transaction.
 */
class MohasibiDatabase(context: Context) :
    SQLiteOpenHelper(context.applicationContext, "mohasibi_pro.db", null, 1) {

    data class SaleLine(
        val productId: Long,
        val quantity: Long,
        val unitPrice: Long,
        val discount: Long = 0L
    )

    data class SaleResult(
        val saleId: Long,
        val invoiceNo: String,
        val total: Long,
        val paid: Long,
        val due: Long
    )

    override fun onConfigure(db: SQLiteDatabase) {
        super.onConfigure(db)
        db.setForeignKeyConstraintsEnabled(true)
        db.enableWriteAheadLogging()
    }

    override fun onCreate(db: SQLiteDatabase) {
        val ddl = listOf(
            """CREATE TABLE users(id INTEGER PRIMARY KEY, username TEXT NOT NULL UNIQUE, password_hash TEXT NOT NULL, role TEXT NOT NULL CHECK(role IN ('ADMIN','CASHIER')), active INTEGER NOT NULL DEFAULT 1, created_at TEXT NOT NULL)""",
            """CREATE TABLE categories(id INTEGER PRIMARY KEY, name TEXT NOT NULL UNIQUE)""",
            """CREATE TABLE products(id INTEGER PRIMARY KEY, barcode TEXT UNIQUE, sku TEXT UNIQUE, name TEXT NOT NULL, category_id INTEGER REFERENCES categories(id), unit TEXT NOT NULL DEFAULT 'قطعة', cost_price INTEGER NOT NULL DEFAULT 0 CHECK(cost_price>=0), sale_price INTEGER NOT NULL DEFAULT 0 CHECK(sale_price>=0), wholesale_price INTEGER NOT NULL DEFAULT 0 CHECK(wholesale_price>=0), stock_qty INTEGER NOT NULL DEFAULT 0 CHECK(stock_qty>=0), min_stock INTEGER NOT NULL DEFAULT 0 CHECK(min_stock>=0), active INTEGER NOT NULL DEFAULT 1, created_at TEXT NOT NULL)""",
            """CREATE TABLE customers(id INTEGER PRIMARY KEY, name TEXT NOT NULL, phone TEXT, address TEXT, opening_balance INTEGER NOT NULL DEFAULT 0, active INTEGER NOT NULL DEFAULT 1)""",
            """CREATE TABLE suppliers(id INTEGER PRIMARY KEY, name TEXT NOT NULL, phone TEXT, address TEXT, opening_balance INTEGER NOT NULL DEFAULT 0, active INTEGER NOT NULL DEFAULT 1)""",
            """CREATE TABLE batches(id INTEGER PRIMARY KEY, product_id INTEGER NOT NULL REFERENCES products(id), batch_no TEXT, expiry_date TEXT, quantity INTEGER NOT NULL CHECK(quantity>=0))""",
            """CREATE TABLE sales(id INTEGER PRIMARY KEY, invoice_no TEXT NOT NULL UNIQUE, customer_id INTEGER REFERENCES customers(id), user_id INTEGER REFERENCES users(id), subtotal INTEGER NOT NULL, discount INTEGER NOT NULL DEFAULT 0, total INTEGER NOT NULL, paid INTEGER NOT NULL, due INTEGER NOT NULL, payment_method TEXT NOT NULL, status TEXT NOT NULL DEFAULT 'POSTED', created_at TEXT NOT NULL)""",
            """CREATE TABLE sale_items(id INTEGER PRIMARY KEY, sale_id INTEGER NOT NULL REFERENCES sales(id), product_id INTEGER NOT NULL REFERENCES products(id), quantity INTEGER NOT NULL CHECK(quantity>0), unit_price INTEGER NOT NULL, unit_cost INTEGER NOT NULL, discount INTEGER NOT NULL DEFAULT 0, line_total INTEGER NOT NULL)""",
            """CREATE TABLE purchases(id INTEGER PRIMARY KEY, invoice_no TEXT NOT NULL UNIQUE, supplier_id INTEGER REFERENCES suppliers(id), subtotal INTEGER NOT NULL, discount INTEGER NOT NULL DEFAULT 0, total INTEGER NOT NULL, paid INTEGER NOT NULL DEFAULT 0, due INTEGER NOT NULL DEFAULT 0, created_at TEXT NOT NULL)""",
            """CREATE TABLE stock_movements(id INTEGER PRIMARY KEY, product_id INTEGER NOT NULL REFERENCES products(id), movement_type TEXT NOT NULL, quantity_delta INTEGER NOT NULL, reference_type TEXT, reference_id INTEGER, note TEXT, created_at TEXT NOT NULL)""",
            """CREATE TABLE accounts(id INTEGER PRIMARY KEY, code TEXT NOT NULL UNIQUE, name TEXT NOT NULL, account_type TEXT NOT NULL CHECK(account_type IN ('ASSET','LIABILITY','EQUITY','REVENUE','EXPENSE')), parent_id INTEGER REFERENCES accounts(id), active INTEGER NOT NULL DEFAULT 1)""",
            """CREATE TABLE journal_entries(id INTEGER PRIMARY KEY, reference_type TEXT NOT NULL, reference_id INTEGER NOT NULL, memo TEXT NOT NULL, posted_at TEXT NOT NULL, UNIQUE(reference_type,reference_id))""",
            """CREATE TABLE journal_lines(id INTEGER PRIMARY KEY, entry_id INTEGER NOT NULL REFERENCES journal_entries(id) ON DELETE CASCADE, account_id INTEGER NOT NULL REFERENCES accounts(id), debit INTEGER NOT NULL DEFAULT 0 CHECK(debit>=0), credit INTEGER NOT NULL DEFAULT 0 CHECK(credit>=0), CHECK((debit=0) != (credit=0)))""",
            """CREATE TABLE expenses(id INTEGER PRIMARY KEY, title TEXT NOT NULL, category TEXT NOT NULL, amount INTEGER NOT NULL CHECK(amount>0), payment_method TEXT NOT NULL, note TEXT, created_at TEXT NOT NULL)""",
            """CREATE TABLE cash_sessions(id INTEGER PRIMARY KEY, user_id INTEGER REFERENCES users(id), opening_cash INTEGER NOT NULL, expected_cash INTEGER, counted_cash INTEGER, difference INTEGER, opened_at TEXT NOT NULL, closed_at TEXT, status TEXT NOT NULL DEFAULT 'OPEN')""",
            """CREATE TABLE cash_movements(id INTEGER PRIMARY KEY, session_id INTEGER REFERENCES cash_sessions(id), direction TEXT NOT NULL CHECK(direction IN ('IN','OUT')), amount INTEGER NOT NULL CHECK(amount>0), reason TEXT NOT NULL, reference_type TEXT, reference_id INTEGER, created_at TEXT NOT NULL)""",
            """CREATE TABLE payments(id INTEGER PRIMARY KEY, party_type TEXT NOT NULL CHECK(party_type IN ('CUSTOMER','SUPPLIER')), party_id INTEGER NOT NULL, amount INTEGER NOT NULL CHECK(amount>0), method TEXT NOT NULL, note TEXT, created_at TEXT NOT NULL)""",
            """CREATE TABLE audit_logs(id INTEGER PRIMARY KEY, user_id INTEGER, action TEXT NOT NULL, entity TEXT NOT NULL, entity_id INTEGER, details TEXT, created_at TEXT NOT NULL)""",
            """CREATE INDEX idx_products_name ON products(name)""",
            """CREATE INDEX idx_products_barcode ON products(barcode)""",
            """CREATE INDEX idx_sales_date ON sales(created_at)""",
            """CREATE INDEX idx_stock_product ON stock_movements(product_id,created_at)""",
            """CREATE INDEX idx_journal_entry ON journal_lines(entry_id)"""
        )
        db.beginTransaction()
        try {
            ddl.forEach(db::execSQL)
            seedAccounts(db)
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
    }

    private fun seedAccounts(db: SQLiteDatabase) {
        val rows = listOf(
            arrayOf("1000", "الأصول", "ASSET"),
            arrayOf("1010", "الصندوق", "ASSET"),
            arrayOf("1020", "البنك والمحافظ", "ASSET"),
            arrayOf("1100", "العملاء - الذمم المدينة", "ASSET"),
            arrayOf("1200", "المخزون", "ASSET"),
            arrayOf("2000", "الالتزامات", "LIABILITY"),
            arrayOf("2010", "الموردون - الذمم الدائنة", "LIABILITY"),
            arrayOf("3000", "حقوق الملكية", "EQUITY"),
            arrayOf("4000", "إيرادات المبيعات", "REVENUE"),
            arrayOf("5000", "تكلفة البضاعة المباعة", "EXPENSE"),
            arrayOf("5100", "المصروفات التشغيلية", "EXPENSE")
        )
        rows.forEach { row ->
            db.insertOrThrow("accounts", null, ContentValues().apply {
                put("code", row[0]); put("name", row[1]); put("account_type", row[2])
            })
        }
    }

    private fun accountId(db: SQLiteDatabase, code: String): Long =
        db.rawQuery("SELECT id FROM accounts WHERE code=?", arrayOf(code)).use { c ->
            check(c.moveToFirst()) { "Missing chart account: $code" }
            c.getLong(0)
        }

    private fun journalLine(db: SQLiteDatabase, entryId: Long, code: String, debit: Long, credit: Long) {
        if (debit == 0L && credit == 0L) return
        db.insertOrThrow("journal_lines", null, ContentValues().apply {
            put("entry_id", entryId); put("account_id", accountId(db, code))
            put("debit", debit); put("credit", credit)
        })
    }

    private fun postJournal(
        db: SQLiteDatabase, referenceType: String, referenceId: Long,
        memo: String, lines: List<Triple<String, Long, Long>>
    ) {
        require(lines.isNotEmpty()) { "Journal must contain lines" }
        val debit = lines.sumOf { it.second }
        val credit = lines.sumOf { it.third }
        require(debit == credit && debit > 0L) { "Unbalanced journal: debit=$debit credit=$credit" }
        val entryId = db.insertOrThrow("journal_entries", null, ContentValues().apply {
            put("reference_type", referenceType); put("reference_id", referenceId)
            put("memo", memo); put("posted_at", Instant.now().toString())
        })
        lines.forEach { (code, dr, cr) -> journalLine(db, entryId, code, dr, cr) }
    }

    /** Posts a cash/credit sale atomically. All values are whole YER. */
    fun postSale(
        lines: List<SaleLine>,
        paid: Long,
        paymentMethod: String,
        customerId: Long? = null,
        userId: Long? = null,
        billDiscount: Long = 0L
    ): SaleResult {
        require(lines.isNotEmpty()) { "السلة فارغة" }
        require(paid >= 0L && billDiscount >= 0L) { "قيمة غير صحيحة" }
        require(paymentMethod in setOf("CASH", "CREDIT", "WALLET", "CARD")) { "طريقة دفع غير مدعومة" }

        val db = writableDatabase
        var result: SaleResult? = null
        db.beginTransaction()
        try {
            var subtotal = 0L
            var costTotal = 0L
            val normalized = lines.map { line ->
                require(line.productId > 0 && line.quantity > 0 && line.unitPrice >= 0 && line.discount >= 0)
                val p = db.rawQuery(
                    "SELECT name,cost_price,stock_qty FROM products WHERE id=? AND active=1",
                    arrayOf(line.productId.toString())
                ).use { c ->
                    check(c.moveToFirst()) { "المنتج غير موجود أو غير نشط" }
                    Triple(c.getLong(1), c.getLong(2), c.getString(0))
                }
                check(p.second >= line.quantity) { "المخزون غير كافٍ: ${p.third}" }
                val gross = Math.multiplyExact(line.quantity, line.unitPrice)
                require(line.discount <= gross) { "خصم الصنف أكبر من قيمته" }
                val net = gross - line.discount
                subtotal = Math.addExact(subtotal, gross)
                costTotal = Math.addExact(costTotal, Math.multiplyExact(line.quantity, p.first))
                line to (net to p.first)
            }
            val totalBeforeBillDiscount = normalized.sumOf { it.second.first }
            require(billDiscount <= totalBeforeBillDiscount) { "خصم الفاتورة أكبر من الإجمالي" }
            val total = totalBeforeBillDiscount - billDiscount
            require(paid <= total) { "المبلغ المدفوع أكبر من إجمالي الفاتورة" }
            val due = total - paid
            require(due == 0L || customerId != null) { "يجب تحديد العميل للبيع الآجل" }
            require(paymentMethod != "CREDIT" || paid == 0L) { "استخدم دفعاً مختلطاً عند وجود دفعة جزئية" }

            val now = Instant.now().toString()
            val invoice = "S-${System.currentTimeMillis()}-${UUID.randomUUID().toString().take(6)}"
            val saleId = db.insertOrThrow("sales", null, ContentValues().apply {
                put("invoice_no", invoice); if (customerId == null) putNull("customer_id") else put("customer_id", customerId)
                if (userId == null) putNull("user_id") else put("user_id", userId)
                put("subtotal", subtotal); put("discount", subtotal - total); put("total", total)
                put("paid", paid); put("due", due); put("payment_method", paymentMethod)
                put("created_at", now)
            })
            normalized.forEach { (line, values) ->
                val net = values.first
                val unitCost = values.second
                db.insertOrThrow("sale_items", null, ContentValues().apply {
                    put("sale_id", saleId); put("product_id", line.productId); put("quantity", line.quantity)
                    put("unit_price", line.unitPrice); put("unit_cost", unitCost)
                    put("discount", line.discount); put("line_total", net)
                })
                db.execSQL("UPDATE products SET stock_qty=stock_qty-? WHERE id=? AND stock_qty>=?",
                    arrayOf(line.quantity, line.productId, line.quantity))
                check(db.compileStatement("SELECT changes()").simpleQueryForLong() == 1L) { "تعذر تحديث المخزون" }
                db.insertOrThrow("stock_movements", null, ContentValues().apply {
                    put("product_id", line.productId); put("movement_type", "SALE")
                    put("quantity_delta", -line.quantity); put("reference_type", "SALE")
                    put("reference_id", saleId); put("created_at", now)
                })
            }

            val cashCode = if (paymentMethod == "CASH") "1010" else "1020"
            val journal = mutableListOf<Triple<String, Long, Long>>()
            if (paid > 0L) journal += Triple(cashCode, paid, 0L)
            if (due > 0L) journal += Triple("1100", due, 0L)
            if (total > 0L) journal += Triple("4000", 0L, total)
            if (costTotal > 0L) {
                journal += Triple("5000", costTotal, 0L)
                journal += Triple("1200", 0L, costTotal)
            }
            if (total > 0L) postJournal(db, "SALE", saleId, "فاتورة بيع $invoice", journal)
            db.setTransactionSuccessful()
            result = SaleResult(saleId, invoice, total, paid, due)
        } finally {
            db.endTransaction()
        }
        return checkNotNull(result)
    }

    fun findProducts(query: String, limit: Int = 50): List<Map<String, Any?>> {
        val safeLimit = limit.coerceIn(1, 200)
        val pattern = "%${query.trim()}%"
        return readableDatabase.rawQuery(
            """SELECT id,name,barcode,sku,sale_price,cost_price,stock_qty,min_stock,unit
               FROM products WHERE active=1 AND (name LIKE ? OR barcode LIKE ? OR sku LIKE ?)
               ORDER BY name LIMIT $safeLimit""",
            arrayOf(pattern, pattern, pattern)
        ).use { c ->
            buildList {
                while (c.moveToNext()) add(mapOf(
                    "id" to c.getLong(0), "name" to c.getString(1),
                    "barcode" to c.getString(2), "sku" to c.getString(3),
                    "salePrice" to c.getLong(4), "costPrice" to c.getLong(5),
                    "stock" to c.getLong(6), "minStock" to c.getLong(7),
                    "unit" to c.getString(8)
                ))
            }
        }
    }

    fun addProduct(
        name: String, barcode: String?, sku: String?, cost: Long,
        sale: Long, wholesale: Long, openingStock: Long, minStock: Long, unit: String
    ): Long {
        require(name.isNotBlank() && cost >= 0 && sale >= 0 && wholesale >= 0)
        require(openingStock >= 0 && minStock >= 0 && unit.isNotBlank())
        val db = writableDatabase
        db.beginTransaction()
        try {
            val id = db.insertOrThrow("products", null, ContentValues().apply {
                put("name", name.trim()); if (barcode.isNullOrBlank()) putNull("barcode") else put("barcode", barcode.trim())
                if (sku.isNullOrBlank()) putNull("sku") else put("sku", sku.trim())
                put("cost_price", cost); put("sale_price", sale); put("wholesale_price", wholesale)
                put("stock_qty", openingStock); put("min_stock", minStock); put("unit", unit)
                put("created_at", Instant.now().toString())
            })
            if (openingStock > 0) db.insertOrThrow("stock_movements", null, ContentValues().apply {
                put("product_id", id); put("movement_type", "OPENING")
                put("quantity_delta", openingStock); put("note", "رصيد افتتاحي")
                put("created_at", Instant.now().toString())
            })
            db.setTransactionSuccessful()
            return id
        } finally { db.endTransaction() }
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        // Add explicit, forward-only migrations here. Never drop user accounting data.
    }
}
