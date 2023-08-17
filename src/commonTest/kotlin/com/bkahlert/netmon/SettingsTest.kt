package com.bkahlert.netmon

import kotlin.test.Test

expect class SettingsTest {

    @Test
    fun no_user_value_provided()

    @Test
    fun user_value_provided()

    @Test
    fun user_value_of_illegal_type()

    @Test
    fun derived_name()

    @Test
    fun nested_derived_name()
}


open class TestSettings(name: String? = null) : Settings(name) {
    val stringWithNoDefault: String? by setting(name = "foo")
    val stringWithDefault: String by setting("default", name = "foo")
    val intWithNoDefault by setting<Int>(name = "foo")
    val intWithDefault by setting(37, name = "foo")
}
