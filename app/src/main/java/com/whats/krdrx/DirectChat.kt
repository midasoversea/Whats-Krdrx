package com.whats.krdrx

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Konversi otomatis saat mengetik nomor.
 *
 * - "08123..."      -> "+628123..."  (awalan 0 dianggap nomor Indonesia)
 * - "628123..."     -> "+628123..."
 * - "0062812..."    -> "+62812..."   (awalan 00 = kode internasional)
 * - "+6208123..."   -> "+628123..."  (angka 0 setelah kode 62 dibuang)
 * - Spasi, tanda hubung, titik, dan tanda kurung dibuang.
 * - Nomor lain yang tidak diawali 0 / 62 / + dibiarkan apa adanya.
 */
internal fun autoConvertPhone(input: String): String {
    val cleaned = input.filter { it.isDigit() || it == '+' }
    val hasPlus = cleaned.startsWith("+")
    var digits = cleaned.filter { it.isDigit() }
    var international = hasPlus

    when {
        hasPlus -> Unit
        digits.startsWith("00") -> {
            digits = digits.drop(2)
            international = true
        }
        digits.startsWith("0") -> {
            digits = "62" + digits.drop(1)
            international = true
        }
        digits.startsWith("62") -> international = true
    }

    // Nomor Indonesia tidak pernah memiliki angka 0 tepat setelah kode negara 62.
    if (international && digits.startsWith("620")) {
        digits = "62" + digits.drop(3)
    }

    return if (international) "+$digits" else digits
}

/** Valid bila diawali "+" dan memiliki 8 sampai 15 digit (batas E.164). */
internal fun isValidPhone(number: String): Boolean =
    number.startsWith("+") && number.count { it.isDigit() } in 8..15

private fun openDirectChat(context: Context, number: String, message: String, packageName: String): Boolean {
    val digits = number.filter { it.isDigit() }
    val uri = Uri.parse("https://wa.me/$digits").buildUpon().apply {
        if (message.isNotBlank()) appendQueryParameter("text", message)
    }.build()
    val intent = Intent(Intent.ACTION_VIEW, uri).setPackage(packageName)
    return try {
        context.startActivity(intent)
        true
    } catch (_: ActivityNotFoundException) {
        false
    }
}

@Composable
fun DirectChatSection(hasWhatsApp: Boolean, hasBusiness: Boolean) {
    val context = LocalContext.current
    var number by rememberSaveable { mutableStateOf("") }
    var message by rememberSaveable { mutableStateOf("") }
    val valid = isValidPhone(number)

    fun open(packageName: String) {
        if (!openDirectChat(context, number, message, packageName)) {
            Toast.makeText(context, "WhatsApp tidak ditemukan atau tidak dapat membuka chat.", Toast.LENGTH_LONG).show()
        }
    }

    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(
                "Kirim pesan ke nomor tanpa menyimpan kontak.",
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
            )
            OutlinedTextField(
                value = number,
                onValueChange = { number = autoConvertPhone(it) },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Nomor WhatsApp") },
                placeholder = { Text("08123456789") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                isError = number.isNotEmpty() && !valid,
                supportingText = {
                    Text(
                        when {
                            number.isEmpty() -> "Ketik 08... atau +kode negara. Awalan 0 otomatis menjadi +62."
                            !valid -> "Belum valid: harus diawali kode negara (+) dengan 8-15 digit."
                            else -> "Siap dikirim ke $number"
                        }
                    )
                },
                trailingIcon = {
                    if (number.isNotEmpty()) {
                        IconButton(onClick = { number = "" }) {
                            Icon(Icons.Default.Close, contentDescription = "Hapus nomor")
                        }
                    }
                }
            )
            OutlinedTextField(
                value = message,
                onValueChange = { message = it },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Pesan (opsional)") },
                minLines = 2,
                maxLines = 5
            )
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    modifier = Modifier.weight(1f),
                    enabled = valid && hasWhatsApp,
                    onClick = { open(StatusOptimizer.PACKAGE_WHATSAPP) }
                ) {
                    Icon(Icons.Default.Share, null)
                    Spacer(Modifier.width(6.dp))
                    Text("WhatsApp")
                }
                Button(
                    modifier = Modifier.weight(1f),
                    enabled = valid && hasBusiness,
                    onClick = { open(StatusOptimizer.PACKAGE_WHATSAPP_BUSINESS) }
                ) {
                    Icon(Icons.Default.Share, null)
                    Spacer(Modifier.width(6.dp))
                    Text("WA Business")
                }
            }
            if (!hasWhatsApp && !hasBusiness) {
                Text(
                    "WhatsApp tidak terdeteksi di perangkat ini.",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.error
                )
            }
        }
    }
}
