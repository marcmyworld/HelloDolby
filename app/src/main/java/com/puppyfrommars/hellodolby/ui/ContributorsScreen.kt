/*
 * SPDX-FileCopyrightText: 2026 marcmyworld <moviesminister@gmail.com>
 * SPDX-License-Identifier: Apache-2.0
 */

package com.puppyfrommars.hellodolby.ui

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.Image
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.puppyfrommars.hellodolby.R
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.preference.ArrowPreference
import top.yukonga.miuix.kmp.preference.RadioButtonPreference
import top.yukonga.miuix.kmp.theme.MiuixTheme

@Composable
fun ContributorsScreen(
    padding: PaddingValues,
    themeMode: AppThemeMode,
    onThemeChange: (AppThemeMode) -> Unit
) {
    val context = LocalContext.current
    val scrollState = rememberScrollState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(padding)
            .verticalScroll(scrollState)
            .padding(horizontal = 16.dp, vertical = 8.dp)
    ) {
        // Lead Developer Card
        Text(
            text = stringResource(R.string.lead_developer),
            fontSize = 14.sp,
            fontWeight = FontWeight.Medium,
            color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
            modifier = Modifier.padding(start = 12.dp, top = 8.dp, bottom = 8.dp)
        )

        Card(
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Image(
                    painter = painterResource(R.drawable.avatar_marcmyworld),
                    contentDescription = "marc avatar",
                    modifier = Modifier
                        .size(68.dp)
                        .clip(CircleShape)
                        .border(2.dp, MiuixTheme.colorScheme.primary.copy(alpha = 0.6f), CircleShape)
                )

                Spacer(modifier = Modifier.width(16.dp))

                Column {
                    Text(
                        text = stringResource(R.string.developer_name),
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = MiuixTheme.colorScheme.onSurface
                    )
                    Text(
                        text = stringResource(R.string.developer_handle),
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Medium,
                        color = MiuixTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = stringResource(R.string.developer_bio),
                        fontSize = 12.sp,
                        color = MiuixTheme.colorScheme.onSurfaceVariantSummary
                    )
                }
            }

            ArrowPreference(
                title = stringResource(R.string.github_profile),
                summary = "https://github.com/marcmyworld",
                onClick = {
                    val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com/marcmyworld"))
                    context.startActivity(intent)
                }
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Appearance & Theme Card
        Text(
            text = stringResource(R.string.category_appearance),
            fontSize = 14.sp,
            fontWeight = FontWeight.Medium,
            color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
            modifier = Modifier.padding(start = 12.dp, top = 8.dp, bottom = 8.dp)
        )

        Card(
            modifier = Modifier.fillMaxWidth()
        ) {
            RadioButtonPreference(
                title = stringResource(R.string.theme_mode_system),
                summary = stringResource(R.string.theme_mode_system_summary),
                selected = themeMode == AppThemeMode.SYSTEM,
                onClick = { onThemeChange(AppThemeMode.SYSTEM) }
            )
            RadioButtonPreference(
                title = stringResource(R.string.theme_mode_light),
                summary = stringResource(R.string.theme_mode_light_summary),
                selected = themeMode == AppThemeMode.LIGHT,
                onClick = { onThemeChange(AppThemeMode.LIGHT) }
            )
            RadioButtonPreference(
                title = stringResource(R.string.theme_mode_dark),
                summary = stringResource(R.string.theme_mode_dark_summary),
                selected = themeMode == AppThemeMode.DARK,
                onClick = { onThemeChange(AppThemeMode.DARK) }
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Application Details Card
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
                summary = "${android.os.Build.MANUFACTURER.replaceFirstChar { it.uppercase() }} ${android.os.Build.MODEL} (${android.os.Build.DEVICE})",
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

        // Extra clearance for floating bottom bar
        Spacer(modifier = Modifier.height(100.dp))
    }
}
