package com.azhulabs.sesnot.mobile

import android.app.Application

class SesNotApp : Application() {
    lateinit var repository: NoteRepository
    lateinit var pipeline: ProcessingPipeline

    override fun onCreate() {
        super.onCreate()
        repository = NoteRepository(this)
        pipeline = ProcessingPipeline(this, repository)
        instance = this
    }

    companion object {
        lateinit var instance: SesNotApp
    }
}
