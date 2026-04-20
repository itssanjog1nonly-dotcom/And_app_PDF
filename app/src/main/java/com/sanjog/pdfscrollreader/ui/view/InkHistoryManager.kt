package com.sanjog.pdfscrollreader.ui.view

import java.util.LinkedList

class InkHistoryManager(
    private val maxHistory: Int,
    private val onHistoryChanged: (canUndo: Boolean, canRedo: Boolean) -> Unit
) {
    private val undoStack = LinkedList<Pair<List<Stroke>, List<ShapeAnnotation>>>()
    private val redoStack = LinkedList<Pair<List<Stroke>, List<ShapeAnnotation>>>()

    fun pushUndo(snapshot: Pair<List<Stroke>, List<ShapeAnnotation>>) {
        undoStack.addFirst(snapshot)
        if (undoStack.size > maxHistory) undoStack.removeLast()
        redoStack.clear()
        notifyChanged()
    }

    fun undo(currentSnapshot: Pair<List<Stroke>, List<ShapeAnnotation>>): Pair<List<Stroke>, List<ShapeAnnotation>>? {
        if (undoStack.isEmpty()) return null
        redoStack.addFirst(currentSnapshot)
        val snapshot = undoStack.removeFirst()
        notifyChanged()
        return snapshot
    }

    fun redo(currentSnapshot: Pair<List<Stroke>, List<ShapeAnnotation>>): Pair<List<Stroke>, List<ShapeAnnotation>>? {
        if (redoStack.isEmpty()) return null
        undoStack.addFirst(currentSnapshot)
        val snapshot = redoStack.removeFirst()
        notifyChanged()
        return snapshot
    }

    fun clear() {
        undoStack.clear()
        redoStack.clear()
        notifyChanged()
    }

    fun notifyChanged() {
        onHistoryChanged(undoStack.isNotEmpty(), redoStack.isNotEmpty())
    }
}
