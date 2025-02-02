package org.odk.collect.android.events

import org.odk.collect.forms.instances.Instance
/**
 * Events exporter class for form events. This class contains all the
 * events that occur during the lifecycle of a form.
 */
object FormEventBus: ODKEventBus<FormStateEvent>() {
    //TODO: make these functions internal

    fun formOpened(formId: String) {
        state.onNext(FormStateEvent.OnFormOpened(formId))
    }

    fun formOpenFailed(formId: String, errorMessage: String) {
        state.onNext(FormStateEvent.OnFormOpenFailed(formId, errorMessage))
    }

    fun formSaved(formId: String, instancePath: String) {
        state.onNext(FormStateEvent.OnFormSaved(formId, instancePath))
    }

    fun formSaveError(formId: String, errorMessage: String) {
        state.onNext(FormStateEvent.OnFormSaveError(formId, errorMessage))
    }

    fun formSubmitted(formId: String, jsonData: String) {
        state.onNext(FormStateEvent.OnFormSubmitted(formId, jsonData))
    }

    fun formCompleted(instance: Instance) {
        state.onNext(FormStateEvent.OnFormCompleted(instance))
    }

    fun formUploaded(formId: String, instancePath: String) {
        state.onNext(FormStateEvent.OnFormUploaded(formId, instancePath))
    }

    fun formUploadError(formId: String, errorMessage: String) {
        state.onNext(FormStateEvent.OnFormUploadFailed(formId, errorMessage))
    }

    fun formDownloaded(formId: String) {
        state.onNext(FormStateEvent.OnFormDownloaded(formId))
    }

    fun formDownloadFailed(formId: String?, errorMessage: String?) {
        state.onNext(FormStateEvent.OnFormDownloadFailed(formId, errorMessage))
    }

    fun formAbandoned(formId: String) {
        state.onNext(FormStateEvent.OnFormAbandoned(formId))
    }
}

sealed class FormStateEvent {
    /** Called when a form is opened. */
    data class OnFormOpened(val formId: String): FormStateEvent()

    /** Called when an error occurs while opening a form. */
    data class OnFormOpenFailed(val formId: String, val errorMessage: String): FormStateEvent()

    /** Called when a form is saved. */
    data class OnFormSaved(val formId: String, val instancePath: String): FormStateEvent()

    /** Called when a form is submitted. */
    data class OnFormSubmitted(val formId: String, val jsonData: String): FormStateEvent()

    /** Called when a form is completed. */
    data class OnFormCompleted(val instance:Instance): FormStateEvent()

    /** Called when a form save process errors out. */
    data class OnFormSaveError(val formId: String, val errorMessage: String): FormStateEvent()

    /** Called when a form upload is successful. */
    data class OnFormUploaded(val formId: String, val instancePath: String): FormStateEvent()

    /** Called when a form upload fails. */
    data class OnFormUploadFailed(val formId: String, val errorMessage: String): FormStateEvent()

    /** Called when a form is successfully downloaded. */
    data class OnFormDownloaded(val formId: String): FormStateEvent()

    /** Called when a form download fails. */
    data class OnFormDownloadFailed(val formId: String?, val errorMessage: String?): FormStateEvent()

    /** Called when a form is abandoned midway without saving. */
    data class OnFormAbandoned(val formId: String): FormStateEvent()
}