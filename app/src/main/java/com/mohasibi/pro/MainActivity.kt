package com.mohasibi.pro

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private val Navy = Color(0xFF071B3A)
private val Navy2 = Color(0xFF102B55)
private val Gold = Color(0xFFE9B83F)
private val Canvas = Color(0xFFF3F6FC)
private val Ink = Color(0xFF17243B)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = android.graphics.Color.rgb(7, 27, 58)
        window.navigationBarColor = android.graphics.Color.rgb(7, 27, 58)
        setContent { MohasibiApp() }
    }
}

@Composable
fun MohasibiApp() {
    var signedIn by remember { mutableStateOf(false) }
    var page by remember { mutableStateOf("الرئيسية") }
    MaterialTheme(colorScheme = lightColorScheme(primary = Navy, secondary = Gold, background = Canvas)) {
        CompositionLocalProvider(LocalLayoutDirection provides androidx.compose.ui.unit.LayoutDirection.Rtl) {
            if (!signedIn) LoginScreen(onLogin = { signedIn = true })
            else MainShell(page = page, onNavigate = { page = it }, onLogout = { signedIn = false })
        }
    }
}

@Composable
fun BrandMark(size: Int = 76) {
    Surface(shape = RoundedCornerShape(22.dp), color = Navy2, modifier = Modifier.size(size.dp)) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
            Icon(Icons.Default.Store, null, tint = Gold, modifier = Modifier.size((size * .42).dp))
            Icon(Icons.Default.TrendingUp, null, tint = Gold, modifier = Modifier.size((size * .25).dp))
        }
    }
}

@Composable
fun LoginScreen(onLogin: () -> Unit) {
    var username by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var showPassword by remember { mutableStateOf(false) }
    Box(Modifier.fillMaxSize().background(Navy), contentAlignment = Alignment.Center) {
        Column(Modifier.fillMaxWidth().padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            BrandMark(112)
            Spacer(Modifier.height(18.dp))
            Text("محاسبي", color = Color.White, fontSize = 34.sp, fontWeight = FontWeight.ExtraBold)
            Text("MOHASIBI PRO", color = Gold, fontSize = 17.sp, fontWeight = FontWeight.Bold, letterSpacing = 3.sp)
            Text("نظامك المتكامل لإدارة التجارة والمخزون والحسابات", color = Color.White.copy(alpha = .8f), textAlign = TextAlign.Center, fontSize = 13.sp)
            Spacer(Modifier.height(32.dp))
            Surface(color = Color.White, shape = RoundedCornerShape(28.dp), modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(22.dp)) {
                    Text("تسجيل الدخول", color = Navy, fontSize = 22.sp, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(18.dp))
                    OutlinedTextField(value = username, onValueChange = { username = it }, modifier = Modifier.fillMaxWidth(), label = { Text("اسم المستخدم") }, leadingIcon = { Icon(Icons.Default.Person, null) }, singleLine = true)
                    Spacer(Modifier.height(12.dp))
                    OutlinedTextField(value = password, onValueChange = { password = it }, modifier = Modifier.fillMaxWidth(), label = { Text("كلمة المرور") }, leadingIcon = { Icon(Icons.Default.Lock, null) }, trailingIcon = { IconButton(onClick = { showPassword = !showPassword }) { Icon(if (showPassword) Icons.Default.VisibilityOff else Icons.Default.Visibility, null) } }, visualTransformation = if (showPassword) androidx.compose.ui.text.input.VisualTransformation.None else androidx.compose.ui.text.input.PasswordVisualTransformation(), singleLine = true)
                    Spacer(Modifier.height(12.dp))
                    Text("نسيت كلمة المرور؟", color = Navy2, modifier = Modifier.align(Alignment.Start))
                    Spacer(Modifier.height(20.dp))
                    Button(onClick = onLogin, modifier = Modifier.fillMaxWidth().height(52.dp), colors = ButtonDefaults.buttonColors(containerColor = Gold), shape = RoundedCornerShape(14.dp)) {
                        Text("دخول", color = Navy, fontWeight = FontWeight.Bold, fontSize = 17.sp)
                    }
                    Spacer(Modifier.height(10.dp))
                    Text("واجهة أولية للعرض — سيتم ربط الدخول بنظام المستخدمين الآمن", color = Color.Gray, fontSize = 10.sp, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
                }
            }
            Spacer(Modifier.height(20.dp))
            Text("محاسبي .. شريك نجاحك التجاري", color = Gold, fontWeight = FontWeight.SemiBold)
        }
    }
}

@Composable
fun MainShell(page: String, onNavigate: (String) -> Unit, onLogout: () -> Unit) {
    val menu = listOf("الرئيسية", "المبيعات", "نقطة البيع", "المشتريات", "المنتجات والمخزون", "العملاء والديون", "الموردون", "المصروفات والإيرادات", "الصندوق والخزينة", "التقارير", "المستخدمون والصلاحيات", "النسخ الاحتياطي", "الإعدادات")
    Scaffold(
        topBar = {
            Row(Modifier.fillMaxWidth().background(Navy).padding(horizontal = 16.dp, vertical = 14.dp), verticalAlignment = Alignment.CenterVertically) {
                BrandMark(42)
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text("محاسبي PRO", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 18.sp)
                    Text(page, color = Gold, fontSize = 12.sp)
                }
                IconButton(onClick = onLogout) { Icon(Icons.Default.Logout, "خروج", tint = Color.White) }
            }
        },
        bottomBar = {
            NavigationBar(containerColor = Color.White) {
                listOf("الرئيسية", "المبيعات", "العملاء", "التقارير").forEach { item ->
                    NavigationBarItem(selected = page == item, onClick = { onNavigate(item) }, icon = { Icon(when(item) { "الرئيسية" -> Icons.Default.Home; "المبيعات" -> Icons.Default.ShoppingCart; "العملاء" -> Icons.Default.People; else -> Icons.Default.BarChart }, null) }, label = { Text(item, fontSize = 10.sp) })
                }
            }
        }
    ) { padding ->
        Row(Modifier.fillMaxSize().background(Canvas).padding(padding)) {
            LazyColumn(Modifier.width(76.dp).fillMaxHeight().background(Navy).padding(vertical = 8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                items(menu.size) { index ->
                    val item = menu[index]
                    Column(Modifier.fillMaxWidth().clickable { onNavigate(item) }.padding(vertical = 10.dp, horizontal = 3.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(menuIcon(item), null, tint = if (page == item) Gold else Color.White.copy(alpha = .8f), modifier = Modifier.size(22.dp))
                        Text(item, color = if (page == item) Gold else Color.White, fontSize = 8.sp, textAlign = TextAlign.Center, lineHeight = 11.sp)
                    }
                }
            }
            Column(Modifier.weight(1f).fillMaxHeight().padding(12.dp)) {
                if (page == "الرئيسية") Dashboard()
                else ModulePage(page)
            }
        }
    }
}

@Composable
fun menuIcon(item: String) = when {
    item.contains("مخزون") || item.contains("منتجات") -> Icons.Default.Inventory2
    item.contains("مبيعات") || item.contains("بيع") -> Icons.Default.PointOfSale
    item.contains("مشتريات") -> Icons.Default.ShoppingBag
    item.contains("عملاء") || item.contains("ديون") -> Icons.Default.People
    item.contains("مورد") -> Icons.Default.LocalShipping
    item.contains("مصروف") || item.contains("إيراد") -> Icons.Default.Payments
    item.contains("صندوق") -> Icons.Default.AccountBalanceWallet
    item.contains("تقارير") -> Icons.Default.BarChart
    item.contains("مستخدم") -> Icons.Default.AdminPanelSettings
    item.contains("نسخ") -> Icons.Default.CloudUpload
    item.contains("إعداد") -> Icons.Default.Settings
    else -> Icons.Default.Dashboard
}

@Composable
fun Dashboard() {
    Text("مرحباً بك في محاسبي", color = Ink, fontSize = 21.sp, fontWeight = FontWeight.Bold)
    Text("ملخص أداء المنشأة", color = Color.Gray, fontSize = 12.sp)
    Spacer(Modifier.height(14.dp))
    val metrics = listOf(
        Triple("مبيعات اليوم", "0 ر.ي", Icons.Default.TrendingUp),
        Triple("مبيعات الشهر", "0 ر.ي", Icons.Default.BarChart),
        Triple("صافي الأرباح", "0 ر.ي", Icons.Default.AccountBalance),
        Triple("المشتريات", "0 ر.ي", Icons.Default.ShoppingCart),
        Triple("المخزون الحالي", "0 صنف", Icons.Default.Inventory2),
        Triple("الديون علينا", "0 ر.ي", Icons.Default.MoneyOff)
    )
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        metrics.chunked(2).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                row.forEach { (label, value, icon) ->
                    Card(Modifier.weight(1f), colors = CardDefaults.cardColors(containerColor = Color.White), shape = RoundedCornerShape(18.dp)) {
                        Column(Modifier.padding(13.dp)) {
                            Icon(icon, null, tint = Gold, modifier = Modifier.size(24.dp))
                            Spacer(Modifier.height(8.dp))
                            Text(label, color = Color.Gray, fontSize = 11.sp)
                            Text(value, color = Navy, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
    }
    Spacer(Modifier.height(14.dp))
    Card(colors = CardDefaults.cardColors(containerColor = Color.White), shape = RoundedCornerShape(18.dp)) {
        Column(Modifier.fillMaxWidth().padding(16.dp)) {
            Text("نظرة عامة", color = Navy, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(8.dp))
            Text("ستظهر هنا حركة المبيعات والأرباح بعد ربط قاعدة البيانات وإدخال العمليات.", color = Color.Gray, fontSize = 12.sp)
        }
    }
    Spacer(Modifier.height(12.dp))
    Text("إجراءات سريعة", color = Ink, fontWeight = FontWeight.Bold)
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        listOf("بيع جديد", "شراء جديد", "إضافة منتج").forEach { label ->
            OutlinedButton(onClick = {}, modifier = Modifier.weight(1f), contentPadding = PaddingValues(4.dp)) { Text(label, fontSize = 10.sp) }
        }
    }
}

@Composable
fun ModulePage(title: String) {
    Text(title, color = Ink, fontSize = 21.sp, fontWeight = FontWeight.Bold)
    Spacer(Modifier.height(12.dp))
    Card(colors = CardDefaults.cardColors(containerColor = Color.White), shape = RoundedCornerShape(18.dp)) {
        Column(Modifier.fillMaxWidth().padding(18.dp)) {
            Icon(menuIcon(title), null, tint = Gold, modifier = Modifier.size(34.dp))
            Spacer(Modifier.height(10.dp))
            Text("قسم $title", color = Navy, fontWeight = FontWeight.Bold, fontSize = 17.sp)
            Spacer(Modifier.height(6.dp))
            Text("تم إنشاء مدخل القسم ضمن الهيكل الأولي. ستتم إضافة الشاشات والنماذج وقواعد العمل وربط التخزين قبل اعتباره جاهزاً للاستخدام.", color = Color.Gray, fontSize = 12.sp)
        }
    }
}
