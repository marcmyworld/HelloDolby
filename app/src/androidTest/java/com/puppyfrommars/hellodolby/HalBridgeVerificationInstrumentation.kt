/*
 * SPDX-FileCopyrightText: 2026 marcmyworld <moviesminister@gmail.com>
 * SPDX-License-Identifier: Apache-2.0
 */

package com.puppyfrommars.hellodolby

import android.app.Activity
import android.app.Instrumentation
import android.os.Bundle
import android.provider.Settings
import com.puppyfrommars.hellodolby.audio.AudioEffectManager
import com.puppyfrommars.hellodolby.audio.DmsBinderClient
import com.puppyfrommars.hellodolby.audio.DolbyProfile
import com.puppyfrommars.hellodolby.audio.EffectMode
import com.puppyfrommars.hellodolby.audio.IeqPreset
import com.puppyfrommars.hellodolby.data.autoeq.AutoEqRepository
import kotlinx.coroutines.runBlocking

/**
 * Live on-device HAL, AudioEffect, IDms, MiSound, Direct DAC, and AutoEQ verification runner.
 */
class HalBridgeVerificationInstrumentation : Instrumentation() {

    override fun onCreate(arguments: Bundle?) {
        super.onCreate(arguments)
        start()
    }

    override fun onStart() {
        super.onStart()
        val results = Bundle()
        val report = StringBuilder()
        var passed = true

        fun check(name: String, condition: Boolean, detail: String = "") {
            if (!condition) {
                passed = false
            }
            report.appendLine("${if (condition) "PASS" else "FAIL"}: $name${if (detail.isNotEmpty()) " -> $detail" else ""}")
        }

        fun getProp(name: String): String = runCatching {
            ProcessBuilder("getprop", name).start().inputStream.bufferedReader().use { it.readText().trim() }
        }.getOrElse { "" }

        runCatching {
            val ctx = targetContext
            val mgr = AudioEffectManager.getInstance(ctx)
            val dms = DmsBinderClient()
            Thread.sleep(250)

            // 1. Initial IDms + AudioEffect availability
            check("IDms binder available", dms.isAvailable(), "version=${dms.getInterfaceVersion()}")
            check("IDms GADV non-null", dms.getActiveDevice() != null, "GADV=${dms.getActiveDevice()}")
            val initialProbe = mgr.verifyHalStatus()
            check("dap_hw attached", initialProbe.contains("dap_hw: OK"))
            check("misoundfx attached", initialProbe.contains("misoundfx: OK"))

            // 2. EffectMode.MISOUND
            mgr.setEffectMode(EffectMode.MISOUND)
            Thread.sleep(200)
            val misoundDapo = dms.getIntParam(DmsBinderClient.INT_PARAM_DAP_ON)
            val misoundImpl = Settings.Global.getString(ctx.contentResolver, "effect_implementer")
            val misoundDisableProp = getProp("persist.vendor.audio.misound.disable")
            val misoundProbe = mgr.verifyHalStatus()
            check("MISOUND IDms DAPO==0", misoundDapo == 0L, "DAPO=$misoundDapo")
            check("MISOUND prop false", misoundDisableProp == "false", "persist.vendor.audio.misound.disable=$misoundDisableProp")
            check("MISOUND effect_implementer", misoundImpl == "misound", "impl=$misoundImpl")
            check("MISOUND dap_hw off & misoundfx on",
                misoundProbe.contains("dap_hw: OK") && misoundProbe.contains("enabled=false") && misoundProbe.contains("misoundfx: OK"))

            // MiSound EQ parameter write via setParams(2)
            mgr.setEqualizerEnabled(true)
            mgr.setEqualizerBand(0, 4.0f)
            mgr.setEqualizerBand(9, -3.5f)
            Thread.sleep(150)
            val miSoundEqProp = getProp("persist.vendor.audio.sfx.hd.eq")
            check("MISOUND EQ updated prop", miSoundEqProp.isNotEmpty() && miSoundEqProp != "0.0,0.0,0.0,0.0,0.0,0.0,0.0,0.0,0.0,0.0",
                "persist.vendor.audio.sfx.hd.eq=$miSoundEqProp")

            // MiSound Immersive Sound must remain disabled in MiSound mode
            mgr.setSpatialAudioEnabled(true)
            mgr.setSurroundStrength(750)
            Thread.sleep(200)
            val mi3dProp = getProp("persist.vendor.audio.3dsurround.enable")
            val miProbe = mgr.verifyHalStatus()
            check("MISOUND spatial disabled",
                mi3dProp != "true" && mi3dProp != "1" &&
                    miProbe.lineSequence().any { it.startsWith("aosp_virtualizer:") && it.contains("enabled=false") },
                "3dsurround=$mi3dProp")

            // 3. EffectMode.OFF (Direct DAC)
            mgr.setEffectMode(EffectMode.OFF)
            Thread.sleep(200)
            val offDapo = dms.getIntParam(DmsBinderClient.INT_PARAM_DAP_ON)
            val offImpl = Settings.Global.getString(ctx.contentResolver, "effect_implementer")
            val offDisableProp = getProp("persist.vendor.audio.misound.disable")
            val offProbe = mgr.verifyHalStatus()
            check("OFF IDms DAPO==0", offDapo == 0L, "DAPO=$offDapo")
            check("OFF misound disabled", offDisableProp == "true", "persist.vendor.audio.misound.disable=$offDisableProp")
            check("OFF effect_implementer==none", offImpl == "none", "impl=$offImpl")
            check("OFF virtualizer disabled",
                offProbe.lineSequence().any { it.startsWith("aosp_virtualizer:") && it.contains("enabled=false") },
                offProbe.lineSequence().firstOrNull { it.startsWith("aosp_virtualizer:") } ?: "missing")

            // 4. EffectMode.DOLBY + Profiles
            mgr.setEffectMode(EffectMode.DOLBY)
            Thread.sleep(200)
            val dolbyDapo = dms.getIntParam(DmsBinderClient.INT_PARAM_DAP_ON)
            val dolbyImpl = Settings.Global.getString(ctx.contentResolver, "effect_implementer")
            check("DOLBY IDms DAPO==1", dolbyDapo == 1L, "DAPO=$dolbyDapo")
            check("DOLBY effect_implementer==dolby", dolbyImpl == "dolby", "impl=$dolbyImpl")

            for (profile in listOf(
                DolbyProfile.MOVIE, DolbyProfile.MUSIC, DolbyProfile.GAME,
                DolbyProfile.CUSTOM, DolbyProfile.VOICE, DolbyProfile.DYNAMIC
            )) {
                mgr.setDolbyProfile(profile)
                Thread.sleep(250)
                val activeProfile = dms.getSelectedProfile()
                check("DOLBY profile ${profile.name}", activeProfile == profile.id, "expected=${profile.id}, GDAP=$activeProfile")
            }

            // 5. Dolby EQ presets, IEQ mutual exclusion, Advanced DSP controls & Virtualizer
            mgr.setDolbyProfile(DolbyProfile.CUSTOM)
            check("dolbyProfileState updated", mgr.dolbyProfileState.value == DolbyProfile.CUSTOM)
            mgr.setEqualizerPreset("Rock")
            check("DOLBY EQ enables cleanly", mgr.equalizerEnabledState.value && mgr.ieqPresetState.value == IeqPreset.OFF)
            mgr.setIeqPreset(IeqPreset.WARM)
            mgr.setIeqAmount(80)
            Thread.sleep(150)
            val ieqReadBack = dms.getProfileIntParam(DolbyProfile.CUSTOM.id, DmsBinderClient.DAP_PARAM_IEQ_ID)
            check("IEQ disables Custom EQ & writes HAL",
                mgr.ieqPresetState.value == IeqPreset.WARM && !mgr.equalizerEnabledState.value && ieqReadBack == IeqPreset.WARM.id,
                "ieq=${mgr.ieqPresetState.value}, eq=${mgr.equalizerEnabledState.value}, GDAP_IEQ=$ieqReadBack")
            mgr.setEqualizerEnabled(true)
            Thread.sleep(150)
            check("Enabling Custom EQ clears IEQ", mgr.equalizerEnabledState.value && mgr.ieqPresetState.value == IeqPreset.OFF)

            mgr.setBassEnhancerEnabled(true)
            mgr.setMidEnhancerEnabled(true)
            mgr.setMidEnhancerLevel(75)
            mgr.setTrebleEnhancerEnabled(true)
            mgr.setTrebleEnhancerLevel(80)
            mgr.setDialogueEnhancerEnabled(true)
            mgr.setDialogueEnhancerLevel(8)
            mgr.setDialogueDucking(50)
            mgr.setVolumeLevelerEnabled(true)
            mgr.setVolumeLevelerLevel(5)
            mgr.setVirtualBassEnabled(true)
            mgr.setReverbSuppressionEnabled(true)
            mgr.setReverbSuppressionAmount(75)
            mgr.setLoudnessBoostEnabled(true)
            mgr.setLoudnessBoostDb(6)
            mgr.setSpeakerTuningDevice("medium_mode_speaker")
            mgr.setSpatialAudioEnabled(true)
            mgr.setSurroundStrength(750)
            Thread.sleep(200)
            val virtProbe = mgr.verifyHalStatus()
            val selectedTuning = dms.getSelectedTuningDevice(0)
            val vbParam = dms.getProfileIntParam(DolbyProfile.CUSTOM.id, DmsBinderClient.DAP_PARAM_VIRTUAL_BASS_ENABLE)
            val revParam = dms.getProfileIntParam(DolbyProfile.CUSTOM.id, DmsBinderClient.DAP_PARAM_REVERB_SUPPRESSION_ENABLE)
            val geqEnableAfterLoudness = dms.getProfileIntParam(DolbyProfile.CUSTOM.id, DmsBinderClient.DAP_PARAM_GRAPHIC_EQUALIZER_ENABLE)
            check("DOLBY EQ state active", mgr.equalizerEnabledState.value && mgr.equalizerPresetState.value == "Rock")
            check("DOLBY mid & treble enhancers",
                mgr.midEnhancerEnabledState.value && mgr.midEnhancerLevelState.value == 75 &&
                    mgr.trebleEnhancerEnabledState.value && mgr.trebleEnhancerLevelState.value == 80)
            check("Advanced HAL controls active",
                vbParam == 1 && revParam == 1 && geqEnableAfterLoudness == 1 &&
                    selectedTuning == "medium_mode_speaker" &&
                    virtProbe.contains("aosp_loudness: OK (enabled=true, db=6)"),
                "vb=$vbParam, rev=$revParam, geq=$geqEnableAfterLoudness, tuning=$selectedTuning")
            check("Dolby virtualizer & Game-safe session",
                !virtProbe.contains("gamedap: OK") && mgr.surroundStrengthState.value == 750)

            // Verify Loudness Boost drives QDSP GEQ gain even when EQ & tonal enhancers are off
            mgr.setEqualizerEnabled(false)
            mgr.setBassEnhancerEnabled(false)
            mgr.setMidEnhancerEnabled(false)
            mgr.setTrebleEnhancerEnabled(false)
            Thread.sleep(200)
            val geqFromLoudnessOnly = dms.getProfileIntParam(DolbyProfile.CUSTOM.id, DmsBinderClient.DAP_PARAM_GRAPHIC_EQUALIZER_ENABLE)
            val dedParam = dms.getProfileIntParam(DolbyProfile.CUSTOM.id, DmsBinderClient.DAP_PARAM_DIALOG_ENHANCER_DUCKING)
            check("Loudness Boost drives QDSP GEQ independently & ducking stored",
                geqFromLoudnessOnly == 1 && dedParam == 8,
                "geq=$geqFromLoudnessOnly, ded=$dedParam")

            mgr.saveCustomPreset("VerifyPreset", listOf(2f, 1f, 0f, -1f, 0f, 1f, 2f, 1f, 0f, 0f))
            check("Custom preset saved", mgr.customPresetsState.value.containsKey("VerifyPreset"))
            mgr.renameCustomPreset("VerifyPreset", "VerifyPreset2")
            check("Custom preset renamed", mgr.customPresetsState.value.containsKey("VerifyPreset2"))
            mgr.deleteCustomPreset("VerifyPreset2")
            check("Custom preset deleted", !mgr.customPresetsState.value.containsKey("VerifyPreset2"))

            // 6. AutoEQ repository real catalog + curve parsing + remote/cache fetch
            runBlocking {
                val repo = AutoEqRepository(ctx)
                val index = repo.getIndex()
                check("AutoEQ index loaded", index.size > 1000, "entries=${index.size}")
                val matches = repo.search("Sony WH-1000XM5", 10)
                check("AutoEQ search Sony WH-1000XM5", matches.isNotEmpty(), "top=${matches.firstOrNull()?.name}")
                val first = matches.firstOrNull()
                if (first != null) {
                    val profile = repo.fetchProfile(first.id)
                    val bands = profile?.let { repo.parseGraphicEqTo10Bands(it.graphicEq) }
                    check("AutoEQ profile fetch & 10-band parse", bands?.size == 10 && bands.any { it != 0f },
                        "bands=$bands")
                }
            }

            // 7. Device-specific snapshot reset & deletion + restore clean defaults
            val activeKey = mgr.activeDeviceKeyState.value
            mgr.resetDeviceDefaults(activeKey)
            Thread.sleep(300)
            check("Final restore clean",
                mgr.getEffectMode() == EffectMode.DOLBY &&
                    mgr.getDolbyProfile() == DolbyProfile.DYNAMIC &&
                    mgr.dolbyProfileState.value == DolbyProfile.DYNAMIC &&
                    mgr.surroundStrengthState.value == 500 &&
                    !mgr.equalizerEnabledState.value &&
                    mgr.savedDeviceSnapshotsState.value.any { it.deviceKey == activeKey },
                "mode=${mgr.getEffectMode()}, profile=${mgr.getDolbyProfile()}, eq=${mgr.equalizerEnabledState.value}")

            report.appendLine()
            report.append(mgr.verifyHalStatus())
        }.onFailure { err ->
            passed = false
            report.appendLine("EXCEPTION: ${err.javaClass.simpleName}: ${err.message}")
        }

        results.putString("verification_report", report.toString())
        finish(if (passed) Activity.RESULT_OK else Activity.RESULT_CANCELED, results)
    }
}
