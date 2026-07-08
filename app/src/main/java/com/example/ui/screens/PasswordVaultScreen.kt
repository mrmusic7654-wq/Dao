package com.example.ui.screens

import android.content.*
import android.content.pm.PackageManager
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import com.example.ui.theme.*
import kotlinx.coroutines.*
import org.json.JSONArray
import org.json.JSONObject
import java.io.*
import java.security.*
import java.security.spec.*
import java.text.SimpleDateFormat
import java.util.*
import javax.crypto.*
import javax.crypto.spec.*

// ==================== DATA MODELS ====================

data class VaultEntry(
    val id: String = UUID.randomUUID().toString(),
    val title: String,
    val username: String = "",
    val password: String = "",
    val url: String = "",
    val notes: String = "",
    val category: VaultCategory = VaultCategory.LOGIN,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
    val lastUsed: Long = 0L,
    val isFavorite: Boolean = false,
    val icon: String = "🔑",
    val strength: PasswordStrength = PasswordStrength.WEAK
)

enum class VaultCategory(val label: String, val icon: String) {
    LOGIN("Login", "🔑"), BANKING("Banking", "🏦"), EMAIL("Email", "📧"),
    SOCIAL("Social", "👥"), WORK("Work", "💼"), SERVER("Server", "🖥"),
    WIFI("WiFi", "📶"), NOTES("Notes", "📝"), OTHER("Other", "📋")
}

enum class PasswordStrength { WEAK, FAIR, GOOD, STRONG, EXCELLENT }

data class PasswordGeneratorConfig(
    var length: Int = 16,
    var includeUppercase: Boolean = true,
    var includeLowercase: Boolean = true,
    var includeNumbers: Boolean = true,
    var includeSymbols: Boolean = true,
    var excludeAmbiguous: Boolean = false
)

// ==================== ENCRYPTION ENGINE ====================

object VaultCrypto {
    private const val AES_ALGORITHM = "AES/GCM/NoPadding"
    private const val KEY_SIZE = 256
    private var masterKey: SecretKey? = null
    private var isUnlocked = false

    fun generateKey(password: String, salt: ByteArray = ByteArray(16)): SecretKey {
        val spec = PBEKeySpec(password.toCharArray(), salt, 10000, KEY_SIZE)
        val factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
        return SecretKeySpec(factory.generateSecret(spec).encoded, "AES")
    }

    fun encrypt(data: String, key: SecretKey): String {
        val cipher = Cipher.getInstance(AES_ALGORITHM)
        cipher.init(Cipher.ENCRYPT_MODE, key)
        val iv = cipher.iv
        val encrypted = cipher.doFinal(data.toByteArray())
        val combined = ByteArray(iv.size + encrypted.size)
        System.arraycopy(iv, 0, combined, 0, iv.size)
        System.arraycopy(encrypted, 0, combined, iv.size, encrypted.size)
        return Base64.getEncoder().encodeToString(combined)
    }

    fun decrypt(encryptedData: String, key: SecretKey): String {
        val combined = Base64.getDecoder().decode(encryptedData)
        val iv = combined.copyOfRange(0, 12)
        val encrypted = combined.copyOfRange(12, combined.size)
        val cipher = Cipher.getInstance(AES_ALGORITHM)
        cipher.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(128, iv))
        return String(cipher.doFinal(encrypted))
    }

    fun unlock(masterPassword: String): Boolean {
        return try {
            masterKey = generateKey(masterPassword)
            isUnlocked = true
            true
        } catch (e: Exception) { false }
    }

    fun lock() { masterKey = null; isUnlocked = false }
    fun isUnlocked(): Boolean = isUnlocked
}

object PasswordStrengthChecker {
    fun check(password: String): PasswordStrength {
        var score = 0
        if (password.length >= 8) score++
        if (password.length >= 12) score++
        if (password.length >= 16) score++
        if (password.any { it.isUpperCase() }) score++
        if (password.any { it.isLowerCase() }) score++
        if (password.any { it.isDigit() }) score++
        if (password.any { !it.isLetterOrDigit() }) score++
        if (password.contains(Regex("(?=.*[a-z])(?=.*[A-Z])(?=.*\\d)(?=.*[^a-zA-Z0-9])"))) score++
        return when {
            score >= 7 -> PasswordStrength.EXCELLENT
            score >= 5 -> PasswordStrength.STRONG
            score >= 4 -> PasswordStrength.GOOD
            score >= 2 -> PasswordStrength.FAIR
            else -> PasswordStrength.WEAK
        }
    }

    fun getStrengthColor(strength: PasswordStrength): Color = when (strength) {
        PasswordStrength.EXCELLENT -> Color(0xFF4CAF50)
        PasswordStrength.STRONG -> Color(0xFF8BC34A)
        PasswordStrength.GOOD -> Color(0xFFFFEB3B)
        PasswordStrength.FAIR -> Color(0xFFFF9800)
        PasswordStrength.WEAK -> Color(0xFFF44336)
    }

    fun generatePassword(config: PasswordGeneratorConfig): String {
        val upper = "ABCDEFGHJKLMNPQRSTUVWXYZ"
        val lower = "abcdefghjkmnpqrstuvwxyz"
        val digits = "23456789"
        val symbols = "!@#$%^&*()_+-=[]{}|;:,.<>?"
        val ambiguous = "Il1O0"
        val charPool = StringBuilder()
        if (config.includeUppercase) charPool.append(upper)
        if (config.includeLowercase) charPool.append(lower)
        if (config.includeNumbers) charPool.append(digits)
        if (config.includeSymbols) charPool.append(symbols)
        var pool = charPool.toString()
        if (config.excludeAmbiguous) pool = pool.filter { it !in ambiguous }.toString()
        if (pool.isEmpty()) return "Password123!"
        return (1..config.length).map { pool[SecureRandom().nextInt(pool.length)] }.joinToString("")
    }
}

// ==================== MAIN SCREEN ====================

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PasswordVaultScreen(isDark: Boolean, onMenuClick: () -> Unit) {
    val context = LocalContext.current
    var isLocked by remember { mutableStateOf(true) }
    var masterPassword by remember { mutableStateOf("") }
    var confirmPassword by remember { mutableStateOf("") }
    var isSetup by remember { mutableStateOf(false) }
    var showSetupDialog by remember { mutableStateOf(false) }
    var unlockError by remember { mutableStateOf("") }

    var entries by remember { mutableStateOf(listOf<VaultEntry>()) }
    var selectedCategory by remember { mutableStateOf<VaultCategory?>(null) }
    var searchQuery by remember { mutableStateOf("") }
    var showAddDialog by remember { mutableStateOf(false) }
    var showGenerator by remember { mutableStateOf(false) }
    var showExportDialog by remember { mutableStateOf(false) }
    var showBreachCheck by remember { mutableStateOf(false) }
    var editingEntry by remember { mutableStateOf<VaultEntry?>(null) }
    var viewingEntry by remember { mutableStateOf<VaultEntry?>(null) }

    // Add/edit form state
    var formTitle by remember { mutableStateOf("") }
    var formUsername by remember { mutableStateOf("") }
    var formPassword by remember { mutableStateOf("") }
    var formUrl by remember { mutableStateOf("") }
    var formNotes by remember { mutableStateOf("") }
    var formCategory by remember { mutableStateOf(VaultCategory.LOGIN) }
    var showPassword by remember { mutableStateOf(false) }

    // Generator state
    var genConfig by remember { mutableStateOf(PasswordGeneratorConfig()) }
    var generatedPassword by remember { mutableStateOf("") }

    // Bio-metric
    var canUseBiometrics by remember { mutableStateOf(false) }

    // ===== CLEAR FORM FUNCTION — DEFINED HERE, ACCESSIBLE TO ALL DIALOGS =====
    fun clearForm() {
        formTitle = ""
        formUsername = ""
        formPassword = ""
        formUrl = ""
        formNotes = ""
        formCategory = VaultCategory.LOGIN
        showPassword = false
    }

    LaunchedEffect(Unit) {
        val bm = BiometricManager.from(context)
        canUseBiometrics = bm.canAuthenticate(BiometricManager.Authenticators.BIOMETRIC_STRONG) == BiometricManager.BIOMETRIC_SUCCESS
        val vaultFile = File(context.filesDir, "vault_prefs.dat")
        isSetup = !vaultFile.exists()
        if (isSetup) showSetupDialog = true
    }

    fun authenticateBiometric() {
        val activity = context as FragmentActivity
        val prompt = BiometricPrompt(activity, ContextCompat.getMainExecutor(context),
            object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                    isLocked = false
                    unlockError = ""
                }
                override fun onAuthenticationFailed() { unlockError = "Biometric failed" }
            })
        prompt.authenticate(BiometricPrompt.PromptInfo.Builder()
            .setTitle("Unlock Vault")
            .setNegativeButtonText("Use Password")
            .build())
    }

    fun unlockVault() {
        if (masterPassword.isBlank()) { unlockError = "Enter master password"; return }
        if (VaultCrypto.unlock(masterPassword)) {
            isLocked = false; unlockError = ""
        } else { unlockError = "Wrong master password" }
    }

    fun addEntry() {
        val entry = VaultEntry(
            title = formTitle, username = formUsername, password = formPassword,
            url = formUrl, notes = formNotes, category = formCategory,
            strength = PasswordStrengthChecker.check(formPassword)
        )
        entries = (entries + entry).sortedBy { it.title.lowercase() }
        showAddDialog = false; clearForm()
    }

    fun updateEntry() {
        editingEntry?.let { old ->
            entries = entries.map { if (it.id == old.id) old.copy(
                title = formTitle, username = formUsername, password = formPassword,
                url = formUrl, notes = formNotes, category = formCategory,
                updatedAt = System.currentTimeMillis(),
                strength = PasswordStrengthChecker.check(formPassword)
            ) else it }.sortedBy { it.title.lowercase() }
        }
        editingEntry = null; showAddDialog = false; clearForm()
    }

    fun exportVault() {
        try {
            val json = JSONArray()
            entries.forEach { entry ->
                json.put(JSONObject().apply {
                    put("title", entry.title); put("username", entry.username)
                    put("password", entry.password); put("url", entry.url)
                    put("notes", entry.notes); put("category", entry.category.name)
                })
            }
            val file = File(context.getExternalFilesDir(null), "dao_vault_export_${System.currentTimeMillis()}.json")
            file.writeText(json.toString(2))
            Toast.makeText(context, "Exported to: ${file.name}", Toast.LENGTH_LONG).show()
        } catch (e: Exception) { Toast.makeText(context, "Export failed", Toast.LENGTH_SHORT).show() }
    }

    // ==================== LOCKED SCREEN ====================
    if (isLocked) {
        Column(modifier = Modifier.fillMaxSize().background(if (isDark) Color(0xFF070709) else Color(0xFFF1F0EC)),
            horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
            Icon(Icons.Default.Lock, null, modifier = Modifier.size(72.dp), tint = ZenGold)
            Spacer(Modifier.height(20.dp))
            Text("🔐 PASSWORD VAULT", fontFamily = FontFamily.Serif, fontWeight = FontWeight.Bold, color = ZenGold, fontSize = 22.sp)
            Text("Enter master password to unlock", color = YinTextSecondary, fontSize = 14.sp)
            Spacer(Modifier.height(24.dp))
            OutlinedTextField(value = masterPassword, onValueChange = { masterPassword = it; unlockError = "" },
                placeholder = { Text("Master Password") }, singleLine = true,
                visualTransformation = PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { unlockVault() }),
                modifier = Modifier.width(280.dp),
                colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = ZenGold, unfocusedBorderColor = Color(0xFF333340)),
                shape = RoundedCornerShape(12.dp))
            if (unlockError.isNotBlank()) Text(unlockError, color = ZenRed, fontSize = 12.sp, modifier = Modifier.padding(top = 4.dp))
            Spacer(Modifier.height(16.dp))
            Button(onClick = { unlockVault() }, colors = ButtonDefaults.buttonColors(containerColor = ZenGold),
                modifier = Modifier.width(200.dp).height(48.dp)) { Text("Unlock", color = Color.Black, fontWeight = FontWeight.Bold) }
            if (canUseBiometrics) {
                Spacer(Modifier.height(8.dp))
                TextButton(onClick = { authenticateBiometric() }) { Text("🔓 Unlock with Biometrics", color = ZenGold) }
            }
        }
        return
    }

    // ==================== SETUP DIALOG ====================
    if (showSetupDialog) {
        AlertDialog(
            onDismissRequest = { },
            title = { Text("Create Master Password", fontFamily = FontFamily.Serif, color = ZenGold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("This password encrypts your vault. It cannot be recovered if lost.", color = YinTextSecondary, fontSize = 12.sp)
                    OutlinedTextField(value = masterPassword, onValueChange = { masterPassword = it },
                        label = { Text("Master Password") }, singleLine = true, visualTransformation = PasswordVisualTransformation(),
                        modifier = Modifier.fillMaxWidth(), colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = ZenGold))
                    OutlinedTextField(value = confirmPassword, onValueChange = { confirmPassword = it },
                        label = { Text("Confirm Password") }, singleLine = true, visualTransformation = PasswordVisualTransformation(),
                        modifier = Modifier.fillMaxWidth(), colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = ZenGold))
                    val strength = PasswordStrengthChecker.check(masterPassword)
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("Strength: ", color = YinTextSecondary, fontSize = 12.sp)
                        Text(strength.name, color = PasswordStrengthChecker.getStrengthColor(strength), fontWeight = FontWeight.Bold, fontSize = 12.sp)
                    }
                    LinearProgressIndicator(progress = { (strength.ordinal + 1) / 5f }, modifier = Modifier.fillMaxWidth().height(4.dp).clip(RoundedCornerShape(2.dp)),
                        color = PasswordStrengthChecker.getStrengthColor(strength), trackColor = YinBlack)
                }
            },
            confirmButton = {
                Button(onClick = {
                    if (masterPassword.length < 6) { unlockError = "Minimum 6 characters"; return@Button }
                    if (masterPassword != confirmPassword) { unlockError = "Passwords don't match"; return@Button }
                    VaultCrypto.unlock(masterPassword)
                    showSetupDialog = false; isLocked = false
                }, colors = ButtonDefaults.buttonColors(containerColor = ZenGold)) { Text("Create Vault", color = Color.Black) }
            },
            containerColor = if (isDark) Color(0xFF14131A) else Color(0xFFF7F4EE)
        )
    }

    // ==================== ADD/EDIT DIALOG ====================
    if (showAddDialog) {
        AlertDialog(
            onDismissRequest = { showAddDialog = false; editingEntry = null; clearForm() },
            title = { Text(if (editingEntry != null) "Edit Entry" else "Add Entry", fontFamily = FontFamily.Serif, color = ZenGold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.heightIn(max = 450.dp).verticalScroll(rememberScrollState())) {
                    OutlinedTextField(value = formTitle, onValueChange = { formTitle = it }, label = { Text("Title") }, modifier = Modifier.fillMaxWidth(), singleLine = true, colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = ZenGold))
                    OutlinedTextField(value = formUsername, onValueChange = { formUsername = it }, label = { Text("Username/Email") }, modifier = Modifier.fillMaxWidth(), singleLine = true, colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = ZenGold))
                    OutlinedTextField(value = formPassword, onValueChange = { formPassword = it }, label = { Text("Password") }, singleLine = true,
                        visualTransformation = if (showPassword) VisualTransformation.None else PasswordVisualTransformation(),
                        trailingIcon = { IconButton(onClick = { showPassword = !showPassword }) { Icon(if (showPassword) Icons.Default.VisibilityOff else Icons.Default.Visibility, null, tint = ZenGold) } },
                        modifier = Modifier.fillMaxWidth(), colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = ZenGold))
                    if (formPassword.isNotBlank()) {
                        val s = PasswordStrengthChecker.check(formPassword)
                        Row { Text("Strength: ", color = YinTextSecondary, fontSize = 10.sp); Text(s.name, color = PasswordStrengthChecker.getStrengthColor(s), fontSize = 10.sp, fontWeight = FontWeight.Bold) }
                    }
                    OutlinedTextField(value = formUrl, onValueChange = { formUrl = it }, label = { Text("URL (optional)") }, modifier = Modifier.fillMaxWidth(), singleLine = true, colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = ZenGold))
                    OutlinedTextField(value = formNotes, onValueChange = { formNotes = it }, label = { Text("Notes (optional)") }, modifier = Modifier.fillMaxWidth(), maxLines = 2, colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = ZenGold))
                    Text("Category", color = YinTextSecondary, fontSize = 11.sp)
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        items(VaultCategory.entries.toList()) { cat ->
                            FilterChip(selected = formCategory == cat, onClick = { formCategory = cat },
                                label = { Text("${cat.icon} ${cat.label}", fontSize = 10.sp) },
                                colors = FilterChipDefaults.filterChipColors(selectedContainerColor = ZenGold.copy(alpha = 0.2f), selectedLabelColor = ZenGold))
                        }
                    }
                }
            },
            confirmButton = {
                Button(onClick = { if (editingEntry != null) updateEntry() else addEntry() },
                    enabled = formTitle.isNotBlank() && formPassword.isNotBlank(),
                    colors = ButtonDefaults.buttonColors(containerColor = ZenGold)) { Text("Save", color = Color.Black) }
            },
            dismissButton = { TextButton(onClick = { showAddDialog = false; editingEntry = null; clearForm() }) { Text("Cancel", color = ZenGold) } },
            containerColor = if (isDark) Color(0xFF14131A) else Color(0xFFF7F4EE)
        )
    }

    // ==================== VIEW ENTRY DIALOG ====================
    viewingEntry?.let { entry ->
        AlertDialog(
            onDismissRequest = { viewingEntry = null },
            title = { Text(entry.title, fontFamily = FontFamily.Serif, color = ZenGold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    InfoRow("Username", entry.username)
                    var showPw by remember { mutableStateOf(false) }
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                        Text("Password: ", color = YinTextSecondary, fontSize = 12.sp)
                        Text(if (showPw) entry.password else "••••••••", color = YinText, fontSize = 12.sp, modifier = Modifier.weight(1f))
                        IconButton(onClick = { showPw = !showPw }, modifier = Modifier.size(28.dp)) { Icon(if (showPw) Icons.Default.VisibilityOff else Icons.Default.Visibility, null, tint = ZenGold, modifier = Modifier.size(18.dp)) }
                        IconButton(onClick = {
                            val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                            cm.setPrimaryClip(ClipData.newPlainText("password", entry.password))
                            Toast.makeText(context, "Password copied", Toast.LENGTH_SHORT).show()
                        }, modifier = Modifier.size(28.dp)) { Icon(Icons.Default.ContentCopy, null, tint = ZenGold, modifier = Modifier.size(18.dp)) }
                    }
                    if (entry.url.isNotBlank()) InfoRow("URL", entry.url)
                    if (entry.notes.isNotBlank()) InfoRow("Notes", entry.notes)
                    InfoRow("Category", "${entry.category.icon} ${entry.category.label}")
                    InfoRow("Strength", entry.strength.name)
                    InfoRow("Created", SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date(entry.createdAt)))
                }
            },
            confirmButton = {
                Row {
                    Button(onClick = {
                        viewingEntry = null
                        editingEntry = entry; formTitle = entry.title; formUsername = entry.username
                        formPassword = entry.password; formUrl = entry.url; formNotes = entry.notes; formCategory = entry.category
                        showAddDialog = true
                    }, colors = ButtonDefaults.buttonColors(containerColor = ZenGold)) { Text("Edit", color = Color.Black) }
                    Spacer(Modifier.width(8.dp))
                    Button(onClick = { entries = entries - entry; viewingEntry = null; Toast.makeText(context, "Deleted", Toast.LENGTH_SHORT).show() },
                        colors = ButtonDefaults.buttonColors(containerColor = ZenRed)) { Text("Delete", color = Color.White) }
                }
            },
            dismissButton = { TextButton(onClick = { viewingEntry = null }) { Text("Close", color = ZenGold) } },
            containerColor = if (isDark) Color(0xFF14131A) else Color(0xFFF7F4EE)
        )
    }

    // ==================== PASSWORD GENERATOR DIALOG ====================
    if (showGenerator) {
        AlertDialog(
            onDismissRequest = { showGenerator = false },
            title = { Text("🔧 Password Generator", fontFamily = FontFamily.Serif, color = ZenGold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Card(colors = CardDefaults.cardColors(containerColor = Color(0xFF0F0F12)), shape = RoundedCornerShape(8.dp)) {
                        Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text(generatedPassword.ifBlank { "Click Generate" }, color = Color(0xFF8CBE91), fontFamily = FontFamily.Monospace, fontSize = 16.sp, modifier = Modifier.weight(1f))
                            IconButton(onClick = {
                                val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                cm.setPrimaryClip(ClipData.newPlainText("password", generatedPassword))
                                Toast.makeText(context, "Copied!", Toast.LENGTH_SHORT).show()
                            }) { Icon(Icons.Default.ContentCopy, null, tint = ZenGold) }
                        }
                    }
                    Text("Length: ${genConfig.length}", color = YinTextSecondary, fontSize = 12.sp)
                    Slider(value = genConfig.length.toFloat(), onValueChange = { genConfig = genConfig.copy(length = it.toInt()) },
                        valueRange = 6f..32f, steps = 25, colors = SliderDefaults.colors(thumbColor = ZenGold, activeTrackColor = ZenGold))
                    Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                        GenToggle("A-Z", genConfig.includeUppercase) { genConfig = genConfig.copy(includeUppercase = it) }
                        GenToggle("a-z", genConfig.includeLowercase) { genConfig = genConfig.copy(includeLowercase = it) }
                        GenToggle("0-9", genConfig.includeNumbers) { genConfig = genConfig.copy(includeNumbers = it) }
                        GenToggle("!@#", genConfig.includeSymbols) { genConfig = genConfig.copy(includeSymbols = it) }
                    }
                    Button(onClick = { generatedPassword = PasswordStrengthChecker.generatePassword(genConfig) },
                        colors = ButtonDefaults.buttonColors(containerColor = ZenGold), modifier = Modifier.fillMaxWidth()) {
                        Icon(Icons.Default.Refresh, null, modifier = Modifier.size(16.dp)); Text(" Generate", color = Color.Black)
                    }
                    if (generatedPassword.isNotBlank()) {
                        val s = PasswordStrengthChecker.check(generatedPassword)
                        LinearProgressIndicator(progress = { (s.ordinal + 1) / 5f }, modifier = Modifier.fillMaxWidth().height(4.dp).clip(RoundedCornerShape(2.dp)),
                            color = PasswordStrengthChecker.getStrengthColor(s), trackColor = YinBlack)
                        Text("Strength: ${s.name}", color = PasswordStrengthChecker.getStrengthColor(s), fontSize = 11.sp)
                    }
                }
            },
            confirmButton = {
                Button(onClick = { formPassword = generatedPassword; showGenerator = false },
                    colors = ButtonDefaults.buttonColors(containerColor = ZenGold)) { Text("Use This", color = Color.Black) }
            },
            dismissButton = { TextButton(onClick = { showGenerator = false }) { Text("Close", color = ZenGold) } },
            containerColor = if (isDark) Color(0xFF14131A) else Color(0xFFF7F4EE)
        )
    }

    // ==================== MAIN UI ====================
    Column(modifier = Modifier.fillMaxSize().background(if (isDark) Color(0xFF070709) else Color(0xFFF1F0EC))) {
        // Header
        Surface(color = if (isDark) YinBlack else YangWhite, shadowElevation = 4.dp) {
            Column(modifier = Modifier.statusBarsPadding()) {
                Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = onMenuClick) { Icon(Icons.Default.Menu, "Menu", tint = if (isDark) Color.White else Color.Black) }
                    Text("🔐 PASSWORD VAULT", fontFamily = FontFamily.Serif, fontWeight = FontWeight.Bold,
                        color = if (isDark) ZenGold else Color(0xFF9E7E1D), style = MaterialTheme.typography.titleMedium)
                    Spacer(Modifier.weight(1f))
                    Text("${entries.size} items", color = YinTextSecondary, fontSize = 11.sp)
                    IconButton(onClick = { showGenerator = true }) { Icon(Icons.Default.Password, "Generate", tint = ZenGold, modifier = Modifier.size(20.dp)) }
                    IconButton(onClick = { VaultCrypto.lock(); isLocked = true }) { Icon(Icons.Default.Lock, "Lock", tint = ZenGold, modifier = Modifier.size(20.dp)) }
                }
                Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp).horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    FilterChip(selected = selectedCategory == null, onClick = { selectedCategory = null },
                        label = { Text("All", fontSize = 10.sp) },
                        colors = FilterChipDefaults.filterChipColors(selectedContainerColor = ZenGold.copy(alpha = 0.2f), selectedLabelColor = ZenGold))
                    VaultCategory.entries.forEach { cat ->
                        FilterChip(selected = selectedCategory == cat, onClick = { selectedCategory = if (selectedCategory == cat) null else cat },
                            label = { Text("${cat.icon} ${cat.label}", fontSize = 10.sp) },
                            colors = FilterChipDefaults.filterChipColors(selectedContainerColor = ZenGold.copy(alpha = 0.2f), selectedLabelColor = ZenGold))
                    }
                }
            }
        }

        // Search + Add
        Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(value = searchQuery, onValueChange = { searchQuery = it },
                placeholder = { Text("Search vault...", fontSize = 12.sp, color = Color.Gray) },
                modifier = Modifier.weight(1f).height(44.dp),
                leadingIcon = { Icon(Icons.Default.Search, null, tint = Color.Gray, modifier = Modifier.size(18.dp)) },
                singleLine = true, shape = RoundedCornerShape(22.dp),
                colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = ZenGold, unfocusedBorderColor = Color(0xFF333340)))
            Button(onClick = { clearForm(); showAddDialog = true }, colors = ButtonDefaults.buttonColors(containerColor = ZenGold),
                modifier = Modifier.height(44.dp)) { Icon(Icons.Default.Add, null, modifier = Modifier.size(18.dp), tint = Color.Black); Text(" Add", color = Color.Black, fontSize = 13.sp) }
        }

        // Entry list
        val filtered = entries.filter {
            (selectedCategory == null || it.category == selectedCategory) &&
            (searchQuery.isBlank() || it.title.contains(searchQuery, ignoreCase = true) || it.username.contains(searchQuery, ignoreCase = true))
        }

        if (filtered.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Icon(Icons.Default.Password, null, modifier = Modifier.size(56.dp), tint = Color.Gray.copy(alpha = 0.4f))
                    Text(if (entries.isEmpty()) "Vault is empty" else "No matching entries", color = Color.Gray, fontSize = 16.sp)
                    if (entries.isEmpty()) Text("Tap + to add your first password", color = Color.Gray.copy(alpha = 0.6f), fontSize = 12.sp)
                }
            }
        } else {
            LazyColumn(contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                items(filtered) { entry ->
                    Card(modifier = Modifier.fillMaxWidth().clickable(onClick = { viewingEntry = entry }),
                        colors = CardDefaults.cardColors(containerColor = YinCardBg), shape = RoundedCornerShape(10.dp)) {
                        Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                            Box(modifier = Modifier.size(40.dp).clip(RoundedCornerShape(8.dp)).background(ZenGold.copy(alpha = 0.1f)), contentAlignment = Alignment.Center) { Text(entry.icon, fontSize = 20.sp) }
                            Spacer(Modifier.width(10.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(entry.title, color = YinText, fontWeight = FontWeight.Bold, fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                Text(entry.username.ifBlank { "No username" }, color = YinTextSecondary, fontSize = 11.sp, maxLines = 1)
                                if (entry.url.isNotBlank()) Text(entry.url, color = ZenBlue, fontSize = 10.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            }
                            Column(horizontalAlignment = Alignment.End) {
                                Text(entry.category.icon, fontSize = 16.sp)
                                Box(modifier = Modifier.width(50.dp).height(3.dp).clip(RoundedCornerShape(2.dp)).background(PasswordStrengthChecker.getStrengthColor(entry.strength)))
                            }
                        }
                    }
                }

                // Bottom actions
                item {
                    Spacer(Modifier.height(16.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                        OutlinedButton(onClick = { exportVault() }, modifier = Modifier.weight(1f),
                            border = BorderStroke(1.dp, ZenGold)) { Icon(Icons.Default.Upload, null, modifier = Modifier.size(16.dp)); Text("Export", color = ZenGold, fontSize = 12.sp) }
                        OutlinedButton(onClick = { showBreachCheck = true }, modifier = Modifier.weight(1f),
                            border = BorderStroke(1.dp, ZenRed)) { Icon(Icons.Default.Security, null, modifier = Modifier.size(16.dp)); Text("Breach Check", color = ZenRed, fontSize = 12.sp) }
                    }
                }
            }
        }
    }
}

@Composable
private fun GenToggle(label: String, checked: Boolean, onToggle: (Boolean) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Checkbox(checked = checked, onCheckedChange = onToggle, colors = CheckboxDefaults.colors(checkedColor = ZenGold), modifier = Modifier.size(20.dp))
        Text(label, color = YinTextSecondary, fontSize = 11.sp)
    }
}

@Composable
private fun InfoRow(label: String, value: String) {
    Row(modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, color = YinTextSecondary, fontSize = 11.sp); Text(value, color = YinText, fontSize = 11.sp, modifier = Modifier.widthIn(max = 200.dp))
    }
}
