package com.mohasibi.pro

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper

data class Product(val id: Long, val name: String, val barcode: String, val cost: Double, val price: Double, val quantity: Double, val minQuantity: Double)

class CoreDb(context: Context) : SQLiteOpenHelper(context, "mohasibi_core.db", null, 1) {
    override fun onConfigure(db: SQLiteDatabase) {
        db.setForeignKeyConstraintsEnabled(true)
    }
    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL("CREATE TABLE products(id INTEGER PRIMARY KEY AUTOINCREMENT, name TEXT NOT NULL, barcode TEXT NOT NULL DEFAULT '', cost REAL NOT NULL CHECK(cost>=0), price REAL NOT NULL CHECK(price>=0), quantity REAL NOT NULL DEFAULT 0 CHECK(quantity>=0), min_quantity REAL NOT NULL DEFAULT 0 CHECK(min_quantity>=0), created_at TEXT NOT NULL DEFAULT CURRENT_TIMESTAMP)")
        db.execSQL("CREATE UNIQUE INDEX idx_products_barcode ON products(barcode) WHERE barcode <> ''")
        db.execSQL("CREATE TABLE customers(id INTEGER PRIMARY KEY AUTOINCREMENT, name TEXT NOT NULL, phone TEXT NOT NULL DEFAULT '', opening_balance REAL NOT NULL DEFAULT 0)")
        db.execSQL("CREATE TABLE suppliers(id INTEGER PRIMARY KEY AUTOINCREMENT, name TEXT NOT NULL, phone TEXT NOT NULL DEFAULT '', opening_balance REAL NOT NULL DEFAULT 0)")
        db.execSQL("CREATE TABLE sales(id INTEGER PRIMARY KEY AUTOINCREMENT, invoice_no TEXT NOT NULL UNIQUE, customer_id INTEGER, subtotal REAL NOT NULL, discount REAL NOT NULL DEFAULT 0, total REAL NOT NULL, paid REAL NOT NULL DEFAULT 0, created_at TEXT NOT NULL DEFAULT CURRENT_TIMESTAMP, FOREIGN KEY(customer_id) REFERENCES customers(id))")
        db.execSQL("CREATE TABLE sale_items(id INTEGER PRIMARY KEY AUTOINCREMENT, sale_id INTEGER NOT NULL, product_id INTEGER NOT NULL, quantity REAL NOT NULL CHECK(quantity>0), unit_price REAL NOT NULL CHECK(unit_price>=0), cost_snapshot REAL NOT NULL, line_total REAL NOT NULL, FOREIGN KEY(sale_id) REFERENCES sales(id) ON DELETE CASCADE, FOREIGN KEY(product_id) REFERENCES products(id))")
        db.execSQL("CREATE TABLE purchases(id INTEGER PRIMARY KEY AUTOINCREMENT, invoice_no TEXT NOT NULL UNIQUE, supplier_id INTEGER, total REAL NOT NULL, paid REAL NOT NULL DEFAULT 0, created_at TEXT NOT NULL DEFAULT CURRENT_TIMESTAMP, FOREIGN KEY(supplier_id) REFERENCES suppliers(id))")
        db.execSQL("CREATE TABLE purchase_items(id INTEGER PRIMARY KEY AUTOINCREMENT, purchase_id INTEGER NOT NULL, product_id INTEGER NOT NULL, quantity REAL NOT NULL CHECK(quantity>0), unit_cost REAL NOT NULL CHECK(unit_cost>=0), line_total REAL NOT NULL, FOREIGN KEY(purchase_id) REFERENCES purchases(id) ON DELETE CASCADE, FOREIGN KEY(product_id) REFERENCES products(id))")
        db.execSQL("CREATE TABLE expenses(id INTEGER PRIMARY KEY AUTOINCREMENT, title TEXT NOT NULL, amount REAL NOT NULL CHECK(amount>0), category TEXT NOT NULL DEFAULT 'عام', note TEXT NOT NULL DEFAULT '', created_at TEXT NOT NULL DEFAULT CURRENT_TIMESTAMP)")
        db.execSQL("CREATE TABLE cash_transactions(id INTEGER PRIMARY KEY AUTOINCREMENT, kind TEXT NOT NULL CHECK(kind IN ('IN','OUT')), source TEXT NOT NULL, reference_id INTEGER, amount REAL NOT NULL CHECK(amount>0), note TEXT NOT NULL DEFAULT '', created_at TEXT NOT NULL DEFAULT CURRENT_TIMESTAMP)")
        db.execSQL("CREATE TABLE journal_entries(id INTEGER PRIMARY KEY AUTOINCREMENT, entry_no TEXT NOT NULL UNIQUE, description TEXT NOT NULL, created_at TEXT NOT NULL DEFAULT CURRENT_TIMESTAMP)")
        db.execSQL("CREATE TABLE journal_lines(id INTEGER PRIMARY KEY AUTOINCREMENT, entry_id INTEGER NOT NULL, account_code TEXT NOT NULL, account_name TEXT NOT NULL, debit REAL NOT NULL DEFAULT 0, credit REAL NOT NULL DEFAULT 0, FOREIGN KEY(entry_id) REFERENCES journal_entries(id) ON DELETE CASCADE, CHECK(debit>=0 AND credit>=0 AND NOT(debit>0 AND credit>0)))")
    }
    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {}
    fun products(): List<Product> {
        val out = mutableListOf<Product>()
        readableDatabase.rawQuery("SELECT id,name,barcode,cost,price,quantity,min_quantity FROM products ORDER BY name", null).use { c ->
            while(c.moveToNext()) out.add(Product(c.getLong(0),c.getString(1),c.getString(2),c.getDouble(3),c.getDouble(4),c.getDouble(5),c.getDouble(6)))
        }
        return out
    }
    fun addProduct(name: String, barcode: String, cost: Double, price: Double, quantity: Double, minQuantity: Double) {
        val v = ContentValues().apply { put("name",name); put("barcode",barcode); put("cost",cost); put("price",price); put("quantity",quantity); put("min_quantity",minQuantity) }
        writableDatabase.insertOrThrow("products",null,v)
    }

    fun checkout(lines: List<Pair<Product, Double>>, paid: Double) {
        require(lines.isNotEmpty()) { "أضف صنفاً واحداً على الأقل" }
        val total = lines.sumOf { it.first.price * it.second }
        val costTotal = lines.sumOf { it.first.cost * it.second }
        require(paid >= 0 && paid <= total) { "المبلغ المدفوع غير صحيح" }
        val db = writableDatabase
        db.beginTransaction()
        try {
            lines.forEach { (product, qty) ->
                val stock = db.rawQuery("SELECT quantity FROM products WHERE id=?", arrayOf(product.id.toString())).use { cur ->
                    if (cur.moveToFirst()) cur.getDouble(0) else throw IllegalArgumentException("منتج غير موجود")
                }
                require(qty > 0 && stock >= qty) { "المخزون غير كافٍ للمنتج: ${product.name}" }
            }
            val invoice = "S-" + System.currentTimeMillis()
            val sale = ContentValues().apply { put("invoice_no",invoice); put("subtotal",total); put("discount",0.0); put("total",total); put("paid",paid) }
            val saleId = db.insertOrThrow("sales",null,sale)
            lines.forEach { (product, qty) ->
                val lineTotal = product.price * qty
                val item = ContentValues().apply {
                    put("sale_id",saleId); put("product_id",product.id); put("quantity",qty)
                    put("unit_price",product.price); put("cost_snapshot",product.cost); put("line_total",lineTotal)
                }
                db.insertOrThrow("sale_items",null,item)
                db.execSQL("UPDATE products SET quantity=quantity-? WHERE id=?", arrayOf(qty,product.id))
            }
            if (paid > 0) {
                val cash = ContentValues().apply { put("kind","IN"); put("source","SALE"); put("reference_id",saleId); put("amount",paid); put("note",invoice) }
                db.insertOrThrow("cash_transactions",null,cash)
            }
            val entryId = db.insertOrThrow("journal_entries",null,ContentValues().apply { put("entry_no","JE-" + invoice); put("description","إثبات مبيعات $invoice") })
            fun journal(account: String, title: String, debit: Double, credit: Double) {
                val line = ContentValues().apply { put("entry_id",entryId); put("account_code",account); put("account_name",title); put("debit",debit); put("credit",credit) }
                db.insertOrThrow("journal_lines",null,line)
            }
            if (paid > 0) journal("101","الصندوق",paid,0.0)
            if (total-paid > 0) journal("103","ذمم العملاء",total-paid,0.0)
            journal("401","إيرادات المبيعات",0.0,total)
            if (costTotal > 0) {
                journal("501","تكلفة البضاعة المباعة",costTotal,0.0)
                journal("1030","المخزون",0.0,costTotal)
            }
            db.setTransactionSuccessful()
        } finally { db.endTransaction() }
    }

    fun adjustStock(productId: Long, delta: Double) {
        val db = writableDatabase
        db.beginTransaction()
        try {
            val current = db.rawQuery("SELECT quantity FROM products WHERE id=?", arrayOf(productId.toString())).use { c -> if(c.moveToFirst()) c.getDouble(0) else throw IllegalArgumentException("المنتج غير موجود") }
            require(current + delta >= 0) { "الكمية غير كافية" }
            db.execSQL("UPDATE products SET quantity=quantity+? WHERE id=?", arrayOf(delta,productId))
            db.setTransactionSuccessful()
        } finally { db.endTransaction() }
    }
}
