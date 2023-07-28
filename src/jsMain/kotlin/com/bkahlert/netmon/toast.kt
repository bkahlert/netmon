package com.bkahlert.netmon

import dev.fritz2.core.Tag
import dev.fritz2.headless.components.toast
import org.w3c.dom.HTMLLIElement

const val toastContainerDefault = "toast-container-default"
const val containerImportant = "toast-container-important"


private var toastCount = 0
private fun nextToastId() = "toast-${toastCount++}"

fun showToast(container: String, initialize: Tag<HTMLLIElement>.() -> Unit) {
    toast(
        container,
        duration = 6000L,
        """flex flex-row flex-shrink-0 gap-2 justify-center
            | w-max px-4 py-2.5
            | rounded-xl shadow-sm
            | border-none
            | text-sm font-sans
        """.trimMargin(),
        nextToastId()
    ) {
        initialize()

        // FIXME: Werden Toast schnell hintereinander geöffnet, werden einige von ihnen bim
        //  Schließen zwar aus dem ToastStore entfernt, bleiben jedoch als DOM-Leiche übrig.
        //  Dies könnte an der Kombination aus renderEach und transition liegen; evtl. kann
        //  ein Element nicht aus dem DOM entfernt werden, solange es animiert wird?
        //  Vgl.: https://github.com/jwstegemann/fritz2/issues/714
        /*transition(
            enter = "transition-all duration-200 ease-in-out",
            enterStart = "opacity-0",
            enterEnd = "opacity-100",
            leave = "transition-all duration-200 ease-in-out",
            leaveStart = "opacity-100",
            leaveEnd = "opacity-0"
        )*/

        button("font-bold") {
            +"✖"
//            icon(
//                classes = "w-4 h-4 text-primary-900",
//                content = HeroIcons.x
//            )
            clicks handledBy close
        }
    }
}
