package com.tzz1ac0.miroir

import org.junit.Assert.assertEquals
import org.junit.Test

class GreetingTest {
    @Test
    fun defaultGreetingIsHelloWorld() {
        assertEquals("Hello World!", greetingFor())
    }

    @Test
    fun greetingIncludesProvidedName() {
        assertEquals("Hello Ada!", greetingFor("Ada"))
    }

    @Test
    fun greetingSupportsAnEmptyName() {
        assertEquals("Hello !", greetingFor(""))
    }
}
