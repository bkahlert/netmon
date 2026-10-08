package com.bkahlert.netmon.scanner.nmap

enum class TimingTemplate(val value: Int) {
    /** For IDS evasion. */
    Paranoid(0),

    /** For IDS evasion. */
    Sneaky(1),

    /** Slows down the scan to use less bandwidth and target machine resources. */
    Polite(2),

    /** Default, does nothing. */
    Normal(3),

    /** Assumes a reasonably fast and reliable network. */
    Aggressive(4),

    /** Assumes an extraordinarily fast network, or are willing to sacrifice some accuracy for speed. */
    Insane(5),
    ;

}
