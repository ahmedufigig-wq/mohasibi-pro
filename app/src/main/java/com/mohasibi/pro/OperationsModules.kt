package com.mohasibi.pro

import android.content.ContentValues
import android.database.sqlite.SQLiteDatabase
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import java.text.DecimalFormat

private val moneyFmt = DecimalFormat("#,##0.##")
private data class SupplierRow(val id: Long, val name: String, val phone: String, val balance: Double)
private data class CashRow(val kind: String, val source: String, val amount: Double, val note: String, val date: String)

private fun CoreDb.suppliers(): List<SupplierRow> = buildList {
    readableDatabase.rawQuery("SELECT id,name,phone,opening_balance FROM suppliers ORDER BY name", null).use { c ->
        while (c.moveToNext()) add(SupplierRow(c.getLong(0),c.getString(1),c.getString(2),c.getDouble(3)))
    }
}
private fun CoreDb.addSupplier(name: String, phone: String, balance: Double) {
    require(name.isNotBlank() && balance >= 0)
    writableDatabase.insertOrThrow("suppliers", null, ContentValues().apply {
        put("name",name.trim()); put("phone",phone.trim()); put("opening_balance",balance)
    })
}
private fun CoreDb.postPurchase(productId: Long, supplierId: Long?, qty: Double, unitCost: Double, paid: Double) {
    require(qty > 0 && unitCost >= 0)
    val total = qty * unitCost
    require(paid >= 0 && paid <= total)
    val db = writableDatabase
    db.beginTransaction()
    try {
        val p = db.rawQuery("SELECT name FROM products WHERE id=?", arrayOf(productId.toString())).use { c ->
            check(c.moveToFirst()) { "المنتج غير موجود" }; c.getString(0)
        }
        val invoice = "P-${System.currentTimeMillis()}"
        val id = db.insertOrThrow("purchases",null,ContentValues().apply {
            put("invoice_no",invoice); if(supplierId==null) putNull("supplier_id") else put("supplier_id",supplierId)
            put("total",total); put("paid",paid)
        })
        db.insertOrThrow("purchase_items",null,ContentValues().apply {
            put("purchase_id",id); put("product_id",productId); put("quantity",qty)
            put("unit_cost",unitCost); put("line_total",total)
        })
        db.execSQL("UPDATE products SET quantity=quantity+?, cost=? WHERE id=?", arrayOf(qty,unitCost,productId))
        if (paid > 0) db.insertOrThrow("cash_transactions",null,ContentValues().apply {
            put("kind","OUT"); put("source","PURCHASE"); put("reference_id",id); put("amount",paid); put("note",invoice)
        })
        val entry = db.insertOrThrow("journal_entries",null,ContentValues().apply {
            put("entry_no","JE-$invoice"); put("description","فاتورة مشتريات $invoice — $p")
        })
        fun line(code:String,title:String,dr:Double,cr:Double) {
            db.insertOrThrow("journal_lines",null,ContentValues().apply {
                put("entry_id",entry); put("account_code",code); put("account_name",title); put("debit",dr); put("credit",cr)
            })
        }
        line("1030","المخزون",total,0.0)
        if(paid>0) line("101","الصندوق",0.0,paid)
        if(total-paid>0) line("201","ذمم الموردين",0.0,total-paid)
        db.setTransactionSuccessful()
    } finally { db.endTransaction() }
}
private fun CoreDb.addExpense(title:String, category:String, amount:Double, note:String) {
    require(title.isNotBlank() && amount>0)
    val db=writableDatabase
    db.beginTransaction()
    try {
        val id=db.insertOrThrow("expenses",null,ContentValues().apply {
            put("title",title.trim()); put("category",category.ifBlank{"عام"}); put("amount",amount); put("note",note)
        })
        db.insertOrThrow("cash_transactions",null,ContentValues().apply {
            put("kind","OUT"); put("source","EXPENSE"); put("reference_id",id); put("amount",amount); put("note",title)
        })
        val entry=db.insertOrThrow("journal_entries",null,ContentValues().apply {
            put("entry_no","JE-EXP-$id"); put("description","مصروف: $title")
        })
        db.insertOrThrow("journal_lines",null,ContentValues().apply {
            put("entry_id",entry); put("account_code","510"); put("account_name","المصروفات التشغيلية"); put("debit",amount); put("credit",0.0)
        })
        db.insertOrThrow("journal_lines",null,ContentValues().apply {
            put("entry_id",entry); put("account_code","101"); put("account_name","الصندوق"); put("debit",0.0); put("credit",amount)
        })
        db.setTransactionSuccessful()
    } finally { db.endTransaction() }
}
private fun CoreDb.cashRows(): List<CashRow> = buildList {
    readableDatabase.rawQuery("SELECT kind,source,amount,note,created_at FROM cash_transactions ORDER BY id DESC LIMIT 200",null).use { c ->
        while(c.moveToNext()) add(CashRow(c.getString(0),c.getString(1),c.getDouble(2),c.getString(3),c.getString(4)))
    }
}
private fun CoreDb.cashBalance(): Double = readableDatabase.rawQuery(
    "SELECT COALESCE(SUM(CASE WHEN kind='IN' THEN amount ELSE -amount END),0) FROM cash_transactions",null
).use { c -> if(c.moveToFirst()) c.getDouble(0) else 0.0 }
private fun CoreDb.reportValues(): List<Pair<String,Double>> {
    fun scalar(sql:String)=readableDatabase.rawQuery(sql,null).use { c -> if(c.moveToFirst()) c.getDouble(0) else 0.0 }
    return listOf(
        "إجمالي المبيعات" to scalar("SELECT COALESCE(SUM(total),0) FROM sales"),
        "المبالغ المحصلة" to scalar("SELECT COALESCE(SUM(paid),0) FROM sales"),
        "المشتريات" to scalar("SELECT COALESCE(SUM(total),0) FROM purchases"),
        "المصروفات" to scalar("SELECT COALESCE(SUM(amount),0) FROM expenses"),
        "قيمة المخزون بالتكلفة" to scalar("SELECT COALESCE(SUM(cost*quantity),0) FROM products"),
        "عدد الأصناف" to scalar("SELECT COUNT(*) FROM products")
    )
}

@Composable
fun SuppliersModule() {
    val ctx= LocalContext.current; val db= remember { CoreDb(ctx) }; var rows by remember { mutableStateOf(db.suppliers()) }
    var name by remember { mutableStateOf("") }; var phone by remember { mutableStateOf("") }; var balance by remember { mutableStateOf("0") }; var error by remember { mutableStateOf("") }
    Column(Modifier.fillMaxSize(),verticalArrangement=Arrangement.spacedBy(8.dp)) {
        Text("إدارة الموردين",style=MaterialTheme.typography.titleLarge)
        OutlinedTextField(name,{name=it},label={Text("اسم المورد")},modifier=Modifier.fillMaxWidth())
        OutlinedTextField(phone,{phone=it},label={Text("الهاتف")},modifier=Modifier.fillMaxWidth())
        OutlinedTextField(balance,{balance=it},label={Text("الرصيد الافتتاحي المستحق (اختياري)")},modifier=Modifier.fillMaxWidth())
        if(error.isNotBlank()) Text(error,color=MaterialTheme.colorScheme.error)
        Button(onClick={ try { db.addSupplier(name,phone,balance.toDoubleOrNull()?:0.0); rows=db.suppliers(); name="";phone="";balance="0";error="" } catch(e:Exception){error=e.message?:"تعذر الحفظ"} },modifier=Modifier.fillMaxWidth()) { Text("حفظ المورد") }
        Text("الموردون المسجلون: ${rows.size}")
        LazyColumn { items(rows,key={it.id}) { s -> Card(Modifier.fillMaxWidth().padding(vertical=3.dp)) { Column(Modifier.padding(12.dp)) { Text(s.name,style=MaterialTheme.typography.titleMedium); Text("الهاتف: ${s.phone}"); Text("الرصيد الافتتاحي: ${moneyFmt.format(s.balance)} ر.ي") } } } }
    }
}

@Composable
fun PurchasesModule() {
    val ctx=LocalContext.current; val db= remember { CoreDb(ctx) }; var products by remember { mutableStateOf(db.products()) }; var suppliers by remember { mutableStateOf(db.suppliers()) }
    var productId by remember { mutableStateOf(0L) }; var supplierId by remember { mutableStateOf(0L) }; var qty by remember { mutableStateOf("1") }; var cost by remember { mutableStateOf("") }; var paid by remember { mutableStateOf("0") }; var message by remember { mutableStateOf("") }
    Column(Modifier.fillMaxSize(),verticalArrangement=Arrangement.spacedBy(9.dp)) {
        Text("تسجيل المشتريات",style=MaterialTheme.typography.titleLarge)
        if(products.isEmpty()) Text("أضف المنتجات أولاً من قسم المخزون.")
        else {
            Text("الصنف")
            products.forEach { p -> Row { RadioButton(selected=productId==p.id,onClick={productId=p.id;cost=p.cost.toString()}); Text("${p.name} — المتوفر ${p.quantity}",modifier=Modifier.padding(top=12.dp)) } }
            Text("المورد (اختياري)")
            Row { RadioButton(selected=supplierId==0L,onClick={supplierId=0L}); Text("بدون مورد",modifier=Modifier.padding(top=12.dp)) }
            suppliers.forEach { s -> Row { RadioButton(selected=supplierId==s.id,onClick={supplierId=s.id}); Text(s.name,modifier=Modifier.padding(top=12.dp)) } }
            OutlinedTextField(qty,{qty=it},label={Text("الكمية المستلمة")},modifier=Modifier.fillMaxWidth())
            OutlinedTextField(cost,{cost=it},label={Text("تكلفة الوحدة")},modifier=Modifier.fillMaxWidth())
            OutlinedTextField(paid,{paid=it},label={Text("المدفوع نقداً")},modifier=Modifier.fillMaxWidth())
            Text("الإجمالي: ${moneyFmt.format((qty.toDoubleOrNull()?:0.0)*(cost.toDoubleOrNull()?:0.0))} ر.ي")
            if(message.isNotBlank()) Text(message)
            Button(onClick={try { db.postPurchase(productId,if(supplierId==0L)null else supplierId,qty.toDoubleOrNull()?:0.0,cost.toDoubleOrNull()?:-1.0,paid.toDoubleOrNull()?:-1.0);products=db.products();message="تم حفظ فاتورة الشراء وتحديث المخزون والصندوق والقيود"; } catch(e:Exception){message=e.message?:"تعذر حفظ الشراء"}},modifier=Modifier.fillMaxWidth(),enabled=productId>0) { Text("حفظ فاتورة الشراء") }
        }
    }
}

@Composable
fun ExpensesModule() {
    val ctx=LocalContext.current; val db= remember { CoreDb(ctx) }; var title by remember { mutableStateOf("") }; var category by remember { mutableStateOf("عام") }; var amount by remember { mutableStateOf("") }; var note by remember { mutableStateOf("") }; var message by remember { mutableStateOf("") }
    Column(Modifier.fillMaxSize(),verticalArrangement=Arrangement.spacedBy(9.dp)) {
        Text("المصروفات والإيرادات",style=MaterialTheme.typography.titleLarge)
        OutlinedTextField(title,{title=it},label={Text("بيان المصروف")},modifier=Modifier.fillMaxWidth())
        OutlinedTextField(category,{category=it},label={Text("التصنيف")},modifier=Modifier.fillMaxWidth())
        OutlinedTextField(amount,{amount=it},label={Text("المبلغ بالريال")},modifier=Modifier.fillMaxWidth())
        OutlinedTextField(note,{note=it},label={Text("ملاحظات")},modifier=Modifier.fillMaxWidth())
        if(message.isNotBlank()) Text(message)
        Button(onClick={try{db.addExpense(title,category,amount.toDoubleOrNull()?:0.0,note);title="";amount="";note="";message="تم تسجيل المصروف وترحيله للصندوق والقيد المحاسبي"}catch(e:Exception){message=e.message?:"تحقق من البيانات"}},modifier=Modifier.fillMaxWidth()){Text("حفظ المصروف")}
        Text("ملاحظة: تسجيل الإيرادات المستقلة يحتاج تعريف مصدر إيراد قبل تفعيله حتى لا تُسجل قيود غير صحيحة.")
    }
}

@Composable
fun CashModule() {
    val ctx=LocalContext.current; val db= remember { CoreDb(ctx) }; var rows by remember { mutableStateOf(db.cashRows()) }; var balance by remember { mutableStateOf(db.cashBalance()) }
    fun refresh(){rows=db.cashRows();balance=db.cashBalance()}
    Column(Modifier.fillMaxSize(),verticalArrangement=Arrangement.spacedBy(8.dp)) {
        Text("الصندوق والخزينة",style=MaterialTheme.typography.titleLarge)
        Card(Modifier.fillMaxWidth()) { Column(Modifier.padding(16.dp)) { Text("الرصيد النقدي المسجل",style=MaterialTheme.typography.titleSmall); Text("${moneyFmt.format(balance)} ر.ي",style=MaterialTheme.typography.headlineMedium) } }
        Row(horizontalArrangement=Arrangement.spacedBy(8.dp)) { Button(onClick={refresh()}){Text("تحديث")}; OutlinedButton(onClick={refresh()}){Text("تحديث الحركات")} }
        Text("آخر 200 حركة")
        LazyColumn { items(rows) { r -> ListItem(headlineContent={Text(if(r.kind=="IN") "قبض — ${r.source}" else "صرف — ${r.source}")}, supportingContent={Text("${r.note} • ${r.date}")}, trailingContent={Text("${moneyFmt.format(r.amount)} ر.ي",color=if(r.kind=="IN")MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error)}) } }
        Text("الرصيد محسوب من حركات البيع والشراء والمصروفات المسجلة. لا يشمل أرصدة افتتاحية أو ورديات مستقلة.")
    }
}

@Composable
fun ReportsModule() {
    val ctx=LocalContext.current; val db= remember { CoreDb(ctx) }; var values by remember { mutableStateOf(db.reportValues()) }
    Column(Modifier.fillMaxSize(),verticalArrangement=Arrangement.spacedBy(8.dp)) {
        Text("التقارير",style=MaterialTheme.typography.titleLarge)
        Button(onClick={values=db.reportValues()},modifier=Modifier.fillMaxWidth()){Text("تحديث التقارير")}
        values.forEach { (label,value) -> Card(Modifier.fillMaxWidth()) { ListItem(headlineContent={Text(label)},trailingContent={Text(if(label=="عدد الأصناف") moneyFmt.format(value) else "${moneyFmt.format(value)} ر.ي")}) } }
        Text("هذه مؤشرات إجمالية من قاعدة البيانات المحلية وليست قوائم مالية معتمدة أو تقرير أرباح نهائي.")
    }
}

@Composable
fun BackupModule() {
    val ctx=LocalContext.current
    val export= rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/octet-stream")) { uri ->
        if(uri!=null) runCatching { ctx.contentResolver.openOutputStream(uri)?.use { out -> ctx.getDatabasePath("mohasibi_core.db").inputStream().use { it.copyTo(out) } } }
    }
    var message by remember { mutableStateOf("أنشئ نسخة من قاعدة بيانات التشغيل المحلية واحتفظ بها خارج الهاتف.") }
    Column(Modifier.fillMaxSize(),verticalArrangement=Arrangement.spacedBy(12.dp)) {
        Text("النسخ الاحتياطي",style=MaterialTheme.typography.titleLarge)
        Text(message)
        Button(onClick={export.launch("mohasibi-backup.db")},modifier=Modifier.fillMaxWidth()){Text("تصدير نسخة احتياطية")}
        Text("مهم: أغلق التطبيق قبل نسخ الملف أو نقله. الاستعادة الآمنة والتحقق من سلامة النسخة ستضاف في مرحلة تالية؛ لا تستبدل ملف قاعدة البيانات يدوياً أثناء تشغيل التطبيق.")
    }
}
