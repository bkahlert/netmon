package com.bkahlert.netmon.serialization

import io.kotest.data.forAll
import io.kotest.data.row
import io.kotest.inspectors.forAll
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeSameInstanceAs
import kotlinx.coroutines.test.runTest
import kotlin.test.Test

class DataUrlTest {

    @Test
    fun media_type() = runTest {
        forAll(
            row(URL_ENCODED_DATA_URL, SVG_MIME_TYPE),
            row(BASE64_ENCODED_DATA_URL, SVG_MIME_TYPE),
        ) { url, expected ->
            DataUrl(url).mediaType shouldBe expected
        }
    }

    @Test
    fun base64() = runTest {
        forAll(
            row(URL_ENCODED_DATA_URL, false),
            row(BASE64_ENCODED_DATA_URL, true),
        ) { url, expected ->
            DataUrl(url).base64 shouldBe expected
        }
    }

    @Test
    fun data() = runTest {
        forAll(
            row(URL_ENCODED_DATA_URL, SVG_DATA_URL_ENCODED),
            row(BASE64_ENCODED_DATA_URL, SVG_DATA_BASE64_ENCODED),
        ) { url, expected ->
            DataUrl(url).data shouldBe expected
        }
    }

    @Test
    fun to_string() = runTest {
        listOf(
            URL_ENCODED_DATA_URL,
            BASE64_ENCODED_DATA_URL,
        ).forAll { url ->
            DataUrl(url).url shouldBeSameInstanceAs url
            DataUrl(url).toString() shouldBeSameInstanceAs url
        }
    }

    @Test
    fun construction() = runTest {
        DataUrl(SVG_MIME_TYPE, SVG_DATA).url shouldBe URL_ENCODED_DATA_URL
        DataUrl(SVG_MIME_TYPE, SVG_DATA.encodeToByteArray()).url shouldBe BASE64_ENCODED_DATA_URL
    }

    @Test
    fun regression() = runTest {
        DataUrl(
            "image/svg+xml", """
            <svg width="29" height="29" viewBox="0 0 29 29" fill="none" xmlns="http://www.w3.org/2000/svg">
                <g clip-path="url(#clip0_2207_35376)">
                    <path
                        d="M2.8125 20.4414C2.29687 20.4414 1.96875 20.1133 1.96875 19.5977V5.60547C1.96875 5.07813 2.29687 4.76172 2.8125 4.76172H25.289C25.8047 4.76172 26.1328 5.07813 26.1328 5.60547V19.5977C26.1328 20.1133 25.8047 20.4414 25.289 20.4414H2.8125Z"
                        fill="black" fill-opacity="0"/>
                    <path
                        d="M2.77734 22.3281H25.3125C27 22.3281 28.0195 21.3086 28.0195 19.6211V5.57031C28.0195 3.88281 27 2.875 25.3125 2.875H2.77734C1.10156 2.875 0.0820312 3.88281 0.0820312 5.57031V19.6211C0.0820312 21.3086 1.10156 22.3281 2.77734 22.3281ZM2.8125 20.4414C2.29687 20.4414 1.96875 20.1133 1.96875 19.5977V5.60547C1.96875 5.07813 2.29687 4.76172 2.8125 4.76172H25.289C25.8047 4.76172 26.1328 5.07813 26.1328 5.60547V19.5977C26.1328 20.1133 25.8047 20.4414 25.289 20.4414H2.8125ZM10.3476 25.1172H17.7539V22.1758H10.3476V25.1172ZM10.2773 26.4883H17.8242C18.3398 26.4883 18.7617 26.0664 18.7617 25.5391C18.7617 25.0117 18.3398 24.5899 17.8242 24.5899H10.2773C9.76172 24.5899 9.32812 25.0117 9.32812 25.5391C9.32812 26.0664 9.76172 26.4883 10.2773 26.4883Z"
                        fill="black" fill-opacity="0.85"/>
                </g>
                <defs>
                    <clipPath id="clip0_2207_35376">
                        <rect width="27.9375" height="24.4336" fill="white" transform="translate(0.0820312 2.05469)"/>
                    </clipPath>
                </defs>
            </svg>

        """.trimIndent().encodeToByteArray()
        ).url shouldBe "data:image/svg+xml;base64,PHN2ZyB3aWR0aD0iMjkiIGhlaWdodD0iMjkiIHZpZXdCb3g9IjAgMCAyOSAyOSIgZmlsbD0ibm9uZSIgeG1sbnM9Imh0dHA6Ly93d3cudzMub3JnLzIwMDAvc3ZnIj4KICAgIDxnIGNsaXAtcGF0aD0idXJsKCNjbGlwMF8yMjA3XzM1Mzc2KSI+CiAgICAgICAgPHBhdGgKICAgICAgICAgICAgZD0iTTIuODEyNSAyMC40NDE0QzIuMjk2ODcgMjAuNDQxNCAxLjk2ODc1IDIwLjExMzMgMS45Njg3NSAxOS41OTc3VjUuNjA1NDdDMS45Njg3NSA1LjA3ODEzIDIuMjk2ODcgNC43NjE3MiAyLjgxMjUgNC43NjE3MkgyNS4yODlDMjUuODA0NyA0Ljc2MTcyIDI2LjEzMjggNS4wNzgxMyAyNi4xMzI4IDUuNjA1NDdWMTkuNTk3N0MyNi4xMzI4IDIwLjExMzMgMjUuODA0NyAyMC40NDE0IDI1LjI4OSAyMC40NDE0SDIuODEyNVoiCiAgICAgICAgICAgIGZpbGw9ImJsYWNrIiBmaWxsLW9wYWNpdHk9IjAiLz4KICAgICAgICA8cGF0aAogICAgICAgICAgICBkPSJNMi43NzczNCAyMi4zMjgxSDI1LjMxMjVDMjcgMjIuMzI4MSAyOC4wMTk1IDIxLjMwODYgMjguMDE5NSAxOS42MjExVjUuNTcwMzFDMjguMDE5NSAzLjg4MjgxIDI3IDIuODc1IDI1LjMxMjUgMi44NzVIMi43NzczNEMxLjEwMTU2IDIuODc1IDAuMDgyMDMxMiAzLjg4MjgxIDAuMDgyMDMxMiA1LjU3MDMxVjE5LjYyMTFDMC4wODIwMzEyIDIxLjMwODYgMS4xMDE1NiAyMi4zMjgxIDIuNzc3MzQgMjIuMzI4MVpNMi44MTI1IDIwLjQ0MTRDMi4yOTY4NyAyMC40NDE0IDEuOTY4NzUgMjAuMTEzMyAxLjk2ODc1IDE5LjU5NzdWNS42MDU0N0MxLjk2ODc1IDUuMDc4MTMgMi4yOTY4NyA0Ljc2MTcyIDIuODEyNSA0Ljc2MTcySDI1LjI4OUMyNS44MDQ3IDQuNzYxNzIgMjYuMTMyOCA1LjA3ODEzIDI2LjEzMjggNS42MDU0N1YxOS41OTc3QzI2LjEzMjggMjAuMTEzMyAyNS44MDQ3IDIwLjQ0MTQgMjUuMjg5IDIwLjQ0MTRIMi44MTI1Wk0xMC4zNDc2IDI1LjExNzJIMTcuNzUzOVYyMi4xNzU4SDEwLjM0NzZWMjUuMTE3MlpNMTAuMjc3MyAyNi40ODgzSDE3LjgyNDJDMTguMzM5OCAyNi40ODgzIDE4Ljc2MTcgMjYuMDY2NCAxOC43NjE3IDI1LjUzOTFDMTguNzYxNyAyNS4wMTE3IDE4LjMzOTggMjQuNTg5OSAxNy44MjQyIDI0LjU4OTlIMTAuMjc3M0M5Ljc2MTcyIDI0LjU4OTkgOS4zMjgxMiAyNS4wMTE3IDkuMzI4MTIgMjUuNTM5MUM5LjMyODEyIDI2LjA2NjQgOS43NjE3MiAyNi40ODgzIDEwLjI3NzMgMjYuNDg4M1oiCiAgICAgICAgICAgIGZpbGw9ImJsYWNrIiBmaWxsLW9wYWNpdHk9IjAuODUiLz4KICAgIDwvZz4KICAgIDxkZWZzPgogICAgICAgIDxjbGlwUGF0aCBpZD0iY2xpcDBfMjIwN18zNTM3NiI+CiAgICAgICAgICAgIDxyZWN0IHdpZHRoPSIyNy45Mzc1IiBoZWlnaHQ9IjI0LjQzMzYiIGZpbGw9IndoaXRlIiB0cmFuc2Zvcm09InRyYW5zbGF0ZSgwLjA4MjAzMTIgMi4wNTQ2OSkiLz4KICAgICAgICA8L2NsaXBQYXRoPgogICAgPC9kZWZzPgo8L3N2Zz4K"
    }

    companion object {
        const val SVG_MIME_TYPE: String = "image/svg+xml"
        const val SVG_DATA: String = "" +
            "<svg fill=\"none\" height=\"29\" viewBox=\"0 0 29 29\" width=\"29\" xmlns=\"http://www.w3.org/2000/svg\">" +
            "<path d=\"m5.06715 23.1101c-.17578.3398.1875.6445.46875.4922l1.75782-.9141-1.27735-1.2773zm1.48828-2.6953 1.7461 " +
            "1.7578.6914-.3633 14.26177-14.19137c.5742-.57422.5742-1.5 0-2.0625-.5743-.57422-1.4883-.57422-2.0508-.01172l-14.27347 14.20309z\" " +
            "fill=\"#000\" fill-opacity=\".85\"/>" +
            "</svg>"
        const val SVG_DATA_URL_ENCODED: String = "" +
            "%3Csvg%20fill=%22none%22%20height=%2229%22%20viewBox=%220%200%2029%2029%22%20width=%2229%22%20xmlns=%22http:%2F%2Fwww.w3.org%2F2000%2Fsvg%22%3E" +
            "%3Cpath%20d=%22m5.06715%2023.1101c-.17578.3398.1875.6445.46875.4922l1.75782-.9141-1.27735-1.2773zm1.48828-2.6953%201.7461%20" +
            "1.7578.6914-.3633%2014.26177-14.19137c.5742-.57422.5742-1.5%200-2.0625-.5743-.57422-1.4883-.57422-2.0508-.01172l-14.27347%2014.20309z%22%20" +
            "fill=%22%23000%22%20fill-opacity=%22.85%22%2F%3E%3C%2Fsvg%3E"
        const val SVG_DATA_BASE64_ENCODED: String = "" +
            "PHN2ZyBmaWxsPSJub25lIiBoZWlnaHQ9IjI5IiB2aWV3Qm94PSIwIDAgMjkgMjkiIHdpZHRoPSIyOSIgeG1sbnM9Imh0dHA6Ly93d3cudzMub3JnLzIwMDAvc3ZnIj48" +
            "cGF0aCBkPSJtNS4wNjcxNSAyMy4xMTAxYy0uMTc1NzguMzM5OC4xODc1LjY0NDUuNDY4NzUuNDkyMmwxLjc1NzgyLS45MTQxLTEuMjc3MzUtMS4yNzczem0xLjQ4ODI4LTIuNjk1MyAxLjc0NjEg" +
            "MS43NTc4LjY5MTQtLjM2MzMgMTQuMjYxNzctMTQuMTkxMzdjLjU3NDItLjU3NDIyLjU3NDItMS41IDAtMi4wNjI1LS41NzQzLS41NzQyMi0xLjQ4ODMtLjU3NDIyLTIuMDUwOC0uMDExNzJsLTE0LjI3MzQ3IDE0LjIwMzA5eiIg" +
            "ZmlsbD0iIzAwMCIgZmlsbC1vcGFjaXR5PSIuODUiLz48" +
            "L3N2Zz4="

        const val URL_ENCODED_DATA_URL = "data:image/svg+xml,$SVG_DATA_URL_ENCODED"
        const val BASE64_ENCODED_DATA_URL = "data:image/svg+xml;base64,$SVG_DATA_BASE64_ENCODED"
    }
}
