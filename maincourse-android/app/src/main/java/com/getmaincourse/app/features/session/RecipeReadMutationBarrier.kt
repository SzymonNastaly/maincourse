package com.getmaincourse.app.features.session

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.withContext

internal class RecipeReadPermit internal constructor(
    internal val epoch: Long,
    internal val admitted: Boolean,
    internal val job: Job?,
)

internal class RecipeMutationPermit internal constructor(internal val epoch: Long)

internal class RecipeReadMutationBarrier {
    private val lock = Any()
    private val reads = mutableSetOf<Job>()
    private var epoch = 0L
    private var mutationPending = false

    suspend fun beginRead(): RecipeReadPermit {
        val job = currentCoroutineContext()[Job]
        return synchronized(lock) {
            val admitted = !mutationPending
            if (admitted && job != null) reads += job
            RecipeReadPermit(epoch, admitted, job)
        }
    }

    fun endRead(permit: RecipeReadPermit) {
        permit.job?.let { job -> synchronized(lock) { reads.remove(job) } }
    }

    fun canCommit(permit: RecipeReadPermit): Boolean = synchronized(lock) {
        permit.admitted && !mutationPending && permit.epoch == epoch
    }

    suspend fun invalidateAndJoinReads() {
        val owner = currentCoroutineContext()[Job]
        val stale = synchronized(lock) {
            epoch++
            reads.filterNot { it == owner }
        }
        stale.forEach(Job::cancel)
        stale.forEach { it.cancelAndJoin() }
    }

    suspend fun beginMutation(): RecipeMutationPermit {
        val owner = currentCoroutineContext()[Job]
        val (permit, stale) = synchronized(lock) {
            check(!mutationPending) { "A recipe mutation is already active" }
            mutationPending = true
            epoch++
            RecipeMutationPermit(epoch) to reads.filterNot { it == owner }
        }
        return withContext(NonCancellable) {
            stale.forEach(Job::cancel)
            stale.forEach { it.cancelAndJoin() }
            permit
        }
    }

    fun requireMutationCanCommit(permit: RecipeMutationPermit) {
        if (!synchronized(lock) { mutationPending && permit.epoch == epoch }) {
            throw CancellationException("Recipe mutation was replaced")
        }
    }

    fun endMutation(permit: RecipeMutationPermit) {
        synchronized(lock) {
            if (mutationPending && permit.epoch == epoch) {
                mutationPending = false
                epoch++
            }
        }
    }
}
