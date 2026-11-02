// SPDX-License-Identifier: GPL-3.0-or-later
package xtr.keymapper.mythic

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import xtr.keymapper.controller.HeroGameplayConfig
import xtr.keymapper.keymap.KeymapProfiles
import xtr.keymapper.keymap.ProfileConfiguration
import xtr.keymapper.structured.StructuredProfiles

/** Configure explicitly while root mapping is exclusively paused. Never launches the game. */
class HeroTrainingActivity:ComponentActivity(){
    private lateinit var lease:RootDiagnosticLease
    private var safe by mutableStateOf(false)
    private var status by mutableStateOf("Acquiring exclusive setup mode…")
    override fun onCreate(savedInstanceState:Bundle?){
        super.onCreate(savedInstanceState)
        lease=RootDiagnosticLease(this){ready,message->safe=ready;status=message}
        val id=intent.getStringExtra("profile")?:""
        val saved=KeymapProfiles(this).sharedPref.getStringSet(id,null)?.toSet()
        val parsed=runCatching{requireNotNull(saved);KeymapProfiles.getProfile(StructuredProfiles.runtime(saved))}
        setContent{MaterialTheme(colorScheme=darkColorScheme()) {Surface(Modifier.fillMaxSize()){
            val p=parsed.getOrNull();val initial=p?.heroGameplay
            if(p==null||initial==null)Column(Modifier.safeDrawingPadding().padding(20.dp)){
                Text("Clone the Beatrix or Kimmy training template first.");Text(parsed.exceptionOrNull()?.message?:"");TextButton(onClick={finish()}){Text("Back")}
            }else{
                var expected by remember{mutableStateOf(saved!!)}
                var lines by remember{mutableStateOf(StructuredProfiles.runtime(saved!!).filterNot{it=="ENABLED"||it.startsWith("HERO_GAMEPLAY ")}.sorted())}
                var weapon by remember{mutableStateOf(initial.weapon)}
                var fire by remember{mutableStateOf(initial.fire)}
                var cadence by remember{mutableStateOf(if(initial.repeatMs==0)"150"else initial.repeatMs.toString())}
                var confirmed by remember{mutableStateOf(false)}
                var assign by remember{mutableStateOf(false)}
                var message by remember{mutableStateOf("")}
                Column(Modifier.fillMaxSize().safeDrawingPadding().padding(20.dp).verticalScroll(rememberScrollState()),verticalArrangement=Arrangement.spacedBy(12.dp)){
                    Text("${initial.hero.name.lowercase().replaceFirstChar{it.uppercase()}} training setup",style=MaterialTheme.typography.headlineSmall)
                    Text(status)
                    Text("Unverified training profile. Shared S10e anchors came from Dyrroth's 2280 × 1080 HUD. Attack drag radius, hero-specific HUD positions, current skill behavior and weapon firing need your calibration and Hero Training check.")
                    Text("X Recall · A Regen · B/L3 one shared Battle Spell · RB Skill 1 · RT Skill 2 · LB Ultimate. Left stick stays native.")
                    if(initial.hero==HeroGameplayConfig.Hero.BEATRIX){
                        Text("Right stick aims Basic Attack; LT controls firing. Select the actual weapon manually; game weapon swaps are not detected.")
                        for(w in HeroGameplayConfig.Weapon.entries.filter{it!=HeroGameplayConfig.Weapon.NONE})Row{
                            RadioButton(weapon==w,{weapon=w;confirmed=false});Text(w.name)
                        }
                        Row{RadioButton(fire==HeroGameplayConfig.Fire.HOLD_RELEASE,{fire=HeroGameplayConfig.Fire.HOLD_RELEASE;confirmed=false});Text("Native hold / release gesture")}
                        Row{RadioButton(fire==HeroGameplayConfig.Fire.PULSE,{fire=HeroGameplayConfig.Fire.PULSE;confirmed=false});Text("Repeat aimed shots only while LT is physically held")}
                        if(fire==HeroGameplayConfig.Fire.PULSE)OutlinedTextField(cadence,{cadence=it;confirmed=false},label={Text("Shot cycle ms · 150 is historical LT tuning, not weapon-verified")})
                        Text("Hold/release lets the game own native weapon timing, reload and charge. Repeating mode is an explicit manual LT option for weapons needing repeated releases; it pauses for skills and stops on release, profile changes or disconnect. Stop and reconfigure after changing weapons if the policy differs.")
                    }else Text("Right-stick displacement shoots; center stops. LT is unused. Held directional skills/spell take priority, then shooting resumes if the stick is still displaced. Recall requires centering before shooting rearms. Verify the installed Kimmy version still supports directional Basic Attack.")
                    Text("Marker coordinates in the saved landscape plane. Edit with Visual editor / Calibrate, or adjust values below. Proposed 80 px aim radii are unverified for these heroes.")
                    lines.forEachIndexed{i,line->
                        val fields=line.split(' ')
                        if(fields[0].startsWith("BTN_")||fields[0]=="STICK_AIM"){
                            val aim=fields[0]=="STICK_AIM"
                            Text(if(aim)when(fields.getOrNull(1)){"BTN_TL2"->"Basic Attack / directional shooting";"BTN_EAST"->"B / L3 Battle Spell";"BTN_TR"->"RB Skill 1";"BTN_TR2"->"RT Skill 2";else->"LB Ultimate"}else if(fields[0]=="BTN_WEST")"X Recall"else"A Regen")
                            Row(horizontalArrangement=Arrangement.spacedBy(8.dp)){
                                for((offset,label)in (if(aim)listOf("X","Y","Aim radius")else listOf("X","Y")).withIndex()){
                                    val field=offset+if(aim)2 else 1
                                    OutlinedTextField(fields[field],{value->
                                        val changed=lines[i].split(' ').toMutableList();changed[field]=value
                                        lines=lines.toMutableList().also{it[i]=changed.joinToString(" ")};confirmed=false
                                    },Modifier.weight(1f),label={Text(label)},singleLine=true)
                                }
                            }
                        }
                    }
                    Row{Checkbox(confirmed,{confirmed=it});Text("These coordinates match my HUD. Allow this unverified touch policy for a manual training test.")}
                    Row{Checkbox(assign,{assign=it});Text("Assign favorite ${if(initial.hero==HeroGameplayConfig.Hero.BEATRIX)1 else 2} only if empty (opt-in)")}
                    Button(enabled=safe&&confirmed,onClick={try{
                        val config="HERO_GAMEPLAY ${initial.hero} $weapon $fire ${if(fire==HeroGameplayConfig.Fire.PULSE)cadence else "0"} 1"
                        val next=ProfileConfiguration.validate((lines+config+"ENABLED").joinToString("\n"))
                        HeroTrainingProfiles.save(this@HeroTrainingActivity,id,expected,next)
                        expected=KeymapProfiles(this@HeroTrainingActivity).sharedPref.getStringSet(id,null)!!.toSet()
                        val favorite=if(assign)runCatching{HeroTrainingProfiles.assignRecommended(this@HeroTrainingActivity,id)}.fold({" Favorite assigned."},{" Favorite unchanged: ${it.message}."})else" Favorites unchanged."
                        message="Training profile saved.$favorite Mapping remains stopped. Return Home and explicitly Use this profile after manually entering Hero Training."
                    }catch(e:Exception){message=e.message?:"Save failed"}}){Text("Save calibrated training profile")}
                    Text(message)
                    Text("Rollback: stop mapping or open Controller Tester, then choose your previous profile. Templates never replace existing mappings or occupied favorites. This screen never enters a match or injects touches.")
                    TextButton(onClick={finish()}){Text("Back · keep mapping stopped")}
                }
            }
        }}}
    }
    override fun onStart(){super.onStart();lease.start()}
    override fun onStop(){lease.stop();super.onStop()}
    override fun onDestroy(){lease.close();super.onDestroy()}
}
