package com.adischat.app.feature.shop.presentation

import kotlin.math.abs

internal const val VISUAL_MATCH_THRESHOLD = .74

internal data class VisualFingerprint(
    val labels: Map<String, Float>,
    val colorHistogram: FloatArray,
    val differenceHash: Long,
    val spatialColors: FloatArray,
)

private val genericImageLabels = setOf("product", "text", "font", "pattern", "design", "material", "screenshot")

// A local heuristic, not a probability or an embedding-based similarity measure.
internal fun visualSimilarity(a: VisualFingerprint, b: VisualFingerprint): Double {
    val semantic = labelCosine(a.labels.filterKeys { it !in genericImageLabels }, b.labels.filterKeys { it !in genericImageLabels })
    val color = vectorCosine(a.colorHistogram, b.colorHistogram)
    val shape = 1.0 - java.lang.Long.bitCount(a.differenceHash xor b.differenceHash) / 64.0
    val spatial = 1.0 - a.spatialColors.indices.sumOf { abs(a.spatialColors[it] - b.spatialColors[it]).toDouble() } / a.spatialColors.size
    val detailed = java.lang.Long.bitCount(a.differenceHash) in 5..59 && java.lang.Long.bitCount(b.differenceHash) in 5..59
    if (detailed && shape >= .94 && spatial >= .96 && color >= .97) return .98 + .02 * semantic
    if (semantic < .55 || shape < .65 || spatial < .75) return 0.0
    return semantic * .45 + color * .20 + shape * .20 + spatial * .15
}
