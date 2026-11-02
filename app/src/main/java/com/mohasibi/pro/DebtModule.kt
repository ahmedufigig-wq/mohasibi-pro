package com.mohasibi.pro

import android.content.ContentValues
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import java.text.DecimalFormat

private data class Debtor(val id: Long, val name: String, val phone: String, val total: Double, val paid: Double) {
    val remaining: Double get() = total - paid
}
private class DebtDb(context: android.content.Context) : SQLiteOpenHelper(context, "mohasibi.db", null, 1) {
    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL("CREATE TABLE customers(id INTEGER PRIMARY KEY AUTOINCREMENT, name TEXT NOT NULL, phone TEXT NOT NULL DEFAULT '', total REAL NOT NULL DEFAULT 0, paid REAL NOT NULL DEFAULT 0)")
        db.execSQL("CREATE TABLE payments(id INTEGER PRIMARY KEY AUTOINCREMENT, customer_id INTEGER NOT NULL, amount REAL NOT NULL, date TEXT NOT NULL DEFAULT CURRENT_TIMESTAMP, FOREIGN KEY(customer_id) REFERENCES customers(id))")
    }
    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {}
    fun all(): List<Debtor> {
        val result = mutableListOf<Debtor>()
        readableDatabase.rawQuery("SELECT id,name,phone,total,paid FROM customers ORDER BY name", null).use { c ->
            while (c.moveToNext()) result.add(Debtor(c.getLong(0),c.getString(1),c.getString(2),c.getDouble(3),c.getDouble(4)))
        }
        return result
    }
    fun add(name: String, phone: String, debt: Double) {
        val v = ContentValues().apply { put("name",name); put("phone",phone); put("total",debt); put("paid",0.0) }
        writableDatabase.insertOrThrow("customers",null,v)
    }
    fun pay(id: Long, amount: Double) {
        writableDatabase.beginTransaction()
        try {
            val p = ContentValues().apply { put("customer_id",id); put("amount",amount) }
            writableDatabase.insertOrThrow("payments",null,p)
            writableDatabase.execSQL("UPDATE customers SET paid = paid + ? WHERE id = ?", arrayOf(amount,id))
            writableDatabase.setTransactionSuccessful()
        } finally { writableDatabase.endTransaction() }
    }
}

@Composable
fun DebtModule() {
    val context = LocalContext.current
    val db = remember { DebtDb(context.applicationContext) }
    var people by remember { mutableStateOf(db.all()) }
    var addOpen by remember { mutableStateOf(false) }
    var selected by remember { mutableStateOf<Debtor?>(null) }
    var payOpen by remember { mutableStateOf(false) }
    var name by remember { mutableStateOf("") }
    var phone by remember { mutableStateOf("") }
    var debt by remember { mutableStateOf("") }
    var payment by remember { mutableStateOf("") }
    val fmt = remember { DecimalFormat("#,##0.##") }
    fun refresh() { people = db.all() }
    Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text("إدارة العملاء والديون", style = MaterialTheme.typography.titleLarge)
        Button(onClick = { name=""; phone=""; debt=""; addOpen=true }, modifier = Modifier.fillMaxWidth()) { Text("＋ إضافة زبون") }
        Text("عدد الزبائن: ${people.size}   •   المتبقي: ${fmt.format(people.sumOf { it.remaining })} ر.ي")
        if (people.isEmpty()) Text("لا يوجد زبائن بعد. اضغط «إضافة زبون» لبدء تسجيل الديون.")
        LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(people, key = { it.id }) { p ->
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                        Text(p.name, style = MaterialTheme.typography.titleMedium)
                        if (p.phone.isNotBlank()) Text("الهاتف: ${p.phone}")
                        Text("إجمالي الدين: ${fmt.format(p.total)} ر.ي")
                        Text("المدفوع: ${fmt.format(p.paid)} ر.ي", color = MaterialTheme.colorScheme.primary)
                        Text("المتبقي: ${fmt.format(p.remaining)} ر.ي", color = if (p.remaining > 0) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary)
                        Button(onClick = { selected=p; payment=""; payOpen=true }, enabled = p.remaining > 0) { Text("تسجيل دفعة") }
                    }
                }
            }
        }
    }
    if (addOpen) AlertDialog(onDismissRequest={addOpen=false}, title={Text("إضافة زبون")}, text={
        Column(verticalArrangement=Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(name,{name=it},label={Text("اسم الزبون")},singleLine=true)
            OutlinedTextField(phone,{phone=it},label={Text("رقم الهاتف")},singleLine=true)
            OutlinedTextField(debt,{debt=it},label={Text("الدين الافتتاحي بالريال")},singleLine=true)
        }
    }, confirmButton={ TextButton(onClick={
        val amount=debt.toDoubleOrNull()
        if(name.isNotBlank() && (debt.isBlank() || (amount != null && amount >= 0))) {
            db.add(name.trim(),phone.trim(),amount ?: 0.0); refresh(); addOpen=false
        }
    }) { Text("حفظ") } }, dismissButton={TextButton(onClick={addOpen=false}){Text("إلغاء")}})
    if(payOpen) AlertDialog(onDismissRequest={payOpen=false},title={Text("تسجيل دفعة — ${selected?.name.orEmpty()}")},text={
        OutlinedTextField(payment,{payment=it},label={Text("مبلغ الدفعة بالريال")},singleLine=true)
    },confirmButton={TextButton(onClick={
        val amount=payment.toDoubleOrNull()
        val current=selected
        if(current!=null && amount!=null && amount>0 && amount<=current.remaining) {
            db.pay(current.id,amount); refresh(); payOpen=false
        }
    }){Text("حفظ الدفعة")}},dismissButton={TextButton(onClick={payOpen=false}){Text("إلغاء")}})
}
