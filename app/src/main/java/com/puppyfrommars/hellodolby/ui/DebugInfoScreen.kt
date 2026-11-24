/*
 * SPDX-FileCopyrightText: 2026 marcmyworld <moviesminister@gmail.com>
 * SPDX-License-Identifier: Apache-2.0
 */

package com.puppyfrommars.hellodolby.ui

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Process
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.puppyfrommars.hellodolby.R
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.preference.ArrowPreference
import top.yukonga.miuix.kmp.theme.MiuixTheme
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun DebugInfoScreen(padding: PaddingValues) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val scrollState = rememberScrollState()

    val saveLogsLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("text/plain")
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        coroutineScope.launch {
            val saved = withContext(Dispatchers.IO) {
                runCatching {
                    val logs = collectAppLogs(context.packageName)
                    context.contentResolver.openOutputStream(uri)?.bufferedWriter()?.use { writer ->
                        writer.write(logs)
                    } ?: error("Unable to open output stream")
                }.isSuccess
            }
            val msgRes = if (saved) R.string.save_logs_success else R.string.save_logs_failed
            Toast.makeText(context, context.getString(msgRes), Toast.LENGTH_SHORT).show()
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(padding)
            .verticalScroll(scrollState)
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        // Top: Save Logs
        Column(modifier = Modifier.fillMaxWidth()) {
            Text(
                text = stringResource(R.string.category_logs),
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium,
                color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                modifier = Modifier.padding(start = 12.dp, top = 8.dp, bottom = 8.dp)
            )

            Card(
                modifier = Modifier.fillMaxWidth()
            ) {
                ArrowPreference(
                    title = stringResource(R.string.save_logs_title),
                    summary = stringResource(R.string.save_logs_summary),
                    onClick = {
                        val stamp = SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US).format(Date())
                        saveLogsLauncher.launch("hellodolby-logs-$stamp.txt")
                    }
                )
            }
        }

        // Bottom: Project Information
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 24.dp, bottom = 16.dp)
        ) {
            Text(
                text = stringResource(R.string.project_info),
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium,
                color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                modifier = Modifier.padding(start = 12.dp, top = 8.dp, bottom = 8.dp)
            )

            Card(
                modifier = Modifier.fillMaxWidth()
            ) {
                ArrowPreference(
                    title = stringResource(R.string.package_name_label),
                    summary = stringResource(R.string.package_name_value),
                    onClick = {}
                )
                ArrowPreference(
                    title = stringResource(R.string.app_version_label),
                    summary = stringResource(R.string.app_version_value),
                    onClick = {}
                )
                ArrowPreference(
                    title = stringResource(R.string.device_info_label),
                    summary = "${Build.MANUFACTURER.replaceFirstChar { it.uppercase() }} ${Build.MODEL} (${Build.DEVICE})",
                    onClick = {}
                )
                ArrowPreference(
                    title = stringResource(R.string.github_repo_label),
                    summary = "https://github.com/marcmyworld/HelloDolby",
                    onClick = {
                        val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com/marcmyworld/HelloDolby"))
                        context.startActivity(intent)
                    }
                )
                ArrowPreference(
                    title = stringResource(R.string.design_system_label),
                    summary = stringResource(R.string.design_system_value),
                    onClick = {
                        val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com/compose-miuix-ui/miuix"))
                        context.startActivity(intent)
                    }
                )
                ArrowPreference(
                    title = stringResource(R.string.license_label),
                    summary = stringResource(R.string.license_value),
                    onClick = {
                        val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://www.apache.org/licenses/LICENSE-2.0"))
                        context.startActivity(intent)
                    }
                )
            }

            Spacer(modifier = Modifier.height(8.dp))
        }
    }
}

private fun collectAppLogs(packageName: String): String {
    val header = buildString {
        appendLine("=== HelloDolby Debug Logs ===")
        appendLine("Timestamp: ${SimpleDateFormat("yyyy-MM-dd HH:mm:ss z", Locale.US).format(Date())}")
        appendLine("Package: $packageName")
        appendLine("Device: ${Build.MANUFACTURER} ${Build.MODEL} (${Build.DEVICE})")
        appendLine("Android SDK: ${Build.VERSION.SDK_INT} (${Build.VERSION.RELEASE})")
        appendLine("Process PID: ${Process.myPid()}")
        appendLine()
    }
    val logBody = runCatching {
        val process = ProcessBuilder("logcat", "-d", "--pid=${Process.myPid()}")
            .redirectErrorStream(true)
            .start()
        process.inputStream.bufferedReader().use { it.readText() }
    }.getOrElse { err ->
        "Failed to collect logcat for PID ${Process.myPid()}: ${err.message}"
    }
    return header + logBody
}
