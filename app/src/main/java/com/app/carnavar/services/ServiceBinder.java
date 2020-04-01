package com.app.carnavar.services;

import android.app.Service;

public interface ServiceBinder<T extends Service> {
    T getService();
}
