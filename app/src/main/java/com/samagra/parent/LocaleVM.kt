package com.samagra.parent

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import java.util.Locale

class LocaleVM : ViewModel() {
    private var _currentLocale = MutableLiveData<Locale>()

    val currentLocale : LiveData<Locale> = _currentLocale

    public fun setLocale(locale: Locale)
    {
        _currentLocale.value = locale
    }
}