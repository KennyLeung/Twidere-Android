package org.mariotaku.twidere.extension.model

import android.content.Context
import org.mariotaku.ktextension.addAllTo
import org.mariotaku.ktextension.toLongOr
import org.mariotaku.microblog.library.mastodon.annotation.StatusVisibility
import org.mariotaku.twidere.R
import org.mariotaku.twidere.TwidereConstants.USER_TYPE_FANFOU_COM
import org.mariotaku.twidere.model.*
import org.mariotaku.twidere.util.HtmlEscapeHelper
import org.mariotaku.twidere.util.UriUtils
import org.mariotaku.twidere.util.UserColorNameManager
import org.mariotaku.twidere.util.Utils
import org.mariotaku.twidere.view.ShortTimeView


inline val ParcelableStatus.originalId: String
    get() = if (is_retweet) (retweet_id ?: id) else id

val ParcelableStatus.media_type: Int
    get() = media?.firstOrNull()?.type ?: 0

val ParcelableStatus.user: ParcelableUser
    get() = ParcelableUser(account_key, user_key, user_name, user_screen_name, user_profile_image_url)

val ParcelableStatus.referencedUsers: Array<ParcelableUser>
    get() {
        val resultList = mutableSetOf(user)
        if (quoted_user_key != null) {
            resultList.add(ParcelableUser(account_key, quoted_user_key, quoted_user_name,
                    quoted_user_screen_name, quoted_user_profile_image))
        }
        if (retweeted_by_user_key != null) {
            resultList.add(ParcelableUser(account_key, retweeted_by_user_key, retweeted_by_user_name,
                    retweeted_by_user_screen_name, retweeted_by_user_profile_image))
        }
        mentions?.forEach { mention ->
            resultList.add(ParcelableUser(account_key, mention.key, mention.name,
                    mention.screen_name, null))
        }
        return resultList.toTypedArray()
    }

val ParcelableStatus.replyMentions: Array<ParcelableUserMention>
    get() {
        val result = ArrayList<ParcelableUserMention>()
        result.add(parcelableUserMention(user_key, user_name, user_screen_name))
        if (is_retweet) retweeted_by_user_key?.let { key ->
            result.add(parcelableUserMention(key, retweeted_by_user_name,
                    retweeted_by_user_screen_name))
        }
        mentions?.addAllTo(result)
        return result.toTypedArray()
    }

inline val ParcelableStatus.user_acct: String
    get() = if (account_key.host == user_key.host) {
        user_screen_name
    } else {
        "$user_screen_name@${user_key.host}"
    }

inline val ParcelableStatus.retweeted_by_user_acct: String?
    get() = if (account_key.host == retweeted_by_user_key?.host) {
        retweeted_by_user_screen_name
    } else {
        "$retweeted_by_user_screen_name@${retweeted_by_user_key?.host}"
    }

inline val ParcelableStatus.quoted_user_acct: String?
    get() = if (account_key.host == quoted_user_key?.host) {
        quoted_user_screen_name
    } else {
        "$quoted_user_screen_name@${quoted_user_key?.host}"
    }

inline val ParcelableStatus.is_my_retweet: Boolean
    get() = Utils.isMyRetweet(account_key, retweeted_by_user_key, my_retweet_id)

inline val ParcelableStatus.can_retweet: Boolean
    get() {
        if (user_key.host == USER_TYPE_FANFOU_COM) return true
        val visibility = extras?.visibility ?: return !user_is_protected
        return when (visibility) {
            StatusVisibility.PRIVATE -> false
            StatusVisibility.DIRECT -> false
            else -> true
        }
    }

val ParcelableStatus.quoted: ParcelableStatus?
    get() {
        val obj = ParcelableStatus()
        obj.account_key = account_key
        obj.id = quoted_id ?: return null
        obj.timestamp = quoted_timestamp
        obj.user_key = quoted_user_key ?: return null
        obj.user_name = quoted_user_name ?: return null
        obj.user_screen_name = quoted_user_screen_name ?: return null
        obj.user_profile_image_url = quoted_user_profile_image ?: return null
        obj.user_is_protected = quoted_user_is_protected
        obj.user_is_verified = quoted_user_is_verified
        obj.text_plain = quoted_text_plain
        obj.text_unescaped = quoted_text_unescaped
        obj.source = quoted_source
        obj.spans = quoted_spans
        obj.media = quoted_media
        return obj
    }

val ParcelableStatus.retweet_sort_id: Long
    get() {
        if (!is_retweet) return -1
        return retweet_id.toLongOr(timestamp)
    }

fun ParcelableStatus.toSummaryLine(): ParcelableActivity.SummaryLine {
    val result = ParcelableActivity.SummaryLine()
    result.key = user_key
    result.name = user_name
    result.screen_name = user_screen_name
    result.content = text_unescaped
    return result
}


fun ParcelableStatus.extractFanfouHashtags(): List<String> {
    return spans?.filter { span ->
        var link = span.link
        if (link.startsWith("/")) {
            link = "http://fanfou.com$link"
        }
        if (UriUtils.getAuthority(link) != "fanfou.com") {
            return@filter false
        }
        if (span.start <= 0 || span.end > text_unescaped.lastIndex) return@filter false
        if (text_unescaped[span.start - 1] == '#' && text_unescaped[span.end] == '#') {
            return@filter true
        }
        return@filter false
    }?.map { text_unescaped.substring(it.start, it.end) }.orEmpty()
}

fun ParcelableStatus.makeOriginal() {
    if (!is_retweet) return
    id = retweet_id
    retweeted_by_user_key = null
    retweeted_by_user_name = null
    retweeted_by_user_screen_name = null
    retweeted_by_user_profile_image = null
    retweet_timestamp = -1
    retweet_id = null
    is_retweet = false
    sort_id = id.toLongOr(timestamp)
}

fun ParcelableStatus.addFilterFlag(@ParcelableStatus.FilterFlags flags: Long) {
    filter_flags = filter_flags or flags
}

fun ParcelableStatus.updateFilterInfo(descriptions: Collection<String?>?) {
    updateContentFilterInfo()
    filter_users = setOf(user_key, quoted_user_key, retweeted_by_user_key).filterNotNull().toTypedArray()
    filter_names = setOf(user_name, quoted_user_name, retweeted_by_user_name).filterNotNull().toTypedArray()
    filter_descriptions = descriptions?.filterNotNull()?.joinToString("\n")
}

fun ParcelableStatus.updateContentFilterInfo() {
    filter_links = generateFilterLinks()
    filter_texts = generateFilterTexts()

    filter_sources = setOf(source?.plainText, quoted_source?.plainText).filterNotNull().toTypedArray()
}

fun ParcelableStatus.generateFilterTexts(): String {
    val texts = StringBuilder()
    texts.appendNonEmptyLine(text_unescaped)
    texts.appendNonEmptyLine(quoted_text_unescaped)
    media?.forEach { item ->
        texts.appendNonEmptyLine(item.alt_text)
    }
    quoted_media?.forEach { item ->
        texts.appendNonEmptyLine(item.alt_text)
    }
    return texts.toString()
}

fun ParcelableStatus.generateFilterLinks(): Array<String> {
    val links = mutableSetOf<String>()
    spans?.mapNotNullTo(links) { span ->
        if (span.type != SpanItem.SpanType.LINK) return@mapNotNullTo null
        return@mapNotNullTo span.link
    }
    quoted_spans?.mapNotNullTo(links) { span ->
        if (span.type != SpanItem.SpanType.LINK) return@mapNotNullTo null
        return@mapNotNullTo span.link
    }
    return links.toTypedArray()
}

fun ParcelableStatus.updateExtraInformation(details: AccountDetails) {
    account_color = details.color
}

fun ParcelableStatus.contentDescription(context: Context, manager: UserColorNameManager,
        nameFirst: Boolean, displayInReplyTo: Boolean, showAbsoluteTime: Boolean): String {
    val displayName = manager.getDisplayName(this)
    val displayTime = if (is_retweet) retweet_timestamp else timestamp
    val timeLabel = ShortTimeView.getTimeLabel(context, displayTime, showAbsoluteTime)
    when {
        retweet_id != null -> {
            val retweetedBy = manager.getDisplayName(retweeted_by_user_key!!,
                    retweeted_by_user_name, retweeted_by_user_acct!!)
            return context.getString(R.string.content_description_item_status_retweet, retweetedBy, displayName,
                    timeLabel, text_unescaped)
        }
        in_reply_to_status_id != null && in_reply_to_user_key != null && displayInReplyTo -> {
            val inReplyTo = manager.getDisplayName(in_reply_to_user_key!!,
                    in_reply_to_name, in_reply_to_screen_name)
            return context.getString(R.string.content_description_item_status_reply, displayName, inReplyTo,
                    timeLabel, text_unescaped)
        }
        else -> return context.getString(R.string.content_description_item_status, displayName, timeLabel,
                text_unescaped)
    }
}

internal inline val String.plainText: String get() = HtmlEscapeHelper.toPlainText(this)

private fun parcelableUserMention(key: UserKey, name: String, screenName: String) = ParcelableUserMention().also {
    it.key = key
    it.name = name
    it.screen_name = screenName
}

private fun StringBuilder.appendNonEmptyLine(line: CharSequence?) {
    if (line.isNullOrEmpty()) return
    append(line)
    append('\n')
}