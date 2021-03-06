/*
 * Copyright (c) 2020 Martin Denham, Tuomas Airaksinen and the And Bible contributors.
 *
 * This file is part of And Bible (http://github.com/AndBible/and-bible).
 *
 * And Bible is free software: you can redistribute it and/or modify it under the
 * terms of the GNU General Public License as published by the Free Software Foundation,
 * either version 3 of the License, or (at your option) any later version.
 *
 * And Bible is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY;
 * without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
 * See the GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License along with And Bible.
 * If not, see http://www.gnu.org/licenses/.
 *
 */

package net.bible.android.database.bookmarks

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import org.crosswire.jsword.passage.Verse
import org.crosswire.jsword.passage.VerseRange
import org.crosswire.jsword.versification.BibleBook
import net.bible.android.database.bookmarks.BookmarkEntities.Bookmark
import net.bible.android.database.bookmarks.BookmarkEntities.Label
import net.bible.android.database.bookmarks.BookmarkEntities.BookmarkToLabel
import java.util.*

@Dao
interface BookmarkDao {
    @Query("SELECT * from Bookmark ORDER BY :orderBy")
    fun allBookmarks(orderBy: String): List<Bookmark>
    fun allBookmarks(orderBy: BookmarkSortOrder = BookmarkSortOrder.BIBLE_ORDER): List<Bookmark> =
        allBookmarks(orderBy.sqlString)

    @Query("SELECT * from Bookmark where id = :bookmarkId")
    fun bookmarkById(bookmarkId: Long): Bookmark

    @Query("SELECT * from Bookmark where id IN (:bookmarkIds)")
    fun bookmarksByIds(bookmarkIds: List<Long>): List<Bookmark>

    @Query("""SELECT * from Bookmark where 
        :rangeStart <= kjvOrdinalStart <= :rangeEnd OR
        :rangeStart <= kjvOrdinalEnd <= :rangeEnd OR 
        (kjvOrdinalStart <= :rangeEnd AND :rangeStart >= kjvOrdinalEnd) OR
        (kjvOrdinalStart <= :rangeStart <= kjvOrdinalEnd AND kjvOrdinalStart <= :rangeEnd <= kjvOrdinalEnd)
        """)
    fun bookmarksForKjvOrdinalRange(rangeStart: Int, rangeEnd: Int): List<Bookmark>
    fun bookmarksForVerseRange(verseRange: VerseRange): List<Bookmark> {
        val v = converter.convert(verseRange, KJVA)
        return bookmarksForKjvOrdinalRange(v.start.ordinal, v.end.ordinal)
    }
    fun bookmarksInBook(book: BibleBook): List<Bookmark> = bookmarksForVerseRange(KJVA.allVerses)

    @Query("SELECT * from Bookmark where kjvOrdinalStart <= :verseId AND :verseId <= kjvOrdinalEnd")
    fun bookmarksForKjvOrdinal(verseId: Int): List<Bookmark>
    fun bookmarksForVerse(verse: Verse): List<Bookmark> =
        bookmarksForKjvOrdinal(converter.convert(verse, KJVA).ordinal)

    @Query("""SELECT * from Bookmark where kjvOrdinalStart = :start""")
    fun bookmarksForKjvOrdinalStart(start: Int): List<Bookmark>
    fun bookmarksStartingAtVerse(verse: Verse): List<Bookmark> =
        bookmarksForKjvOrdinalStart(converter.convert(verse, KJVA).ordinal)

    @Query("SELECT count(*) > 0 from Bookmark where kjvOrdinalStart <= :verseOrdinal AND :verseOrdinal <= kjvOrdinalEnd LIMIT 1")
    fun hasBookmarksForVerse(verseOrdinal: Int): Boolean
    fun hasBookmarksForVerse(verse: Verse): Boolean = hasBookmarksForVerse(converter.convert(verse, KJVA).ordinal)

    @Query("""
        SELECT Bookmark.* FROM Bookmark 
            JOIN BookmarkToLabel ON Bookmark.id = BookmarkToLabel.bookmarkId 
            JOIN Label ON BookmarkToLabel.labelId = Label.id
            WHERE Label.id = :labelId AND Bookmark.kjvOrdinalStart = :startOrdinal
        """)
    fun bookmarksForVerseStartWithLabel(labelId: Long, startOrdinal: Int): List<Bookmark>
    fun bookmarksForVerseStartWithLabel(verse: Verse, label: Label): List<Bookmark> =
        bookmarksForVerseStartWithLabel(label.id, converter.convert(verse, KJVA).ordinal)

    @Insert fun insert(entity: Bookmark): Long

    @Update
    fun update(entity: Bookmark)
    fun updateBookmarkDate(entity: Bookmark): Bookmark {
        entity.createdAt = Date(System.currentTimeMillis())
        update(entity)
        return entity
    }

    @Delete fun delete(b: Bookmark)

    @Query("""
        SELECT * FROM Bookmark WHERE NOT EXISTS 
            (SELECT * FROM BookmarkToLabel WHERE Bookmark.id = BookmarkToLabel.bookmarkId)
            ORDER BY :orderBy
        """)
    fun unlabelledBookmarks(orderBy: String): List<Bookmark>
    fun unlabelledBookmarks(orderBy: BookmarkSortOrder = BookmarkSortOrder.BIBLE_ORDER): List<Bookmark> =
        unlabelledBookmarks(orderBy.sqlString)


    @Query("""
        SELECT Bookmark.* FROM Bookmark 
            JOIN BookmarkToLabel ON Bookmark.id = BookmarkToLabel.bookmarkId 
            JOIN Label ON BookmarkToLabel.labelId = Label.id
            WHERE Label.id = :labelId ORDER BY :orderBy
        """)
    fun bookmarksWithLabel(labelId: Long, orderBy: String): List<Bookmark>
    fun bookmarksWithLabel(label: Label, orderBy: BookmarkSortOrder = BookmarkSortOrder.BIBLE_ORDER): List<Bookmark>
        = bookmarksWithLabel(label.id, orderBy.sqlString)

    // Labels

    @Query("SELECT * from Label ORDER BY name")
    fun allLabelsSortedByName(): List<Label>

    @Insert fun insert(entity: Label): Long

    @Update fun update(entity: Label)

    @Delete fun delete(b: Label)

    @Query("""
        SELECT Label.* from Label 
            JOIN BookmarkToLabel ON Label.id = BookmarkToLabel.labelId 
            JOIN Bookmark ON BookmarkToLabel.bookmarkId = Bookmark.id 
            WHERE Bookmark.id = :bookmarkId
    """)
    fun labelsForBookmark(bookmarkId: Long): List<Label>

    @Insert fun insert(entity: BookmarkToLabel): Long

    @Delete fun delete(entity: BookmarkToLabel): Int

    @Delete fun delete(entities: List<BookmarkToLabel>): Int

    @Insert fun insert(entities: List<BookmarkToLabel>): List<Long>

    @Query("SELECT * from Label WHERE bookmarkStyle = 'SPEAK' LIMIT 1")
    fun speakLabel(): Label?
    fun getOrCreateSpeakLabel(): Label {
        return speakLabel()?: Label(name = "", bookmarkStyle = BookmarkStyle.SPEAK).apply {
            id = insert(this)
        }
    }

}
