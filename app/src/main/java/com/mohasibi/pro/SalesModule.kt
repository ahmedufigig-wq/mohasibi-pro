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

private data class CartLine(val product: Product, val quantity: Double)

@Composable
fun SalesModule() {
    val context = LocalContext.current
    val db = remember { CoreDb(context.applicationContext) }
    var products by remember { mutableStateOf(db.products()) }
    val cart = remember { mutableStateListOf<CartLine>() }
    var paid by remember { mutableStateOf("") }
    var message by remember { mutableStateOf("") }
    var error by remember { mutableStateOf("") }
    val fmt = remember { DecimalFormat("#,##0.##") }
    val total = cart.sumOf { it.product.price * it.quantity }
    Column(Modifier.fillMaxSize(), verticalArrangement=Arrangement.spacedBy(8.dp)) {
        Text("نقطة البيع", style=MaterialTheme.typography.titleLarge)
        Text("اختر المنتجات لإضافتها إلى الفاتورة")
        LazyColumn(Modifier.weight(1f), verticalArrangement=Arrangement.spacedBy(6.dp)) {
            items(products.filter { it.quantity > 0 }, key={it.id}) { p ->
                Card(Modifier.fillMaxWidth()) {
                    Row(Modifier.fillMaxWidth().padding(10.dp), horizontalArrangement=Arrangement.SpaceBetween) {
                        Column(Modifier.weight(1f)) {
                            Text(p.name, style=MaterialTheme.typography.titleSmall)
                            Text("السعر: ${fmt.format(p.price)} ر.ي  •  المتوفر: ${fmt.format(p.quantity)}", style=MaterialTheme.typography.bodySmall)
                        }
                        Button(onClick={
                            val index=cart.indexOfFirst { it.product.id==p.id }
                            if(index>=0) {
                                val current=cart[index]
                                if(current.quantity+1<=p.quantity) cart[index]=current.copy(quantity=current.quantity+1)
                            } else cart.add(CartLine(p,1.0))
                        }) { Text("إضافة") }
                    }
                }
            }
        }
        Divider()
        Text("محتويات الفاتورة", style=MaterialTheme.typography.titleMedium)
        if(cart.isEmpty()) Text("لم تتم إضافة أصناف.")
        cart.forEach { line ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement=Arrangement.SpaceBetween) {
                Text("${line.product.name} × ${fmt.format(line.quantity)}")
                Row {
                    Text("${fmt.format(line.product.price*line.quantity)} ر.ي")
                    TextButton(onClick={cart.remove(line)}) { Text("حذف") }
                }
            }
        }
        Text("الإجمالي: ${fmt.format(total)} ر.ي", style=MaterialTheme.typography.titleMedium)
        OutlinedTextField(paid,{paid=it},label={Text("المبلغ المستلم (اتركه فارغاً للدفع الآجل)")},modifier=Modifier.fillMaxWidth(),singleLine=true)
        if(error.isNotBlank()) Text(error,color=MaterialTheme.colorScheme.error)
        if(message.isNotBlank()) Text(message,color=MaterialTheme.colorScheme.primary)
        Button(onClick={
            val received=if(paid.isBlank()) 0.0 else paid.toDoubleOrNull()
            if(cart.isEmpty()) error="أضف صنفاً إلى الفاتورة أولاً"
            else if(received==null || received<0 || received>total) error="تحقق من المبلغ المستلم"
            else try {
                db.checkout(cart.map { it.product to it.quantity },received)
                cart.clear(); paid=""; products=db.products(); error=""
                message="تم حفظ الفاتورة وترحيل حركة المخزون والصندوق والقيود المحاسبية"
            } catch(e: Exception) { error=e.message ?: "تعذر إتمام البيع" }
        },modifier=Modifier.fillMaxWidth(),enabled=cart.isNotEmpty()) { Text("حفظ الفاتورة وترحيل العملية") }
    }
}
