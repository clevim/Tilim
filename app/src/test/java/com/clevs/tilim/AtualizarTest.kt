package com.clevs.tilim

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AtualizarTest {
    @Test fun comparaVersoes() {
        assertTrue(maisNova("v1.6.0", "1.5.0"))
        assertTrue(maisNova("v1.10.0", "1.9.2")) // numérico, não texto
        assertTrue(maisNova("v2", "1.9.9"))
        assertFalse(maisNova("v1.5.0", "1.5.0"))
        assertFalse(maisNova("v1.5", "1.5.0"))
        assertFalse(maisNova("v1.4.9", "1.5.0"))
    }
}
