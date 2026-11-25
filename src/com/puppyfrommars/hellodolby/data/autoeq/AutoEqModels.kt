/*
 * SPDX-FileCopyrightText: 2026 marcmyworld <moviesminister@gmail.com>
 * SPDX-License-Identifier: Apache-2.0
 */

package com.puppyfrommars.hellodolby.data.autoeq

data class AutoEqIndexEntry(
    val id: String,
    val name: String,
    val source: String,
    val measurementRig: String,
    val searchKey: String
)

data class AutoEqProfile(
    val id: String,
    val name: String,
    val graphicEq: String
)
