/*
 *             Twidere - Twitter client for Android
 *
 *  Copyright (C) 2012-2017 Mariotaku Lee <mariotaku.lee@gmail.com>
 *
 *  This program is free software: you can redistribute it and/or modify
 *  it under the terms of the GNU General Public License as published by
 *  the Free Software Foundation, either version 3 of the License, or
 *  (at your option) any later version.
 *
 *  This program is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  GNU General Public License for more details.
 *
 *  You should have received a copy of the GNU General Public License
 *  along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package org.mariotaku.twidere.data

import android.arch.lifecycle.LiveData
import android.support.annotation.WorkerThread
import nl.komponents.kovenant.task
import nl.komponents.kovenant.ui.failUi
import nl.komponents.kovenant.ui.successUi
import org.mariotaku.twidere.util.DebugLog

abstract class ComputableLiveData<T>(loadOnInstantiate: Boolean) : LiveData<T>() {

    init {
        if (loadOnInstantiate) {
            load()
        }
    }

    fun load() {
        task(body = this::compute).successUi {
            postValue(it)
        }.failUi {
            DebugLog.e(msg = "Exception in ComputableLiveData", tr = it)
            postValue(null)
        }
    }

    @WorkerThread
    protected abstract fun compute(): T

}
