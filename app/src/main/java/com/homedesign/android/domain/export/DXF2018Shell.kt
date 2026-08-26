package com.homedesign.android.domain.export

/**
 * R2018 / AC1032 scaffolding fragments (web export/shell text assets).
 * Loaded from classpath dxf_shell/ so JVM unit tests and device share one copy.
 */
object DXF2018Shell {
    val header: String by lazy { load("header.txt") }
    val classes: String by lazy { load("classes.txt") }
    val tablesPart1: String by lazy { load("tablesPart1.txt") }
    val tablesPart2: String by lazy { load("tablesPart2.txt") }
    val tablesPart3: String by lazy { load("tablesPart3.txt") }
    val tablesPart4: String by lazy { load("tablesPart4.txt") }
    val tablesPart5: String by lazy { load("tablesPart5.txt") }
    val blocksPrefix: String by lazy { load("blocksPrefix.txt") }
    val objects: String by lazy { load("objects.txt") }

    const val modelSpaceRecord = "17"
    const val paperSpaceRecord = "1B"
    const val layerTable = "1"
    const val styleTable = "5"
    const val dimStyleTable = "4"
    const val blockRecordTable = "9"
    const val standardStyleRecord = "29"
    const val layerPlotStyle = "13"
    const val layerMaterial = "21"
    const val templateLayerCount = 2
    const val templateStyleCount = 26
    const val templateDimStyleCount = 12
    const val templateBlockRecordCount = 5

    private fun load(name: String): String {
        val stream = DXF2018Shell::class.java.classLoader?.getResourceAsStream("dxf_shell/$name")
            ?: error("Missing classpath resource dxf_shell/$name")
        return stream.bufferedReader(Charsets.UTF_8).use { it.readText() }
    }
}
