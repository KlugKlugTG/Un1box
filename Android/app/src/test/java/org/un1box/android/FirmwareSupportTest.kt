package org.un1box.android

import org.junit.Assert.*
import org.junit.Test

class FirmwareSupportTest {
    private fun firmware(id: String, status: String = "experimental") = id.split('-').let { (board, build) ->
        CatalogFirmware(id, board, "", "", build, "", "", "", 0L, emptyList(), status)
    }

    @Test fun labelsFollowTheCompatibilityRuns() {
        for (id in listOf("n72ap-7E18", "n18ap-8C148", "n81ap-10B500")) {
            assertEquals(id, Support.TESTED, firmware(id).support)
        }
        // Un1box's own runs on a phone: these work, but slower than the iPod touch 2G on iOS 3.1.3.
        for (id in listOf("n88ap-9B206", "n90ap-8C148", "n90ap-8L1")) {
            assertEquals(id, Support.TESTED_SLOW, firmware(id).support)
        }
        assertEquals(Support.EXPERIMENTAL, firmware("n90ap-11D257").support)
        assertEquals(Support.UNTESTED, firmware("n90ap-10A403", status = "untested").support)
        assertTrue(Support.TESTED.tested); assertTrue(Support.TESTED_SLOW.tested)
        assertFalse(Support.DOESNT_START.tested)
    }

    @Test fun theS5L8900BoardsDontStartWhateverTheCatalogSays() {
        // The tier belongs to the board, and wins over the catalog's own label for it.
        assertEquals(Support.DOESNT_START, firmware("n45ap-4B1").support)
        assertEquals(Support.DOESNT_START, firmware("m68ap-1A543a", status = "untested").support)
        assertEquals(Support.DOESNT_START, firmware("m68ap-1C25", status = "available").support)
    }

    @Test fun theWorstTierIsLastSoADeviceRowShowsItsBest() {
        // PrepareScreen badges a device with minOf over its firmwares, which compares the tiers by order.
        assertTrue(Support.DOESNT_START.ordinal > Support.UNTESTED.ordinal)
        assertTrue(Support.UNTESTED.ordinal > Support.EXPERIMENTAL.ordinal)
        assertTrue(Support.TESTED.ordinal < Support.TESTED_SLOW.ordinal)
    }
}
