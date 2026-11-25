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

private const val COUNTRY_CODE = "62"
private const val MIN_LOCAL_DIGITS = 8
private const val MAX_LOCAL_DIGITS = 13

/**
 * Nomor Indonesia saja. Kode negara +62 sudah tertulis tetap di depan kolom,
 * sehingga kolom hanya menyimpan nomor lokal TANPA angka 0 di depan.
 *
 * Contoh input (termasuk hasil tempel/paste) -> nilai kolom:
 * - "08123456789"      -> "8123456789"
 * - "0812-3456-7890"   -> "81234567890"
 * - "628123456789"     -> "8123456789"
 * - "+62 812 3456"     -> "8123456"
 * - "+6208123..."      -> "8123..."   (angka 0 setelah +62 dibuang)
 * - "0062812345"       -> "812345"
 * - "+62 21 5555 1234" -> "2155551234" (telepon rumah juga diterima)
 *
 * Awalan "62" tanpa tanda "+" hanya dibuang jika diikuti 8 atau 0, agar nomor
 * telepon rumah berkode area 062x tidak ikut terpotong.
 */
internal fun normalizeIndonesianLocal(input: String): String {
    val trimmed = input.trim()
    val hasPlus = trimmed.startsWith("+")
    var digits = trimmed.filter { it.isDigit() }
    digits = when {
        digits.startsWith("00$COUNTRY_CODE") -> digits.drop(2 + COUNTRY_CODE.length)
        digits.startsWith(COUNTRY_CODE) &&
            (hasPlus || (digits.length > COUNTRY_CODE.length && digits[COUNTRY_CODE.length] in "80")) ->
            digits.drop(COUNTRY_CODE.length)
        else -> digits
    }
    return digits.trimStart('0').take(MAX_LOCAL_DIGITS)
}

internal fun isValidIndonesianLocal(local: String): Boolean =
    local.length in MIN_LOCAL_DIGITS..MAX_LOCAL_DIGITS

private fun openDirectChat(context: Context, localNumber: String, message: String, packageName: String): Boolean {
    val uri = Uri.parse("https://wa.me/$COUNTRY_CODE$localNumber").buildUpon().apply {
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
    val valid = isValidIndonesianLocal(number)

    fun open(packageName: String) {
        if (!openDirectChat(context, number, message, packageName)) {
            Toast.makeText(context, "WhatsApp tidak ditemukan atau tidak dapat membuka chat.", Toast.LENGTH_LONG).show()
        }
    }

    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(
                "Kirim pesan ke nomor Indonesia tanpa menyimpan kontak.",
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
            )
            OutlinedTextField(
                value = number,
                onValueChange = { number = normalizeIndonesianLocal(it) },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Nomor WhatsApp") },
                prefix = { Text("+62 ") },
                placeholder = { Text("8123456789") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                isError = number.isNotEmpty() && !valid,
                supportingText = {
                    Text(
                        when {
                            number.isEmpty() -> "Isi tanpa angka 0 di depan, contoh: 8123456789."
                            !valid -> "Belum valid: $MIN_LOCAL_DIGITS-$MAX_LOCAL_DIGITS digit setelah +62."
                            else -> "Siap dikirim ke +62$number"
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
