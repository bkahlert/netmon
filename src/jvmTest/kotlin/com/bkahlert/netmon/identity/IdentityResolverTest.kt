package com.bkahlert.netmon.identity

import com.bkahlert.netmon.Host
import com.bkahlert.netmon.Kind
import com.bkahlert.netmon.Link
import com.bkahlert.netmon.LinkSpeed
import com.bkahlert.netmon.invoke
import io.kotest.matchers.should
import io.kotest.matchers.shouldBe
import kotlin.test.Test

class IdentityResolverTest {

    @Test
    fun each_field_takes_the_first_clue_in_its_source_order() {
        val clues = listOf(
            Clue.Name("unicorn", Source.ROUTER),
            Clue.Name("Stilgar", Source.PROTOCOL),
            Clue.Model("AirPort4", Source.APPLE_CODE),
            Clue.Model("Raspberry Pi Zero 2 W Rev 1.0", Source.PROTOCOL),
            Clue.Vendor("Raspberry Pi Ltd", Source.PROTOCOL),
            Clue.Vendor("Raspberry Pi", Source.OUI),
            Clue.DeviceKind(Kind.ROUTER, Source.ROUTER),
            Clue.DeviceKind(Kind.CIRCUIT_BOARD, Source.PROTOCOL),
        )

        val result = IdentityResolver().resolve(empty(), clues)

        result should {
            it.name shouldBe "Stilgar"
            it.model shouldBe "Raspberry Pi Zero 2 W Rev 1.0"
            it.vendor shouldBe "Raspberry Pi"
            it.kind shouldBe Kind.CIRCUIT_BOARD
        }
    }

    @Test
    fun within_one_source_the_first_clue_wins() {
        val clues = listOf(Clue.Model("Fire TV Stick 4K", Source.PROTOCOL), Clue.Model("AirReceiver", Source.PROTOCOL))

        val result = IdentityResolver().resolve(empty(), clues)

        result.model shouldBe "Fire TV Stick 4K"
    }

    @Test
    fun the_user_name_outranks_every_other_name() {
        val clues = listOf(Clue.Name("Paul", Source.PROTOCOL), Clue.Name("Paul (Wi-Fi)", Source.USER), Clue.Name("paul-wi-fi", Source.ROUTER))

        val result = IdentityResolver().resolve(empty(), clues)

        result.name shouldBe "Paul (Wi-Fi)"
    }

    @Test
    fun a_filled_field_is_kept() {
        val host = empty().copy(model = "iPad8,3")

        val result = IdentityResolver().resolve(host, listOf(Clue.Model("Other", Source.PROTOCOL)))

        result.model shouldBe "iPad8,3"
    }

    @Test
    fun link_speed_and_mac_come_from_the_router_only() {
        val clues = listOf(
            Clue.Attachment(Link.WIFI, Source.PROTOCOL),
            Clue.Speed(LinkSpeed(72), Source.ROUTER),
            Clue.Mac("aa:bb:cc:dd:ee:ff", Source.ROUTER),
        )

        val result = IdentityResolver().resolve(empty(), clues)

        result should {
            it.link shouldBe null
            it.speed shouldBe LinkSpeed(72)
            it.mac shouldBe "aa:bb:cc:dd:ee:ff"
        }
    }

    @Test
    fun a_host_without_kind_clues_is_generic() {
        val result = IdentityResolver().resolve(empty(), emptyList())

        result.kind shouldBe Kind.GENERIC
    }

    @Test
    fun the_oui_vendor_outranks_a_protocol_vendor_and_the_token_outranks_both() {
        val clues = listOf(Clue.Vendor("SoftMedia", Source.PROTOCOL), Clue.Vendor("Amazon", Source.OUI))

        IdentityResolver().resolve(empty(), clues).vendor shouldBe "Amazon"
        IdentityResolver().resolve(empty(), clues + Clue.Vendor("Ledvance", Source.NAME_TOKEN)).vendor shouldBe "Ledvance"
    }
}

private fun empty() = Host(name = null, model = null, vendor = null, services = null)
