package com.daisychain.engine.run

import com.daisychain.engine.edit.ActionTree
import com.daisychain.engine.edit.FlowNode
import com.daisychain.engine.model.ActionInstance
import com.daisychain.engine.model.ParamValue
import com.daisychain.engine.spec.ActionDefinition
import com.daisychain.engine.spec.InputSpec
import com.daisychain.engine.spec.ActionRegistry
import com.daisychain.engine.spec.t
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay

/**
 * Runs a shortcut: walks the blocks in order, lets control-flow blocks pick their branches,
 * and passes each block's output to the next one.
 *
 * @param stepDelayMs every block stays "lit" at least this long so the user can follow the run.
 */
class ShortcutRunner(
    private val device: DeviceApi,
    private val ui: RunUi,
    private val stepDelayMs: Long = 300,
    private val library: ShortcutLibrary? = null,
    private val depth: Int = 0,
) {
    /** @throws RunFailure when a block fails; returns normally when finished or stopped with "Stop this shortcut". */
    suspend fun run(actions: List<ActionInstance>, progress: RunProgress, shortcutInput: RunValue = RunValue.Nothing): RunValue {
        val session = RunSession(device, ui, shortcutInput, library, depth)
        return try {
            runNodes(ActionTree.parse(actions), session, progress)
            session.lastOutput
        } catch (stop: StopShortcut) {
            // Ended on purpose; "Stop and output" decides what is returned.
            stop.output ?: session.lastOutput
        }
    }

    private suspend fun runNodes(nodes: List<FlowNode>, session: RunSession, progress: RunProgress) {
        for (node in nodes) {
            when (node) {
                is FlowNode.Leaf -> runLeaf(node.action, session, progress)
                is FlowNode.Group -> runGroup(node, session, progress)
            }
        }
    }

    private suspend fun runLeaf(action: ActionInstance, session: RunSession, progress: RunProgress) {
        progress.onStart(action.uuid)
        val def = ActionRegistry.find(action.identifier) ?: throw RunFailure(action.uuid, missingText)
        val output = guard(action) { def.run(ActionRunContext(session, action, def.params, explicitInput(def, action, session))) }
        if (output != RunValue.Nothing) {
            session.outputs[action.uuid] = output
            session.lastOutput = output
            if (output is RunValue.Dict) session.lastDictionary = output
        }
        delay(stepDelayMs)
        progress.onFinish(action.uuid)
    }

    private suspend fun runGroup(group: FlowNode.Group, session: RunSession, progress: RunProgress) {
        val header = group.segments.first().header
        progress.onStart(header.uuid)
        val def = ActionRegistry.find(header.identifier) ?: throw RunFailure(header.uuid, missingText)
        delay(stepDelayMs)
        val ctx = FlowRunContext(
            session = session,
            action = header,
            specs = def.params,
            segmentCount = group.segments.size,
            segmentHeaders = group.segments.map { it.header },
            inputOverride = explicitInput(def, header, session),
            runSegmentBody = { index ->
                progress.onFinish(header.uuid)
                group.segments.getOrNull(index)?.let { runNodes(it.children, session, progress) }
            },
        )
        guard(header) { def.runFlow(ctx) }
        progress.onFinish(header.uuid)
        group.end?.let {
            progress.onStart(it.uuid)
            delay(stepDelayMs / 2)
            progress.onFinish(it.uuid)
        }
    }

    /** The variable the user pointed the block's input at, if any (otherwise the previous block's output is used). */
    private suspend fun explicitInput(def: ActionDefinition, action: ActionInstance, session: RunSession): RunValue? =
        def.input?.let { (action.params[InputSpec.INPUT_KEY] as? ParamValue.Variable)?.let { v -> session.resolve(v.ref) } }

    /** Turns a block's failure into a [RunFailure] that knows which block failed. */
    private suspend fun <T> guard(action: ActionInstance, body: suspend () -> T): T = try {
        body()
    } catch (e: ActionFailed) {
        throw RunFailure(action.uuid, e.text)
    } catch (e: RunFailure) {
        throw e
    } catch (e: StopShortcut) {
        throw e
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        val message = e.message ?: e.javaClass.simpleName
        throw RunFailure(action.uuid, t(message))
    }
}
