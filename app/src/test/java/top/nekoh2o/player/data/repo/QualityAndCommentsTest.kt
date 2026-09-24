package top.nekoh2o.player.data.repo

import kotlinx.serialization.json.*
import org.junit.Assert.*
import org.junit.Test
import top.nekoh2o.player.data.model.Song

class QualityAndCommentsTest {
    @Test fun playbackUsesPreferredThenHighestAuthorizedWithoutAcceptingSilentDowngradeEarly() = kotlinx.coroutines.runBlocking {
        val low=AudioQuality("standard","low",0);val high=AudioQuality("lossless","high",3);val top=AudioQuality("hires","top",4)
        val song=Song(1,"song","artist");val requested=mutableListOf<String>()
        val repo=SongQualityRepository({_,_->listOf(low,high,top)},{_,q,download->
            assertFalse(download);requested.add(q.id)
            ResolvedAudio(if(q.id=="hires") low else q,"https://cdn.test/"+q.id)
        })
        assertEquals("standard",repo.resolvePlayback(song,"standard")!!.quality.id)
        assertEquals(listOf("standard"),requested);requested.clear()
        assertEquals("lossless",repo.resolvePlayback(song,"missing")!!.quality.id)
        assertEquals(listOf("hires","lossless"),requested);requested.clear()
        assertEquals("lossless",repo.resolvePlayback(song,"hires")!!.quality.id)
        assertEquals(listOf("hires","lossless"),requested)
    }
    @Test fun downloadListChecksDownloadRightsAndDownloadRejectsSilentDowngrade() = kotlinx.coroutines.runBlocking {
        val low=AudioQuality("standard","low",0);val high=AudioQuality("lossless","high",3);val top=AudioQuality("hires","top",4)
        val song=Song(1,"song","artist")
        val repo=SongQualityRepository({_,download->assertTrue(download);listOf(low,high,top)}, {_,q,download->
            assertTrue(download)
            when(q.id) { "hires" -> null; else -> ResolvedAudio(low,"https://cdn.test/song") }
        })
        assertEquals(listOf("standard"),repo.downloadable(song).map { it.id })
        assertNull(repo.resolveDownload(song,"lossless"));assertNull(repo.resolveDownload(song,"hires"))
        assertEquals("standard",repo.resolveDownload(song,"standard")!!.quality.id)
    }
    @Test fun accountAndVerificationErrorsAreNotMisreportedAsUnavailableQualities() = kotlinx.coroutines.runBlocking {
        val q=AudioQuality("128","standard",0);val song=Song(1,"song","artist",source="kugou")
        for(code in listOf(20018,20028)) {
            val repo=SongQualityRepository({_,_->listOf(q)},{_,_,_->throw top.nekoh2o.player.data.net.KugouApiException("verification",code)})
            try { repo.resolvePlayback(song,"128");fail("Must preserve authentication error") }
            catch(e: top.nekoh2o.player.data.net.KugouApiException) { assertEquals(code,e.errorCode) }
        }
    }
    private fun parse(raw: String)=Json.parseToJsonElement(raw).jsonObject
    @Test fun qualityCandidatesComeFromTrackInsteadOfMembership() {
        val body=parse("""{"data":{"l":{"size":100},"h":{"size":300},"sq":null,"hr":{"size":0},"jm":{"size":900}}}""")
        assertEquals(listOf("standard","exhigh","jymaster"),SongQualityRepository.neteaseCandidates(body).map{it.id})
        assertEquals("high",SongQualityRepository.preferredFor("kugou","hires"))
    }
    @Test fun trialAndForbiddenUrlsCannotBecomeDownloadsAndDowngradesStayHonest() {
        val high=AudioQuality("lossless","SQ",3)
        assertNull(SongQualityRepository.neteaseAudio(parse("""{"url":"https://cdn.test/song","freeTrialInfo":{"start":0,"end":30}}"""),high))
        assertNull(SongQualityRepository.neteaseAudio(parse("""{"url":"https://cdn.test/song","code":403}"""),high))
        val result=SongQualityRepository.neteaseAudio(parse("""{"url":"https://cdn.test/song","level":"standard","br":128000,"code":200}"""),high)!!
        assertEquals("standard",result.quality.id)
        assertEquals(0,result.quality.rank)
    }
    @Test fun kugouCandidatesRetainDifferentQualityHashesAndRejectUnpublishedFormats() {
        val body=parse("""{"data":[{"quality":"128","hash":"low","is_publish":1,"info":{"filesize":100},"relate_goods":[{"quality":"320","hash":"high","is_publish":1,"info":{"filesize":300}},{"quality":"flac","is_publish":0,"info":{"filesize":900}}]}]}""")
        val rows=SongQualityRepository.kugouCandidates(body)
        assertEquals(listOf("128","320"),rows.map{it.id});assertEquals(listOf("low","high"),rows.map{it.hash})
    }
    @Test fun commentOwnershipIsBasedOnVerifiedAccountAndSourceIdsNeverCollide() {
        val body=parse("""{"total":1,"more":false,"comments":[{"commentId":42,"content":"hello","user":{"userId":123,"nickname":"a"},"likedCount":2,"liked":true}]}""")
        assertTrue(CommentsRepository.neteasePage(body,"123").comments.single().owned)
        assertFalse(CommentsRepository.neteasePage(body,"").comments.single().owned)
        assertFalse(CommentsRepository.neteasePage(body,"321").comments.single().owned)
        assertNotEquals(CommentsRepository.songKey(Song(1,"x","a")),CommentsRepository.songKey(Song(1,"x","a",source="kugou",hash="a".repeat(32))))
    }
    @Test fun kugouRootShapeAndSiteDeletionArePreserved() {
        val kg=parse("""{"childrenid":"s","count":3,"list":[{"id":1,"user_id":2,"user_name":"a","content":"hi","reply_num":2,"like":{"count":4,"haslike":true},"special_child_name":"song"}]}""")
        val page=CommentsRepository.kugouPage(kg,"2");assertEquals("s",page.specialId);assertTrue(page.more);assertTrue(page.comments.single().owned);assertEquals(4L,page.comments.single().likes)
        val site=parse("""{"comments":[{"id":"a","userId":"b","content":"","owned":true,"deleted":true,"replies":2}],"total":1,"more":false}""")
        assertTrue(CommentsRepository.sitePage(site).comments.single().deleted)
    }
}
