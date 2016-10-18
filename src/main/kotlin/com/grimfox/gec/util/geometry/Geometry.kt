package com.grimfox.gec.util.geometry

import com.grimfox.gec.model.geometry.*
import com.grimfox.gec.util.drawing.draw
import com.grimfox.gec.util.drawing.drawEdge
import com.grimfox.gec.util.drawing.drawPoint
import com.grimfox.gec.util.geometry.Geometry.debug
import com.grimfox.gec.util.geometry.Geometry.debugCount
import com.grimfox.gec.util.geometry.Geometry.debugIteration
import com.grimfox.gec.util.geometry.Geometry.debugResolution
import com.grimfox.gec.util.geometry.Geometry.debugZoom
import com.grimfox.gec.util.geometry.Geometry.trace
import com.grimfox.gec.util.printList
import java.awt.BasicStroke
import java.awt.Color
import java.lang.Math.max
import java.lang.Math.min
import java.util.*
import java.util.concurrent.atomic.AtomicInteger

class GeometryException(message: String? = null, cause: Throwable? = null, var test: Int? = null, var id: Int? = null, val data: ArrayList<String> = ArrayList<String>()): Exception(message, cause) {

    fun with(adjustment: GeometryException.() -> Unit): GeometryException {
        this.adjustment()
        return this
    }
}

private fun breakPoint() {
    doNothing()
}

private fun doNothing() {}

object Geometry {

    var debug = false
    var trace = false
    var debugCount = AtomicInteger(1)
    var debugIteration = AtomicInteger(1)
    var debugResolution = 4096
    var debugZoom = 15.0f

    @JvmStatic fun main(vararg args: String) {

        val keeper1 = {
            val vertices = PointSet2F(points=arrayListOf(Point3F(x=0.657852f, y=0.52122694f, z=0.0f), Point3F(x=0.6578838f, y=0.5195371f, z=0.0f), Point3F(x=0.6580053f, y=0.5194083f, z=0.0f), Point3F(x=0.6593489f, y=0.5195656f, z=0.0f), Point3F(x=0.66068435f, y=0.51934993f, z=0.0f), Point3F(x=0.6616354f, y=0.51807094f, z=0.0f), Point3F(x=0.6618671f, y=0.5164941f, z=0.0f), Point3F(x=0.6611924f, y=0.5149553f, z=0.0f), Point3F(x=0.6605042f, y=0.51342237f, z=0.0f), Point3F(x=0.66095287f, y=0.5125212f, z=0.0f), Point3F(x=0.6616357f, y=0.5117815f, z=0.0f), Point3F(x=0.66165805f, y=0.5115545f, z=9.758234E-4f), Point3F(x=0.6616804f, y=0.5113275f, z=0.0f), Point3F(x=0.6607708f, y=0.5100297f, z=0.0f), Point3F(x=0.6597271f, y=0.5088368f, z=0.0f), Point3F(x=0.65864193f, y=0.5084596f, z=0.0f), Point3F(x=0.6579075f, y=0.5067031f, z=0.0f), Point3F(x=0.6575375f, y=0.5062737f, z=0.0f), Point3F(x=0.6581144f, y=0.5050086f, z=0.004732944f), Point3F(x=0.6599802f, y=0.50457114f, z=0.005903714f), Point3F(x=0.661846f, y=0.5041337f, z=0.0070744837f), Point3F(x=0.6637118f, y=0.50369626f, z=0.008245254f), Point3F(x=0.66557753f, y=0.5032588f, z=0.009416023f), Point3F(x=0.66709393f, y=0.50457025f, z=0.009447934f), Point3F(x=0.66861033f, y=0.50588167f, z=0.009479845f), Point3F(x=0.6701267f, y=0.50719315f, z=0.009511756f), Point3F(x=0.6702467f, y=0.50898916f, z=0.0073777726f), Point3F(x=0.67036676f, y=0.51078516f, z=0.0052437894f), Point3F(x=0.6704868f, y=0.51258117f, z=0.0031098062f), Point3F(x=0.67060685f, y=0.5143771f, z=9.758234E-4f), Point3F(x=0.6707269f, y=0.5161731f, z=0.0033218227f), Point3F(x=0.67084694f, y=0.51796913f, z=0.005667822f), Point3F(x=0.670967f, y=0.51976514f, z=0.008013821f), Point3F(x=0.6710871f, y=0.52156115f, z=0.01035982f), Point3F(x=0.6691972f, y=0.5217548f, z=0.008879846f), Point3F(x=0.6673072f, y=0.5219485f, z=0.007399872f), Point3F(x=0.6654172f, y=0.52214223f, z=0.0059198975f), Point3F(x=0.6635272f, y=0.52233595f, z=0.004439923f), Point3F(x=0.6616372f, y=0.52252966f, z=0.0029599487f), Point3F(x=0.6597472f, y=0.5227234f, z=0.0014799744f), Point3F(x=0.6578572f, y=0.5229171f, z=0.0f), Point3F(x=0.6691004f, y=0.51313883f, z=8.1323006E-4f), Point3F(x=0.66772753f, y=0.5117935f, z=6.5296283E-4f), Point3F(x=0.66613245f, y=0.5129658f, z=4.8791163E-4f), Point3F(x=0.6645374f, y=0.5141381f, z=3.2286043E-4f), Point3F(x=0.6631645f, y=0.51279277f, z=1.6259319E-4f)))
            val polygon = arrayListOf(Pair(12, 11), Pair(11, 45), Pair(45, 44), Pair(44, 43), Pair(43, 42), Pair(42, 41), Pair(41, 29), Pair(29, 28), Pair(28, 27), Pair(27, 26), Pair(26, 25), Pair(25, 24), Pair(24, 23), Pair(23, 22), Pair(22, 21), Pair(21, 20), Pair(20, 19), Pair(19, 18), Pair(18, 17), Pair(17, 16), Pair(16, 15), Pair(15, 14), Pair(14, 13), Pair(13, 12))
            triangulatePolygon(vertices, polygon)
        }

        val keeper2 = {
            val vertices = PointSet2F(points=arrayListOf(Point3F(x=0.5040726f, y=0.18875736f, z=0.0f), Point3F(x=0.50388956f, y=0.19026884f, z=0.0f), Point3F(x=0.50371873f, y=0.19178124f, z=0.0f), Point3F(x=0.5037761f, y=0.1918694f, z=0.0f), Point3F(x=0.50361437f, y=0.19342753f, z=0.0f), Point3F(x=0.5029236f, y=0.19420259f, z=0.0f), Point3F(x=0.50211763f, y=0.19485693f, z=0.0f), Point3F(x=0.5019059f, y=0.19505912f, z=0.0f), Point3F(x=0.50243187f, y=0.19627202f, z=0.0f), Point3F(x=0.5030295f, y=0.19745126f, z=0.0f), Point3F(x=0.5032157f, y=0.19876012f, z=0.0f), Point3F(x=0.5025165f, y=0.19976038f, z=0.0f), Point3F(x=0.5013204f, y=0.2000029f, z=0.0f), Point3F(x=0.5001255f, y=0.19894356f, z=0.0f), Point3F(x=0.4988648f, y=0.19796355f, z=0.0f), Point3F(x=0.49752885f, y=0.19889478f, z=0.0f), Point3F(x=0.49632633f, y=0.19999295f, z=0.0f), Point3F(x=0.4952897f, y=0.19915582f, z=0.0f), Point3F(x=0.4940482f, y=0.19963315f, z=0.0f), Point3F(x=0.49459764f, y=0.19779412f, z=6.8200426E-4f), Point3F(x=0.495147f, y=0.19595513f, z=0.0013640085f), Point3F(x=0.49569634f, y=0.19411613f, z=0.0020460128f), Point3F(x=0.49624568f, y=0.19227713f, z=0.002728017f), Point3F(x=0.49679503f, y=0.19043814f, z=0.0034100213f), Point3F(x=0.49734437f, y=0.18859914f, z=0.0040920256f), Point3F(x=0.49789372f, y=0.18676014f, z=0.00477403f), Point3F(x=0.49844307f, y=0.18492115f, z=0.005456034f), Point3F(x=0.5003828f, y=0.18569665f, z=0.0036373562f), Point3F(x=0.50232255f, y=0.18647213f, z=0.0018186781f), Point3F(x=0.5042623f, y=0.18724762f, z=0.0f)))
            val polygon = arrayListOf(Pair(0, 1), Pair(1, 2), Pair(2, 3), Pair(3, 4), Pair(4, 5), Pair(5, 6), Pair(6, 7), Pair(7, 8), Pair(8, 9), Pair(9, 10), Pair(10, 11), Pair(11, 12), Pair(12, 13), Pair(13, 14), Pair(14, 15), Pair(15, 16), Pair(16, 17), Pair(17, 18), Pair(18, 19), Pair(19, 20), Pair(20, 21), Pair(21, 22), Pair(22, 23), Pair(23, 24), Pair(24, 25), Pair(25, 26), Pair(26, 27), Pair(27, 28), Pair(28, 29), Pair(29, 0))
            triangulatePolygon(vertices, polygon)
        }

        val keeper3 = {
            val vertices = PointSet2F(points=arrayListOf(Point3F(x=0.6364898f, y=0.13193336f, z=0.0f), Point3F(x=0.6378628f, y=0.1320265f, z=0.0f), Point3F(x=0.6391836f, y=0.13241278f, z=0.0f), Point3F(x=0.6396885f, y=0.13422023f, z=0.0f), Point3F(x=0.63971555f, y=0.13609673f, z=0.0f), Point3F(x=0.64053226f, y=0.13778624f, z=0.0f), Point3F(x=0.6418955f, y=0.13788795f, z=0.0f), Point3F(x=0.6429973f, y=0.13707797f, z=0.0f), Point3F(x=0.6435763f, y=0.1358393f, z=0.0f), Point3F(x=0.6432352f, y=0.13411532f, z=0.0f), Point3F(x=0.6422254f, y=0.13267697f, z=0.0f), Point3F(x=0.641098f, y=0.13132879f, z=0.0f), Point3F(x=0.6398202f, y=0.13008195f, z=0.0f), Point3F(x=0.6384131f, y=0.12898315f, z=0.0f), Point3F(x=0.6372974f, y=0.12758936f, z=0.0f), Point3F(x=0.63703984f, y=0.12617707f, z=0.0f), Point3F(x=0.6378424f, y=0.124986924f, z=0.0f), Point3F(x=0.63951576f, y=0.125747f, z=0.0f), Point3F(x=0.6412372f, y=0.12639144f, z=0.0f), Point3F(x=0.6420176f, y=0.12519458f, z=0.0f), Point3F(x=0.6420088f, y=0.123765685f, z=0.0f), Point3F(x=0.6415246f, y=0.12242131f, z=0.0f), Point3F(x=0.64016795f, y=0.121693045f, z=0.0f), Point3F(x=0.6386363f, y=0.121534325f, z=0.0f), Point3F(x=0.6376598f, y=0.12150804f, z=0.0f), Point3F(x=0.639048f, y=0.120437786f, z=0.0012693903f), Point3F(x=0.64043623f, y=0.11936753f, z=0.0025387807f), Point3F(x=0.64176255f, y=0.120578945f, z=0.0017890271f), Point3F(x=0.6430889f, y=0.12179036f, z=0.0010392736f), Point3F(x=0.64441526f, y=0.12300177f, z=2.895201E-4f), Point3F(x=0.64574164f, y=0.12421318f, z=0.0027656096f), Point3F(x=0.647068f, y=0.1254246f, z=0.0052416995f), Point3F(x=0.64839435f, y=0.12663601f, z=0.007717789f), Point3F(x=0.64834803f, y=0.12860851f, z=0.0076731755f), Point3F(x=0.6483017f, y=0.130581f, z=0.007628562f), Point3F(x=0.6482554f, y=0.1325535f, z=0.0075839483f), Point3F(x=0.6482091f, y=0.134526f, z=0.0075393347f), Point3F(x=0.6481628f, y=0.1364985f, z=0.007494721f), Point3F(x=0.64811647f, y=0.13847099f, z=0.0074501075f), Point3F(x=0.6480703f, y=0.14044349f, z=0.0074054943f), Point3F(x=0.6468344f, y=0.14177372f, z=0.0065884227f), Point3F(x=0.6455984f, y=0.14310394f, z=0.005771351f), Point3F(x=0.64285606f, y=0.14333946f, z=0.005578068f), Point3F(x=0.64145994f, y=0.14214703f, z=0.0055309264f), Point3F(x=0.6400638f, y=0.14095461f, z=0.0054837847f), Point3F(x=0.6386677f, y=0.1397622f, z=0.005436643f), Point3F(x=0.6372716f, y=0.13856977f, z=0.0053895013f), Point3F(x=0.63587546f, y=0.13737735f, z=0.0053423597f), Point3F(x=0.63447934f, y=0.13618493f, z=0.0052952175f), Point3F(x=0.63481706f, y=0.13437124f, z=0.003530145f), Point3F(x=0.6351548f, y=0.13255754f, z=0.0017650723f), Point3F(x=0.63549244f, y=0.13074385f, z=0.0f)))
            val polygon = arrayListOf(Pair(0, 1), Pair(1, 2), Pair(2, 3), Pair(3, 4), Pair(4, 5), Pair(5, 6), Pair(6, 7), Pair(7, 8), Pair(8, 9), Pair(9, 10), Pair(10, 11), Pair(11, 12), Pair(12, 13), Pair(13, 14), Pair(14, 15), Pair(15, 16), Pair(16, 17), Pair(17, 18), Pair(18, 19), Pair(19, 20), Pair(20, 21), Pair(21, 22), Pair(22, 23), Pair(23, 24), Pair(24, 25), Pair(25, 26), Pair(26, 27), Pair(27, 28), Pair(28, 29), Pair(29, 30), Pair(30, 31), Pair(31, 32), Pair(32, 33), Pair(33, 34), Pair(34, 35), Pair(35, 36), Pair(36, 37), Pair(37, 38), Pair(38, 39), Pair(39, 40), Pair(40, 41), Pair(41, 42), Pair(42, 43), Pair(43, 44), Pair(44, 45), Pair(45, 46), Pair(46, 47), Pair(47, 48), Pair(48, 49), Pair(49, 50), Pair(50, 51), Pair(51, 0))
            triangulatePolygon(vertices, polygon)
        }

        val test = {
            val edgeSkeleton = arrayListOf(LineSegment3F(a=Point3F(x=0.33220312f, y=0.7604409f, z=0.0f), b=Point3F(x=0.3323125f, y=0.75871766f, z=5.1800922E-5f)), LineSegment3F(a=Point3F(x=0.3323125f, y=0.75871766f, z=5.1800922E-5f), b=Point3F(x=0.33242187f, y=0.7569944f, z=0.0f)), LineSegment3F(a=Point3F(x=0.33242187f, y=0.7569944f, z=0.0f), b=Point3F(x=0.33242187f, y=0.7569944f, z=0.0f)), LineSegment3F(a=Point3F(x=0.33242187f, y=0.7569944f, z=0.0f), b=Point3F(x=0.33242187f, y=0.7569944f, z=0.0f)), LineSegment3F(a=Point3F(x=0.33242187f, y=0.7569944f, z=0.0f), b=Point3F(x=0.3319056f, y=0.7549584f, z=6.301406E-5f)), LineSegment3F(a=Point3F(x=0.3319056f, y=0.7549584f, z=6.301406E-5f), b=Point3F(x=0.33138934f, y=0.7529224f, z=0.0f)), LineSegment3F(a=Point3F(x=0.33138934f, y=0.7529224f, z=0.0f), b=Point3F(x=0.33138934f, y=0.7529224f, z=0.0f)), LineSegment3F(a=Point3F(x=0.33138934f, y=0.7529224f, z=0.0f), b=Point3F(x=0.33138934f, y=0.7529224f, z=0.0f)), LineSegment3F(a=Point3F(x=0.33138934f, y=0.7529224f, z=0.0f), b=Point3F(x=0.3332535f, y=0.75284f, z=5.5979603E-5f)), LineSegment3F(a=Point3F(x=0.3332535f, y=0.75284f, z=5.5979603E-5f), b=Point3F(x=0.3351177f, y=0.75275755f, z=0.0f)), LineSegment3F(a=Point3F(x=0.3351177f, y=0.75275755f, z=0.0f), b=Point3F(x=0.3351177f, y=0.75275755f, z=0.0f)), LineSegment3F(a=Point3F(x=0.3351177f, y=0.75275755f, z=0.0f), b=Point3F(x=0.3351177f, y=0.75275755f, z=0.0f)), LineSegment3F(a=Point3F(x=0.3351177f, y=0.75275755f, z=0.0f), b=Point3F(x=0.33522505f, y=0.7527528f, z=3.2236144E-6f)), LineSegment3F(a=Point3F(x=0.33522505f, y=0.7527528f, z=3.2236144E-6f), b=Point3F(x=0.3353324f, y=0.7527481f, z=0.0f)), LineSegment3F(a=Point3F(x=0.3353324f, y=0.7527481f, z=0.0f), b=Point3F(x=0.3353324f, y=0.7527481f, z=0.0f)), LineSegment3F(a=Point3F(x=0.3353324f, y=0.7527481f, z=0.0f), b=Point3F(x=0.3353324f, y=0.7527481f, z=0.0f)), LineSegment3F(a=Point3F(x=0.3353324f, y=0.7527481f, z=0.0f), b=Point3F(x=0.3352484f, y=0.75268614f, z=3.1304196E-6f)), LineSegment3F(a=Point3F(x=0.3352484f, y=0.75268614f, z=3.1304196E-6f), b=Point3F(x=0.3351644f, y=0.7526242f, z=0.0f)), LineSegment3F(a=Point3F(x=0.3351644f, y=0.7526242f, z=0.0f), b=Point3F(x=0.3351644f, y=0.7526242f, z=0.0f)), LineSegment3F(a=Point3F(x=0.3351644f, y=0.7526242f, z=0.0f), b=Point3F(x=0.3351644f, y=0.7526242f, z=0.0f)), LineSegment3F(a=Point3F(x=0.3351644f, y=0.7526242f, z=0.0f), b=Point3F(x=0.33516493f, y=0.7526227f, z=4.5785555E-4f)), LineSegment3F(a=Point3F(x=0.33516493f, y=0.7526227f, z=4.5785555E-4f), b=Point3F(x=0.33516493f, y=0.7526227f, z=4.5785555E-4f)), LineSegment3F(a=Point3F(x=0.33516493f, y=0.7526227f, z=4.5785555E-4f), b=Point3F(x=0.3373754f, y=0.753067f, z=6.062471E-4f)), LineSegment3F(a=Point3F(x=0.3373754f, y=0.753067f, z=6.062471E-4f), b=Point3F(x=0.33958587f, y=0.7535113f, z=7.5463863E-4f)), LineSegment3F(a=Point3F(x=0.33958587f, y=0.7535113f, z=7.5463863E-4f), b=Point3F(x=0.34179634f, y=0.75395566f, z=9.0303016E-4f)), LineSegment3F(a=Point3F(x=0.34179634f, y=0.75395566f, z=9.0303016E-4f), b=Point3F(x=0.34179634f, y=0.75395566f, z=9.0303016E-4f)), LineSegment3F(a=Point3F(x=0.34179634f, y=0.75395566f, z=9.0303016E-4f), b=Point3F(x=0.34286377f, y=0.75554633f, z=6.770991E-4f)), LineSegment3F(a=Point3F(x=0.34286377f, y=0.75554633f, z=6.770991E-4f), b=Point3F(x=0.3439312f, y=0.757137f, z=4.5116805E-4f)), LineSegment3F(a=Point3F(x=0.3439312f, y=0.757137f, z=4.5116805E-4f), b=Point3F(x=0.34499866f, y=0.7587276f, z=2.2523702E-4f)), LineSegment3F(a=Point3F(x=0.34499866f, y=0.7587276f, z=2.2523702E-4f), b=Point3F(x=0.34499866f, y=0.7587276f, z=2.2523702E-4f)), LineSegment3F(a=Point3F(x=0.34499866f, y=0.7587276f, z=2.2523702E-4f), b=Point3F(x=0.34499866f, y=0.7587276f, z=2.2523702E-4f)), LineSegment3F(a=Point3F(x=0.34499866f, y=0.7587276f, z=2.2523702E-4f), b=Point3F(x=0.3460661f, y=0.7603183f, z=4.9004646E-4f)), LineSegment3F(a=Point3F(x=0.3460661f, y=0.7603183f, z=4.9004646E-4f), b=Point3F(x=0.34713352f, y=0.76190895f, z=7.548559E-4f)), LineSegment3F(a=Point3F(x=0.34713352f, y=0.76190895f, z=7.548559E-4f), b=Point3F(x=0.34820095f, y=0.76349956f, z=0.0010196653f)), LineSegment3F(a=Point3F(x=0.34820095f, y=0.76349956f, z=0.0010196653f), b=Point3F(x=0.34820095f, y=0.76349956f, z=0.0010196653f)), LineSegment3F(a=Point3F(x=0.34820095f, y=0.76349956f, z=0.0010196653f), b=Point3F(x=0.34687287f, y=0.76503336f, z=8.850018E-4f)), LineSegment3F(a=Point3F(x=0.34687287f, y=0.76503336f, z=8.850018E-4f), b=Point3F(x=0.34554482f, y=0.7665672f, z=7.503383E-4f)), LineSegment3F(a=Point3F(x=0.34554482f, y=0.7665672f, z=7.503383E-4f), b=Point3F(x=0.34421676f, y=0.768101f, z=6.156748E-4f)), LineSegment3F(a=Point3F(x=0.34421676f, y=0.768101f, z=6.156748E-4f), b=Point3F(x=0.34288868f, y=0.7696347f, z=4.8101135E-4f)), LineSegment3F(a=Point3F(x=0.34288868f, y=0.7696347f, z=4.8101135E-4f), b=Point3F(x=0.34288868f, y=0.7696347f, z=4.8101135E-4f)), LineSegment3F(a=Point3F(x=0.34288868f, y=0.7696347f, z=4.8101135E-4f), b=Point3F(x=0.34117174f, y=0.76874965f, z=3.8480907E-4f)), LineSegment3F(a=Point3F(x=0.34117174f, y=0.76874965f, z=3.8480907E-4f), b=Point3F(x=0.3394548f, y=0.7678646f, z=2.886068E-4f)), LineSegment3F(a=Point3F(x=0.3394548f, y=0.7678646f, z=2.886068E-4f), b=Point3F(x=0.33773786f, y=0.7669795f, z=1.9240452E-4f)), LineSegment3F(a=Point3F(x=0.33773786f, y=0.7669795f, z=1.9240452E-4f), b=Point3F(x=0.33602092f, y=0.76609445f, z=9.620225E-5f)), LineSegment3F(a=Point3F(x=0.33602092f, y=0.76609445f, z=9.620225E-5f), b=Point3F(x=0.33430392f, y=0.7652093f, z=0.0f)), LineSegment3F(a=Point3F(x=0.33430392f, y=0.7652093f, z=0.0f), b=Point3F(x=0.33430392f, y=0.7652093f, z=0.0f)), LineSegment3F(a=Point3F(x=0.33430392f, y=0.7652093f, z=0.0f), b=Point3F(x=0.33430392f, y=0.7652093f, z=0.0f)), LineSegment3F(a=Point3F(x=0.33430392f, y=0.7652093f, z=0.0f), b=Point3F(x=0.33574244f, y=0.76425993f, z=5.170708E-5f)), LineSegment3F(a=Point3F(x=0.33574244f, y=0.76425993f, z=5.170708E-5f), b=Point3F(x=0.33718097f, y=0.76331055f, z=0.0f)), LineSegment3F(a=Point3F(x=0.33718097f, y=0.76331055f, z=0.0f), b=Point3F(x=0.33718097f, y=0.76331055f, z=0.0f)), LineSegment3F(a=Point3F(x=0.33718097f, y=0.76331055f, z=0.0f), b=Point3F(x=0.33718097f, y=0.76331055f, z=0.0f)), LineSegment3F(a=Point3F(x=0.33718097f, y=0.76331055f, z=0.0f), b=Point3F(x=0.3376284f, y=0.76230776f, z=3.294229E-5f)), LineSegment3F(a=Point3F(x=0.3376284f, y=0.76230776f, z=3.294229E-5f), b=Point3F(x=0.33807582f, y=0.7613049f, z=0.0f)), LineSegment3F(a=Point3F(x=0.33807582f, y=0.7613049f, z=0.0f), b=Point3F(x=0.33807582f, y=0.7613049f, z=0.0f)), LineSegment3F(a=Point3F(x=0.33807582f, y=0.7613049f, z=0.0f), b=Point3F(x=0.33807582f, y=0.7613049f, z=0.0f)), LineSegment3F(a=Point3F(x=0.33807582f, y=0.7613049f, z=0.0f), b=Point3F(x=0.33524585f, y=0.7608886f, z=8.581291E-5f)), LineSegment3F(a=Point3F(x=0.33524585f, y=0.7608886f, z=8.581291E-5f), b=Point3F(x=0.3324159f, y=0.7604722f, z=0.0f)), LineSegment3F(a=Point3F(x=0.3324159f, y=0.7604722f, z=0.0f), b=Point3F(x=0.3324159f, y=0.7604722f, z=0.0f)), LineSegment3F(a=Point3F(x=0.3324159f, y=0.7604722f, z=0.0f), b=Point3F(x=0.3324159f, y=0.7604722f, z=0.0f)), LineSegment3F(a=Point3F(x=0.3324159f, y=0.7604722f, z=0.0f), b=Point3F(x=0.3323095f, y=0.76045656f, z=3.226028E-6f)), LineSegment3F(a=Point3F(x=0.3323095f, y=0.76045656f, z=3.226028E-6f), b=Point3F(x=0.33220312f, y=0.7604409f, z=0.0f)))
            val riverSkeleton = arrayListOf(LineSegment3F(a=Point3F(x=0.34499866f, y=0.7587276f, z=2.2523702E-4f), b=Point3F(x=0.34499866f, y=0.7587276f, z=0.0f)))
            val globalVertices = PointSet2F(epsilon=0.0001f, points=arrayListOf(Point3F(x=0.33220312f, y=0.7604409f, z=0.0f), Point3F(x=0.3323125f, y=0.75871766f, z=5.1800922E-5f), Point3F(x=0.33242187f, y=0.7569944f, z=0.0f), Point3F(x=0.3319056f, y=0.7549584f, z=6.301406E-5f), Point3F(x=0.33138934f, y=0.7529224f, z=0.0f), Point3F(x=0.3332535f, y=0.75284f, z=5.5979603E-5f), Point3F(x=0.3351177f, y=0.75275755f, z=0.0f), Point3F(x=0.33522505f, y=0.7527528f, z=3.2236144E-6f), Point3F(x=0.3353324f, y=0.7527481f, z=0.0f), Point3F(x=0.3351644f, y=0.7526242f, z=0.0f), Point3F(x=0.3373754f, y=0.753067f, z=6.062471E-4f), Point3F(x=0.33958587f, y=0.7535113f, z=7.5463863E-4f), Point3F(x=0.34179634f, y=0.75395566f, z=9.0303016E-4f), Point3F(x=0.34286377f, y=0.75554633f, z=6.770991E-4f), Point3F(x=0.3439312f, y=0.757137f, z=4.5116805E-4f), Point3F(x=0.34499866f, y=0.7587276f, z=2.2523702E-4f), Point3F(x=0.3460661f, y=0.7603183f, z=4.9004646E-4f), Point3F(x=0.34713352f, y=0.76190895f, z=7.548559E-4f), Point3F(x=0.34820095f, y=0.76349956f, z=0.0010196653f), Point3F(x=0.34687287f, y=0.76503336f, z=8.850018E-4f), Point3F(x=0.34554482f, y=0.7665672f, z=7.503383E-4f), Point3F(x=0.34421676f, y=0.768101f, z=6.156748E-4f), Point3F(x=0.34288868f, y=0.7696347f, z=4.8101135E-4f), Point3F(x=0.34117168f, y=0.7687496f, z=3.8480907E-4f), Point3F(x=0.33945474f, y=0.7678645f, z=2.886068E-4f), Point3F(x=0.3377378f, y=0.76697946f, z=1.9240454E-4f), Point3F(x=0.33602086f, y=0.7660944f, z=9.620227E-5f), Point3F(x=0.33430392f, y=0.7652093f, z=0.0f), Point3F(x=0.33574244f, y=0.76425993f, z=5.170708E-5f), Point3F(x=0.33718097f, y=0.76331055f, z=0.0f), Point3F(x=0.3376284f, y=0.76230776f, z=3.294229E-5f), Point3F(x=0.33807582f, y=0.7613049f, z=0.0f), Point3F(x=0.33524585f, y=0.7608886f, z=8.581291E-5f), Point3F(x=0.3324159f, y=0.7604722f, z=0.0f), Point3F(x=0.3323095f, y=0.76045656f, z=3.226028E-6f)))
            buildMesh(edgeSkeleton, riverSkeleton, globalVertices)
        }

        val test2 = {
            val edgeSkeleton = arrayListOf(LineSegment3F(a=Point3F(x=0.0067341495f, y=0.76376545f, z=0.0f), b=Point3F(x=0.007179815f, y=0.7627611f, z=0.0f)), LineSegment3F(a=Point3F(x=0.007179815f, y=0.7627611f, z=0.0f), b=Point3F(x=0.008497868f, y=0.7632296f, z=0.0f)), LineSegment3F(a=Point3F(x=0.008497868f, y=0.7632296f, z=0.0f), b=Point3F(x=0.009580787f, y=0.76411796f, z=0.0f)), LineSegment3F(a=Point3F(x=0.009580787f, y=0.76411796f, z=0.0f), b=Point3F(x=0.010968776f, y=0.7642652f, z=0.0f)), LineSegment3F(a=Point3F(x=0.010968776f, y=0.7642652f, z=0.0f), b=Point3F(x=0.01145069f, y=0.76284593f, z=0.0f)), LineSegment3F(a=Point3F(x=0.01145069f, y=0.76284593f, z=0.0f), b=Point3F(x=0.011211649f, y=0.7613663f, z=0.0f)), LineSegment3F(a=Point3F(x=0.011211649f, y=0.7613663f, z=0.0f), b=Point3F(x=0.0106831705f, y=0.7599637f, z=0.0f)), LineSegment3F(a=Point3F(x=0.0106831705f, y=0.7599637f, z=0.0f), b=Point3F(x=0.010155018f, y=0.758561f, z=0.0f)), LineSegment3F(a=Point3F(x=0.010155018f, y=0.758561f, z=0.0f), b=Point3F(x=0.009466225f, y=0.7568689f, z=0.0f)), LineSegment3F(a=Point3F(x=0.009466225f, y=0.7568689f, z=0.0f), b=Point3F(x=0.008568201f, y=0.755278f, z=0.0f)), LineSegment3F(a=Point3F(x=0.008568201f, y=0.755278f, z=0.0f), b=Point3F(x=0.0076343725f, y=0.7537078f, z=0.0f)), LineSegment3F(a=Point3F(x=0.0076343725f, y=0.7537078f, z=0.0f), b=Point3F(x=0.007080828f, y=0.7531171f, z=0.0f)), LineSegment3F(a=Point3F(x=0.007080828f, y=0.7531171f, z=0.0f), b=Point3F(x=0.008674584f, y=0.7523296f, z=1.7549588E-4f)), LineSegment3F(a=Point3F(x=0.008674584f, y=0.7523296f, z=1.7549588E-4f), b=Point3F(x=0.010268341f, y=0.75154215f, z=3.5099176E-4f)), LineSegment3F(a=Point3F(x=0.010268341f, y=0.75154215f, z=3.5099176E-4f), b=Point3F(x=0.011984417f, y=0.7521406f, z=3.8382426E-4f)), LineSegment3F(a=Point3F(x=0.011984417f, y=0.7521406f, z=3.8382426E-4f), b=Point3F(x=0.013700493f, y=0.752739f, z=4.1665675E-4f)), LineSegment3F(a=Point3F(x=0.013700493f, y=0.752739f, z=4.1665675E-4f), b=Point3F(x=0.015416568f, y=0.75333744f, z=4.4948925E-4f)), LineSegment3F(a=Point3F(x=0.015416568f, y=0.75333744f, z=4.4948925E-4f), b=Point3F(x=0.017132644f, y=0.7539359f, z=4.8232175E-4f)), LineSegment3F(a=Point3F(x=0.017132644f, y=0.7539359f, z=4.8232175E-4f), b=Point3F(x=0.01884872f, y=0.7545343f, z=5.151542E-4f)), LineSegment3F(a=Point3F(x=0.01884872f, y=0.7545343f, z=5.151542E-4f), b=Point3F(x=0.020564796f, y=0.75513285f, z=5.479867E-4f)), LineSegment3F(a=Point3F(x=0.020564796f, y=0.75513285f, z=5.479867E-4f), b=Point3F(x=0.021884587f, y=0.7567503f, z=5.934255E-4f)), LineSegment3F(a=Point3F(x=0.021884587f, y=0.7567503f, z=5.934255E-4f), b=Point3F(x=0.021956349f, y=0.75884974f, z=5.902431E-4f)), LineSegment3F(a=Point3F(x=0.021956349f, y=0.75884974f, z=5.902431E-4f), b=Point3F(x=0.021001458f, y=0.7603075f, z=5.614883E-4f)), LineSegment3F(a=Point3F(x=0.021001458f, y=0.7603075f, z=5.614883E-4f), b=Point3F(x=0.020046568f, y=0.76176524f, z=5.3273357E-4f)), LineSegment3F(a=Point3F(x=0.020046568f, y=0.76176524f, z=5.3273357E-4f), b=Point3F(x=0.019091675f, y=0.763223f, z=5.0397887E-4f)), LineSegment3F(a=Point3F(x=0.019091675f, y=0.763223f, z=5.0397887E-4f), b=Point3F(x=0.01705477f, y=0.76349574f, z=4.199824E-4f)), LineSegment3F(a=Point3F(x=0.01705477f, y=0.76349574f, z=4.199824E-4f), b=Point3F(x=0.015017865f, y=0.7637685f, z=3.359859E-4f)), LineSegment3F(a=Point3F(x=0.015017865f, y=0.7637685f, z=3.359859E-4f), b=Point3F(x=0.01298096f, y=0.76404124f, z=2.5198943E-4f)), LineSegment3F(a=Point3F(x=0.01298096f, y=0.76404124f, z=2.5198943E-4f), b=Point3F(x=0.010944055f, y=0.764314f, z=1.6799296E-4f)), LineSegment3F(a=Point3F(x=0.010944055f, y=0.764314f, z=1.6799296E-4f), b=Point3F(x=0.0089071505f, y=0.76458675f, z=8.399648E-5f)), LineSegment3F(a=Point3F(x=0.0089071505f, y=0.76458675f, z=8.399648E-5f), b=Point3F(x=0.0068702437f, y=0.7648596f, z=0.0f)), LineSegment3F(a=Point3F(x=0.0068702437f, y=0.7648596f, z=0.0f), b=Point3F(x=0.0067341495f, y=0.76376545f, z=0.0f)))
            val riverSkeleton = arrayListOf<LineSegment3F>()
            val globalVertices = PointSet2F(epsilon=0.0001f, points=arrayListOf(Point3F(x=0.0067341495f, y=0.76376545f, z=0.0f), Point3F(x=0.007179815f, y=0.7627611f, z=0.0f), Point3F(x=0.008497868f, y=0.7632296f, z=0.0f), Point3F(x=0.009580787f, y=0.76411796f, z=0.0f), Point3F(x=0.010944055f, y=0.7643141f, z=1.6799296E-4f), Point3F(x=0.01145069f, y=0.76284593f, z=0.0f), Point3F(x=0.011211649f, y=0.7613663f, z=0.0f), Point3F(x=0.0106831705f, y=0.7599637f, z=0.0f), Point3F(x=0.010155018f, y=0.758561f, z=0.0f), Point3F(x=0.009466225f, y=0.7568689f, z=0.0f), Point3F(x=0.008568201f, y=0.755278f, z=0.0f), Point3F(x=0.0076343725f, y=0.7537078f, z=0.0f), Point3F(x=0.0070808274f, y=0.7531171f, z=0.0f), Point3F(x=0.008674584f, y=0.7523296f, z=1.7549588E-4f), Point3F(x=0.010268341f, y=0.75154215f, z=3.5099176E-4f), Point3F(x=0.011984417f, y=0.7521406f, z=3.8382426E-4f), Point3F(x=0.013700493f, y=0.752739f, z=4.1665675E-4f), Point3F(x=0.015416568f, y=0.75333744f, z=4.4948925E-4f), Point3F(x=0.017132644f, y=0.7539359f, z=4.8232175E-4f), Point3F(x=0.01884872f, y=0.7545343f, z=5.151542E-4f), Point3F(x=0.020564796f, y=0.75513285f, z=5.479867E-4f), Point3F(x=0.021884587f, y=0.7567503f, z=5.934255E-4f), Point3F(x=0.021956349f, y=0.75884974f, z=5.902431E-4f), Point3F(x=0.021001456f, y=0.7603075f, z=5.614884E-4f), Point3F(x=0.020046566f, y=0.76176524f, z=5.327336E-4f), Point3F(x=0.019091675f, y=0.763223f, z=5.0397887E-4f), Point3F(x=0.01705477f, y=0.76349586f, z=4.199824E-4f), Point3F(x=0.015017865f, y=0.7637686f, z=3.359859E-4f), Point3F(x=0.01298096f, y=0.76404136f, z=2.5198943E-4f), Point3F(x=0.0089071505f, y=0.76458687f, z=8.399648E-5f), Point3F(x=0.006870245f, y=0.7648596f, z=0.0f)))
            buildMesh(edgeSkeleton, riverSkeleton, globalVertices)
        }

        val test4 = {
            val edgeSkeleton = arrayListOf(LineSegment3F(a=Point3F(x=0.40774828f, y=0.7181896f, z=0.017279452f), b=Point3F(x=0.4086771f, y=0.7165772f, z=0.017590685f)), LineSegment3F(a=Point3F(x=0.4086771f, y=0.7165772f, z=0.017590685f), b=Point3F(x=0.40960592f, y=0.71496475f, z=0.017901918f)), LineSegment3F(a=Point3F(x=0.40960592f, y=0.71496475f, z=0.017901918f), b=Point3F(x=0.41053474f, y=0.7133523f, z=0.018213151f)), LineSegment3F(a=Point3F(x=0.41053474f, y=0.7133523f, z=0.018213151f), b=Point3F(x=0.41146356f, y=0.7117399f, z=0.018524384f)), LineSegment3F(a=Point3F(x=0.41146356f, y=0.7117399f, z=0.018524384f), b=Point3F(x=0.41239238f, y=0.7101275f, z=0.018835617f)), LineSegment3F(a=Point3F(x=0.41239238f, y=0.7101275f, z=0.018835617f), b=Point3F(x=0.4133212f, y=0.70851505f, z=0.01914685f)), LineSegment3F(a=Point3F(x=0.4133212f, y=0.70851505f, z=0.01914685f), b=Point3F(x=0.41425002f, y=0.7069026f, z=0.019458083f)), LineSegment3F(a=Point3F(x=0.41425002f, y=0.7069026f, z=0.019458083f), b=Point3F(x=0.41517884f, y=0.7052902f, z=0.019769317f)), LineSegment3F(a=Point3F(x=0.41517884f, y=0.7052902f, z=0.019769317f), b=Point3F(x=0.41610768f, y=0.7036779f, z=0.020080557f)), LineSegment3F(a=Point3F(x=0.41610768f, y=0.7036779f, z=0.020080557f), b=Point3F(x=0.41610768f, y=0.7036779f, z=0.020080557f)), LineSegment3F(a=Point3F(x=0.41610768f, y=0.7036779f, z=0.020080557f), b=Point3F(x=0.41779527f, y=0.7030201f, z=0.020523518f)), LineSegment3F(a=Point3F(x=0.41779527f, y=0.7030201f, z=0.020523518f), b=Point3F(x=0.41948286f, y=0.7023623f, z=0.02096648f)), LineSegment3F(a=Point3F(x=0.41948286f, y=0.7023623f, z=0.02096648f), b=Point3F(x=0.42117044f, y=0.7017045f, z=0.02140944f)), LineSegment3F(a=Point3F(x=0.42117044f, y=0.7017045f, z=0.02140944f), b=Point3F(x=0.42285803f, y=0.7010467f, z=0.021852402f)), LineSegment3F(a=Point3F(x=0.42285803f, y=0.7010467f, z=0.021852402f), b=Point3F(x=0.42454562f, y=0.7003889f, z=0.022295363f)), LineSegment3F(a=Point3F(x=0.42454562f, y=0.7003889f, z=0.022295363f), b=Point3F(x=0.42623317f, y=0.69973093f, z=0.022738326f)), LineSegment3F(a=Point3F(x=0.42623317f, y=0.69973093f, z=0.022738326f), b=Point3F(x=0.42623317f, y=0.69973093f, z=0.022738326f)), LineSegment3F(a=Point3F(x=0.42623317f, y=0.69973093f, z=0.022738326f), b=Point3F(x=0.42642236f, y=0.7017276f, z=0.022169983f)), LineSegment3F(a=Point3F(x=0.42642236f, y=0.7017276f, z=0.022169983f), b=Point3F(x=0.42661154f, y=0.7037243f, z=0.02160164f)), LineSegment3F(a=Point3F(x=0.42661154f, y=0.7037243f, z=0.02160164f), b=Point3F(x=0.42680073f, y=0.705721f, z=0.021033296f)), LineSegment3F(a=Point3F(x=0.42680073f, y=0.705721f, z=0.021033296f), b=Point3F(x=0.4269899f, y=0.7077177f, z=0.020464953f)), LineSegment3F(a=Point3F(x=0.4269899f, y=0.7077177f, z=0.020464953f), b=Point3F(x=0.4271791f, y=0.7097144f, z=0.01989661f)), LineSegment3F(a=Point3F(x=0.4271791f, y=0.7097144f, z=0.01989661f), b=Point3F(x=0.42736828f, y=0.7117111f, z=0.019328266f)), LineSegment3F(a=Point3F(x=0.42736828f, y=0.7117111f, z=0.019328266f), b=Point3F(x=0.42755747f, y=0.7137078f, z=0.018759923f)), LineSegment3F(a=Point3F(x=0.42755747f, y=0.7137078f, z=0.018759923f), b=Point3F(x=0.42774665f, y=0.7157045f, z=0.01819158f)), LineSegment3F(a=Point3F(x=0.42774665f, y=0.7157045f, z=0.01819158f), b=Point3F(x=0.42793584f, y=0.7177012f, z=0.017623236f)), LineSegment3F(a=Point3F(x=0.42793584f, y=0.7177012f, z=0.017623236f), b=Point3F(x=0.42812502f, y=0.7196979f, z=0.017054893f)), LineSegment3F(a=Point3F(x=0.42812502f, y=0.7196979f, z=0.017054893f), b=Point3F(x=0.4283142f, y=0.7216946f, z=0.01648655f)), LineSegment3F(a=Point3F(x=0.4283142f, y=0.7216946f, z=0.01648655f), b=Point3F(x=0.42850325f, y=0.72369146f, z=0.015918208f)), LineSegment3F(a=Point3F(x=0.42850325f, y=0.72369146f, z=0.015918208f), b=Point3F(x=0.42850325f, y=0.72369146f, z=0.015918208f)), LineSegment3F(a=Point3F(x=0.42850325f, y=0.72369146f, z=0.015918208f), b=Point3F(x=0.42690554f, y=0.7244217f, z=0.010612139f)), LineSegment3F(a=Point3F(x=0.42690554f, y=0.7244217f, z=0.010612139f), b=Point3F(x=0.42530784f, y=0.7251519f, z=0.005306069f)), LineSegment3F(a=Point3F(x=0.42530784f, y=0.7251519f, z=0.005306069f), b=Point3F(x=0.4237101f, y=0.7258822f, z=0.0f)), LineSegment3F(a=Point3F(x=0.4237101f, y=0.7258822f, z=0.0f), b=Point3F(x=0.4237101f, y=0.7258822f, z=0.0f)), LineSegment3F(a=Point3F(x=0.4237101f, y=0.7258822f, z=0.0f), b=Point3F(x=0.4237101f, y=0.7258822f, z=0.0f)), LineSegment3F(a=Point3F(x=0.4237101f, y=0.7258822f, z=0.0f), b=Point3F(x=0.4237101f, y=0.7258822f, z=0.0f)), LineSegment3F(a=Point3F(x=0.4237101f, y=0.7258822f, z=0.0f), b=Point3F(x=0.4237101f, y=0.7258822f, z=0.0f)), LineSegment3F(a=Point3F(x=0.4237101f, y=0.7258822f, z=0.0f), b=Point3F(x=0.42211238f, y=0.72661245f, z=0.005462838f)), LineSegment3F(a=Point3F(x=0.42211238f, y=0.72661245f, z=0.005462838f), b=Point3F(x=0.42051464f, y=0.7273427f, z=0.010925676f)), LineSegment3F(a=Point3F(x=0.42051464f, y=0.7273427f, z=0.010925676f), b=Point3F(x=0.41891694f, y=0.72807294f, z=0.016388513f)), LineSegment3F(a=Point3F(x=0.41891694f, y=0.72807294f, z=0.016388513f), b=Point3F(x=0.41891694f, y=0.72807294f, z=0.016388513f)), LineSegment3F(a=Point3F(x=0.41891694f, y=0.72807294f, z=0.016388513f), b=Point3F(x=0.41686848f, y=0.72713494f, z=0.016110906f)), LineSegment3F(a=Point3F(x=0.41686848f, y=0.72713494f, z=0.016110906f), b=Point3F(x=0.41482002f, y=0.72619694f, z=0.0158333f)), LineSegment3F(a=Point3F(x=0.41482002f, y=0.72619694f, z=0.0158333f), b=Point3F(x=0.41277155f, y=0.72525895f, z=0.015555694f)), LineSegment3F(a=Point3F(x=0.41277155f, y=0.72525895f, z=0.015555694f), b=Point3F(x=0.41277155f, y=0.72525895f, z=0.015555694f)), LineSegment3F(a=Point3F(x=0.41277155f, y=0.72525895f, z=0.015555694f), b=Point3F(x=0.41185942f, y=0.7239753f, z=0.007777847f)), LineSegment3F(a=Point3F(x=0.41185942f, y=0.7239753f, z=0.007777847f), b=Point3F(x=0.4109473f, y=0.7226916f, z=0.0f)), LineSegment3F(a=Point3F(x=0.4109473f, y=0.7226916f, z=0.0f), b=Point3F(x=0.4109473f, y=0.7226916f, z=0.0f)), LineSegment3F(a=Point3F(x=0.4109473f, y=0.7226916f, z=0.0f), b=Point3F(x=0.4109473f, y=0.7226916f, z=0.0f)), LineSegment3F(a=Point3F(x=0.4109473f, y=0.7226916f, z=0.0f), b=Point3F(x=0.41103268f, y=0.72259533f, z=3.8601784E-6f)), LineSegment3F(a=Point3F(x=0.41103268f, y=0.72259533f, z=3.8601784E-6f), b=Point3F(x=0.4111181f, y=0.72249913f, z=0.0f)), LineSegment3F(a=Point3F(x=0.4111181f, y=0.72249913f, z=0.0f), b=Point3F(x=0.4111181f, y=0.72249913f, z=0.0f)), LineSegment3F(a=Point3F(x=0.4111181f, y=0.72249913f, z=0.0f), b=Point3F(x=0.4111181f, y=0.72249913f, z=0.0f)), LineSegment3F(a=Point3F(x=0.4111181f, y=0.72249913f, z=0.0f), b=Point3F(x=0.4109766f, y=0.7225164f, z=4.2765982E-6f)), LineSegment3F(a=Point3F(x=0.4109766f, y=0.7225164f, z=4.2765982E-6f), b=Point3F(x=0.4108351f, y=0.7225337f, z=0.0f)), LineSegment3F(a=Point3F(x=0.4108351f, y=0.7225337f, z=0.0f), b=Point3F(x=0.4108351f, y=0.7225337f, z=0.0f)), LineSegment3F(a=Point3F(x=0.4108351f, y=0.7225337f, z=0.0f), b=Point3F(x=0.4108351f, y=0.7225337f, z=0.0f)), LineSegment3F(a=Point3F(x=0.4108351f, y=0.7225337f, z=0.0f), b=Point3F(x=0.40901357f, y=0.72275615f, z=5.5051503E-5f)), LineSegment3F(a=Point3F(x=0.40901357f, y=0.72275615f, z=5.5051503E-5f), b=Point3F(x=0.40719202f, y=0.72297865f, z=0.0f)), LineSegment3F(a=Point3F(x=0.40719202f, y=0.72297865f, z=0.0f), b=Point3F(x=0.40719202f, y=0.72297865f, z=0.0f)), LineSegment3F(a=Point3F(x=0.40719202f, y=0.72297865f, z=0.0f), b=Point3F(x=0.40719202f, y=0.72297865f, z=0.0f)), LineSegment3F(a=Point3F(x=0.40719202f, y=0.72297865f, z=0.0f), b=Point3F(x=0.4060156f, y=0.7240021f, z=4.677991E-5f)), LineSegment3F(a=Point3F(x=0.4060156f, y=0.7240021f, z=4.677991E-5f), b=Point3F(x=0.4048392f, y=0.72502565f, z=9.355982E-5f)), LineSegment3F(a=Point3F(x=0.4048392f, y=0.72502565f, z=9.355982E-5f), b=Point3F(x=0.40366277f, y=0.7260492f, z=4.677991E-5f)), LineSegment3F(a=Point3F(x=0.40366277f, y=0.7260492f, z=4.677991E-5f), b=Point3F(x=0.40248635f, y=0.7270727f, z=0.0f)), LineSegment3F(a=Point3F(x=0.40248635f, y=0.7270727f, z=0.0f), b=Point3F(x=0.40248635f, y=0.7270727f, z=0.0f)), LineSegment3F(a=Point3F(x=0.40248635f, y=0.7270727f, z=0.0f), b=Point3F(x=0.40248635f, y=0.7270727f, z=0.0f)), LineSegment3F(a=Point3F(x=0.40248635f, y=0.7270727f, z=0.0f), b=Point3F(x=0.40028688f, y=0.72727764f, z=6.6269895E-5f)), LineSegment3F(a=Point3F(x=0.40028688f, y=0.72727764f, z=6.6269895E-5f), b=Point3F(x=0.3980874f, y=0.72748256f, z=0.0f)), LineSegment3F(a=Point3F(x=0.3980874f, y=0.72748256f, z=0.0f), b=Point3F(x=0.3980874f, y=0.72748256f, z=0.0f)), LineSegment3F(a=Point3F(x=0.3980874f, y=0.72748256f, z=0.0f), b=Point3F(x=0.3980874f, y=0.72748256f, z=0.0f)), LineSegment3F(a=Point3F(x=0.3980874f, y=0.72748256f, z=0.0f), b=Point3F(x=0.39563477f, y=0.72718143f, z=7.413173E-5f)), LineSegment3F(a=Point3F(x=0.39563477f, y=0.72718143f, z=7.413173E-5f), b=Point3F(x=0.39318216f, y=0.7268804f, z=0.0f)), LineSegment3F(a=Point3F(x=0.39318216f, y=0.7268804f, z=0.0f), b=Point3F(x=0.39318216f, y=0.7268804f, z=0.0f)), LineSegment3F(a=Point3F(x=0.39318216f, y=0.7268804f, z=0.0f), b=Point3F(x=0.39318216f, y=0.7268804f, z=0.0f)), LineSegment3F(a=Point3F(x=0.39318216f, y=0.7268804f, z=0.0f), b=Point3F(x=0.3941198f, y=0.7252929f, z=5.5310506E-5f)), LineSegment3F(a=Point3F(x=0.3941198f, y=0.7252929f, z=5.5310506E-5f), b=Point3F(x=0.39505747f, y=0.7237054f, z=0.0f)), LineSegment3F(a=Point3F(x=0.39505747f, y=0.7237054f, z=0.0f), b=Point3F(x=0.39505747f, y=0.7237054f, z=0.0f)), LineSegment3F(a=Point3F(x=0.39505747f, y=0.7237054f, z=0.0f), b=Point3F(x=0.39505747f, y=0.7237054f, z=0.0f)), LineSegment3F(a=Point3F(x=0.39505747f, y=0.7237054f, z=0.0f), b=Point3F(x=0.39704707f, y=0.72322774f, z=6.138421E-5f)), LineSegment3F(a=Point3F(x=0.39704707f, y=0.72322774f, z=6.138421E-5f), b=Point3F(x=0.39903668f, y=0.72275007f, z=0.0f)), LineSegment3F(a=Point3F(x=0.39903668f, y=0.72275007f, z=0.0f), b=Point3F(x=0.39903668f, y=0.72275007f, z=0.0f)), LineSegment3F(a=Point3F(x=0.39903668f, y=0.72275007f, z=0.0f), b=Point3F(x=0.39903668f, y=0.72275007f, z=0.0f)), LineSegment3F(a=Point3F(x=0.39903668f, y=0.72275007f, z=0.0f), b=Point3F(x=0.39902773f, y=0.7215365f, z=3.6407506E-5f)), LineSegment3F(a=Point3F(x=0.39902773f, y=0.7215365f, z=3.6407506E-5f), b=Point3F(x=0.3990188f, y=0.72032297f, z=0.0f)), LineSegment3F(a=Point3F(x=0.3990188f, y=0.72032297f, z=0.0f), b=Point3F(x=0.3990188f, y=0.72032297f, z=0.0f)), LineSegment3F(a=Point3F(x=0.3990188f, y=0.72032297f, z=0.0f), b=Point3F(x=0.3990188f, y=0.72032297f, z=0.0f)), LineSegment3F(a=Point3F(x=0.3990188f, y=0.72032297f, z=0.0f), b=Point3F(x=0.3975471f, y=0.7200244f, z=4.5050307E-5f)), LineSegment3F(a=Point3F(x=0.3975471f, y=0.7200244f, z=4.5050307E-5f), b=Point3F(x=0.3960754f, y=0.71972585f, z=0.0f)), LineSegment3F(a=Point3F(x=0.3960754f, y=0.71972585f, z=0.0f), b=Point3F(x=0.3960754f, y=0.71972585f, z=0.0f)), LineSegment3F(a=Point3F(x=0.3960754f, y=0.71972585f, z=0.0f), b=Point3F(x=0.3960754f, y=0.71972585f, z=0.0f)), LineSegment3F(a=Point3F(x=0.3960754f, y=0.71972585f, z=0.0f), b=Point3F(x=0.39687163f, y=0.7190734f, z=0.0066778576f)), LineSegment3F(a=Point3F(x=0.39687163f, y=0.7190734f, z=0.0066778576f), b=Point3F(x=0.39687163f, y=0.7190734f, z=0.0066778576f)), LineSegment3F(a=Point3F(x=0.39687163f, y=0.7190734f, z=0.0066778576f), b=Point3F(x=0.3986844f, y=0.71892613f, z=0.00844479f)), LineSegment3F(a=Point3F(x=0.3986844f, y=0.71892613f, z=0.00844479f), b=Point3F(x=0.4004972f, y=0.71877885f, z=0.010211722f)), LineSegment3F(a=Point3F(x=0.4004972f, y=0.71877885f, z=0.010211722f), b=Point3F(x=0.40230998f, y=0.71863157f, z=0.011978654f)), LineSegment3F(a=Point3F(x=0.40230998f, y=0.71863157f, z=0.011978654f), b=Point3F(x=0.40412277f, y=0.7184843f, z=0.013745586f)), LineSegment3F(a=Point3F(x=0.40412277f, y=0.7184843f, z=0.013745586f), b=Point3F(x=0.40593556f, y=0.718337f, z=0.015512519f)), LineSegment3F(a=Point3F(x=0.40593556f, y=0.718337f, z=0.015512519f), b=Point3F(x=0.40774828f, y=0.7181896f, z=0.017279452f)))
            val riverSkeleton = arrayListOf(LineSegment3F(a=Point3F(x=0.42222214f, y=0.7179858f, z=0.010226578f), b=Point3F(x=0.4225433f, y=0.7192991f, z=0.009937943f)), LineSegment3F(a=Point3F(x=0.4225433f, y=0.7192991f, z=0.009937943f), b=Point3F(x=0.4225711f, y=0.72065073f, z=0.009649313f)), LineSegment3F(a=Point3F(x=0.4225711f, y=0.72065073f, z=0.009649313f), b=Point3F(x=0.42253873f, y=0.7220093f, z=0.0093591865f)), LineSegment3F(a=Point3F(x=0.42253873f, y=0.7220093f, z=0.0093591865f), b=Point3F(x=0.4229069f, y=0.7233173f, z=0.009069081f)), LineSegment3F(a=Point3F(x=0.4229069f, y=0.7233173f, z=0.009069081f), b=Point3F(x=0.42327547f, y=0.7246101f, z=0.008782088f)), LineSegment3F(a=Point3F(x=0.42327547f, y=0.7246101f, z=0.008782088f), b=Point3F(x=0.4237101f, y=0.7258822f, z=0.0f)))
            val globalVertices = PointSet2F(epsilon=0.0001f, points=arrayListOf(Point3F(x=0.40774828f, y=0.7181896f, z=0.017279452f), Point3F(x=0.4086771f, y=0.7165772f, z=0.017590685f), Point3F(x=0.40960592f, y=0.71496475f, z=0.017901918f), Point3F(x=0.41053474f, y=0.7133523f, z=0.018213151f), Point3F(x=0.41146356f, y=0.7117399f, z=0.018524384f), Point3F(x=0.41239238f, y=0.7101275f, z=0.018835617f), Point3F(x=0.4133212f, y=0.70851505f, z=0.01914685f), Point3F(x=0.41425002f, y=0.7069026f, z=0.019458083f), Point3F(x=0.41517884f, y=0.7052902f, z=0.019769317f), Point3F(x=0.41610768f, y=0.7036779f, z=0.020080557f), Point3F(x=0.41779527f, y=0.7030201f, z=0.020523518f), Point3F(x=0.41948286f, y=0.7023623f, z=0.02096648f), Point3F(x=0.42117044f, y=0.7017045f, z=0.02140944f), Point3F(x=0.42285803f, y=0.7010467f, z=0.021852402f), Point3F(x=0.42454562f, y=0.7003889f, z=0.022295363f), Point3F(x=0.42623317f, y=0.69973093f, z=0.022738326f), Point3F(x=0.42642236f, y=0.7017276f, z=0.022169983f), Point3F(x=0.42661154f, y=0.7037243f, z=0.02160164f), Point3F(x=0.42680073f, y=0.705721f, z=0.021033296f), Point3F(x=0.4269899f, y=0.7077177f, z=0.020464953f), Point3F(x=0.4271791f, y=0.7097144f, z=0.01989661f), Point3F(x=0.42736828f, y=0.7117111f, z=0.019328266f), Point3F(x=0.42755747f, y=0.7137078f, z=0.018759923f), Point3F(x=0.42774665f, y=0.7157045f, z=0.01819158f), Point3F(x=0.42793584f, y=0.7177012f, z=0.017623236f), Point3F(x=0.42812502f, y=0.7196979f, z=0.017054893f), Point3F(x=0.4283142f, y=0.7216946f, z=0.01648655f), Point3F(x=0.42850325f, y=0.72369146f, z=0.015918208f), Point3F(x=0.42690554f, y=0.7244217f, z=0.010612139f), Point3F(x=0.42530784f, y=0.7251519f, z=0.005306069f), Point3F(x=0.4237101f, y=0.7258822f, z=0.0f), Point3F(x=0.42211238f, y=0.72661245f, z=0.005462838f), Point3F(x=0.42051464f, y=0.7273427f, z=0.010925676f), Point3F(x=0.41891694f, y=0.72807294f, z=0.016388513f), Point3F(x=0.41686848f, y=0.72713494f, z=0.016110906f), Point3F(x=0.41482002f, y=0.72619694f, z=0.0158333f), Point3F(x=0.41277155f, y=0.72525895f, z=0.015555694f), Point3F(x=0.41185942f, y=0.7239753f, z=0.007777847f), Point3F(x=0.4109473f, y=0.7226916f, z=0.0f), Point3F(x=0.41103268f, y=0.72259533f, z=3.8601784E-6f), Point3F(x=0.4111181f, y=0.72249913f, z=0.0f), Point3F(x=0.4108351f, y=0.7225337f, z=0.0f), Point3F(x=0.40901357f, y=0.72275615f, z=5.5051503E-5f), Point3F(x=0.40719202f, y=0.72297865f, z=0.0f), Point3F(x=0.4060156f, y=0.7240021f, z=4.677991E-5f), Point3F(x=0.4048392f, y=0.72502565f, z=9.355982E-5f), Point3F(x=0.40366277f, y=0.7260492f, z=4.677991E-5f), Point3F(x=0.40248635f, y=0.7270727f, z=0.0f), Point3F(x=0.40028688f, y=0.72727764f, z=6.6269895E-5f), Point3F(x=0.3980874f, y=0.72748256f, z=0.0f), Point3F(x=0.39563477f, y=0.72718143f, z=7.413173E-5f), Point3F(x=0.39318216f, y=0.7268804f, z=0.0f), Point3F(x=0.3941198f, y=0.7252929f, z=5.5310506E-5f), Point3F(x=0.39505747f, y=0.7237054f, z=0.0f), Point3F(x=0.39704707f, y=0.72322774f, z=6.138421E-5f), Point3F(x=0.39903668f, y=0.72275007f, z=0.0f), Point3F(x=0.39902773f, y=0.7215365f, z=3.6407506E-5f), Point3F(x=0.3990188f, y=0.72032297f, z=0.0f), Point3F(x=0.3975471f, y=0.7200244f, z=4.5050307E-5f), Point3F(x=0.3960754f, y=0.71972585f, z=0.0f), Point3F(x=0.39687163f, y=0.7190734f, z=0.0066778576f), Point3F(x=0.3986844f, y=0.71892613f, z=0.00844479f), Point3F(x=0.4004972f, y=0.71877885f, z=0.010211722f), Point3F(x=0.40230998f, y=0.71863157f, z=0.011978654f), Point3F(x=0.40412277f, y=0.7184843f, z=0.013745586f), Point3F(x=0.40593556f, y=0.718337f, z=0.015512519f), Point3F(x=0.42222214f, y=0.7179858f, z=0.010226578f), Point3F(x=0.4225433f, y=0.7192991f, z=0.009937943f), Point3F(x=0.4225711f, y=0.72065073f, z=0.009649313f), Point3F(x=0.42253873f, y=0.7220093f, z=0.0093591865f), Point3F(x=0.4229069f, y=0.7233173f, z=0.009069081f), Point3F(x=0.42327547f, y=0.7246101f, z=0.008782088f)))
            buildMesh(edgeSkeleton, riverSkeleton, globalVertices)
        }

        val tests = listOf<() -> Any?>(
                test4
        )

        debug = true

        tests.forEach { test ->
            test()
            debugIteration.incrementAndGet()
        }
    }
}

private class CollinearPatch(val start: Point2F, val end: Point2F, val points: ArrayList<Point2F>)

fun triangulatePolygon(vertices: PointSet2F, polygon: ArrayList<Pair<Int, Int>>): LinkedHashSet<Set<Int>> {
    val points = ArrayList(polygon.map { vertices[it.first]!! })
    if (areaOfPolygon(points) < 0) {
        points.reverse()
    }
    val collinearPatches = findCollinearPatches(points)
    collinearPatches.forEach {
        points.removeAll(it.points)
    }
    val reducedPoints = ArrayList(points)
    val newEdges = ArrayList<LineSegment2F>()
    while (points.size > 3) {
        val (ai, bi, ci) = findNextEar(points)
        try {
            newEdges.add(LineSegment2F(points[ai], points[ci]))
            if (debug) {
                draw(debugResolution, "debug-triangulatePolygon1-${debugIteration.get()}-${debugCount.andIncrement}", "output", Color.WHITE, debugZoom, Vector2F(-(vertices.map { it.x }.min()!!) + 0.0005f, -(vertices.map { it.y }.min()!!) + 0.0005f)) {
                    graphics.color = Color.BLACK
                    for (i in 1..points.size) {
                        val a = points[i - 1]!!
                        val b = points[i % points.size]!!
                        drawEdge(a, b)
                        drawPoint(a, 3)
                        drawPoint(b, 3)
                    }
                    graphics.color = Color.RED
                    drawEdge(points[ai], points[ci])
                    drawPoint(points[ai], 4)
                    drawPoint(points[ci], 4)
                    graphics.color = Color.GREEN
                    points.forEach {
                        drawPoint(it, 2)
                    }
                }
                breakPoint()
            }
            points.removeAt(bi)
        } catch (e: Exception) {
            if (debug) {
                draw(debugResolution, "debug-triangulatePolygon2-${debugIteration.get()}-${debugCount.andIncrement}", "output", Color.WHITE, debugZoom, Vector2F(-(vertices.map { it.x }.min()!!) + 0.0005f, -(vertices.map { it.y }.min()!!) + 0.0005f)) {
                    graphics.color = Color.BLACK
                    newEdges.forEach {
                        val a = it.a
                        val b = it.b
                        drawEdge(a, b)
                        drawPoint(a, 4)
                        drawPoint(b, 4)
                    }
                    graphics.color = Color.RED
                    polygon.forEach {
                        val a = vertices[it.first]!!
                        val b = vertices[it.second]!!
                        drawEdge(a, b)
                        drawPoint(a, 3)
                        drawPoint(b, 3)
                    }
                    graphics.color = Color.GREEN
                    points.forEach {
                        drawPoint(it, 2)
                    }
                }
                breakPoint()
            }
            throw GeometryException("unable to triangulate", e).with {
                data.add("val test = {")
                data.add("val vertices = $vertices")
                data.add("val polygon = ${printList(polygon) { "Pair$it" }}")
                data.add("triangulatePolygon(vertices, polygon)")
                data.add("}")
            }
        }
    }
    var reducedPolygon = polygonFromPoints(vertices, reducedPoints)
    var meshMinusPatches = buildMesh(reducedPolygon + newEdges.map { Pair(vertices[it.a], vertices[it.b]) }, vertices.size)
    while (collinearPatches.isNotEmpty()) {
        val patch = collinearPatches.first()
        collinearPatches.remove(patch)
        val sid = vertices[patch.start]
        val eid = vertices[patch.end]
        val edge = listOf(sid, eid)
        for (tri in ArrayList(meshMinusPatches)) {
            if (tri.containsAll(edge)) {
                meshMinusPatches.remove(tri)
                val convergence = LinkedHashSet(tri)
                convergence.removeAll(edge)
                val focus = vertices[convergence.first()]!!
                patch.points.forEach {
                    newEdges.add(LineSegment2F(it, focus))
                }
                break
            }
        }
        addPatchPoints(reducedPoints, patch)
        reducedPolygon = polygonFromPoints(vertices, reducedPoints)
        meshMinusPatches = buildMesh(reducedPolygon + newEdges.map { Pair(vertices[it.a], vertices[it.b]) }, vertices.size)
    }
    val triangles = buildMesh(polygon + newEdges.map { Pair(vertices[it.a], vertices[it.b]) }, vertices.size)
    if (debug) {
        draw(debugResolution, "debug-triangulatePolygon1-${debugIteration.get()}-${debugCount.andIncrement}", "output", Color.WHITE, debugZoom, Vector2F(-(vertices.map { it.x }.min()!!) + 0.0005f, -(vertices.map { it.y }.min()!!) + 0.0005f)) {
            graphics.color = Color.BLACK
            triangles.forEach {
                val tri = it.toList()
                val a = vertices[tri[0]]!!
                val b = vertices[tri[1]]!!
                val c = vertices[tri[2]]!!
                drawEdge(a, b)
                drawEdge(b, c)
                drawEdge(c, a)
                drawPoint(a, 3)
                drawPoint(b, 3)
                drawPoint(c, 3)
            }
        }
        breakPoint()
    }
    val flipped = flipEdges(vertices, triangles)
    if (debug) {
        draw(debugResolution, "debug-triangulatePolygon1-${debugIteration.get()}-${debugCount.andIncrement}", "output", Color.WHITE, debugZoom, Vector2F(-(vertices.map { it.x }.min()!!) + 0.0005f, -(vertices.map { it.y }.min()!!) + 0.0005f)) {
            graphics.color = Color.BLACK
            flipped.forEach {
                val tri = it.toList()
                val a = vertices[tri[0]]!!
                val b = vertices[tri[1]]!!
                val c = vertices[tri[2]]!!
                drawEdge(a, b)
                drawEdge(b, c)
                drawEdge(c, a)
                drawPoint(a, 3)
                drawPoint(b, 3)
                drawPoint(c, 3)
            }
        }
        breakPoint()
    }
    return flipped
}

private fun addPatchPoints(points: ArrayList<Point2F>, patch: CollinearPatch) {
    val insertionPoint = points.indexOf(patch.start) + 1
    patch.points.reversed().forEach {
        points.add(insertionPoint, it)
    }
}

private fun polygonFromPoints(vertices: PointSet2F, points: ArrayList<Point2F>): ArrayList<Pair<Int, Int>> {
    val edges = ArrayList<Pair<Int, Int>>()
    for (i in 1..points.size) {
        val a = vertices[points[i - 1]]
        val b = vertices[points[i % points.size]]
        edges.add(Pair(a, b))
    }
    return edges
}

private fun findNextEar(points: ArrayList<Point2F>): Triple<Int, Int, Int> {
    var index1 = -1
    var index2 = -1
    var index3 = -1
    var angle = -0.1
    for (i in 1..points.size) {
        val ai = i - 1
        val bi = i % points.size
        val ci = (i + 1) % points.size
        val a = points[ai]
        val b = points[bi]
        val c = points[ci]
        val normal = (a - b).cross(c - b)
        if (normal >= 0.0f) {
            continue
        }
        if (anyPointWithin(points, ai, bi, ci)) {
            continue
        }
        val newWeight = angle(points, ai, bi, ci)
        if (newWeight > angle) {
            angle = newWeight
            index1 = ai
            index2 = bi
            index3 = ci
        }
    }
    return Triple(index1, index2, index3)
}

private fun findCollinearPatches(points: ArrayList<Point2F>): ArrayList<CollinearPatch> {
    val collinearIds = LinkedHashSet<Int>()
    var patchSum = 0.0
    for (i in 1..points.size) {
        val ai = i - 1
        val bi = i % points.size
        val ci = (i + 1) % points.size
        if (debug && trace) {
            draw(debugResolution, "debug-findCollinearPatches-${debugIteration.get()}-${debugCount.andIncrement}", "output", Color.WHITE, debugZoom, Vector2F(-(points.map { it.x }.min()!!) + 0.0005f, -(points.map { it.y }.min()!!) + 0.0005f)) {
                graphics.color = Color.BLACK
                for (p in 1..points.size) {
                    val a = points[p - 1]
                    val b = points[p % points.size]
                    drawEdge(a, b)
                    drawPoint(a, 3)
                    drawPoint(b, 3)
                }
                graphics.color = Color.RED
                drawEdge(points[bi], points[ai])
                drawEdge(points[bi], points[ci])
                drawPoint(points[ai], 4)
                drawPoint(points[bi], 4)
                drawPoint(points[ci], 4)
                graphics.color = Color.GREEN
                points.forEach {
                    drawPoint(it, 2)
                }
            }
            breakPoint()
        }
        val angle = halfAngle(points, ai, bi, ci)
        if (Math.abs(angle) < 0.08 && patchSum + angle < 0.16) {
            collinearIds.add(bi)
            patchSum += angle
        } else {
            patchSum = 0.0
        }
    }
    val collinearPatches = ArrayList<CollinearPatch>()
    while (collinearIds.isNotEmpty()) {
        collinearPatches.add(buildCollinearPatch(points, collinearIds, collinearIds.first()))
    }
    return collinearPatches
}

private fun buildCollinearPatch(points: ArrayList<Point2F>, collinearIds: LinkedHashSet<Int>, seed: Int): CollinearPatch {
    val collinearPoints = ArrayList<Point2F>()
    collinearIds.remove(seed)
    collinearPoints.add(points[seed])
    var next = (seed + 1) % points.size
    while (collinearIds.contains(next)) {
        collinearIds.remove(next)
        collinearPoints.add(points[next])
        next = (next + 1) % points.size
    }
    val end = points[next]
    next = (seed - 1 + points.size) % points.size
    while (collinearIds.contains(next)) {
        collinearIds.remove(next)
        collinearPoints.add(0, points[next])
        next = (next - 1 + points.size) % points.size
    }
    val start = points[next]
    return (CollinearPatch(start, end, collinearPoints))
}

private fun anyPointWithin(points: ArrayList<Point2F>, ai: Int, bi: Int, ci: Int): Boolean {
    val a = points[ai]
    val b = points[bi]
    val c = points[ci]

    val area = 1.0 / (-b.y * c.x + a.y * (-b.x + c.x) + a.x * (b.y - c.y) + b.x * c.y)

    val s1 = a.y*c.x - a.x*c.y
    val sx = c.y - a.y
    val sy = a.x - c.x

    val t1 = a.x * b.y - a.y*b.x
    val tx = a.y - b.y
    val ty = b.x - a.x

    for (i in 0..points.size - 1) {
        if (i == ai || i == bi || i == ci) {
            continue
        }
        val p = points[i]
        val s = area * (s1 + sx * p.x + sy * p.y)
        val t = area * (t1 + tx * p.x + ty * p.y)
        if (s > 0 && t > 0 && 1.0 - s - t > 0) {
            return true
        }
        if (debug && trace) {
            draw(debugResolution, "debug-anyPointWithin-${debugIteration.get()}-${debugCount.andIncrement}", "output", Color.WHITE, debugZoom, Vector2F(-(points.map { it.x }.min()!!) + 0.0005f, -(points.map { it.y }.min()!!) + 0.0005f)) {
                graphics.color = Color.BLACK
                for (pi in 1..points.size) {
                    val p1 = points[pi - 1]
                    val p2 = points[pi % points.size]
                    drawEdge(p1, p2)
                    drawPoint(p1, 7)
                    drawPoint(p2, 7)
                }
                graphics.color = Color.RED
                drawEdge(points[bi], points[ai])
                drawEdge(points[bi], points[ci])
                drawEdge(points[ci], points[ai])
                drawPoint(points[ai], 5)
                graphics.color = Color.GREEN
                drawPoint(points[bi], 5)
                graphics.color = Color.CYAN
                drawPoint(points[ci], 5)
                graphics.color = Color.MAGENTA
                drawPoint(p, 5)
                graphics.color = Color.BLUE
                drawEdge(points[ai], points[ci])
            }
            breakPoint()
        }
        val distance2 = LineSegment2F(a, c).distance2(p)
        val check1 = distance2 < 0.000000005f
        if (!check1) {
            continue
        }
        val angle = angle(points, ai, i, ci)
        val check2 = angle < 0.08f
        if (check2) {
            return true
        }
    }
    return false
}

private fun halfAngle(points: ArrayList<Point2F>, ai: Int, bi: Int, ci: Int): Double {
    val a = points[ai]
    val b = points[bi]
    val c = points[ci]
    return halfAngle(a, b, c)
}

private fun halfAngle(a: Point2F, b: Point2F, c: Point2F): Double {
    val ba = Vector2F(b, a)
    val bc = Vector2F(b, c)
    val ba3d = Vector3F(ba.a, ba.b, 0.0f)
    val bc3d = Vector3F(bc.a, bc.b, 0.0f)
    return Math.atan(ba3d.cross(bc3d).length / ba.dot(bc).toDouble())
}

private fun angle(points: ArrayList<Point2F>, ai: Int, bi: Int, ci: Int): Double {
    return normalizeHalfAngle(halfAngle(points, ai, bi, ci))
}

private fun angle(a: Point2F, b: Point2F, c: Point2F): Double {
    return normalizeHalfAngle(halfAngle(a, b, c))
}

private fun normalizeHalfAngle(halfAngle: Double): Double {
    return Math.abs(if (halfAngle < 0.0f) {
        halfAngle
    } else {
        -(Math.PI / 2) - ((Math.PI / 2.0) - halfAngle)
    })
}

private fun areaOfPolygon(points: ArrayList<Point2F>): Float {
    var sum1 = 0.0f
    var sum2 = 0.0f
    for (i in 1..points.size) {
        val p1 = points[i - 1]
        val p2 = points[i % points.size]
        sum1 += p1.x * p2.y
        sum2 += p1.y * p2.x
    }
    return (sum1 - sum2) / 2
}

private fun buildMesh(edges: Collection<Pair<Int, Int>>, vertexCount: Int): LinkedHashSet<Set<Int>> {
    val vertexToVertexMap = buildVertexAdjacencyMap(edges, vertexCount)
    val triangleIndices = LinkedHashSet<Set<Int>>()
    for (a in 0..vertexCount - 1) {
        val adjacents = vertexToVertexMap[a]
        for (p in 0..adjacents.size - 2) {
            val b = adjacents[p]
            if (b != a) {
                val secondAdjacents = vertexToVertexMap[b]
                for (q in p + 1..adjacents.size - 1) {
                    val c = adjacents[q]
                    if (c != a && c != b && secondAdjacents.contains(c)) {
                        triangleIndices.add(setOf(a, b, c))
                    }
                }
            }
        }
    }
    return triangleIndices
}

private fun buildVertexAdjacencyMap(edges: Collection<Pair<Int, Int>>, vertexCount: Int): ArrayList<ArrayList<Int>> {
    val vertexToVertexMap = ArrayList<ArrayList<Int>>()
    for (v in 0..vertexCount - 1) {
        vertexToVertexMap.add(ArrayList(5))
    }
    edges.forEach { edge ->
        vertexToVertexMap[edge.first].add(edge.second)
        vertexToVertexMap[edge.second].add(edge.first)
    }
    return vertexToVertexMap
}

private class EdgeNode(var p1: Int, var p2: Int, var t1: TriNode, var t2: TriNode)

private class TriNode(var p1: Int, var p2: Int, var p3: Int, val edges: ArrayList<EdgeNode> = ArrayList())

private fun flipEdges(vertices: PointSet2F, triangles: LinkedHashSet<Set<Int>>): LinkedHashSet<Set<Int>> {
    val edgeNodes = ArrayList<EdgeNode>()
    val triNodes = ArrayList<TriNode>()
    val edgeMap = LinkedHashMap<Set<Int>, ArrayList<TriNode>>()
    triangles.forEach {
        val tri = it.toList()
        val a = tri[0]
        val b = tri[1]
        val c = tri[2]
        val ab = setOf(a, b)
        val bc = setOf(b, c)
        val ca = setOf(c, a)
        val triNode = TriNode(a, b, c)
        edgeMap.getOrPut(ab, { ArrayList() }).add(triNode)
        edgeMap.getOrPut(bc, { ArrayList() }).add(triNode)
        edgeMap.getOrPut(ca, { ArrayList() }).add(triNode)
        triNodes.add(triNode)
    }
    edgeMap.filter { it.value.size == 2 }.entries.forEach {
        val edge = it.key.toList()
        val edgeNode = EdgeNode(edge[0], edge[1], it.value[0], it.value[1])
        edgeNode.t1.edges.add(edgeNode)
        edgeNode.t2.edges.add(edgeNode)
        edgeNodes.add(edgeNode)
    }
    var iterations = 0
    var flips = 1
    while (flips > 0 && iterations < 100) {
        flips = 0
        edgeNodes.forEach { edgeNode ->
            val tri1 = edgeNode.t1
            val tri2 = edgeNode.t2
            val peaks = mutableSetOf(tri1.p1, tri1.p2, tri1.p3, tri2.p1, tri2.p2, tri2.p3)
            val quad = ArrayList(peaks.map { vertices[it]!! })
            peaks.remove(edgeNode.p1)
            peaks.remove(edgeNode.p2)
            val peakLineIds = peaks.toList()
            val baseLine = LineSegment2F(vertices[edgeNode.p1]!!, vertices[edgeNode.p2]!!)
            val peakLine = LineSegment2F(vertices[peakLineIds[0]]!!, vertices[peakLineIds[1]]!!)
            if (hasCollinearTriangle(vertices, tri1, tri2) || (baseLine.intersects(peakLine) && !containsCollinearPoints(quad) && peakLine.length2 < baseLine.length2)) {
                val t1Edges = ArrayList(tri1.edges)
                val t2Edges = ArrayList(tri2.edges)
                t1Edges.remove(edgeNode)
                t2Edges.remove(edgeNode)
                tri1.p1 = peakLineIds[0]
                tri1.p2 = peakLineIds[1]
                tri1.p3 = edgeNode.p1
                tri2.p1 = peakLineIds[0]
                tri2.p2 = peakLineIds[1]
                tri2.p3 = edgeNode.p2
                edgeNode.p1 = peakLineIds[0]
                edgeNode.p2 = peakLineIds[1]
                tri1.edges.clear()
                tri2.edges.clear()
                tri1.edges.add(edgeNode)
                tri2.edges.add(edgeNode)
                (t1Edges + t2Edges).forEach { edge ->
                    if ((edge.p1 == tri1.p1 || edge.p1 == tri1.p2 || edge.p1 == tri1.p3) && (edge.p2 == tri1.p1 || edge.p2 == tri1.p2 || edge.p2 == tri1.p3)) {
                        if (edge.t1 == tri1 || edge.t1 == tri2) {
                            edge.t1 = tri1
                        } else {
                            edge.t2 = tri1
                        }
                        tri1.edges.add(edge)
                    } else {
                        if (edge.t1 == tri1 || edge.t1 == tri2) {
                            edge.t1 = tri2
                        } else {
                            edge.t2 = tri2
                        }
                        tri2.edges.add(edge)
                    }
                }
                flips++
            }
        }
        iterations++
    }
    edgeNodes.forEach { edgeNode ->
        val tri1 = edgeNode.t1
        val tri2 = edgeNode.t2
        val peaks = mutableSetOf(tri1.p1, tri1.p2, tri1.p3, tri2.p1, tri2.p2, tri2.p3)
        val quad = ArrayList(peaks.map { vertices[it]!! })
        peaks.remove(edgeNode.p1)
        peaks.remove(edgeNode.p2)
        val peakLineIds = peaks.toList()
        val baseLine = LineSegment2F(vertices[edgeNode.p1]!!, vertices[edgeNode.p2]!!)
        val peakLine = LineSegment2F(vertices[peakLineIds[0]]!!, vertices[peakLineIds[1]]!!)
        val angle1 = angle(baseLine.a, peakLine.a, baseLine.b)
        val angle2 = angle(baseLine.b, peakLine.b, baseLine.a)
        val minAngle = min(angle1, angle2)
        val maxAngle = max(angle1, angle2)
        val check1 = baseLine.intersects(peakLine)
        val check2 = !containsCollinearPoints(quad)
        val check3 = minAngle < 0.55f && maxAngle > 2.0f
        if (debug) {
            draw(debugResolution, "debug-triangulatePolygon1-${debugIteration.get()}-${debugCount.andIncrement}", "output", Color.WHITE, debugZoom, Vector2F(-(vertices.map { it.x }.min()!!) + 0.0005f, -(vertices.map { it.y }.min()!!) + 0.0005f)) {
                graphics.color = Color.BLACK
                triNodes.forEach {
                    val a = vertices[it.p1]!!
                    val b = vertices[it.p2]!!
                    val c = vertices[it.p3]!!
                    drawEdge(a, b)
                    drawEdge(b, c)
                    drawEdge(c, a)
                    drawPoint(a, 3)
                    drawPoint(b, 3)
                    drawPoint(c, 3)
                }
                graphics.color = if (check1 && check2 && check3) Color.RED else Color.BLUE
                listOf(tri1, tri2).forEach {
                    val a = vertices[it.p1]!!
                    val b = vertices[it.p2]!!
                    val c = vertices[it.p3]!!
                    drawEdge(a, b)
                    drawEdge(b, c)
                    drawEdge(c, a)
                    drawPoint(a, 2)
                    drawPoint(b, 2)
                    drawPoint(c, 2)
                }
            }
            breakPoint()
        }
        if (check1 && check2 && check3) {
            val t1Edges = ArrayList(tri1.edges)
            val t2Edges = ArrayList(tri2.edges)
            t1Edges.remove(edgeNode)
            t2Edges.remove(edgeNode)
            tri1.p1 = peakLineIds[0]
            tri1.p2 = peakLineIds[1]
            tri1.p3 = edgeNode.p1
            tri2.p1 = peakLineIds[0]
            tri2.p2 = peakLineIds[1]
            tri2.p3 = edgeNode.p2
            edgeNode.p1 = peakLineIds[0]
            edgeNode.p2 = peakLineIds[1]
            tri1.edges.clear()
            tri2.edges.clear()
            tri1.edges.add(edgeNode)
            tri2.edges.add(edgeNode)
            (t1Edges + t2Edges).forEach { edge ->
                if ((edge.p1 == tri1.p1 || edge.p1 == tri1.p2 || edge.p1 == tri1.p3) && (edge.p2 == tri1.p1 || edge.p2 == tri1.p2 || edge.p2 == tri1.p3)) {
                    if (edge.t1 == tri1 || edge.t1 == tri2) {
                        edge.t1 = tri1
                    } else {
                        edge.t2 = tri1
                    }
                    tri1.edges.add(edge)
                } else {
                    if (edge.t1 == tri1 || edge.t1 == tri2) {
                        edge.t1 = tri2
                    } else {
                        edge.t2 = tri2
                    }
                    tri2.edges.add(edge)
                }
            }
        }
    }
    triangles.clear()
    triNodes.forEach {
        triangles.add(setOf(it.p1, it.p2, it.p3))
    }
    return triangles
}

private fun hasCollinearTriangle(vertices: PointSet2F, tri1: TriNode, tri2: TriNode) = isCollinearTriangle(vertices, tri1) || isCollinearTriangle(vertices, tri2)

private fun isCollinearTriangle(vertices: PointSet2F, triangle: TriNode) = (vertices[triangle.p2]!! - vertices[triangle.p1]!!).cross(vertices[triangle.p3]!! - vertices[triangle.p1]!!) == 0.0f

private fun containsCollinearPoints(points: ArrayList<Point2F>): Boolean {
    for (i in 1..points.size) {
        val ai = i % points.size
        val bi = i - 1
        val ci = (i + 1) % points.size
        val a = points[ai]
        val b = points[bi]
        val c = points[ci]
        val area = 0.5f * (-b.y * c.x + a.y * (-b.x + c.x) + a.x * (b.y - c.y) + b.x * c.y)
        if (area == 0.0f) {
            return true
        }
        val normal = (a - b).cross(c - b) / LineSegment2F(b, c).length
        if (Math.abs(normal) < 0.00001f) {
            return true
        }
    }
    return false
}

fun buildMesh(edgeSkeletonIn: ArrayList<LineSegment3F>, riverSkeletonIn: ArrayList<LineSegment3F>, globalVertices: PointSet2F): Pair<ArrayList<Point3F>, LinkedHashSet<Set<Int>>> {
    val edgeSkeleton = ArrayList(edgeSkeletonIn)
    val riverSkeleton = ArrayList(riverSkeletonIn)
    if (debug) {
        draw(debugResolution, "debug-buildMesh1-${debugIteration.get()}-${debugCount.andIncrement}", "output", Color.WHITE, debugZoom, Vector2F(-(edgeSkeleton.flatMap { listOf(it.a.x, it.b.x) }.min()!!) + 0.0005f, -(edgeSkeleton.flatMap { listOf(it.a.y, it.b.y) }.min()!!) + 0.0005f)) {
            graphics.color = Color.BLACK
            graphics.stroke = BasicStroke(3.0f)
            edgeSkeleton.forEach {
                drawEdge(it.a, it.b)
                drawPoint(it.a, 5)
                drawPoint(it.b, 5)
            }
            graphics.stroke = BasicStroke(1.0f)
            graphics.color = Color.RED
            riverSkeleton.forEach {
                drawEdge(it.a, it.b)
                drawPoint(it.a, 3)
                drawPoint(it.b, 3)
            }
        }
        breakPoint()
    }
    globalMapEdges(globalVertices, edgeSkeleton)
    globalMapEdges(globalVertices, riverSkeleton)
    closeEdge(edgeSkeleton)
    unTwistEdges(edgeSkeleton)
    moveRiverInsideBorder(globalVertices, edgeSkeleton, riverSkeleton)
    unTwistEdges(riverSkeleton)
    if (debug) {
        draw(debugResolution, "debug-buildMesh2-${debugIteration.get()}-${debugCount.andIncrement}", "output", Color.WHITE, debugZoom, Vector2F(-(edgeSkeleton.flatMap { listOf(it.a.x, it.b.x) }.min()!!) + 0.0005f, -(edgeSkeleton.flatMap { listOf(it.a.y, it.b.y) }.min()!!) + 0.0005f)) {
            graphics.color = Color.BLACK
            graphics.stroke = BasicStroke(3.0f)
            edgeSkeleton.forEach {
                drawEdge(it.a, it.b)
                drawPoint(it.a, 5)
                drawPoint(it.b, 5)
            }
            graphics.stroke = BasicStroke(1.0f)
            graphics.color = Color.RED
            riverSkeleton.forEach {
                drawEdge(it.a, it.b)
                drawPoint(it.a, 3)
                drawPoint(it.b, 3)
            }
        }
        breakPoint()
    }
    moveRiverInsideBorder(globalVertices, edgeSkeleton, riverSkeleton)
    if (debug) {
        draw(debugResolution, "debug-buildMesh3-${debugIteration.get()}-${debugCount.andIncrement}", "output", Color.WHITE, debugZoom, Vector2F(-(edgeSkeleton.flatMap { listOf(it.a.x, it.b.x) }.min()!!) + 0.0005f, -(edgeSkeleton.flatMap { listOf(it.a.y, it.b.y) }.min()!!) + 0.0005f)) {
            graphics.color = Color.BLACK
            graphics.stroke = BasicStroke(3.0f)
            edgeSkeleton.forEach {
                drawEdge(it.a, it.b)
                drawPoint(it.a, 5)
                drawPoint(it.b, 5)
            }
            graphics.stroke = BasicStroke(1.0f)
            graphics.color = Color.RED
            riverSkeleton.forEach {
                drawEdge(it.a, it.b)
                drawPoint(it.a, 3)
                drawPoint(it.b, 3)
            }
        }
        breakPoint()
    }
    val edgePoints = PointSet2F()
    edgePoints.addAll(edgeSkeleton.flatMap { listOf(it.a, it.b) })
    val edgeEdges = LinkedHashSet<Pair<Int, Int>>()
    fun edgeEdge(a: Int, b: Int) = edgeEdges.add(Pair(min(a, b), max(a, b)))
    edgeSkeleton.forEach {
        edgeEdge(edgePoints[it.a], edgePoints[it.b])
    }
    val edgePolygons = getPolygonEdgeSets(edgePoints, edgeEdges, 0, false, false)
    val riverPoints = PointSet2F()
    riverPoints.addAll(riverSkeleton.flatMap { listOf(it.a, it.b) })
    val riverEdges = LinkedHashSet<Pair<Int, Int>>()
    fun riverEdge(a: Int, b: Int) = riverEdges.add(Pair(min(a, b), max(a, b)))
    riverSkeleton.forEach {
        riverEdge(riverPoints[it.a], riverPoints[it.b])
    }
    val riverPolygons = getPolygonEdgeSets(riverPoints, riverEdges, 0, false, true)
    removeCycles(riverPolygons)
    val meshPoints = PointSet2F()
    val edges = LinkedHashSet<Pair<Int, Int>>()
    fun edge(a: Int, b: Int) = edges.add(Pair(min(a, b), max(a, b)))
    meshPoints.addAll(edgePolygons.flatMap { it.flatMap { listOf(edgePoints[it.first]!!, edgePoints[it.second]!!) } })
    edgePolygons.flatMap { it }.forEach {
        edge(meshPoints[edgePoints[it.first]!!], meshPoints[edgePoints[it.second]!!])
    }
    meshPoints.addAll(riverPolygons.flatMap { it.flatMap { listOf(riverPoints[it.first]!!, riverPoints[it.second]!!) } })
    riverPolygons.flatMap { it }.forEach {
        edge(meshPoints[riverPoints[it.first]!!], meshPoints[riverPoints[it.second]!!])
    }
    val polygons = getPolygonEdgeSets(meshPoints, edges, 0)
    if (debug) {
        draw(debugResolution, "debug-buildMesh4-${debugIteration.get()}-${debugCount.andIncrement}", "output", Color.WHITE, debugZoom, Vector2F(-(edgeSkeleton.flatMap { listOf(it.a.x, it.b.x) }.min()!!) + 0.0005f, -(edgeSkeleton.flatMap { listOf(it.a.y, it.b.y) }.min()!!) + 0.0005f)) {
            graphics.color = Color.BLACK
            polygons.forEach {
                it.forEach {
                    drawEdge(meshPoints[it.first]!!, meshPoints[it.second]!!)
                }
            }
        }
        breakPoint()
    }
    val vertices = ArrayList(meshPoints.map { it as Point3F })
    if (debug) {
        draw(debugResolution, "debug-buildMesh5-${debugIteration.get()}-${debugCount.andIncrement}", "output", Color.WHITE, debugZoom, Vector2F(-(edgeSkeleton.flatMap { listOf(it.a.x, it.b.x) }.min()!!) + 0.0005f, -(edgeSkeleton.flatMap { listOf(it.a.y, it.b.y) }.min()!!) + 0.0005f)) {
            graphics.color = Color.BLACK
            polygons.forEach {
                it.forEach {
                    drawEdge(vertices[it.first], vertices[it.second])
                }
            }
        }
        breakPoint()
    }
    val triangles = LinkedHashSet<Set<Int>>()
    polygons.forEach {
        triangles.addAll(triangulatePolygon(meshPoints, it))
    }
    if (debug) {
        draw(debugResolution, "debug-buildMesh6-${debugIteration.get()}-${debugCount.andIncrement}", "output", Color.WHITE, debugZoom, Vector2F(-(edgeSkeleton.flatMap { listOf(it.a.x, it.b.x) }.min()!!) + 0.0005f, -(edgeSkeleton.flatMap { listOf(it.a.y, it.b.y) }.min()!!) + 0.0005f)) {
            graphics.color = Color.BLACK
            triangles.forEach {
                val tri = it.toList()
                val a = vertices[tri[0]]
                val b = vertices[tri[1]]
                val c = vertices[tri[2]]
                drawEdge(a, b)
                drawEdge(b, c)
                drawEdge(c, a)
                drawPoint(a, 3)
                drawPoint(b, 3)
                drawPoint(c, 3)
            }
        }
        breakPoint()
    }
    return Pair(vertices, triangles)
}

private fun removeCycles(polygons: ArrayList<ArrayList<Pair<Int, Int>>>) {
    for (polygon in polygons) {
        if (polygon.first().first == polygon.last().second) {
            var drop = false
            val drops = ArrayList<Pair<Int, Int>>()
            for (edge in polygon) {
                if (isJunction(polygons, polygon, edge)) {
                    drop = !drop
                    if (!drop) {
                        break
                    }
                }
                if (drop) {
                    drops.add(edge)
                }
            }
            polygon.removeAll(drops)
        }
    }
}

fun isJunction(polygons: ArrayList<ArrayList<Pair<Int, Int>>>, polygon: ArrayList<Pair<Int, Int>>, edge: Pair<Int, Int>): Boolean {
    for (each in polygons) {
        if (each == polygon) {
            continue
        }
        for (other in each) {
            if (edge.first == other.first || edge.first == other.second) {
                return true
            }
        }
    }
    return false
}

private fun globalMapEdges(globalVertexSet: PointSet2F, edgeSkeleton: ArrayList<LineSegment3F>) {
    edgeSkeleton.forEach {
        globalVertexSet.add(it.a)
        globalVertexSet.add(it.b)
    }
    val globalMappedEdgeSkeleton = edgeSkeleton.map { LineSegment3F(globalVertexSet[globalVertexSet[it.a]] as Point3F, globalVertexSet[globalVertexSet[it.b]] as Point3F) }.filter { it.a != it.b }
    edgeSkeleton.clear()
    edgeSkeleton.addAll(globalMappedEdgeSkeleton)
}

private fun unTwistEdges(skeleton: ArrayList<LineSegment3F>) {
    var hasFix = true
    while (hasFix) {
        hasFix = false
        var fixUp: Pair<LineSegment3F, LineSegment3F>? = null
        for (first in skeleton) {
            for (second in skeleton) {
                if (first != second && LineSegment2F(first.a, first.b).intersects(LineSegment2F(second.a, second.b))) {
                    fixUp = Pair(first, second)
                    break
                }
            }
            if (fixUp != null) {
                break
            }
        }
        if (fixUp != null) {
            skeleton.remove(fixUp.first)
            skeleton.remove(fixUp.second)
            val skeletonCopy = LinkedHashSet(skeleton)
            val fix1 = LineSegment3F(fixUp.first.a, fixUp.second.a)
            val fix2 = LineSegment3F(fixUp.first.b, fixUp.second.b)
            skeletonCopy.add(fix1)
            skeletonCopy.add(fix2)
            if (LineSegment2F.getConnectedEdgeSegments(skeletonCopy.map { LineSegment2F(it.a, it.b) }).size == 1) {
                skeleton.add(fix1)
                skeleton.add(fix2)
            } else {
                skeleton.add(LineSegment3F(fixUp.first.a, fixUp.second.b))
                skeleton.add(LineSegment3F(fixUp.first.b, fixUp.second.a))
            }
            hasFix = true
            if (debug) {
                draw(debugResolution, "debug-unTwistEdges-${debugIteration.get()}-${debugCount.andIncrement}", "output", Color.WHITE, debugZoom, Vector2F(-(skeleton.flatMap { listOf(it.a.x, it.b.x) }.min()!!) + 0.0005f, -(skeleton.flatMap { listOf(it.a.y, it.b.y) }.min()!!) + 0.0005f)) {
                    graphics.color = Color.BLACK
                    skeleton.forEach {
                        drawEdge(it.a, it.b)
                        drawPoint(it.a, 2)
                        drawPoint(it.b, 2)
                    }
                    graphics.color = Color.RED
                    drawEdge(fixUp!!.first.a, fixUp!!.first.b)
                    drawPoint(fixUp!!.first.a, 2)
                    drawPoint(fixUp!!.first.b, 2)
                    drawEdge(fixUp!!.second.a, fixUp!!.second.b)
                    drawPoint(fixUp!!.second.a, 2)
                    drawPoint(fixUp!!.second.b, 2)
                }
                breakPoint()
            }
        }
    }
}

private fun closeEdge(edges: ArrayList<LineSegment3F>) {
    if (edges.first().a.epsilonEquals(edges.last().b)) {
        return
    }
    val unmodified = Polygon2F.fromUnsortedEdges(edges.map { LineSegment2F(it.a, it.b) })
    if (unmodified.isClosed) {
        return
    }
    val newEdges = Polygon2F(unmodified.points, true).edges.map { LineSegment3F(it.a as Point3F, it.b as Point3F) }
    if (debug) {
        draw(debugResolution, "debug-closeEdge-${debugIteration.get()}-${debugCount.andIncrement}", "output", Color.WHITE, debugZoom, Vector2F(-(edges.flatMap { listOf(it.a.x, it.b.x) }.min()!!) + 0.0005f, -(edges.flatMap { listOf(it.a.y, it.b.y) }.min()!!) + 0.0005f)) {
            graphics.color = Color.RED
            newEdges.forEach {
                drawEdge(it.a, it.b)
                drawPoint(it.a, 2)
                drawPoint(it.b, 2)
            }
            graphics.color = Color.BLACK
            edges.forEach {
                drawEdge(it.a, it.b)
                drawPoint(it.a, 2)
                drawPoint(it.b, 2)
            }
        }
        breakPoint()
    }
    edges.clear()
    edges.addAll(newEdges)
}

private fun moveRiverInsideBorder(globalVertices: PointSet2F, edgeSkeleton: ArrayList<LineSegment3F>, riverSkeleton: ArrayList<LineSegment3F>) {
    if (riverSkeleton.isEmpty()) {
        return
    }
    val polygon = ArrayList(edgeSkeleton.map { Pair(globalVertices[it.a], globalVertices[it.b]) })
    val borderPoints = LinkedHashSet(polygon.map { it.first })
    val segments = LineSegment2F.getConnectedEdgeSegments(riverSkeleton.map { LineSegment2F(it.a, it.b) })
    val newRiverSkeleton = ArrayList<LineSegment3F>()
    var unmodified = true
    for (segment in segments) {
        val dropVertices = LinkedHashSet<Int>()
        segment.forEach {
            val a = globalVertices[it.a]
            val b = globalVertices[it.b]
            if (!borderPoints.contains(a) && !containsPoint(globalVertices, polygon, a)) {
                dropVertices.add(a)
            }
            if (!borderPoints.contains(b) && !containsPoint(globalVertices, polygon, b)) {
                dropVertices.add(b)
            }
//            if (!containsPoint(globalVertices, polygon, it.interpolate(0.5f))) {
//                val borderContainsA = borderPoints.contains(a)
//                val borderContainsB = borderPoints.contains(b)
//                if (!borderContainsA) {
//                    dropVertices.add(a)
//                }
//                if (!borderContainsB) {
//                    dropVertices.add(b)
//                }
//                if (borderContainsA && borderContainsB) {
//                    dropVertices.add(b)
//                }
//            }
//            var intersects = false
//            for (edge in polygon) {
//                if (LineSegment2F(globalVertices[edge.first]!!, globalVertices[edge.second]!!).intersects(it)) {
//                    intersects = true
//                    break
//                }
//            }
//            if (intersects) {
//                val borderContainsA = borderPoints.contains(a)
//                val borderContainsB = borderPoints.contains(b)
//                if (!borderContainsA) {
//                    dropVertices.add(a)
//                }
//                if (!borderContainsB) {
//                    dropVertices.add(b)
//                }
//                if (borderContainsA && borderContainsB) {
//                    dropVertices.add(b)
//                }
//            }
        }
        if (dropVertices.isEmpty()) {
            if (segments.size == 1) {
                return
            }
            newRiverSkeleton.addAll(segment.map { LineSegment3F(it.a as Point3F, it.b as Point3F) })
        } else {
            unmodified = false
            val segmentIndex = segment.map { Pair(globalVertices[it.a], globalVertices[it.b]) }
            val dropMap = HashMap<Int, ArrayList<Int>>()
            dropVertices.forEach { dropVertex ->
                segmentIndex.forEach {
                    if (it.first == dropVertex) {
                        dropMap.getOrPut(dropVertex, { ArrayList() }).add(it.second)
                    } else if (it.second == dropVertex) {
                        dropMap.getOrPut(dropVertex, { ArrayList() }).add(it.first)
                    }
                }
            }
            for (line in segmentIndex) {
                if (dropVertices.contains(line.first) && dropVertices.contains(line.second)) {
                    continue
                }
                if (!dropVertices.contains(line.first) && !dropVertices.contains(line.second)) {
                    newRiverSkeleton.add(LineSegment3F(globalVertices[line.first] as Point3F, globalVertices[line.second] as Point3F))
                } else if (dropVertices.contains(line.first)) {
                    newRiverSkeleton.add(LineSegment3F(globalVertices[findSuitableReplacement(dropMap, line.first, line.second)] as Point3F, globalVertices[line.second] as Point3F))
                } else {
                    newRiverSkeleton.add(LineSegment3F(globalVertices[line.first] as Point3F, globalVertices[findSuitableReplacement(dropMap, line.second, line.first)] as Point3F))
                }
            }
        }
    }
    if (unmodified) {
        return
    }
    riverSkeleton.clear()
    riverSkeleton.addAll(newRiverSkeleton)
}

private fun findSuitableReplacement(dropMap: HashMap<Int, ArrayList<Int>>, toReplace: Int, cantUse: Int): Int {
    val cantUseSet = LinkedHashSet<Int>()
    cantUseSet.add(cantUse)
    val options = LinkedHashSet<Int>()
    options.add(toReplace)
    while (options.isNotEmpty()) {
        val option = options.first()
        options.remove(option)
        if (!dropMap.containsKey(option) && !cantUseSet.contains(option)) {
            return option
        } else {
            cantUseSet.add(option)
            val newOptions = LinkedHashSet(dropMap[option]!!)
            newOptions.removeAll(cantUseSet)
            options.addAll(newOptions)
        }
    }
    throw GeometryException("unable to reconnect segment")
}

private fun getPolygonEdgeSets(meshPoints: PointSet2F, edges: Collection<Pair<Int, Int>>, count: Int, putNonCyclesInCycles: Boolean = true, keepNonCycles: Boolean = true): ArrayList<ArrayList<Pair<Int, Int>>> {
    if (count > 100) {
        throw GeometryException("infinite recursion trying to get polygon edge sets")
    }
    val allPaths = ArrayList<ArrayList<Pair<Int, Int>>>()
    val segmentCycles = LinkedHashSet<LinkedHashSet<Int>>()
    val nodesInCycles = LinkedHashSet<Int>()
    val segments = getConnectedSegments(edges)
    val newEdges = LinkedHashSet<Pair<Int, Int>>()
    segments.forEach { segment ->
        val connections = HashMap<Int, LinkedHashSet<Int>>()
        val nodes = LinkedHashSet<Int>()
        segment.forEach {
            val edge = it.toList()
            val a = edge[0]
            val b = edge[1]
            nodes.add(a)
            nodes.add(b)
            connections.getOrPut(a, { LinkedHashSet() }).add(b)
            connections.getOrPut(b, { LinkedHashSet() }).add(a)
        }
        val segmentPaths = ArrayList<ArrayList<Pair<Int, Int>>>()
        val nonCycleNodes = LinkedHashSet<Int>()
        nodes.forEach { node ->
            if (!nodesInCycles.contains(node)) {
                val paths = findPaths(connections, LinkedHashSet<Set<Int>>(), node, node)
                if (paths != null) {
                    removeDuplicates(paths)
                    for (path in paths) {
                        if (isInnerPath(meshPoints, paths, path)) {
                            val segmentCycle = LinkedHashSet(path.flatMap { listOf(it.first, it.second) })
                            if (segmentCycles.add(segmentCycle)) {
                                nodesInCycles.addAll(segmentCycle)
                                segmentPaths.add(path)
                            }
                            break
                        }
                    }
                } else {
                    nonCycleNodes.add(node)
                }
            }
        }
        if (nonCycleNodes.isNotEmpty()) {
            val nonCycleSegments = getConnectedSegments(edges.filter { nonCycleNodes.contains(it.first) || nonCycleNodes.contains(it.second) })
            val orderedNonCycleSegments = ArrayList<ArrayList<Pair<Int, Int>>>()
            nonCycleSegments.forEach {
                orderedNonCycleSegments.add(orderSegment(it))
            }
            if (putNonCyclesInCycles) {
                orderedNonCycleSegments.forEach {
                    val (splicePoint, containingCycle) = findContainingCycle(meshPoints, segmentPaths, it)
                    if (splicePoint != null && containingCycle != null) {
                        newEdges.add(findSuitableSpliceEdge(meshPoints, orderedNonCycleSegments, containingCycle, it, splicePoint))
                    }
                }
            } else if (keepNonCycles) {
                segmentPaths.addAll(orderedNonCycleSegments)
            }
        }
        allPaths.addAll(segmentPaths)
    }
    if (newEdges.isEmpty()) {
        return allPaths
    } else {
        return getPolygonEdgeSets(meshPoints, edges + newEdges, count + 1, putNonCyclesInCycles)
    }
}

private fun containsPoint(meshPoints: PointSet2F, polygon: ArrayList<Pair<Int, Int>>, id: Int): Boolean {
    return containsPoint(meshPoints, polygon, meshPoints[id]!!)
}

private fun containsPoint(meshPoints: PointSet2F, polygon: ArrayList<Pair<Int, Int>>, point: Point2F): Boolean {
    val points = polygon.map { meshPoints[it.first]!! }
    var i: Int = 0
    var j: Int = points.size - 1
    var c = false
    while (i < points.size) {
        val pi = points[i]
        val pj = points[j]
        if (((pi.y > point.y) != (pj.y > point.y)) && (point.x < (pj.x - pi.x) * (point.y - pi.y) / (pj.y - pi.y) + pi.x)) {
            c = !c
        }
        j = i
        i++
    }
    return c
}

private fun  getConnectedSegments(edges: Collection<Pair<Int, Int>>): List<Set<Set<Int>>> {
    val fullSet = LinkedHashSet(edges.map { setOf(it.first, it.second) }.filter { it.size == 2 })
    val unconnected = LinkedHashSet(fullSet)
    val segments = ArrayList<Set<Set<Int>>>()
    while (unconnected.isNotEmpty()) {
        val seed = unconnected.first()
        unconnected.remove(seed)
        val segment = getConnectedEdges(seed, fullSet)
        unconnected.removeAll(segment)
        segments.add(segment)
    }
    return segments
}

private fun getConnectedEdges(seed: Set<Int>, edgeSet: Collection<Set<Int>>): Set<Set<Int>> {
    val connectedEdges = LinkedHashSet<Set<Int>>()
    connectedEdges.add(seed)
    var nextEdges = LinkedHashSet<Set<Int>>(connectedEdges)
    while (nextEdges.isNotEmpty()) {
        val newEdges = LinkedHashSet<Set<Int>>()
        nextEdges.forEach { edge ->
            edgeSet.forEach {
                val intersection = HashSet(edge)
                intersection.retainAll(it)
                if (intersection.isNotEmpty()) {
                    newEdges.add(it)
                }
            }
        }
        newEdges.removeAll(connectedEdges)
        connectedEdges.addAll(newEdges)
        nextEdges = newEdges
    }
    return connectedEdges
}

private fun findPaths(connections: HashMap<Int, LinkedHashSet<Int>>, usedEdges: LinkedHashSet<Set<Int>>, start: Int, end: Int): ArrayList<ArrayList<Pair<Int, Int>>>? {
    val options = connections[start] ?: return null
    if (options.isEmpty()) {
        return null
    }
    val pathsFromHere = ArrayList<ArrayList<Pair<Int, Int>>>()
    if (options.contains(end) && !usedEdges.contains(setOf(start, end))) {
        pathsFromHere.add(arrayListOf(Pair(start, end)))
        return pathsFromHere
    }
    val paths = ArrayList<Pair<Pair<Int, Int>, ArrayList<Pair<Int, Int>>>>()
    options.forEach { option ->
        val theEdge = setOf(start, option)
        if (!usedEdges.contains(theEdge)) {
            val newUsedEdges = LinkedHashSet(usedEdges)
            newUsedEdges.add(theEdge)
            val nextLegs = findPaths(connections, newUsedEdges, option, end)
            nextLegs?.forEach { nextLeg ->
                paths.add(Pair(Pair(start, option), nextLeg))
            }
        }
    }
    if (paths.isEmpty()) {
        return null
    } else {
        paths.sortBy { it.second.size }
        paths.forEach {
            val thePath = ArrayList<Pair<Int, Int>>()
            thePath.add(it.first)
            thePath.addAll(it.second)
            pathsFromHere.add(thePath)
        }
        return pathsFromHere
    }
}

private fun removeDuplicates(paths: ArrayList<ArrayList<Pair<Int, Int>>>) {
    if (paths.size < 2) {
        return
    }
    val unorderedPaths = ArrayList<LinkedHashSet<Set<Int>>>()
    paths.forEach {
        unorderedPaths.add(LinkedHashSet(it.map { setOf(it.first, it.second) }))
    }
    for (i in paths.size - 1 downTo 0) {
        val oneUnorderedPath = unorderedPaths[i]
        for (j in 0..i - 1) {
            if (oneUnorderedPath == unorderedPaths[j]) {
                paths.removeAt(i)
                break
            }
        }
    }
}

private fun isInnerPath(meshPoints: PointSet2F, paths: ArrayList<ArrayList<Pair<Int, Int>>>, path: ArrayList<Pair<Int, Int>>): Boolean {
    val otherPaths = ArrayList(paths)
    otherPaths.remove(path)
    otherPaths.forEach {
        if (pathAContainsB(meshPoints, path, it)) {
            return false
        }
    }
    return true
}

private fun pathAContainsB(meshPoints: PointSet2F, a: ArrayList<Pair<Int, Int>>, b: ArrayList<Pair<Int, Int>>): Boolean {
    val aIds = a.map { it.first }
    val bIds = LinkedHashSet(b.map { it.first })
    bIds.removeAll(aIds)
    if (bIds.isEmpty()) {
        return true
    }
    return containsPoint(meshPoints, a, bIds.first())
}

private fun orderSegment(segment: Collection<Set<Int>>): ArrayList<Pair<Int, Int>> {
    val path = ArrayList<Pair<Int, Int>>()
    val mutable = ArrayList(segment.filter { it.size == 2 })
    val seed = mutable.removeAt(mutable.size - 1).toList()
    val seedPair = Pair(seed[0], seed[1])
    path.add(seedPair)
    var pair = seedPair
    var hasNext = true
    while (hasNext) {
        hasNext = false
        for (i in 0..mutable.size - 1) {
            if (mutable[i].contains(pair.first)) {
                val next = LinkedHashSet(mutable.removeAt(i))
                next.remove(pair.first)
                pair = Pair(next.first(), pair.first)
                path.add(0, pair)
                hasNext = true
                break
            }
        }
    }
    pair = seedPair
    hasNext = true
    while (hasNext) {
        hasNext = false
        for (i in 0..mutable.size - 1) {
            if (mutable[i].contains(pair.second)) {
                val next = LinkedHashSet(mutable.removeAt(i))
                next.remove(pair.second)
                pair = Pair(pair.second, next.first())
                path.add(pair)
                hasNext = true
                break
            }
        }
    }
    return path
}

private fun  findContainingCycle(meshPoints: PointSet2F, cycles: ArrayList<ArrayList<Pair<Int, Int>>>, segment: ArrayList<Pair<Int, Int>>): Pair<Int?, ArrayList<Pair<Int, Int>>?> {
    val end1 = segment.first().first
    val end2 = segment.last().second
    val cyclesToTest = ArrayList<Pair<Int, ArrayList<Pair<Int, Int>>>>()
    for (cycle in cycles) {
        for (edge in cycle) {
            if (edge.first == end1 || edge.second == end1) {
                cyclesToTest.add(Pair(end2, cycle))
                break
            }
            if (edge.first == end2 || edge.second == end2) {
                cyclesToTest.add(Pair(end1, cycle))
                break
            }
        }
    }
    cyclesToTest.forEach {
        if (containsPoint(meshPoints, it.second, it.first)) {
            return it
        }
    }
    return Pair(null, null)
}

private fun findSuitableSpliceEdge(meshPoints: PointSet2F, segments: ArrayList<ArrayList<Pair<Int, Int>>>, containingCycle: ArrayList<Pair<Int, Int>>, segment: ArrayList<Pair<Int, Int>>, splicePoint: Int): Pair<Int, Int> {
    val b = meshPoints[splicePoint]!!
    val a = if (segment.first().first == splicePoint) {
        meshPoints[segment.last().second]!!
    } else {
        meshPoints[segment.first().first]!!
    }
    val vector = LineSegment2F(a, b).toVector().getUnit()
    val c = b + vector
    val testLine = LineSegment2F(b, c)
    var intersection = c
    var minDist2 = Float.MAX_VALUE
    containingCycle.forEach {
        val line = LineSegment2F(meshPoints[it.first]!!, meshPoints[it.second]!!)
        val currentIntersect = line.intersection(testLine)
        if (currentIntersect != null) {
            val distance2 = currentIntersect.distance2(b)
            if (distance2 < minDist2) {
                intersection = currentIntersect
                minDist2 = distance2
            }
        }
    }
    val constrainedEdges = (segments.flatMap { it } + containingCycle).map { LineSegment2F(meshPoints[it.first]!!, meshPoints[it.second]!!) }
    for ((id, point) in containingCycle.map { Pair(it.first, meshPoints[it.first]!!) }.sortedBy { it.second.distance2(intersection) }) {
        val line = LineSegment2F(b, point)
        var intersects = false
        for (constrainedEdge in constrainedEdges) {
            if (line.intersects(constrainedEdge)) {
                intersects = true
                break
            }
        }
        if (!intersects) {
            return Pair(splicePoint, id)
        }
    }
    throw GeometryException("there are no non-intersecting connections between a line segment contained within an edge cycle").with {
        data.add("val meshPoints = $meshPoints")
        data.add("val segments = ${printList(segments) { printList(it) { "Pair$it" }}}")
        data.add("val containingCycle = ${printList(containingCycle) { "Pair$it" }}")
        data.add("val segment = ${printList(segment) { "Pair$it" }}")
        data.add("val splicePoint = $splicePoint")
    }
}

fun spliceZeroHeightTriangles(vertices: ArrayList<Point3F>, triangles: LinkedHashSet<Set<Int>>, maxTerrainSlope: Float) {
    val splices = LinkedHashMap<Pair<Int, Int>, Point3F>()
    triangles.forEach { triangle ->
        val tri = triangle.toList()
        val aId = tri[0]
        val bId = tri[1]
        val cId = tri[2]
        val a = vertices[aId]
        val b = vertices[bId]
        val c = vertices[cId]
        if (a.z == 0.0f && b.z == 0.0f) {
            triangles.forEach { other ->
                addHeightPointIfNeeded(splices, triangle, other, a, b, c, aId, bId, maxTerrainSlope)
            }
        }
        if (b.z == 0.0f && c.z == 0.0f) {
            triangles.forEach { other ->
                addHeightPointIfNeeded(splices, triangle, other, b, c, a, bId, cId, maxTerrainSlope)
            }

        }
        if (c.z == 0.0f && a.z == 0.0f) {
            triangles.forEach { other ->
                addHeightPointIfNeeded(splices, triangle, other, c, a, b, cId, aId, maxTerrainSlope)
            }
        }
    }
    splices.forEach { edge, point ->
        val trianglesToModify = ArrayList<Set<Int>>(2)
        val pointIndex = vertices.size
        vertices.add(point)
        triangles.forEach {
            if (it.containsAll(edge.toList())) {
                trianglesToModify.add(it)
            }
        }
        triangles.removeAll(trianglesToModify)
        trianglesToModify.forEach {
            val first = HashSet(it)
            val second = HashSet(it)
            first.remove(edge.first)
            second.remove(edge.second)
            first.add(pointIndex)
            second.add(pointIndex)
            triangles.add(first)
            triangles.add(second)
            if (debug) {
                draw(debugResolution * 4, "debug-triangulatePolygon1-${debugIteration.get()}-${debugCount.andIncrement}", "output", Color.WHITE, debugZoom, Vector2F(-(vertices.map { it.x }.min()!!) + 0.0005f, -(vertices.map { it.y }.min()!!) + 0.0005f)) {
                    graphics.color = Color.BLACK
                    triangles.forEach {
                        val tri = it.toList()
                        val p1 = vertices[tri[0]]
                        val p2 = vertices[tri[1]]
                        val p3 = vertices[tri[2]]
                        drawEdge(p1, p2)
                        drawEdge(p2, p3)
                        drawEdge(p3, p1)
                        drawPoint(p1, 8)
                        drawPoint(p2, 8)
                        drawPoint(p3, 8)
                    }
                    graphics.color = Color.RED
                    trianglesToModify.forEach {
                        val tri = it.toList()
                        val p1 = vertices[tri[0]]
                        val p2 = vertices[tri[1]]
                        val p3 = vertices[tri[2]]
                        drawEdge(p1, p2)
                        drawEdge(p2, p3)
                        drawEdge(p3, p1)
                        drawPoint(p1, 5)
                        drawPoint(p2, 5)
                        drawPoint(p3, 5)
                    }
                    graphics.color = Color.GREEN
                    drawEdge(vertices[edge.first], vertices[edge.second])
                    drawPoint(vertices[edge.first],3)
                    drawPoint(vertices[edge.second], 3)
                    graphics.color = Color.MAGENTA
                    drawPoint(point, 2)
                    graphics.color = Color.BLUE
                    val tri1 = first.toList()
                    val p11 = vertices[tri1[0]]
                    val p12 = vertices[tri1[1]]
                    val p13 = vertices[tri1[2]]
                    drawEdge(p11, p12)
                    drawEdge(p12, p13)
                    drawEdge(p13, p11)
                    drawPoint(p11, 2)
                    drawPoint(p12, 2)
                    drawPoint(p13, 2)
                    graphics.color = Color.CYAN
                    val tri2 = second.toList()
                    val p21 = vertices[tri2[0]]
                    val p22 = vertices[tri2[1]]
                    val p23 = vertices[tri2[2]]
                    drawEdge(p21, p22)
                    drawEdge(p22, p23)
                    drawEdge(p23, p21)
                    drawPoint(p21, 2)
                    drawPoint(p22, 2)
                    drawPoint(p23, 2)
                }
                breakPoint()
            }
        }
    }
}

private fun addHeightPointIfNeeded(splices: LinkedHashMap<Pair<Int, Int>, Point3F>, triangle: Set<Int>, other: Set<Int>, p1: Point3F, p2: Point3F, p3: Point3F, id1: Int, id2: Int, maxTerrainSlope: Float) {
    if (triangle != other && other.contains(id1) && other.contains(id2)) {
        val splicePoint = LineSegment2F(p1, p2).interpolate(0.5f)
        val height = Math.sqrt(min(splicePoint.distance2(p1), splicePoint.distance2(p2), splicePoint.distance2(p3)).toDouble()) * maxTerrainSlope
        val key = Pair(id1, id2)
        if (height < splices[key]?.z ?: Float.MAX_VALUE) {
            splices[key] = Point3F(splicePoint.x, splicePoint.y, height.toFloat())
        }
    }
}

fun max(a: Int, b: Int, c: Int) = max(max(a, b), c)

fun min(a: Int, b: Int, c: Int) = min(min(a, b), c)

fun min(a: Float, b: Float, c: Float) = min(min(a, b), c)

fun max(a: Float, b: Float, c: Float) = max(max(a, b), c)

fun clamp(min: Float, max: Float, f: Float) = min(max(min, f), max)

