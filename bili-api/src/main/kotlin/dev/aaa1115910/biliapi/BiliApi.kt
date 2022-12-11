package dev.aaa1115910.biliapi

import dev.aaa1115910.biliapi.entity.BiliResponse
import dev.aaa1115910.biliapi.entity.danmaku.DanmakuData
import dev.aaa1115910.biliapi.entity.danmaku.DanmakuResponse
import dev.aaa1115910.biliapi.entity.dynamic.DynamicData
import dev.aaa1115910.biliapi.entity.history.HistoryData
import dev.aaa1115910.biliapi.entity.user.MyInfoData
import dev.aaa1115910.biliapi.entity.user.UserCardData
import dev.aaa1115910.biliapi.entity.user.UserInfoData
import dev.aaa1115910.biliapi.entity.user.favorite.FavoriteFolderInfo
import dev.aaa1115910.biliapi.entity.user.favorite.FavoriteFolderInfoListData
import dev.aaa1115910.biliapi.entity.user.favorite.FavoriteItemIdListResponse
import dev.aaa1115910.biliapi.entity.user.favorite.UserFavoriteFoldersData
import dev.aaa1115910.biliapi.entity.video.PlayUrlData
import dev.aaa1115910.biliapi.entity.video.PopularVideoData
import dev.aaa1115910.biliapi.entity.video.RelatedVideosResponse
import dev.aaa1115910.biliapi.entity.video.VideoInfo
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.plugins.BrowserUserAgent
import io.ktor.client.plugins.compression.ContentEncoding
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.defaultRequest
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.parameter
import io.ktor.client.statement.bodyAsChannel
import io.ktor.serialization.kotlinx.json.json
import io.ktor.utils.io.jvm.javaio.toInputStream
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import mu.KotlinLogging
import javax.xml.parsers.DocumentBuilderFactory

object BiliApi {
    private var endPoint: String = ""
    private lateinit var client: HttpClient
    private val logger = KotlinLogging.logger { }

    init {
        createClient()
    }

    private fun createClient() {
        client = HttpClient(OkHttp) {
            BrowserUserAgent()
            install(ContentNegotiation) {
                json(Json {
                    coerceInputValues = true
                    ignoreUnknownKeys = true
                    prettyPrint = true
                })
            }
            install(ContentEncoding) {
                deflate(1.0F)
                gzip(0.9F)
            }
            defaultRequest {
                host = "api.bilibili.com"
            }
        }
    }

    /**
     * 获取热门视频列表
     */
    suspend fun getPopularVideoData(
        pageNumber: Int = 1,
        pageSize: Int = 20,
        sessData: String = ""
    ): BiliResponse<PopularVideoData> = client.get("/x/web-interface/popular") {
        parameter("pn", pageNumber)
        parameter("ps", pageSize)
        header("Cookie", "SESSDATA=$sessData;")
    }.body()

    /**
     * 获取视频详细信息
     */
    suspend fun getVideoInfo(
        av: Int? = null,
        bv: String? = null,
        sessData: String = ""
    ): BiliResponse<VideoInfo> = client.get("/x/web-interface/view") {
        parameter("aid", av)
        parameter("bvid", bv)
        header("Cookie", "SESSDATA=$sessData;")
    }.body()

    /**
     * 获取视频流
     */
    suspend fun getVideoPlayUrl(
        av: Int? = null,
        bv: String? = null,
        cid: Int,
        qn: Int? = null,
        fnval: Int? = null,
        fnver: Int? = null,
        fourk: Int? = 0,
        session: String? = null,
        otype: String = "json",
        type: String = "",
        platform: String = "oc",
        sessData: String = ""
    ): BiliResponse<PlayUrlData> = client.get("/x/player/playurl") {
        parameter("avid", av)
        parameter("bvid", bv)
        parameter("cid", cid)
        parameter("qn", qn)
        parameter("fnval", fnval)
        parameter("fnver", fnver)
        parameter("fourk", fourk)
        parameter("session", session)
        parameter("otype", otype)
        parameter("type", type)
        parameter("platform", platform)
        header("Cookie", "SESSDATA=$sessData;")
    }.body()

    /**
     * 通过[cid]获取视频弹幕
     */
    suspend fun getDanmakuXml(
        cid: Int,
        sessData: String = ""
    ): DanmakuResponse {
        val xmlChannel = client.get("/x/v1/dm/list.so") {
            parameter("oid", cid)
            header("Cookie", "SESSDATA=$sessData;")
        }.bodyAsChannel()

        val dbFactory = DocumentBuilderFactory.newInstance()
        val dBuilder = dbFactory.newDocumentBuilder()
        val doc = withContext(Dispatchers.IO) {
            dBuilder.parse(xmlChannel.toInputStream())
        }
        doc.documentElement.normalize()

        val chatServer = doc.getElementsByTagName("chatserver").item(0).textContent
        val chatId = doc.getElementsByTagName("chatid").item(0).textContent.toInt()
        val maxLimit = doc.getElementsByTagName("maxlimit").item(0).textContent.toInt()
        val state = doc.getElementsByTagName("state").item(0).textContent.toInt()
        val realName = doc.getElementsByTagName("real_name").item(0).textContent.toInt()
        val source = doc.getElementsByTagName("source").item(0).textContent

        val data = mutableListOf<DanmakuData>()
        val danmakuNodes = doc.getElementsByTagName("d")

        for (i in 0 until danmakuNodes.length) {
            val danmakuNode = danmakuNodes.item(i)
            val p = danmakuNode.attributes.item(0).textContent
            val text = danmakuNode.textContent
            data.add(DanmakuData.fromString(p, text))
        }

        return DanmakuResponse(chatServer, chatId, maxLimit, state, realName, source, data)
    }

    /**
     * 获取动态列表
     *
     * @param type 返回数据额类型 all:全部 video:视频投稿 pgc:追番追剧 article：专栏
     * @param offset 请求第2页及其之后时填写，填写上一次请求获得的offset
     */
    suspend fun getDynamicList(
        timezoneOffset: Int = -480,
        type: String = "all",
        page: Int = 1,
        offset: String? = null,
        sessData: String = ""
    ): BiliResponse<DynamicData> = client.get("/x/polymer/web-dynamic/v1/feed/all") {
        parameter("timezone_offset", timezoneOffset)
        parameter("type", type)
        parameter("page", page)
        offset?.let { parameter("offset", offset) }
        header("Cookie", "SESSDATA=$sessData;")
    }.body()

    /**
     * 获取用户[uid]的详细信息
     */
    suspend fun getUserInfo(
        uid: Long,
        sessData: String = ""
    ): BiliResponse<UserInfoData> = client.get("/x/space/acc/info") {
        parameter("mid", uid)
        header("Cookie", "SESSDATA=$sessData;")
    }.body()


    /**
     * 获取用户[uid]的卡片信息
     *
     * @param uid 用户id
     * @param photo 是否请求用户主页头图
     */
    suspend fun getUserCardInfo(
        uid: Long,
        photo: Boolean = false,
        sessData: String = ""
    ): BiliResponse<UserCardData> = client.get("/x/web-interface/card") {
        parameter("mid", uid)
        parameter("photo", photo)
        header("Cookie", "SESSDATA=$sessData;")
    }.body()

    /**
     * 通过[sessData]获取用户个人信息
     */
    suspend fun getUserSelfInfo(
        sessData: String = ""
    ): BiliResponse<MyInfoData> = client.get("/x/space/myinfo") {
        header("Cookie", "SESSDATA=$sessData;")
    }.body()

    /**
     * 获取截止至目标id[max]和目标时间[viewAt]历史记录
     *
     * @param business 分类 貌似无效
     * @param pageSize 页面大小
     */
    suspend fun getHistories(
        max: Long = 0,
        business: String = "",
        viewAt: Long = 0,
        pageSize: Int = 20,
        sessData: String = ""
    ): BiliResponse<HistoryData> = client.get("/x/web-interface/history/cursor") {
        parameter("max", max)
        parameter("business", business)
        parameter("view_at", viewAt)
        parameter("ps", pageSize)
        header("Cookie", "SESSDATA=$sessData;")
    }.body()

    /**
     * 获取与视频[avid]或[bvid]有关的相关推荐视频
     */
    suspend fun getRelatedVideos(
        avid: Long? = null,
        bvid: String? = null
    ): RelatedVideosResponse = client.get("/x/web-interface/archive/related") {
        check(avid != null || bvid != null) { "avid and bvid cannot be null at the same time" }
        parameter("aid", avid)
        parameter("bvid", bvid)
    }.body()

    /**
     * 获取收藏夹[mediaId]的元数据
     */
    suspend fun getFavoriteFolderInfo(
        mediaId: Long,
        sessData: String = ""
    ): BiliResponse<FavoriteFolderInfo> = client.get("/x/v3/fav/folder/info") {
        parameter("media_id", mediaId)
        header("Cookie", "SESSDATA=$sessData;")
    }.body()

    /**
     * 获取用户[mid]的所有收藏夹信息
     *
     * @param type 目标内容属性 默认为全部 0：全部 2：视频稿件
     * @param rid 目标内容id 视频稿件：视频稿件avid
     */
    suspend fun getAllFavoriteFoldersInfo(
        mid: Long,
        type: Int = 0,
        rid: Int? = null,
        sessData: String = ""
    ): BiliResponse<UserFavoriteFoldersData> = client.get("/x/v3/fav/folder/created/list-all") {
        parameter("up_mid", mid)
        parameter("type", type)
        parameter("rid", rid)
        header("Cookie", "SESSDATA=$sessData;")
    }.body()

    /**
     * 获取收藏夹[mediaId]的详细内容
     *
     * @param tid 分区tid 默认为全部分区 0：全部分区
     * @param keyword 搜索关键字
     * @param order 排序方式 按收藏时间:mtime 按播放量: view 按投稿时间：pubtime
     * @param type 查询范围 0：当前收藏夹（对应media_id） 1：全部收藏夹
     * @param pageSize 每页数量 定义域：1-20
     * @param pageNumber 页码 默认为1
     * @param platform 平台标识 可为web（影响内容列表类型）
     */
    suspend fun getFavoriteList(
        mediaId: Long,
        tid: Int = 0,
        keyword: String? = null,
        order: String? = null,
        type: Int = 0,
        pageSize: Int = 20,
        pageNumber: Int = 1,
        platform: String? = null,
        sessData: String = ""
    ): BiliResponse<FavoriteFolderInfoListData> = client.get("/x/v3/fav/resource/list") {
        parameter("media_id", mediaId)
        parameter("tid", tid)
        parameter("keyword", keyword)
        parameter("order", order)
        parameter("type", type)
        parameter("ps", pageSize)
        parameter("pn", pageNumber)
        parameter("platform", platform)
        header("Cookie", "SESSDATA=$sessData;")
    }.body()

    /**
     * 获取收藏夹[mediaId]的全部内容id
     */
    suspend fun getFavoriteIdList(
        mediaId: Long,
        platform: String? = null,
        sessData: String = ""
    ): FavoriteItemIdListResponse = client.get("/x/v3/fav/resource/ids") {
        parameter("media_id", mediaId)
        parameter("platform", platform)
        header("Cookie", "SESSDATA=$sessData;")
    }.body()
}