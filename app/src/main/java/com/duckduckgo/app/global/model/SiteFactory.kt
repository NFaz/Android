/*
 * Copyright (c) 2018 DuckDuckGo
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.duckduckgo.app.global.model

import androidx.annotation.AnyThread
import androidx.annotation.WorkerThread
import com.duckduckgo.app.privacy.model.PrivacyPractices
import com.duckduckgo.app.privacy.store.PrevalenceStore
import com.duckduckgo.app.trackerdetection.model.TrackerNetworks
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton


@Singleton
class SiteFactory @Inject constructor(
    private val privacyPractices: PrivacyPractices,
    private val trackerNetworks: TrackerNetworks,
    private val prevalenceStore: PrevalenceStore
) {

    @AnyThread
            /*
             * Provides a basic site, that is updated with data as it becomes available
             */
    fun buildSite(url: String, title: String? = null): Site {
        val site = SiteMonitor(url, title, prevalenceStore)
        // TODO move this call to view models
        GlobalScope.launch(Dispatchers.IO) {
            appendData(site)
        }
        return site
    }

    @WorkerThread
    suspend fun appendData(site: Site) {
        val practices = privacyPractices.privacyPracticesFor(site.url)
        val memberNetwork = trackerNetworks.network(site.url)
        site.updateData(practices, memberNetwork)
    }
}