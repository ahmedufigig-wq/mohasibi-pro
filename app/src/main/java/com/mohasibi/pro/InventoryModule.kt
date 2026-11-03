package com.mohasibi.pro

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import java.text.DecimalFormat

@Composable
fun InventoryModule() {
    val context = LocalContext.current
    val db = remember { CoreDb(context.applicationContext) }
    var products by remember { mutableStateOf(db.products()) }
    var addOpen by remember { mutableStateOf(false) }
    var stockTarget by remember { mutableStateOf<Product?>(null) }
    var name by remember { mutableStateOf("") }
    var barcode by remember { mutableStateOf("") }
    var cost by remember { mutableStateOf("") }
    var price by remember { mutableStateOf("") }
    var quantity by remember { mutableStateOf("") }
    var minimum by remember { mutableStateOf("") }
    var adjustment by remember { mutableStateOf("") }
    var error by remember { mutableStateOf("") }
    val fmt = remember { DecimalFormat("#,##0.##") }
    fun refresh() { products = db.products() }
    Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text("المنتجات وإدارة المخزون", style = MaterialTheme.typography.titleLarge)
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("عدد الأصناف: ${products.size}")
            Text("قيمة التكلفة: ${fmt.format(products.sumOf { it.cost * it.quantity })} ر.ي")
        }
        Button(onClick = { name=""; barcode=""; cost=""; price=""; quantity=""; minimum=""; error=""; addOpen=true }, modifier=Modifier.fillMaxWidth()) { Text("＋ إضافة منتج") }
        if(products.isEmpty()) Text("لا توجد منتجات. أضف أول صنف لبدء إدارة المخزون.")
        LazyColumn(verticalArrangement=Arrangement.spacedBy(8.dp)) {
            items(products, key={it.id}) { p ->
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.fillMaxWidth().padding(14.dp), verticalArrangement=Arrangement.spacedBy(4.dp)) {
                        Text(p.name, style=MaterialTheme.typography.titleMedium)
                        if(p.barcode.isNotBlank()) Text("الباركود: ${p.barcode}", style=MaterialTheme.typography.bodySmall)
                        Text("التكلفة: ${fmt.format(p.cost)} ر.ي   •   البيع: ${fmt.format(p.price)} ر.ي")
                        Text("المتوفر: ${fmt.format(p.quantity)}   •   حد التنبيه: ${fmt.format(p.minQuantity)}", color=if(p.quantity<=p.minQuantity) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary)
                        OutlinedButton(onClick={stockTarget=p; adjustment=""; error=""}) { Text("تسوية / تعديل الكمية") }
                    }
                }
            }
        }
    }
    if(addOpen) AlertDialog(onDismissRequest={addOpen=false}, title={Text("إضافة منتج")}, text={
        Column(verticalArrangement=Arrangement.spacedBy(6.dp)) {
            OutlinedTextField(name,{name=it},label={Text("اسم المنتج")},singleLine=true)
            OutlinedTextField(barcode,{barcode=it},label={Text("الباركود (اختياري)")},singleLine=true)
            OutlinedTextField(cost,{cost=it},label={Text("سعر التكلفة بالريال")},singleLine=true)
            OutlinedTextField(price,{price=it},label={Text("سعر البيع بالريال")},singleLine=true)
            OutlinedTextField(quantity,{quantity=it},label={Text("الكمية الافتتاحية")},singleLine=true)
            OutlinedTextField(minimum,{minimum=it},label={Text("حد التنبيه عند النقص")},singleLine=true)
            if(error.isNotBlank()) Text(error,color=MaterialTheme.colorScheme.error)
        }
    }, confirmButton={TextButton(onClick={
        val c=cost.toDoubleOrNull(); val s=price.toDoubleOrNull(); val q=quantity.toDoubleOrNull() ?: 0.0; val m=minimum.toDoubleOrNull() ?: 0.0
        if(name.isBlank() || c==null || s==null || c<0 || s<0 || q<0 || m<0 || (cost.isBlank()) || (price.isBlank()) || (quantity.isNotBlank() && q<0) || (minimum.isNotBlank() && m<0)) error="أدخل الاسم والأسعار والكميات بأرقام صحيحة غير سالبة"
        else try { db.addProduct(name.trim(),barcode.trim(),c,s,q,m); refresh(); addOpen=false } catch(e: Exception) { error="تعذر الحفظ. قد يكون الباركود مستخدماً مسبقاً." }
    }){Text("حفظ")}}, dismissButton={TextButton(onClick={addOpen=false}){Text("إلغاء")}})
    stockTarget?.let { p ->
        AlertDialog(onDismissRequest={stockTarget=null},title={Text("تسوية مخزون: ${p.name}")},text={
            Column(verticalArrangement=Arrangement.spacedBy(6.dp)) {
                Text("الكمية الحالية: ${fmt.format(p.quantity)}")
                OutlinedTextField(adjustment,{adjustment=it},label={Text("فرق الكمية (+ إضافة / - صرف)")},singleLine=true)
                if(error.isNotBlank()) Text(error,color=MaterialTheme.colorScheme.error)
            }
        },confirmButton={TextButton(onClick={
            val delta=adjustment.toDoubleOrNull()
            if(delta==null || delta==0.0) error="أدخل فرق كمية صحيحاً غير صفري"
            else try { db.adjustStock(p.id,delta); refresh(); stockTarget=null } catch(e: Exception) { error=e.message ?: "تعذر تعديل المخزون" }
        }){Text("تطبيق")}},dismissButton={TextButton(onClick={stockTarget=null}){Text("إلغاء")}})
    }
}
