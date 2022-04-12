package com.skeagis.monitorporastov

import androidx.lifecycle.LifecycleObserver
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlin.coroutines.CoroutineContext

interface CoroutineScopeInterface: CoroutineScope{
    var job: Job

}

class CoroutineScopeDelegate : CoroutineScopeInterface, LifecycleObserver {
    override var job: Job = Job()
    override val coroutineContext: CoroutineContext
        get() = Dispatchers.IO + job
}

