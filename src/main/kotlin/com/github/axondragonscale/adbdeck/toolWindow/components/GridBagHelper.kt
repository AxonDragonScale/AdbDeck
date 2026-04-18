package com.github.axondragonscale.adbdeck.toolwindow.components

import java.awt.GridBagConstraints

/**
 * Common [GridBagConstraints] factory for the full-width row pattern used across all tab panels.
 * Returns a constraint that fills horizontally, anchors northwest, with the given grid row.
 */
fun fillXConstraints(row: Int) = GridBagConstraints().apply {
    gridx = 0; gridy = row; weightx = 1.0; fill = GridBagConstraints.HORIZONTAL
    anchor = GridBagConstraints.NORTHWEST
}

/**
 * Vertical glue constraint — pushes content above to the top of the container.
 */
fun verticalGlueConstraints(row: Int) = GridBagConstraints().apply {
    gridx = 0; gridy = row; weighty = 1.0; fill = GridBagConstraints.VERTICAL
}

/**
 * Horizontal spacer constraint — fills remaining width in a row.
 */
fun horizontalSpacerConstraints(column: Int) = GridBagConstraints().apply {
    gridx = column; gridy = 0; weightx = 1.0; fill = GridBagConstraints.HORIZONTAL
}

