package com.getmaincourse.app.features.session

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

internal class RecipeReadPermit internal constructor(
    internal val epoch: Long,
    internal val job: Job?,
)

internal class RecipeMutationPermit internal constructor(internal val owner: Any)

internal class RecipeReadMutationBarrier {
    private val lock = Any()
    private val mutationGate = Mutex()
    private val reads = mutableSetOf<Job>()
    private var readEpoch = 0L
    private var activeMutation: Any? = null

    suspend fun beginRead(): RecipeReadPermit {
        val job = currentCoroutineContext()[Job]
        return mutationGate.withLock {
            synchronized(lock) {
                if (job != null) reads += job
                RecipeReadPermit(readEpoch, job)
            }
        }
    }

    fun endRead(permit: RecipeReadPermit) {
        permit.job?.let { job -> synchronized(lock) { reads.remove(job) } }
    }

    fun canCommit(permit: RecipeReadPermit): Boolean = synchronized(lock) {
        permit.epoch == readEpoch
    }

    suspend fun invalidateAndJoinReads() {
        val owner = currentCoroutineContext()[Job]
        val stale = synchronized(lock) {
            readEpoch++
            reads.filterNot { it == owner }
        }
        stale.forEach(Job::cancel)
        stale.forEach { it.cancelAndJoin() }
    }

    suspend fun beginMutation(): RecipeMutationPermit {
        val owner = currentCoroutineContext()[Job]
        val mutationOwner = Any()
        var acquired = false
        try {
            mutationGate.lock(mutationOwner)
            acquired = true
            val stale = synchronized(lock) {
                activeMutation = mutationOwner
                readEpoch++
                reads.filterNot { it == owner }
            }
            stale.forEach(Job::cancel)
            stale.forEach { it.cancelAndJoin() }
            return RecipeMutationPermit(mutationOwner)
        } catch (failure: Throwable) {
            if (acquired) releaseMutation(mutationOwner)
            throw failure
        }
    }

    fun requireMutationCanCommit(permit: RecipeMutationPermit) {
        if (!synchronized(lock) { activeMutation === permit.owner }) {
            throw CancellationException("Recipe mutation was replaced")
        }
    }

    fun endMutation(permit: RecipeMutationPermit) {
        releaseMutation(permit.owner)
    }

    private fun releaseMutation(owner: Any) {
        val shouldUnlock = synchronized(lock) {
            if (activeMutation === owner) {
                activeMutation = null
                true
            } else {
                false
            }
        }
        if (shouldUnlock) mutationGate.unlock(owner)
    }
}
