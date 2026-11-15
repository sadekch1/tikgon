package com.example.tikgon.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.tikgon.ChatEntry
import com.example.tikgon.Prefs
import com.example.tikgon.Repo

/**
 * اختيار محادثة البوت التي سيراقبها TikGon.
 * القائمة تأتي من المحرك (أحداث المحادثات/الرسائل عند Baileys)،
 * مع إدخال يدوي احتياطي برقم البوت أو JID كامل.
 */
@Composable
fun ChatPickerScreen() {
    val chats by Repo.chats.collectAsState()
    val conn by Repo.conn.collectAsState()
    var manual by remember { mutableStateOf("") }

    LaunchedEffect(conn) {
        if (conn == "open") Repo.requestChats()
    }

    Column(Modifier.fillMaxSize().systemBarsPadding().padding(20.dp)) {
        PageHeader("اختر محادثة البوت")
        Spacer(Modifier.height(8.dp))
        SubText("TikGon سيراقب هذه المحادثة فقط: كل فيديو يرسله البوت فيها يُنزَّل ويظهر في الخلاصة.")
        Spacer(Modifier.height(14.dp))

        OutlinedButton(
            onClick = { Repo.requestChats() },
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(14.dp)
        ) { Text("🔄 تحديث القائمة") }
        Spacer(Modifier.height(10.dp))

        if (chats.isEmpty()) {
            AppCard {
                SectionTitle("القائمة فارغة حالياً")
                Spacer(Modifier.height(4.dp))
                SubText(
                    "تظهر المحادثات هنا بعد أن تصل رسائل أثناء اتصال المحرك. " +
                        "افتح محادثة البوت في واتساب وانتظر لحظة، أو أدخل رقم البوت يدوياً بالأسفل."
                )
                Spacer(Modifier.height(4.dp))
                SubText("الحالة: " + connText(conn))
            }
            Spacer(Modifier.height(12.dp))
        } else {
            LazyColumn(Modifier.weight(1f)) {
                items(chats, key = { it.jid }) { c -> ChatRow(c) }
            }
        }

        AppCard {
            SectionTitle("إدخال يدوي")
            Spacer(Modifier.height(4.dp))
            SubText("رقم البوت مع رمز الدولة (يُبنى منه JID)، أو JID كامل مثل 1234567890@s.whatsapp.net أو معرف مجموعة …@g.us")
            Spacer(Modifier.height(10.dp))
            OutlinedTextField(
                value = manual,
                onValueChange = { manual = it },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("رقم البوت أو JID") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Text),
                shape = RoundedCornerShape(14.dp)
            )
            Spacer(Modifier.height(10.dp))
            Button(
                onClick = {
                    val raw = manual.trim()
                    val jid = if (raw.contains("@")) raw
                    else raw.filter { it.isDigit() }.let { if (it.isEmpty()) "" else "$it@s.whatsapp.net" }
                    if (jid.isNotEmpty()) pickChat(jid, jid.substringBefore("@"))
                },
                enabled = manual.isNotBlank(),
                modifier = Modifier.fillMaxWidth().height(48.dp),
                shape = RoundedCornerShape(14.dp)
            ) { Text("استخدام هذا", fontWeight = FontWeight.Bold) }
        }
    }
}

@Composable
private fun ChatRow(c: ChatEntry) {
    AppCard(Modifier.padding(bottom = 10.dp).clickable { pickChat(c.jid, c.name) }) {
        Text(c.name, fontSize = 16.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
        SubText(c.jid)
        if (c.preview.isNotEmpty()) {
            Spacer(Modifier.height(2.dp))
            SubText(c.preview.take(80))
        }
        if (c.ts > 0) SubText(ago(c.ts * 1000))
    }
}

private fun pickChat(jid: String, name: String) {
    Prefs.set(Prefs.chatName, "chatName", name)
    Prefs.set(Prefs.chatJid, "chatJid", jid)   // set() يدفع الإعداد للمحرك
    Repo.pushConfig()
}
