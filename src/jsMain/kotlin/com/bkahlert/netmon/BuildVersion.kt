package com.bkahlert.netmon

/** `git describe --tags --always --dirty` of the checkout the bundle was built from; webpack replaces it (webpack.config.d/build-version.js). */
@JsName("NETMON_VERSION")
external val BUILD_VERSION: String
