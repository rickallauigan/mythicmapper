// SPDX-License-Identifier: GPL-3.0-or-later
package xtr.keymapper.tester

import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class) @Config(sdk=[34],manifest=Config.NONE)
class HardwareEvidencePersistenceTest {
    @Test fun observedLifecycleRoundTripsAndFutureSchemaIsRejectedWithoutMutation(){
        val source=HardwareEvidence();source.record("A","Android KEY_ACTION",96,0f,true,false,10);source.record("A","Android KEY_ACTION",96,1f,false,true,20)
        val restored=HardwareEvidence();restored.restore(JSONObject(source.encode().toString()))
        assertEquals(source.snapshot().controls,restored.snapshot().controls)
        val before=restored.snapshot()
        assertThrows(IllegalArgumentException::class.java){restored.restore(source.encode().put("schema",99))}
        assertEquals(before,restored.snapshot())
    }
    @Test fun malformedCountsCannotBecomePairedEvidence(){
        val source=HardwareEvidence();source.record("A","Android KEY_ACTION",96,0f,true,false,10)
        val json=source.encode();json.getJSONArray("controls").getJSONObject(0).put("pairs",2)
        assertThrows(IllegalArgumentException::class.java){HardwareEvidence().restore(json)}
    }
}
