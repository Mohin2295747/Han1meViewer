package com.yenaly.han1meviewer.logic

import android.annotation.SuppressLint
import android.content.Context
import android.util.Log
import com.yenaly.han1meviewer.EMPTY_STRING
import com.yenaly.han1meviewer.HanimeConstants.HANIME_URL
import com.yenaly.han1meviewer.HanimeResolution
import com.yenaly.han1meviewer.LOCAL_DATE_FORMAT
import com.yenaly.han1meviewer.Preferences
import com.yenaly.han1meviewer.Preferences.isAlreadyLogin
import com.yenaly.han1meviewer.logic.entity.TranslationCache
import com.yenaly.han1meviewer.logic.exception.ParseException
import com.yenaly.han1meviewer.logic.model.HanimeInfo
import com.yenaly.han1meviewer.logic.model.HanimePreview
import com.yenaly.han1meviewer.logic.model.HanimeVideo
import com.yenaly.han1meviewer.logic.model.HomePage
import com.yenaly.han1meviewer.logic.model.MyListItems
import com.yenaly.han1meviewer.logic.model.MySubscriptions
import com.yenaly.han1meviewer.logic.model.Playlists
import com.yenaly.han1meviewer.logic.model.SubscriptionItem
import com.yenaly.han1meviewer.logic.model.SubscriptionVideosItem
import com.yenaly.han1meviewer.logic.model.VideoComments
import com.yenaly.han1meviewer.logic.state.PageLoadingState
import com.yenaly.han1meviewer.logic.state.VideoLoadingState
import com.yenaly.han1meviewer.logic.state.WebsiteState
import com.yenaly.han1meviewer.logic.TranslationManager
import com.yenaly.han1meviewer.toVideoCode
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.datetime.LocalDate
import org.json.JSONObject
import org.jsoup.Jsoup
import org.jsoup.nodes.Comment
import org.jsoup.nodes.Element
import org.jsoup.select.Elements

object Parser {

    object Regex {
        val videoSource = Regex("""const source = '(.+)'""")
        val viewAndUploadTime = Regex("""(觀看次數|观看次数)：(.+)次 *(\d{4}-\d{2}-\d{2})""")
    }

    private var translationContext: Context? = null

    fun initialize(context: Context) {
        translationContext = context.applicationContext
        Log.d("Parser", "Translation context initialized")
    }

    private suspend fun translateIfEnabled(
        text: String,
        contentType: TranslationCache.ContentType,
        videoCode: String? = null
    ): String {
        val context = translationContext ?: return text.also {
            Log.w("Parser", "Translation context is null, skipping translation")
        }
        if (!Preferences.isTranslationEnabled || text.isBlank()) {
            return text
        }

        return try {
            withContext(Dispatchers.IO) {
                val translationManager = TranslationManager.getInstance(context)
                translationManager.translate(text, contentType, videoCode)
            }
        } catch (e: Exception) {
            Log.e("Parser", "Translation failed for ${contentType.name}: ${e.message}")
            text
        }
    }

    private suspend fun translateTagsIfEnabled(
        tags: List<String>,
        videoCode: String? = null
    ): List<String> {
        val context = translationContext ?: return tags.also {
            Log.w("Parser", "Translation context is null, skipping tag translation")
        }
        if (!Preferences.isTranslationEnabled || !Preferences.translateTags || tags.isEmpty()) {
            return tags
        }

        return try {
            withContext(Dispatchers.IO) {
                val translationManager = TranslationManager.getInstance(context)
                translationManager.translateTags(tags, videoCode)
            }
        } catch (e: Exception) {
            Log.e("Parser", "Tag translation failed: ${e.message}")
            tags
        }
    }

    fun extractTokenFromLoginPage(body: String): String {
        val parseBody = Jsoup.parse(body).body()
        return parseBody.selectFirst("input[name=_token]")?.attr("value")
            ?: throw ParseException("Can't find csrf token from login page.")
    }

    suspend fun homePageVer2(body: String): WebsiteState<HomePage> {
        return withContext(Dispatchers.IO) {
            val isAVSite = Preferences.baseUrl == HANIME_URL[3]
            val parseBody = Jsoup.parse(body).body()
            val csrfToken = parseBody.selectFirst("input[name=_token]")?.attr("value")
            val homePageParse = parseBody.select("div[id=home-rows-wrapper] > div")

            val userInfo = parseBody.selectFirst("div[id=user-modal-dp-wrapper]")
            val avatarUrl: String? = userInfo?.selectFirst("img")?.absUrl("src")
            val username: String? = userInfo?.getElementById("user-modal-name")?.text()
            val userHomePageLink = parseBody.getElementById("user-modal-trigger")!!.attr("href")

            val userIdRegex = Regex("""/user/(\d+)""")
            val userId: String = userIdRegex.find(userHomePageLink)?.groupValues?.get(1) ?: ""

            val bannerCSS = parseBody.selectFirst("div[id=home-banner-wrapper]")
            val bannerImg = bannerCSS?.previousElementSibling()
            val bannerTitle = bannerImg?.selectFirst("img")?.attr("alt")
                .logIfParseNull(Parser::homePageVer2.name, "bannerTitle")
            val bannerPic = bannerImg?.select("img")?.let { imgList ->
                imgList.getOrNull(1)?.absUrl("src") ?: imgList.getOrNull(0)?.absUrl("src")
            }?.logIfParseNull(Parser::homePageVer2.name, "bannerPic")
            val bannerDesc = bannerCSS?.selectFirst("h4")?.ownText()
            val bannerVideoCodeScript = parseBody.select("script")
                .firstOrNull{ it.data().contains("watch?v=")}
                ?.data()
            val regex = Regex("""watch\?v=(\d+)""")
            var bannerVideoCode = bannerVideoCodeScript?.let { script ->
                regex.find(script)?.groupValues?.get(1)
            }
            if (bannerVideoCode == null) {
                bannerCSS?.traverse { node, _ ->
                    if (node is Comment) {
                        node.data.toVideoCode()?.let {
                            bannerVideoCode = it
                            return@traverse
                        }
                    }
                }
            }
            bannerVideoCode.logIfParseNull(Parser::homePageVer2.name, "bannerVideoCode")
            
            val translatedBannerTitle = bannerTitle?.let { 
                translateIfEnabled(it, TranslationCache.ContentType.TITLE, bannerVideoCode) 
            } ?: bannerTitle
            
            val banner = if (bannerTitle != null && bannerPic != null) {
                HomePage.Banner(
                    title = translatedBannerTitle ?: bannerTitle, 
                    description = bannerDesc,
                    picUrl = bannerPic, 
                    videoCode = bannerVideoCode,
                )
            } else null

            val latestReleaseClass = homePageParse.getOrNull(0)
            val latestUploadClass = homePageParse.getOrNull(1)
            val ecchiAnimeClass = homePageParse.getOrNull(2)
            val shortEpisodeAnimeClass = homePageParse.getOrNull(3)
            val motionAnimeClass = homePageParse.getOrNull(5)
            val threeDCGClass = homePageParse.getOrNull(6)
            val twoPointFiveDAnimeClass = homePageParse.getOrNull(7)
            val twoDAnimeClass = homePageParse.getOrNull(8)
            val aiGeneratedClass = homePageParse.getOrNull(10)
            val mmdClass = homePageParse.getOrNull(11)
            val cosplayClass = homePageParse.getOrNull(12)
            val watchingNowClass = homePageParse.getOrNull(13)

            val newAnimeTrailerClass = homePageParse.getOrNull(if (isAVSite) 13 else 12)

            val latestReleaseList = latestReleaseClass.extractHanimeInfo()
            val latestHanimeList = mutableListOf<HanimeInfo>()
            if (isAVSite){
                latestHanimeList.addAll(latestUploadClass.extractHanimeInfo())
            } else {
                latestHanimeList.addAll(latestUploadClass.extractHanimeInfo())
            }
            val ecchiAnimeList = ecchiAnimeClass.extractHanimeInfo()
            val shortEpisodeAnimeList = shortEpisodeAnimeClass.extractHanimeInfo()
            val motionAnimeList = motionAnimeClass.extractHanimeInfo()
            val threeDCGList = threeDCGClass.extractHanimeInfo()
            val twoPointFiveDAnimeList = twoPointFiveDAnimeClass.extractHanimeInfo()
            val twoDAnimeList = mutableListOf<HanimeInfo>()
            if (isAVSite){
                twoDAnimeList.addAll(twoDAnimeClass.extractHanimeInfo())
            } else {
                twoDAnimeList.addAll(twoDAnimeClass.extractHanimeInfo())
            }

            val aiGeneratedList = aiGeneratedClass.extractHanimeInfo()
            val mmdList = mmdClass.extractHanimeInfo()
            val cosplayList = cosplayClass.extractHanimeInfo()
            val watchingNowList = watchingNowClass.extractHanimeInfo()

            val newAnimeTrailerList = mutableListOf<HanimeInfo>()
            if (isAVSite){
                newAnimeTrailerList.addAll(newAnimeTrailerClass.extractHanimeInfo())
            } else {
                val newAnimeTrailerItems =
                    newAnimeTrailerClass?.select("a")
                newAnimeTrailerItems?.forEach { newAnimeTrailerItem ->
                    val videoCode = newAnimeTrailerItem.attr("href").toVideoCode()
                    val coverUrl = newAnimeTrailerItem.selectFirst("img")?.attr("src")
                    val title = newAnimeTrailerItem.selectFirst("div.home-rows-videos-title")?.text()
                    if (title == null || coverUrl == null || videoCode == null) return@forEach
                    
                    val translatedTitle = translateIfEnabled(title, TranslationCache.ContentType.TITLE, videoCode)
                    
                    newAnimeTrailerList.add(
                        HanimeInfo(
                            title = translatedTitle,
                            coverUrl = coverUrl,
                            videoCode = videoCode,
                            duration = "",
                            currentArtist = null,
                            views = null,
                            uploadTime = null,
                            genre = null,
                            itemType = HanimeInfo.SIMPLIFIED
                        )
                    )
                }
            }

            WebsiteState.Success(
                HomePage(
                    csrfToken,
                    avatarUrl, username, banner = banner,
                    latestHanime = latestHanimeList,
                    latestRelease = latestReleaseList,
                    ecchiAnime = ecchiAnimeList,
                    shortEpisodeAnime = shortEpisodeAnimeList,
                    twoPointFiveDAnime = twoPointFiveDAnimeList,
                    threeDCG = threeDCGList,
                    motionAnime = motionAnimeList,
                    twoDAnime = twoDAnimeList,
                    aiGenerated = aiGeneratedList,
                    mmd = mmdList,
                    cosplay = cosplayList,
                    watchingNow = watchingNowList,
                    newAnimeTrailer = newAnimeTrailerList,
                    userId = userId
                )
            )
        }
    }
    
    private suspend fun Element?.extractHanimeInfo(selector: String = "div[class^=horizontal-card]"): MutableList<HanimeInfo> {
        return withContext(Dispatchers.IO) {
            val resultList = mutableListOf<HanimeInfo>()
            this@extractHanimeInfo?.select(selector)?.forEach { item ->
                hanimeNormalItemVer2(item)?.let { hanimeInfo ->
                    resultList.add(hanimeInfo)
                }
            }
            resultList
        }
    }

    suspend fun hanimeSearch(body: String): PageLoadingState<MutableList<HanimeInfo>> {
        return withContext(Dispatchers.IO) {
            val parseBody = Jsoup.parse(body).body()
            val allContentsClass =
                parseBody.getElementsByClass("content-padding-new").firstOrNull()
            val allSimplifiedContentsClass =
                parseBody.getElementsByClass("home-rows-videos-wrapper").firstOrNull()

            if (allContentsClass != null) {
                hanimeSearchNormalVer2(allContentsClass)
            } else if (allSimplifiedContentsClass != null) {
                hanimeSearchSimplified(allSimplifiedContentsClass)
            } else {
                PageLoadingState.Success(mutableListOf())
            }
        }
    }

    private suspend fun hanimeNormalItemVer2(hanimeSearchItem: Element): HanimeInfo? {
        val title =
            hanimeSearchItem.selectFirst("div.title, h4.video-title")?.text()?.trim()
                .logIfParseNull(Parser::hanimeNormalItemVer2.name, "title")
        val coverUrl =
            hanimeSearchItem.select("img").getOrNull(0)?.absUrl("src")
                .logIfParseNull(Parser::hanimeNormalItemVer2.name, "coverUrl")
        val videoCode =
            hanimeSearchItem.select("a").getOrNull(0)?.absUrl("href")?.toVideoCode()
                .logIfParseNull(Parser::hanimeNormalItemVer2.name, "videoCode")
        if (title == null || coverUrl == null || videoCode == null) return null
        
        val durationAndViews = hanimeSearchItem.select("div[class^=thumb-container]")
        val duration = durationAndViews.select("div[class^=duration]").text()
        val views = durationAndViews.select("div[class^=stat-item]").getOrNull(1)?.text()
        val artistAndUploadTime = hanimeSearchItem.selectFirst("div.subtitle a, div.video-meta-data a")!!.text().trim()
        var artist = ""
        var uploadTime = ""
        if (artistAndUploadTime.contains("•")) {
            val parts = artistAndUploadTime.split("•").map { it.trim() }
            artist = parts[0].trim()
            uploadTime = parts[1].trim()
        }
        val infoBoxes = hanimeSearchItem.selectFirst(".stats-container .stat-item")
        val fullText = infoBoxes?.text() ?: ""
        val reviews = fullText.replace("thumb_up", "").trim()
        
        val translatedTitle = translateIfEnabled(title, TranslationCache.ContentType.TITLE, videoCode)
        
        val translatedArtist = if (artist.isNotBlank()) {
            translateIfEnabled(artist, TranslationCache.ContentType.ARTIST_NAME, videoCode)
        } else artist
        
        return HanimeInfo(
            title = translatedTitle,
            coverUrl = coverUrl,
            videoCode = videoCode,
            duration = duration.logIfParseNull(Parser::hanimeNormalItemVer2.name, "duration"),
            currentArtist = translatedArtist,
            views = views.logIfParseNull(Parser::hanimeNormalItemVer2.name, "views"),
            uploadTime = uploadTime,
            genre = null,
            itemType = HanimeInfo.NORMAL,
            reviews = reviews
        )
    }

    private suspend fun hanimeSimplifiedItem(hanimeSearchItem: Element): HanimeInfo? {
        val videoCode = hanimeSearchItem.attr("href").toVideoCode()
            .logIfParseNull(Parser::hanimeSimplifiedItem.name, "videoCode")
        val coverUrl = hanimeSearchItem.selectFirst("img")?.attr("src")
            .logIfParseNull(Parser::hanimeSimplifiedItem.name, "coverUrl")
        val title = hanimeSearchItem.selectFirst("div[class=home-rows-videos-title]")?.text()
            .logIfParseNull(Parser::hanimeSimplifiedItem.name, "title")
        if (videoCode == null || coverUrl == null || title == null) return null
        
        val translatedTitle = translateIfEnabled(title, TranslationCache.ContentType.TITLE, videoCode)
        
        return HanimeInfo(
            title = translatedTitle,
            coverUrl = coverUrl,
            videoCode = videoCode,
            itemType = HanimeInfo.SIMPLIFIED
        )
    }

    private suspend fun hanimeSearchNormalVer2(
        allContentsClass: Element,
    ): PageLoadingState<MutableList<HanimeInfo>> {
        return withContext(Dispatchers.IO) {
            val hanimeSearchList = mutableListOf<HanimeInfo>()
            val hanimeSearchItems =
                allContentsClass.select("div[class^=horizontal-card]")
            if (hanimeSearchItems.isEmpty()) {
                PageLoadingState.NoMoreData
            } else {
                hanimeSearchItems.forEach { hanimeSearchItem ->
                    hanimeNormalItemVer2(hanimeSearchItem)?.let(hanimeSearchList::add)
                }
                PageLoadingState.Success(hanimeSearchList)
            }
        }
    }

    private suspend fun hanimeSearchSimplified(
        allSimplifiedContentsClass: Element,
    ): PageLoadingState<MutableList<HanimeInfo>> {
        return withContext(Dispatchers.IO) {
            val hanimeSearchList = mutableListOf<HanimeInfo>()
            val hanimeSearchItems = allSimplifiedContentsClass.children()
            if (hanimeSearchItems.isEmpty()) {
                PageLoadingState.NoMoreData
            } else {
                hanimeSearchItems.forEach { hanimeSearchItem ->
                    hanimeSimplifiedItem(hanimeSearchItem)?.let(hanimeSearchList::add)
                }
                PageLoadingState.Success(hanimeSearchList)
            }
        }
    }

    suspend fun hanimeVideoVer2(body: String): VideoLoadingState<HanimeVideo> {
        return withContext(Dispatchers.IO) {
            val parseBody = Jsoup.parse(body).body()
            val csrfToken = parseBody.selectFirst("input[name=_token]")?.attr("value")
            val currentUserId =
                parseBody.selectFirst("input[name=like-user-id]")?.attr("value")
            val title = parseBody.getElementById("shareBtn-title")?.text()
                .throwIfParseNull(Parser::hanimeVideoVer2.name, "title")

            var likeStatus = parseBody.selectFirst("[name=like-status]")
                ?.attr("value")
            if (!likeStatus.isNullOrEmpty()) {
                likeStatus = "1"
            }
            val likesCount = parseBody.selectFirst("input[name=likes-count]")
                ?.attr("value")?.toIntOrNull()
            val videoDetailWrapper = parseBody.selectFirst("div[class=video-details-wrapper]")
            val videoCaptionText = videoDetailWrapper?.selectFirst("div[class^=video-caption-text]")
            val chineseTitle = videoCaptionText?.previousElementSibling()?.ownText()
            val introduction = videoCaptionText?.ownText()
            val uploadTimeWithViews = videoDetailWrapper?.selectFirst("div > div > div")?.text()
            val uploadTimeWithViewsGroups = uploadTimeWithViews?.let {
                Regex.viewAndUploadTime.find(it)?.groups
            }
            val uploadTime = uploadTimeWithViewsGroups?.get(3)?.value?.let { time ->
                runCatching {
                    LocalDate.parse(time, LOCAL_DATE_FORMAT)
                }.getOrNull()
            }

            val views = uploadTimeWithViewsGroups?.get(2)?.value

            val tags = parseBody.getElementsByClass("single-video-tag")
            val tagListWithLikeNum = mutableListOf<String>()
            tags.forEach { tag ->
                val child = tag.childOrNull(0)
                if (child != null && child.hasAttr("href")) {
                    tagListWithLikeNum.add(child.text())
                }
            }
            val tagList = tagListWithLikeNum.map {
                it.substringBefore(" (")
                    .removePrefix("#")
                    .trim()
            }
            
            val translatedTags = translateTagsIfEnabled(tagList)
            
            val myListCheckboxWrapper = parseBody.select("div[class~=playlist-checkbox-wrapper]")
            val myListInfo = mutableListOf<HanimeVideo.MyList.MyListInfo>()
            myListCheckboxWrapper.forEach {
                val listTitle = it.selectFirst("span")?.ownText()
                    .logIfParseNull(Parser::hanimeVideoVer2.name, "myListTitle", loginNeeded = true)
                val listInput = it.selectFirst("input")
                val listCode = listInput?.attr("id")
                    .logIfParseNull(Parser::hanimeVideoVer2.name, "myListCode", loginNeeded = true)
                val isSelected = listInput?.hasAttr("checked") == true
                if (listTitle != null && listCode != null) {
                    myListInfo += HanimeVideo.MyList.MyListInfo(
                        code = listCode, title = listTitle, isSelected = isSelected
                    )
                }
            }
            val isWatchLater = parseBody.getElementById("playlist-save-checkbox")
                ?.selectFirst("input")?.hasAttr("checked") == true
            val myList = HanimeVideo.MyList(isWatchLater = isWatchLater, myListInfo = myListInfo)

            val playlistWrapper = parseBody.selectFirst("div[id=video-playlist-wrapper]")
            val playlist = playlistWrapper?.let {
                val playlistVideoList = mutableListOf<HanimeInfo>()
                val playlistName = it.selectFirst("div > div > h4")?.text()
                val playlistScroll = it.getElementById("playlist-scroll")
                playlistScroll?.children()?.forEach { parent ->
                    if (parent.tagName() == "a") {
                        return@forEach
                    }
                    val videoCode = parent.selectFirst("div > a")?.absUrl("href")?.toVideoCode()
                        .throwIfParseNull(Parser::hanimeVideoVer2.name, "videoCode")
                    val cardMobilePanel = parent.selectFirst("div[class^=card-mobile-panel]")
                    val eachTitleCover = cardMobilePanel?.select("div > div > div > img")?.getOrNull(1)
                    val eachIsPlaying = cardMobilePanel?.select("div > div > div > div")
                        ?.firstOrNull()
                        ?.text()
                        ?.contains("播放") == true
                    val cardMobileDuration = cardMobilePanel?.select("div[class*=card-mobile-duration]")
                    val eachDuration = cardMobileDuration?.firstOrNull()?.text()
                    val eachViews = cardMobileDuration?.getOrNull(2)?.text()
                        ?.substringBefore("次")
                    val playlistEachCoverUrl = eachTitleCover?.absUrl("src")
                        .throwIfParseNull(Parser::hanimeVideoVer2.name, "playlistEachCoverUrl")
                    val playlistEachTitle = eachTitleCover?.attr("alt")
                        .throwIfParseNull(Parser::hanimeVideoVer2.name, "playlistEachTitle")
                        
                    val translatedPlaylistTitle = translateIfEnabled(playlistEachTitle, TranslationCache.ContentType.TITLE, videoCode)
                    
                    playlistVideoList.add(
                        HanimeInfo(
                            title = translatedPlaylistTitle, 
                            coverUrl = playlistEachCoverUrl,
                            videoCode = videoCode,
                            duration = eachDuration.logIfParseNull(
                                Parser::hanimeVideoVer2.name,
                                "$playlistEachTitle duration"
                            ),
                            views = eachViews.logIfParseNull(
                                Parser::hanimeVideoVer2.name,
                                "$playlistEachTitle views"
                            ),
                            isPlaying = eachIsPlaying,
                            itemType = HanimeInfo.NORMAL
                        )
                    )
                }
                HanimeVideo.Playlist(playlistName = playlistName, video = playlistVideoList)
            }

            val relatedAnimeList = mutableListOf<HanimeInfo>()
            val relatedTabContent = parseBody.getElementById("related-tabcontent")

            relatedTabContent?.also {
                val children = it.childOrNull(0)?.children()
                val isSimplified =
                    children?.getOrNull(0)?.select("a")?.getOrNull(0)
                        ?.getElementsByClass("home-rows-videos-div")
                        ?.firstOrNull() != null
                if (isSimplified) {
                    for (each in children) {
                        val eachContent = each.selectFirst("a")
                        val homeRowsVideosDiv =
                            eachContent?.getElementsByClass("home-rows-videos-div")?.firstOrNull()

                        if (homeRowsVideosDiv != null) {
                            val eachVideoCode = eachContent.absUrl("href").toVideoCode() ?: continue
                            val eachCoverUrl = homeRowsVideosDiv.selectFirst("img")?.absUrl("src")
                                .throwIfParseNull(Parser::hanimeVideoVer2.name, "eachCoverUrl")
                            val eachTitle =
                                homeRowsVideosDiv.selectFirst("div[class$=title]")?.text()
                                    .throwIfParseNull(Parser::hanimeVideoVer2.name, "eachTitle")
                            
                            val translatedEachTitle = translateIfEnabled(eachTitle, TranslationCache.ContentType.TITLE, eachVideoCode)
                            
                            relatedAnimeList.add(
                                HanimeInfo(
                                    title = translatedEachTitle, 
                                    coverUrl = eachCoverUrl,
                                    videoCode = eachVideoCode,
                                    itemType = HanimeInfo.SIMPLIFIED
                                )
                            )
                        }
                    }
                } else {
                    it.extractHanimeInfo().forEach { info ->
                        val translatedTitle = translateIfEnabled(info.title, TranslationCache.ContentType.TITLE, info.videoCode)
                        relatedAnimeList.add(info.copy(title = translatedTitle))
                    }
                }
            }

            val hanimeResolution = HanimeResolution()
            val videoClass = parseBody.selectFirst("video[id=player]")
            val videoCoverUrl = videoClass?.absUrl("poster").orEmpty()
            val videos = videoClass?.children()
            if (!videos.isNullOrEmpty()) {
                videos.forEach { source ->
                    val resolution = source.attr("size") + "P"
                    val sourceUrl = source.absUrl("src")
                    val videoType = source.attr("type")
                    hanimeResolution.parseResolution(resolution, sourceUrl, videoType)
                }
            } else {
                val playerDivWrapper = parseBody.selectFirst("div[id=player-div-wrapper]")
                playerDivWrapper?.select("script")?.let { scripts ->
                    for (script in scripts) {
                        val data = script.data()
                        if (data.isBlank()) continue
                        val result =
                            Regex.videoSource.find(data)?.groups?.get(1)?.value ?: continue
                        hanimeResolution.parseResolution(null, result)
                        break
                    }
                }
            }

            val artistAvatarUrl = parseBody
                .select("div.video-details-wrapper > div > a > div > img[style*='position: absolute'][style*='border-radius: 50%']")
                .attr("src")
            val artistNameCSS = parseBody.getElementById("video-artist-name")
            val artistGenre = artistNameCSS?.nextElementSibling()?.text()?.trim()
            val artistName = artistNameCSS?.text()?.trim()
            val postCSS = parseBody.getElementById("video-subscribe-form")
            val post = postCSS?.let {
                val userId = it.selectFirst("input[name=subscribe-user-id]")?.attr("value")
                val artistId = it.selectFirst("input[name=subscribe-artist-id]")?.attr("value")
                val isSubscribed = it.selectFirst("input[name=subscribe-status]")?.attr("value")
                if (userId != null && artistId != null && isSubscribed != null) {
                    HanimeVideo.Artist.POST(
                        userId = userId,
                        artistId = artistId,
                        isSubscribed = isSubscribed == "1"
                    )
                } else null
            }
            
            val translatedArtistName = artistName?.let {
                translateIfEnabled(it, TranslationCache.ContentType.ARTIST_NAME)
            } ?: artistName
            
            val translatedArtistGenre = artistGenre?.let {
                translateIfEnabled(it, TranslationCache.ContentType.OTHER)
            } ?: artistGenre
            
            val artist = if (artistName != null && artistGenre != null) {
                HanimeVideo.Artist(
                    name = translatedArtistName ?: artistName,
                    avatarUrl = artistAvatarUrl,
                    genre = translatedArtistGenre ?: artistGenre,
                    post = post,
                )
            } else null
            val originalComic = parseBody.selectFirst("a.video-comic-btn")?.attr("href")

            val translatedTitle = translateIfEnabled(title, TranslationCache.ContentType.TITLE)
            
            val translatedChineseTitle = chineseTitle?.let {
                translateIfEnabled(it, TranslationCache.ContentType.DESCRIPTION)
            }
            
            val translatedIntroduction = introduction?.let {
                translateIfEnabled(it, TranslationCache.ContentType.DESCRIPTION)
            }

            VideoLoadingState.Success(
                HanimeVideo(
                    title = translatedTitle, 
                    coverUrl = videoCoverUrl,
                    chineseTitle = translatedChineseTitle?.logIfParseNull(
                        Parser::hanimeVideoVer2.name,
                        "chineseTitle"
                    ),
                    uploadTime = uploadTime.logIfParseNull(Parser::hanimeVideoVer2.name, "uploadTime"),
                    views = views.logIfParseNull(Parser::hanimeVideoVer2.name, "views"),
                    introduction = translatedIntroduction?.logIfParseNull(
                        Parser::hanimeVideoVer2.name,
                        "introduction"
                    ),
                    videoUrls = hanimeResolution.toResolutionLinkMap(),
                    tags = translatedTags,
                    myList = myList,
                    playlist = playlist,
                    relatedHanimes = relatedAnimeList,
                    artist = artist.logIfParseNull(Parser::hanimeVideoVer2.name, "artist"),
                    favTimes = likesCount,
                    isFav = likeStatus == "1",
                    csrfToken = csrfToken,
                    currentUserId = currentUserId,
                    originalComic = originalComic
                )
            )
        }
    }

    suspend fun hanimePreview(body: String): WebsiteState<HanimePreview> {
        return withContext(Dispatchers.IO) {
            val parseBody = Jsoup.parse(body).body()

            val latestHanimeList = mutableListOf<HanimeInfo>()
            val latestHanimeClass = parseBody.selectFirst("div[class$=owl-theme]")
            latestHanimeClass?.let {
                val latestHanimeItems = latestHanimeClass.select("div[class=home-rows-videos-div]")
                latestHanimeItems.forEach { latestHanimeItem ->
                    val coverUrl = latestHanimeItem.selectFirst("img")?.absUrl("src")
                        .throwIfParseNull(Parser::hanimePreview.name, "coverUrl")
                    val title = latestHanimeItem.selectFirst("div[class$=title]")?.text()
                        .throwIfParseNull(Parser::hanimePreview.name, "title")
                        
                    val translatedTitle = translateIfEnabled(title, TranslationCache.ContentType.TITLE)
                    
                    latestHanimeList.add(
                        HanimeInfo(
                            coverUrl = coverUrl,
                            title = translatedTitle,
                            videoCode = EMPTY_STRING,
                            itemType = HanimeInfo.SIMPLIFIED
                        )
                    )
                }
            }

            val contentPaddingClass = parseBody.select("div[class=content-padding] > div")
            val previewInfo = mutableListOf<HanimePreview.PreviewInfo>()
            for (i in 0 until contentPaddingClass.size / 2) {

                val firstPart = contentPaddingClass.getOrNull(i * 2)
                val secondPart = contentPaddingClass.getOrNull(i * 2 + 1)

                val videoCode = firstPart?.id()
                val title = firstPart?.selectFirst("h4")?.text()
                val coverUrl =
                    firstPart?.selectFirst("div[class=preview-info-cover] > img")?.absUrl("src")
                val previewInfoContentClass =
                    firstPart?.getElementsByClass("preview-info-content-padding")?.firstOrNull()
                val videoTitle = previewInfoContentClass?.selectFirst("h4")?.text()
                val brand = previewInfoContentClass?.selectFirst("h5")?.selectFirst("a")?.text()
                val releaseDate = previewInfoContentClass?.select("h5")?.getOrNull(1)?.ownText()

                val introduction = secondPart?.selectFirst("h5")?.text()
                val tagClass = secondPart?.select("div[class=single-video-tag] > a")
                val tags = mutableListOf<String>()
                tagClass?.forEach { tag: Element? ->
                    tag?.let { tags.add(tag.text()) }
                }
                val relatedPicClass = secondPart?.select("img[class=preview-image-modal-trigger]")
                val relatedPics = mutableListOf<String>()
                relatedPicClass?.forEach { relatedPic: Element? ->
                    relatedPic?.let { relatedPics.add(relatedPic.absUrl("src")) }
                }
                
                val translatedTitle = title?.let {
                    translateIfEnabled(it, TranslationCache.ContentType.TITLE, videoCode)
                } ?: title
                
                val translatedVideoTitle = videoTitle?.let {
                    translateIfEnabled(it, TranslationCache.ContentType.TITLE, videoCode)
                } ?: videoTitle
                
                val translatedBrand = brand?.let {
                    translateIfEnabled(it, TranslationCache.ContentType.OTHER, videoCode)
                } ?: brand
                
                val translatedIntroduction = introduction?.let {
                    translateIfEnabled(it, TranslationCache.ContentType.DESCRIPTION, videoCode)
                } ?: introduction
                
                val translatedTags = if (tags.isNotEmpty()) {
                    translateTagsIfEnabled(tags, videoCode)
                } else tags

                previewInfo.add(
                    HanimePreview.PreviewInfo(
                        title = translatedTitle,
                        videoTitle = translatedVideoTitle,
                        coverUrl = coverUrl,
                        introduction = translatedIntroduction.logIfParseNull(
                            Parser::hanimePreview.name,
                            "$title introduction"
                        ),
                        brand = translatedBrand.logIfParseNull(Parser::hanimePreview.name, "$title brand"),
                        releaseDate = releaseDate.logIfParseNull(
                            Parser::hanimePreview.name,
                            "$title releaseDate"
                        ),
                        videoCode = videoCode.logIfParseNull(
                            Parser::hanimePreview.name,
                            "$title videoCode"
                        ),
                        tags = translatedTags,
                        relatedPicsUrl = relatedPics
                    )
                )
            }

            val header = parseBody.selectFirst("div[id=player-div-wrapper]")
            val headerPicUrl = header?.selectFirst("img")?.absUrl("src")
            val hasPrevious = parseBody.getElementsByClass("hidden-md hidden-lg").firstOrNull()
                ?.select("div[style*=left]")?.firstOrNull() != null
            val hasNext = parseBody.getElementsByClass("hidden-md hidden-lg").firstOrNull()
                ?.select("div[style*=right]")?.firstOrNull() != null

            WebsiteState.Success(
                HanimePreview(
                    headerPicUrl = headerPicUrl.logIfParseNull(
                        Parser::hanimePreview.name,
                        "headerPicUrl"
                    ),
                    hasPrevious = hasPrevious,
                    hasNext = hasNext,
                    latestHanime = latestHanimeList,
                    previewInfo = previewInfo
                )
            )
        }
    }

    suspend fun myListItems(body: String): PageLoadingState<MyListItems<HanimeInfo>> {
        return withContext(Dispatchers.IO) {
            val parseBody = Jsoup.parse(body).body()
            val csrfToken = parseBody.selectFirst("input[name=_token]")?.attr("value")
            val desc = parseBody.getElementById("playlist-show-description")?.ownText()
            val allHanimeClass = parseBody.getElementsByClass("horizontal-row").firstOrNull()
            val myListHanimeList = allHanimeClass.extractHanimeInfo("div[class^=user-tab-item-wrapper]")
            
            val translatedDesc = desc?.let {
                translateIfEnabled(it, TranslationCache.ContentType.DESCRIPTION)
            } ?: desc

            PageLoadingState.Success(
                MyListItems(
                    myListHanimeList,
                    desc = translatedDesc,
                    csrfToken = csrfToken
                )
            )
        }
    }

    suspend fun myPlayListItems(body: String): PageLoadingState<MyListItems<HanimeInfo>> {
        return withContext(Dispatchers.IO) {
            val parseBody = Jsoup.parse(body).body()
            val csrfToken = parseBody.selectFirst("input[name=_token]")?.attr("value")
            val desc = parseBody.select("p.playlist-description").first()?.text()
            val allHanimeClass = parseBody.getElementsByClass("playlist-video-list").firstOrNull()
            val myListHanimeList = allHanimeClass.extractHanimeInfo("div[class^=user-tab-item-wrapper]")
            
            val translatedDesc = desc?.let {
                translateIfEnabled(it, TranslationCache.ContentType.DESCRIPTION)
            } ?: desc

            PageLoadingState.Success(
                MyListItems(
                    myListHanimeList,
                    desc = translatedDesc,
                    csrfToken = csrfToken
                )
            )
        }
    }

    suspend fun playlists(body: String): WebsiteState<Playlists> {
        return withContext(Dispatchers.IO) {
            val parseBody = Jsoup.parse(body).body()
            val csrfToken = parseBody.selectFirst("input[name=_token]")?.attr("value")
            val lists = parseBody.getElementsByClass("user-tab-item-wrapper")
            val playlists = mutableListOf<Playlists.Playlist>()
            lists.forEach {
                val listCode = it.selectFirst("a[class=video-link]")?.absUrl("href")?.substringAfter('=')
                    .throwIfParseNull(Parser::playlists.name, "listCode")
                val listTitle = it.selectFirst("div[class=title]")?.ownText()
                    .throwIfParseNull(Parser::playlists.name, "listTitle")
                val listTotal = it.selectFirst("div[class=stat-item]")?.text()
                val formatedTotal = listTotal?.filter { char -> char.isDigit() }?.toIntOrNull() ?: -1
                val coverUrl = it.select("img[class=main-thumb]").first()?.attr("src")
                
                val translatedListTitle = translateIfEnabled(listTitle, TranslationCache.ContentType.OTHER, listCode)
                
                playlists += Playlists.Playlist(
                    listCode = listCode, 
                    title = translatedListTitle, 
                    total = formatedTotal, 
                    coverUrl = coverUrl
                )
            }
            WebsiteState.Success(Playlists(playlists = playlists, csrfToken = csrfToken))
        }
    }

    @SuppressLint("BuildListAdds")
    suspend fun comments(body: String): WebsiteState<VideoComments> {
        return withContext(Dispatchers.IO) {
            val jsonObject = JSONObject(body)
            val commentBody = jsonObject.get("comments").toString()
            val parseBody = Jsoup.parse(commentBody).body()
            val csrfToken = parseBody.selectFirst("input[name=_token]")?.attr("value")
            val currentUserId = parseBody.selectFirst("input[name=comment-user-id]")?.attr("value")
            val commentList = mutableListOf<VideoComments.VideoComment>()
            val allCommentsClass = parseBody.getElementById("comment-start")

            buildList {
                allCommentsClass?.children()?.chunked(4)?.forEach { elements ->
                    add(Element("div").apply { appendChildren(elements) })
                }
            }.forEach { child: Element ->
                val avatarUrl = child.selectFirst("img")?.absUrl("src")
                    .throwIfParseNull(Parser::comments.name, "avatarUrl")
                val textClass = child.getElementsByClass("comment-index-text")
                val nameAndDateClass = textClass.firstOrNull()
                val username = nameAndDateClass?.selectFirst("a")?.ownText()?.trim()
                    .throwIfParseNull(Parser::comments.name, "username")
                val date = nameAndDateClass?.selectFirst("span")?.ownText()?.trim()
                    .throwIfParseNull(Parser::comments.name, "date")
                val content = textClass.getOrNull(1)?.text()
                    .throwIfParseNull(Parser::comments.name, "content")
                val hasMoreReplies = child.selectFirst("div[class^=load-replies-btn]") != null
                val thumbUp = child.getElementById("comment-like-form-wrapper")
                    ?.select("span[style]")?.getOrNull(1)
                    ?.text()?.toIntOrNull()
                val id = child.selectFirst("div[id^=reply-section-wrapper]")
                    ?.id()?.substringAfterLast("-")

                val foreignId = child.getElementById("foreign_id")?.attr("value")
                val isPositive = child.getElementById("is_positive")?.attr("value")
                val likeUserId = child.selectFirst("input[name=comment-like-user-id]")?.attr("value")
                val commentLikesCount =
                    child.selectFirst("input[name=comment-likes-count]")?.attr("value")
                val commentLikesSum = child.selectFirst("input[name=comment-likes-sum]")?.attr("value")
                val likeCommentStatus =
                    child.selectFirst("input[name=like-comment-status]")?.attr("value")
                val unlikeCommentStatus =
                    child.selectFirst("input[name=unlike-comment-status]")?.attr("value")

                val post = VideoComments.VideoComment.POST(
                    foreignId.logIfParseNull(Parser::comments.name, "foreignId", loginNeeded = true),
                    isPositive == "1",
                    likeUserId.logIfParseNull(Parser::comments.name, "likeUserId", loginNeeded = true),
                    commentLikesCount?.toIntOrNull().logIfParseNull(
                        Parser::comments.name,
                        "commentLikesCount", loginNeeded = true
                    ),
                    commentLikesSum?.toIntOrNull().logIfParseNull(
                        Parser::comments.name,
                        "commentLikesSum", loginNeeded = true
                    ),
                    likeCommentStatus == "1",
                    unlikeCommentStatus == "1",
                )
                val regex = """\d+""".toRegex()
                val replyCountText = child.select("div.load-replies-btn").text()
                val replyCount = regex.find(replyCountText)?.value?.toInt()
                val reportRedirectUrl = ""
                val reportableId = child.select("span.report-btn").first()?.attr("data-reportable-id")
                val reportableType = child.select("span.report-btn").first()?.attr("data-reportable-type")
                
                val translatedContent = translateIfEnabled(content, TranslationCache.ContentType.COMMENT, id)
                
                commentList.add(
                    VideoComments.VideoComment(
                        avatar = avatarUrl, 
                        username = username, 
                        date = date,
                        content = translatedContent, 
                        hasMoreReplies = hasMoreReplies, 
                        replyCount = replyCount,
                        thumbUp = thumbUp.logIfParseNull(Parser::comments.name, "thumbUp"),
                        id = id.logIfParseNull(Parser::comments.name, "id"),
                        isChildComment = false, 
                        post = post,
                        redirectUrl = reportRedirectUrl, 
                        reportableId = reportableId, 
                        reportableType = reportableType
                    )
                )
            }
            WebsiteState.Success(
                VideoComments(
                    commentList,
                    currentUserId,
                    csrfToken
                )
            )
        }
    }

    suspend fun commentReply(body: String): WebsiteState<VideoComments> {
        return withContext(Dispatchers.IO) {
            val jsonObject = JSONObject(body)
            val replyBody = jsonObject.get("replies").toString()
            val replyList = mutableListOf<VideoComments.VideoComment>()
            val parseBody = Jsoup.parse(replyBody).body()
            val replyStart = parseBody.selectFirst("div[id^=reply-start]")
            replyStart?.let {
                val allRepliesClass = it.children()
                for (i in allRepliesClass.indices step 2) {
                    val basicClass = allRepliesClass.getOrNull(i)
                    val postClass = allRepliesClass.getOrNull(i + 1)

                    val avatarUrl = basicClass?.selectFirst("img")?.absUrl("src")
                        .throwIfParseNull(Parser::commentReply.name, "avatarUrl")
                    val textClass = basicClass?.getElementsByClass("comment-index-text")
                    val nameAndDateClass = textClass?.firstOrNull()
                    val username = nameAndDateClass?.selectFirst("a")?.ownText()?.trim()
                        .throwIfParseNull(Parser::commentReply.name, "name")
                    val date = nameAndDateClass?.selectFirst("span")?.ownText()?.trim()
                        .throwIfParseNull(Parser::commentReply.name, "date")
                    val content = textClass?.getOrNull(1)?.text()
                        .throwIfParseNull(Parser::commentReply.name, "content")
                    val thumbUp = postClass
                        ?.select("span[style]")?.getOrNull(1)
                        ?.text()?.toIntOrNull()

                    val foreignId =
                        postClass?.getElementById("foreign_id")?.attr("value")
                    val isPositive =
                        postClass?.getElementById("is_positive")?.attr("value")
                    val likeUserId =
                        postClass?.selectFirst("input[name=comment-like-user-id]")?.attr("value")
                    val commentLikesCount =
                        postClass?.selectFirst("input[name=comment-likes-count]")?.attr("value")
                    val commentLikesSum =
                        postClass?.selectFirst("input[name=comment-likes-sum]")?.attr("value")
                    val likeCommentStatus =
                        postClass?.selectFirst("input[name=like-comment-status]")?.attr("value")
                    val unlikeCommentStatus =
                        postClass?.selectFirst("input[name=unlike-comment-status]")?.attr("value")
                    val post = VideoComments.VideoComment.POST(
                        foreignId.logIfParseNull(
                            Parser::commentReply.name,
                            "foreignId",
                            loginNeeded = true
                        ),
                        isPositive == "1",
                        likeUserId.logIfParseNull(
                            Parser::commentReply.name,
                            "likeUserId",
                            loginNeeded = true
                        ),
                        commentLikesCount?.toIntOrNull().logIfParseNull(
                            Parser::commentReply.name,
                            "commentLikesCount", loginNeeded = true
                        ),
                        commentLikesSum?.toIntOrNull().logIfParseNull(
                            Parser::commentReply.name,
                            "commentLikesSum", loginNeeded = true
                        ),
                        likeCommentStatus == "1",
                        unlikeCommentStatus == "1",
                    )
                    val reportRedirectUrl = ""
                    val reportableId = basicClass?.select("span.report-btn")?.first()?.attr("data-reportable-id")
                    val reportableType = basicClass?.select("span.report-btn")?.first()?.attr("data-reportable-type")
                    
                    val translatedContent = content?.let {
                        translateIfEnabled(it, TranslationCache.ContentType.COMMENT)
                    } ?: content
                    
                    replyList.add(
                        VideoComments.VideoComment(
                            avatar = avatarUrl, 
                            username = username, 
                            date = date,
                            content = translatedContent,
                            thumbUp = thumbUp.logIfParseNull(Parser::commentReply.name, "thumbUp"),
                            id = null,
                            isChildComment = true, 
                            post = post, 
                            reportableId = reportableId,
                            reportableType = reportableType, 
                            redirectUrl = reportRedirectUrl
                        )
                    )
                }
            }

            WebsiteState.Success(VideoComments(replyList))
        }
    }

    suspend fun reportCommentResponse(body: String): WebsiteState<String> {
        return withContext(Dispatchers.IO) {
            if (body.contains("已成功檢舉該則評論")) {
                WebsiteState.Success("已成功檢舉該則評論，我們會儘快處理您的檢舉。")
            } else {
                val doc = Jsoup.parse(body)
                val msg = doc.select("#error").text()
                if (msg.contains("已成功檢舉")) {
                    WebsiteState.Success(msg)
                } else {
                    WebsiteState.Error(Throwable("举报失败或未检测到成功提示"))
                }
            }
        }
    }

    suspend fun getMySubscriptions(body: String): WebsiteState<MySubscriptions> {
        return withContext(Dispatchers.IO) {
            val parseBody = Jsoup.parse(body).body()
            val maxPage = parseMaxPage(parseBody)
            val subscriptionsRoot = parseBody.selectFirst("div.subscriptions-nav")
                ?: return@withContext WebsiteState.Error(IllegalStateException("找不到 subscriptions-nav"))
            val subscriptionsVideosRoot = parseBody.selectFirst("div.content-padding-new")
                ?: return@withContext WebsiteState.Error(IllegalStateException("找不到 subscriptionsVideosRoot"))

            val artists = subscriptionsRoot.select("div.subscriptions-artist-card").mapNotNull { card ->
                try {
                    val imgs = card.select("img")
                    val avatarSrc = imgs.getOrNull(1)?.absUrl("src") ?: return@mapNotNull null
                    val artistName = card.selectFirst("div.card-mobile-title")?.text()?.trim()
                        ?: return@mapNotNull null
                        
                    val translatedArtistName = translateIfEnabled(artistName, TranslationCache.ContentType.ARTIST_NAME)

                    SubscriptionItem(
                        artistName = translatedArtistName,
                        avatar = avatarSrc
                    )
                } catch (_: Exception) {
                    null
                }
            }

            val videos = subscriptionsVideosRoot.select("div[class^=video-item-container]")
                .mapNotNull { videoCard ->
                    try {
                        val link =
                            videoCard.selectFirst("a[class^=video-link]")?.absUrl("href") ?: return@mapNotNull null
                        val videoCode = Regex("""watch\?v=(\d+)""").find(link)?.groupValues?.get(1)
                            ?: return@mapNotNull null
                        val coverUrl = videoCard.select("img[class^=main-thumb]").getOrNull(0)?.absUrl("src") ?: return@mapNotNull null
                        val title = videoCard.attr("title").trim()
                        val durationAndViews = videoCard.select("div[class^=thumb-container]")
                        val duration = durationAndViews.select("div[class^=duration]").text()
                        val views = durationAndViews.select("div[class^=stat-item]").getOrNull(1)?.text()
                        val artistAndUploadTime = videoCard.select("div.subtitle a").text().trim()
                        var artist = ""
                        var uploadTime = ""
                        if (artistAndUploadTime.contains("•")) {
                            val parts = artistAndUploadTime.split("•").map { it.trim() }
                            artist = parts[0].trim()
                            uploadTime = parts[1].trim()
                        }
                        val infoBoxes = videoCard.selectFirst(".stats-container .stat-item")
                        val fullText = infoBoxes?.text() ?: ""
                        val reviews = fullText.replace("thumb_up", "").trim()
                        
                        val translatedTitle = translateIfEnabled(title, TranslationCache.ContentType.TITLE, videoCode)
                        
                        val translatedArtist = if (artist.isNotBlank()) {
                            translateIfEnabled(artist, TranslationCache.ContentType.ARTIST_NAME, videoCode)
                        } else artist

                        SubscriptionVideosItem(
                            title = translatedTitle,
                            coverUrl = coverUrl,
                            videoCode = videoCode,
                            duration = duration,
                            views = views,
                            reviews = reviews,
                            currentArtist = translatedArtist,
                            uploadTime = uploadTime
                        )
                    } catch (_: Exception) {
                        null
                    }
                }

            WebsiteState.Success(
                MySubscriptions(
                    subscriptions = artists,
                    subscriptionsVideos = videos,
                    maxPage = maxPage
                )
            )
        }
    }

    private fun parseMaxPage(parseBody: Element): Int {
        return parseBody
            .select("ul.pagination")
            .lastOrNull()
            ?.select("a.page-link[href]")
            ?.mapNotNull {
                Regex("""\?page=(\d+)""").find(it.attr("href"))?.groupValues?.get(1)?.toIntOrNull()
            }
            ?.maxOrNull() ?: 1
    }

    private inline fun Elements.forEachStep2(action: (Element) -> Unit) {
        for (i in 0 until size step 2) {
            action(get(i))
        }
    }

    private fun Element.childOrNull(index: Int): Element? {
        return try {
            child(index)
        } catch (_: IndexOutOfBoundsException) {
            null
        }
    }

    private fun <T> T?.throwIfParseNull(funcName: String, varName: String): T = this
        ?: throw ParseException(funcName, varName)

    private fun <T> T?.logIfParseNull(
        funcName: String, varName: String, loginNeeded: Boolean = false,
    ): T? = also {
        if (it == null) {
            if (loginNeeded) {
                if (isAlreadyLogin) {
                    Log.d("Parse::$funcName", "[$varName] is null. 而且處於登入狀態，這有點不正常")
                }
            } else {
                Log.d("Parse::$funcName", "[$varName] is null. 這有點不正常")
            }
        }
    }
}