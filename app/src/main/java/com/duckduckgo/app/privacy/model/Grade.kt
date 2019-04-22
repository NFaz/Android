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

package com.duckduckgo.app.privacy.model

import com.duckduckgo.app.privacy.model.Grade.Grading.*
import com.duckduckgo.app.privacy.store.PrevalenceStore
import com.duckduckgo.app.trackerdetection.model.TrackerNetwork
import com.squareup.moshi.Json

class Grade(
    val https: Boolean = false,
    var privacyScore: Int? = null,
    val prevalenceStore: PrevalenceStore,
    memberNetwork: TrackerNetwork?
) {

    val httpsAutoUpgrade: Boolean = https // not support yet, don't penalise sites for now

    init {
        memberNetwork?.let {
            setParentEntityAndPrevalence(it.name)
        }
    }

    enum class Grading {

        A,
        @Json(name = "B+")
        B_PLUS,
        B,
        @Json(name = "C+")
        C_PLUS,
        C,
        D,
        @Json(name = "D-")
        D_MINUS
    }


    data class Score(
        val grade: Grading,
        val score: Int,
        val httpsScore: Int,
        val trackerScore: Int,
        val privacyScore: Int
    )

    data class Scores(
        val site: Score,
        val enhanced: Score
    )


    val scores: Scores get() = calculate()

    private var entitiesNotBlocked: Map<String, Double> = mapOf()
    private var entitiesBlocked: Map<String, Double> = mapOf()

    private fun calculate(): Scores {

        // HTTPS
        val siteHttpsScore: Int
        val enhancedHttpsScore: Int

        when {
            httpsAutoUpgrade -> {
                siteHttpsScore = 0
                enhancedHttpsScore = 0
            }
            https -> {
                siteHttpsScore = 3
                enhancedHttpsScore = 0
            }
            else -> {
                siteHttpsScore = 10
                enhancedHttpsScore = 10
            }
        }

        // PRIVACY
        val privacyScore = Math.min(privacyScore ?: UNKNOWN_PRIVACY_SCORE, MAX_PRIVACY_SCORE)

        // TRACKERS
        val enhancedTrackerScore = trackerScore(entitiesNotBlocked)
        val siteTrackerScore = trackerScore(entitiesBlocked) + enhancedTrackerScore

        // TOTALS
        val siteTotalScore = siteHttpsScore + siteTrackerScore + privacyScore
        val enhancedTotalScore = enhancedHttpsScore + enhancedTrackerScore + privacyScore

        // GRADES
        val siteGrade = gradeForScore(siteTotalScore)
        val enhancedGrade = gradeForScore(enhancedTotalScore)

        val site = Score(
            grade = siteGrade,
            httpsScore = siteHttpsScore,
            privacyScore = privacyScore,
            score = siteTotalScore,
            trackerScore = siteTrackerScore
        )

        val enhanced = Score(
            grade = enhancedGrade,
            httpsScore = enhancedHttpsScore,
            privacyScore = privacyScore,
            score = enhancedTotalScore,
            trackerScore = enhancedTrackerScore
        )

        return Scores(site = site, enhanced = enhanced)
    }

    private fun gradeForScore(score: Int): Grading {
        return when {
            score <= 1 -> A
            score <= 3 -> B_PLUS
            score <= 9 -> B
            score <= 13 -> C_PLUS
            score <= 19 -> C
            score <= 29 -> D
            else -> D_MINUS
        }
    }

    private fun trackerScore(entities: Map<String, Double>): Int {
        return entities.entries.fold(0) { acc, entry ->
            acc + scoreFromPrevalence(entry.value)
        }
    }

    private fun scoreFromPrevalence(prevalence: Double): Int {
        return when {
            prevalence <= 0.0 -> 0
            prevalence <= 0.1 -> 1
            prevalence <= 1.0 -> 2
            prevalence <= 5.0 -> 3
            prevalence <= 10.0 -> 4
            prevalence <= 15.0 -> 5
            prevalence <= 20.0 -> 6
            prevalence <= 30.0 -> 7
            prevalence <= 45.0 -> 8
            prevalence <= 66.0 -> 9
            else -> 10
        }
    }

    fun setParentEntityAndPrevalence(parentEntity: String?) {
        parentEntity ?: return
        addEntityNotBlocked(parentEntity)
    }

    fun addEntityNotBlocked(entity: String) {
        val prevalence = prevalenceStore.findPrevalenceOf(entity) ?: return
        entitiesNotBlocked = entitiesNotBlocked.plus(Pair(entity, prevalence))
    }

    fun addEntityBlocked(entity: String) {
        val prevalence = prevalenceStore.findPrevalenceOf(entity) ?: return
        entitiesBlocked = entitiesBlocked.plus(Pair(entity, prevalence))
    }

    companion object {
        const val UNKNOWN_PRIVACY_SCORE = 2
        const val MAX_PRIVACY_SCORE = 10
    }

}
