package com.grimfox.gec.util.geometry

import com.grimfox.gec.model.ArrayListMatrix
import com.grimfox.gec.model.geometry.*
import com.grimfox.gec.util.drawing.*
import com.grimfox.gec.util.geometry.Geometry.debug
import com.grimfox.gec.util.geometry.Geometry.debugCount
import com.grimfox.gec.util.geometry.Geometry.debugIteration
import com.grimfox.gec.util.geometry.Geometry.debugResolution
import com.grimfox.gec.util.geometry.Geometry.trace
import com.grimfox.gec.util.printList
import java.awt.BasicStroke
import java.awt.Color
import java.awt.image.BufferedImage
import java.io.File
import java.lang.Math.*
import java.util.*
import java.util.concurrent.atomic.AtomicInteger
import javax.imageio.ImageIO

class GeometryException(message: String? = null, cause: Throwable? = null, var test: Int? = null, var id: Int? = null, val data: ArrayList<String> = ArrayList<String>()): Exception(message, cause) {

    fun with(adjustment: GeometryException.() -> Unit): GeometryException {
        this.adjustment()
        return this
    }
}

fun breakPoint() {
    doNothing()
}

private fun doNothing() {}

object Geometry {

    var debug = false
    var trace = false
    var debugCount = AtomicInteger(1)
    var debugIteration = AtomicInteger(1)
    var debugResolution = 4096

    @JvmStatic fun main(vararg args: String) {

        val keeper1 = {
            val vertices = arrayListOf(Point3F(x=0.657852f, y=0.52122694f, z=0.0f), Point3F(x=0.6578838f, y=0.5195371f, z=0.0f), Point3F(x=0.6580053f, y=0.5194083f, z=0.0f), Point3F(x=0.6593489f, y=0.5195656f, z=0.0f), Point3F(x=0.66068435f, y=0.51934993f, z=0.0f), Point3F(x=0.6616354f, y=0.51807094f, z=0.0f), Point3F(x=0.6618671f, y=0.5164941f, z=0.0f), Point3F(x=0.6611924f, y=0.5149553f, z=0.0f), Point3F(x=0.6605042f, y=0.51342237f, z=0.0f), Point3F(x=0.66095287f, y=0.5125212f, z=0.0f), Point3F(x=0.6616357f, y=0.5117815f, z=0.0f), Point3F(x=0.66165805f, y=0.5115545f, z=9.758234E-4f), Point3F(x=0.6616804f, y=0.5113275f, z=0.0f), Point3F(x=0.6607708f, y=0.5100297f, z=0.0f), Point3F(x=0.6597271f, y=0.5088368f, z=0.0f), Point3F(x=0.65864193f, y=0.5084596f, z=0.0f), Point3F(x=0.6579075f, y=0.5067031f, z=0.0f), Point3F(x=0.6575375f, y=0.5062737f, z=0.0f), Point3F(x=0.6581144f, y=0.5050086f, z=0.004732944f), Point3F(x=0.6599802f, y=0.50457114f, z=0.005903714f), Point3F(x=0.661846f, y=0.5041337f, z=0.0070744837f), Point3F(x=0.6637118f, y=0.50369626f, z=0.008245254f), Point3F(x=0.66557753f, y=0.5032588f, z=0.009416023f), Point3F(x=0.66709393f, y=0.50457025f, z=0.009447934f), Point3F(x=0.66861033f, y=0.50588167f, z=0.009479845f), Point3F(x=0.6701267f, y=0.50719315f, z=0.009511756f), Point3F(x=0.6702467f, y=0.50898916f, z=0.0073777726f), Point3F(x=0.67036676f, y=0.51078516f, z=0.0052437894f), Point3F(x=0.6704868f, y=0.51258117f, z=0.0031098062f), Point3F(x=0.67060685f, y=0.5143771f, z=9.758234E-4f), Point3F(x=0.6707269f, y=0.5161731f, z=0.0033218227f), Point3F(x=0.67084694f, y=0.51796913f, z=0.005667822f), Point3F(x=0.670967f, y=0.51976514f, z=0.008013821f), Point3F(x=0.6710871f, y=0.52156115f, z=0.01035982f), Point3F(x=0.6691972f, y=0.5217548f, z=0.008879846f), Point3F(x=0.6673072f, y=0.5219485f, z=0.007399872f), Point3F(x=0.6654172f, y=0.52214223f, z=0.0059198975f), Point3F(x=0.6635272f, y=0.52233595f, z=0.004439923f), Point3F(x=0.6616372f, y=0.52252966f, z=0.0029599487f), Point3F(x=0.6597472f, y=0.5227234f, z=0.0014799744f), Point3F(x=0.6578572f, y=0.5229171f, z=0.0f), Point3F(x=0.6691004f, y=0.51313883f, z=8.1323006E-4f), Point3F(x=0.66772753f, y=0.5117935f, z=6.5296283E-4f), Point3F(x=0.66613245f, y=0.5129658f, z=4.8791163E-4f), Point3F(x=0.6645374f, y=0.5141381f, z=3.2286043E-4f), Point3F(x=0.6631645f, y=0.51279277f, z=1.6259319E-4f))
            val polygon = arrayListOf(Pair(12, 11), Pair(11, 45), Pair(45, 44), Pair(44, 43), Pair(43, 42), Pair(42, 41), Pair(41, 29), Pair(29, 28), Pair(28, 27), Pair(27, 26), Pair(26, 25), Pair(25, 24), Pair(24, 23), Pair(23, 22), Pair(22, 21), Pair(21, 20), Pair(20, 19), Pair(19, 18), Pair(18, 17), Pair(17, 16), Pair(16, 15), Pair(15, 14), Pair(14, 13), Pair(13, 12))
            triangulatePolygon(vertices, polygon)
        }

        val keeper2 = {
            val vertices = arrayListOf(Point3F(x=0.5040726f, y=0.18875736f, z=0.0f), Point3F(x=0.50388956f, y=0.19026884f, z=0.0f), Point3F(x=0.50371873f, y=0.19178124f, z=0.0f), Point3F(x=0.5037761f, y=0.1918694f, z=0.0f), Point3F(x=0.50361437f, y=0.19342753f, z=0.0f), Point3F(x=0.5029236f, y=0.19420259f, z=0.0f), Point3F(x=0.50211763f, y=0.19485693f, z=0.0f), Point3F(x=0.5019059f, y=0.19505912f, z=0.0f), Point3F(x=0.50243187f, y=0.19627202f, z=0.0f), Point3F(x=0.5030295f, y=0.19745126f, z=0.0f), Point3F(x=0.5032157f, y=0.19876012f, z=0.0f), Point3F(x=0.5025165f, y=0.19976038f, z=0.0f), Point3F(x=0.5013204f, y=0.2000029f, z=0.0f), Point3F(x=0.5001255f, y=0.19894356f, z=0.0f), Point3F(x=0.4988648f, y=0.19796355f, z=0.0f), Point3F(x=0.49752885f, y=0.19889478f, z=0.0f), Point3F(x=0.49632633f, y=0.19999295f, z=0.0f), Point3F(x=0.4952897f, y=0.19915582f, z=0.0f), Point3F(x=0.4940482f, y=0.19963315f, z=0.0f), Point3F(x=0.49459764f, y=0.19779412f, z=6.8200426E-4f), Point3F(x=0.495147f, y=0.19595513f, z=0.0013640085f), Point3F(x=0.49569634f, y=0.19411613f, z=0.0020460128f), Point3F(x=0.49624568f, y=0.19227713f, z=0.002728017f), Point3F(x=0.49679503f, y=0.19043814f, z=0.0034100213f), Point3F(x=0.49734437f, y=0.18859914f, z=0.0040920256f), Point3F(x=0.49789372f, y=0.18676014f, z=0.00477403f), Point3F(x=0.49844307f, y=0.18492115f, z=0.005456034f), Point3F(x=0.5003828f, y=0.18569665f, z=0.0036373562f), Point3F(x=0.50232255f, y=0.18647213f, z=0.0018186781f), Point3F(x=0.5042623f, y=0.18724762f, z=0.0f))
            val polygon = arrayListOf(Pair(0, 1), Pair(1, 2), Pair(2, 3), Pair(3, 4), Pair(4, 5), Pair(5, 6), Pair(6, 7), Pair(7, 8), Pair(8, 9), Pair(9, 10), Pair(10, 11), Pair(11, 12), Pair(12, 13), Pair(13, 14), Pair(14, 15), Pair(15, 16), Pair(16, 17), Pair(17, 18), Pair(18, 19), Pair(19, 20), Pair(20, 21), Pair(21, 22), Pair(22, 23), Pair(23, 24), Pair(24, 25), Pair(25, 26), Pair(26, 27), Pair(27, 28), Pair(28, 29), Pair(29, 0))
            triangulatePolygon(vertices, polygon)
        }

        val keeper3 = {
            val vertices = arrayListOf(Point3F(x=0.6364898f, y=0.13193336f, z=0.0f), Point3F(x=0.6378628f, y=0.1320265f, z=0.0f), Point3F(x=0.6391836f, y=0.13241278f, z=0.0f), Point3F(x=0.6396885f, y=0.13422023f, z=0.0f), Point3F(x=0.63971555f, y=0.13609673f, z=0.0f), Point3F(x=0.64053226f, y=0.13778624f, z=0.0f), Point3F(x=0.6418955f, y=0.13788795f, z=0.0f), Point3F(x=0.6429973f, y=0.13707797f, z=0.0f), Point3F(x=0.6435763f, y=0.1358393f, z=0.0f), Point3F(x=0.6432352f, y=0.13411532f, z=0.0f), Point3F(x=0.6422254f, y=0.13267697f, z=0.0f), Point3F(x=0.641098f, y=0.13132879f, z=0.0f), Point3F(x=0.6398202f, y=0.13008195f, z=0.0f), Point3F(x=0.6384131f, y=0.12898315f, z=0.0f), Point3F(x=0.6372974f, y=0.12758936f, z=0.0f), Point3F(x=0.63703984f, y=0.12617707f, z=0.0f), Point3F(x=0.6378424f, y=0.124986924f, z=0.0f), Point3F(x=0.63951576f, y=0.125747f, z=0.0f), Point3F(x=0.6412372f, y=0.12639144f, z=0.0f), Point3F(x=0.6420176f, y=0.12519458f, z=0.0f), Point3F(x=0.6420088f, y=0.123765685f, z=0.0f), Point3F(x=0.6415246f, y=0.12242131f, z=0.0f), Point3F(x=0.64016795f, y=0.121693045f, z=0.0f), Point3F(x=0.6386363f, y=0.121534325f, z=0.0f), Point3F(x=0.6376598f, y=0.12150804f, z=0.0f), Point3F(x=0.639048f, y=0.120437786f, z=0.0012693903f), Point3F(x=0.64043623f, y=0.11936753f, z=0.0025387807f), Point3F(x=0.64176255f, y=0.120578945f, z=0.0017890271f), Point3F(x=0.6430889f, y=0.12179036f, z=0.0010392736f), Point3F(x=0.64441526f, y=0.12300177f, z=2.895201E-4f), Point3F(x=0.64574164f, y=0.12421318f, z=0.0027656096f), Point3F(x=0.647068f, y=0.1254246f, z=0.0052416995f), Point3F(x=0.64839435f, y=0.12663601f, z=0.007717789f), Point3F(x=0.64834803f, y=0.12860851f, z=0.0076731755f), Point3F(x=0.6483017f, y=0.130581f, z=0.007628562f), Point3F(x=0.6482554f, y=0.1325535f, z=0.0075839483f), Point3F(x=0.6482091f, y=0.134526f, z=0.0075393347f), Point3F(x=0.6481628f, y=0.1364985f, z=0.007494721f), Point3F(x=0.64811647f, y=0.13847099f, z=0.0074501075f), Point3F(x=0.6480703f, y=0.14044349f, z=0.0074054943f), Point3F(x=0.6468344f, y=0.14177372f, z=0.0065884227f), Point3F(x=0.6455984f, y=0.14310394f, z=0.005771351f), Point3F(x=0.64285606f, y=0.14333946f, z=0.005578068f), Point3F(x=0.64145994f, y=0.14214703f, z=0.0055309264f), Point3F(x=0.6400638f, y=0.14095461f, z=0.0054837847f), Point3F(x=0.6386677f, y=0.1397622f, z=0.005436643f), Point3F(x=0.6372716f, y=0.13856977f, z=0.0053895013f), Point3F(x=0.63587546f, y=0.13737735f, z=0.0053423597f), Point3F(x=0.63447934f, y=0.13618493f, z=0.0052952175f), Point3F(x=0.63481706f, y=0.13437124f, z=0.003530145f), Point3F(x=0.6351548f, y=0.13255754f, z=0.0017650723f), Point3F(x=0.63549244f, y=0.13074385f, z=0.0f))
            val polygon = arrayListOf(Pair(0, 1), Pair(1, 2), Pair(2, 3), Pair(3, 4), Pair(4, 5), Pair(5, 6), Pair(6, 7), Pair(7, 8), Pair(8, 9), Pair(9, 10), Pair(10, 11), Pair(11, 12), Pair(12, 13), Pair(13, 14), Pair(14, 15), Pair(15, 16), Pair(16, 17), Pair(17, 18), Pair(18, 19), Pair(19, 20), Pair(20, 21), Pair(21, 22), Pair(22, 23), Pair(23, 24), Pair(24, 25), Pair(25, 26), Pair(26, 27), Pair(27, 28), Pair(28, 29), Pair(29, 30), Pair(30, 31), Pair(31, 32), Pair(32, 33), Pair(33, 34), Pair(34, 35), Pair(35, 36), Pair(36, 37), Pair(37, 38), Pair(38, 39), Pair(39, 40), Pair(40, 41), Pair(41, 42), Pair(42, 43), Pair(43, 44), Pair(44, 45), Pair(45, 46), Pair(46, 47), Pair(47, 48), Pair(48, 49), Pair(49, 50), Pair(50, 51), Pair(51, 0))
            triangulatePolygon(vertices, polygon)
        }

        val test = {
            val edgeSkeleton = arrayListOf(LineSegment3F(a=Point3F(x=0.28617966f, y=0.8587587f, z=0.0f), b=Point3F(x=0.28607213f, y=0.8588574f, z=4.378843E-6f)), LineSegment3F(a=Point3F(x=0.28607213f, y=0.8588574f, z=4.378843E-6f), b=Point3F(x=0.28596464f, y=0.85895604f, z=0.0f)), LineSegment3F(a=Point3F(x=0.28596464f, y=0.85895604f, z=0.0f), b=Point3F(x=0.28503317f, y=0.85981095f, z=3.792965E-5f)), LineSegment3F(a=Point3F(x=0.28503317f, y=0.85981095f, z=3.792965E-5f), b=Point3F(x=0.28410172f, y=0.8606658f, z=0.0f)), LineSegment3F(a=Point3F(x=0.28410172f, y=0.8606658f, z=0.0f), b=Point3F(x=0.28263855f, y=0.85880196f, z=7.108651E-5f)), LineSegment3F(a=Point3F(x=0.28263855f, y=0.85880196f, z=7.108651E-5f), b=Point3F(x=0.2811754f, y=0.85693806f, z=0.0f)), LineSegment3F(a=Point3F(x=0.2811754f, y=0.85693806f, z=0.0f), b=Point3F(x=0.27927902f, y=0.85744506f, z=5.888954E-5f)), LineSegment3F(a=Point3F(x=0.27927902f, y=0.85744506f, z=5.888954E-5f), b=Point3F(x=0.27738264f, y=0.85795206f, z=0.0f)), LineSegment3F(a=Point3F(x=0.27738264f, y=0.85795206f, z=0.0f), b=Point3F(x=0.2752999f, y=0.8588828f, z=6.8437075E-5f)), LineSegment3F(a=Point3F(x=0.2752999f, y=0.8588828f, z=6.8437075E-5f), b=Point3F(x=0.2732172f, y=0.8598135f, z=0.0f)), LineSegment3F(a=Point3F(x=0.2732172f, y=0.8598135f, z=0.0f), b=Point3F(x=0.2735042f, y=0.8609328f, z=3.4665703E-5f)), LineSegment3F(a=Point3F(x=0.2735042f, y=0.8609328f, z=3.4665703E-5f), b=Point3F(x=0.27379116f, y=0.8620521f, z=0.0f)), LineSegment3F(a=Point3F(x=0.27379116f, y=0.8620521f, z=0.0f), b=Point3F(x=0.27229136f, y=0.86064637f, z=4.186113E-4f)), LineSegment3F(a=Point3F(x=0.27229136f, y=0.86064637f, z=4.186113E-4f), b=Point3F(x=0.27079153f, y=0.85924065f, z=8.372226E-4f)), LineSegment3F(a=Point3F(x=0.27079153f, y=0.85924065f, z=8.372226E-4f), b=Point3F(x=0.2692917f, y=0.85783494f, z=0.0012558339f)), LineSegment3F(a=Point3F(x=0.2692917f, y=0.85783494f, z=0.0012558339f), b=Point3F(x=0.2677919f, y=0.8564292f, z=0.0016744452f)), LineSegment3F(a=Point3F(x=0.2677919f, y=0.8564292f, z=0.0016744452f), b=Point3F(x=0.26894632f, y=0.8550047f, z=0.0013973926f)), LineSegment3F(a=Point3F(x=0.26894632f, y=0.8550047f, z=0.0013973926f), b=Point3F(x=0.27010074f, y=0.85358024f, z=0.00112034f)), LineSegment3F(a=Point3F(x=0.27010074f, y=0.85358024f, z=0.00112034f), b=Point3F(x=0.27125517f, y=0.85215575f, z=8.4328733E-4f)), LineSegment3F(a=Point3F(x=0.27125517f, y=0.85215575f, z=8.4328733E-4f), b=Point3F(x=0.27240956f, y=0.85073125f, z=5.662347E-4f)), LineSegment3F(a=Point3F(x=0.27240956f, y=0.85073125f, z=5.662347E-4f), b=Point3F(x=0.27356398f, y=0.84930676f, z=0.0012900624f)), LineSegment3F(a=Point3F(x=0.27356398f, y=0.84930676f, z=0.0012900624f), b=Point3F(x=0.2747184f, y=0.8478823f, z=0.00201389f)), LineSegment3F(a=Point3F(x=0.2747184f, y=0.8478823f, z=0.00201389f), b=Point3F(x=0.27587283f, y=0.8464578f, z=0.0027377177f)), LineSegment3F(a=Point3F(x=0.27587283f, y=0.8464578f, z=0.0027377177f), b=Point3F(x=0.27702725f, y=0.8450333f, z=0.0034615458f)), LineSegment3F(a=Point3F(x=0.27702725f, y=0.8450333f, z=0.0034615458f), b=Point3F(x=0.27977726f, y=0.84454703f, z=0.004976585f)), LineSegment3F(a=Point3F(x=0.27977726f, y=0.84454703f, z=0.004976585f), b=Point3F(x=0.281235f, y=0.8453686f, z=0.004852961f)), LineSegment3F(a=Point3F(x=0.281235f, y=0.8453686f, z=0.004852961f), b=Point3F(x=0.28195333f, y=0.8474322f, z=0.0036397206f)), LineSegment3F(a=Point3F(x=0.28195333f, y=0.8474322f, z=0.0036397206f), b=Point3F(x=0.28267163f, y=0.84949577f, z=0.0024264804f)), LineSegment3F(a=Point3F(x=0.28267163f, y=0.84949577f, z=0.0024264804f), b=Point3F(x=0.28338993f, y=0.85155934f, z=0.0012132402f)), LineSegment3F(a=Point3F(x=0.28338993f, y=0.85155934f, z=0.0012132402f), b=Point3F(x=0.28410825f, y=0.853623f, z=0.0f)), LineSegment3F(a=Point3F(x=0.28410825f, y=0.853623f, z=0.0f), b=Point3F(x=0.2842222f, y=0.8547922f, z=3.5242283E-5f)), LineSegment3F(a=Point3F(x=0.2842222f, y=0.8547922f, z=3.5242283E-5f), b=Point3F(x=0.2843361f, y=0.8559614f, z=0.0f)), LineSegment3F(a=Point3F(x=0.2843361f, y=0.8559614f, z=0.0f), b=Point3F(x=0.28495723f, y=0.85690385f, z=3.3862303E-5f)), LineSegment3F(a=Point3F(x=0.28495723f, y=0.85690385f, z=3.3862303E-5f), b=Point3F(x=0.28557834f, y=0.8578463f, z=0.0f)), LineSegment3F(a=Point3F(x=0.28557834f, y=0.8578463f, z=0.0f), b=Point3F(x=0.28587902f, y=0.8583025f, z=1.639007E-5f)), LineSegment3F(a=Point3F(x=0.28587902f, y=0.8583025f, z=1.639007E-5f), b=Point3F(x=0.28617966f, y=0.8587587f, z=0.0f)))
            val riverSkeleton = arrayListOf(LineSegment3F(a=Point3F(x=0.27240956f, y=0.85073125f, z=5.662347E-4f), b=Point3F(x=0.27336484f, y=0.85184425f, z=4.7186238E-4f)), LineSegment3F(a=Point3F(x=0.27240956f, y=0.85073125f, z=5.662347E-4f), b=Point3F(x=0.27336484f, y=0.85184425f, z=4.7186238E-4f)), LineSegment3F(a=Point3F(x=0.27336484f, y=0.85184425f, z=4.7186238E-4f), b=Point3F(x=0.27406502f, y=0.8531331f, z=3.7749004E-4f)), LineSegment3F(a=Point3F(x=0.27406502f, y=0.8531331f, z=3.7749004E-4f), b=Point3F(x=0.27489612f, y=0.8543417f, z=2.8311522E-4f)), LineSegment3F(a=Point3F(x=0.27489612f, y=0.8543417f, z=2.8311522E-4f), b=Point3F(x=0.2757272f, y=0.8555503f, z=1.887404E-4f)), LineSegment3F(a=Point3F(x=0.2757272f, y=0.8555503f, z=1.887404E-4f), b=Point3F(x=0.27642736f, y=0.85683906f, z=9.437235E-5f)), LineSegment3F(a=Point3F(x=0.27642736f, y=0.85683906f, z=9.437235E-5f), b=Point3F(x=0.27738264f, y=0.85795206f, z=0.0f)), LineSegment3F(a=Point3F(x=0.27642736f, y=0.85683906f, z=9.437235E-5f), b=Point3F(x=0.27738264f, y=0.85795206f, z=0.0f)))
            val globalVertices = PointSet2F(points=arrayListOf(Point3F(x=0.28617966f, y=0.8587587f, z=0.0f), Point3F(x=0.28607213f, y=0.8588574f, z=4.378843E-6f), Point3F(x=0.28596464f, y=0.85895604f, z=0.0f), Point3F(x=0.28503317f, y=0.85981095f, z=3.792965E-5f), Point3F(x=0.28410172f, y=0.8606658f, z=0.0f), Point3F(x=0.28263855f, y=0.85880196f, z=7.108651E-5f), Point3F(x=0.2811754f, y=0.85693806f, z=0.0f), Point3F(x=0.27927902f, y=0.85744506f, z=5.888954E-5f), Point3F(x=0.27738264f, y=0.85795206f, z=0.0f), Point3F(x=0.2752999f, y=0.8588828f, z=6.8437075E-5f), Point3F(x=0.2732172f, y=0.8598135f, z=0.0f), Point3F(x=0.2735042f, y=0.8609328f, z=3.4665703E-5f), Point3F(x=0.27379116f, y=0.8620521f, z=0.0f), Point3F(x=0.27229136f, y=0.86064637f, z=4.186113E-4f), Point3F(x=0.27079153f, y=0.85924065f, z=8.372226E-4f), Point3F(x=0.2692917f, y=0.85783494f, z=0.0012558339f), Point3F(x=0.2677919f, y=0.8564292f, z=0.0016744452f), Point3F(x=0.26894632f, y=0.8550047f, z=0.0013973926f), Point3F(x=0.27010074f, y=0.85358024f, z=0.00112034f), Point3F(x=0.27125517f, y=0.85215575f, z=8.4328733E-4f), Point3F(x=0.27240956f, y=0.85073125f, z=5.662347E-4f), Point3F(x=0.27356398f, y=0.84930676f, z=0.0012900624f), Point3F(x=0.2747184f, y=0.8478823f, z=0.00201389f), Point3F(x=0.27587283f, y=0.8464578f, z=0.0027377177f), Point3F(x=0.27702725f, y=0.8450333f, z=0.0034615458f), Point3F(x=0.27977726f, y=0.84454703f, z=0.004976585f), Point3F(x=0.281235f, y=0.8453686f, z=0.004852961f), Point3F(x=0.28195333f, y=0.84743226f, z=0.0036397206f), Point3F(x=0.28267163f, y=0.8494958f, z=0.0024264804f), Point3F(x=0.28338993f, y=0.8515594f, z=0.0012132402f), Point3F(x=0.28410825f, y=0.853623f, z=0.0f), Point3F(x=0.2842222f, y=0.8547922f, z=3.5242283E-5f), Point3F(x=0.2843361f, y=0.8559614f, z=0.0f), Point3F(x=0.28495723f, y=0.85690385f, z=3.3862303E-5f), Point3F(x=0.28557834f, y=0.8578463f, z=0.0f), Point3F(x=0.28587902f, y=0.8583025f, z=1.639007E-5f), Point3F(x=0.27336484f, y=0.85184425f, z=4.7186238E-4f), Point3F(x=0.27406502f, y=0.8531331f, z=3.7749004E-4f), Point3F(x=0.27489612f, y=0.8543417f, z=2.8311522E-4f), Point3F(x=0.2757272f, y=0.8555503f, z=1.887404E-4f), Point3F(x=0.27642736f, y=0.85683906f, z=9.437235E-5f)))
            buildMesh(edgeSkeleton, riverSkeleton, globalVertices)
        }

        val test2 = {
            val edgeSkeleton = arrayListOf(LineSegment3F(a=Point3F(x=0.2489997f, y=0.17742132f, z=0.0f), b=Point3F(x=0.25112677f, y=0.17799503f, z=0.0f)), LineSegment3F(a=Point3F(x=0.25112677f, y=0.17799503f, z=0.0f), b=Point3F(x=0.25325385f, y=0.17856875f, z=0.0f)), LineSegment3F(a=Point3F(x=0.25325385f, y=0.17856875f, z=0.0f), b=Point3F(x=0.25453934f, y=0.17753592f, z=0.0f)), LineSegment3F(a=Point3F(x=0.25453934f, y=0.17753592f, z=0.0f), b=Point3F(x=0.25586307f, y=0.17870252f, z=0.0f)), LineSegment3F(a=Point3F(x=0.25586307f, y=0.17870252f, z=0.0f), b=Point3F(x=0.25704408f, y=0.18001348f, z=0.0f)), LineSegment3F(a=Point3F(x=0.25704408f, y=0.18001348f, z=0.0f), b=Point3F(x=0.25836074f, y=0.18047026f, z=0.0f)), LineSegment3F(a=Point3F(x=0.25836074f, y=0.18047026f, z=0.0f), b=Point3F(x=0.25974515f, y=0.18063025f, z=0.0f)), LineSegment3F(a=Point3F(x=0.25974515f, y=0.18063025f, z=0.0f), b=Point3F(x=0.26105848f, y=0.1801641f, z=0.0f)), LineSegment3F(a=Point3F(x=0.26105848f, y=0.1801641f, z=0.0f), b=Point3F(x=0.2609803f, y=0.17875561f, z=0.0f)), LineSegment3F(a=Point3F(x=0.2609803f, y=0.17875561f, z=0.0f), b=Point3F(x=0.26077545f, y=0.17736012f, z=0.0f)), LineSegment3F(a=Point3F(x=0.26077545f, y=0.17736012f, z=0.0f), b=Point3F(x=0.26184866f, y=0.17656477f, z=0.0f)), LineSegment3F(a=Point3F(x=0.26184866f, y=0.17656477f, z=0.0f), b=Point3F(x=0.263184f, y=0.17659676f, z=0.0f)), LineSegment3F(a=Point3F(x=0.263184f, y=0.17659676f, z=0.0f), b=Point3F(x=0.2641694f, y=0.17776361f, z=0.0f)), LineSegment3F(a=Point3F(x=0.2641694f, y=0.17776361f, z=0.0f), b=Point3F(x=0.26525122f, y=0.17884162f, z=0.0f)), LineSegment3F(a=Point3F(x=0.26525122f, y=0.17884162f, z=0.0f), b=Point3F(x=0.26654974f, y=0.17865564f, z=0.0f)), LineSegment3F(a=Point3F(x=0.26654974f, y=0.17865564f, z=0.0f), b=Point3F(x=0.2676694f, y=0.17797226f, z=0.0f)), LineSegment3F(a=Point3F(x=0.2676694f, y=0.17797226f, z=0.0f), b=Point3F(x=0.26851392f, y=0.17696847f, z=0.0f)), LineSegment3F(a=Point3F(x=0.26851392f, y=0.17696847f, z=0.0f), b=Point3F(x=0.26916882f, y=0.17565145f, z=0.0f)), LineSegment3F(a=Point3F(x=0.26916882f, y=0.17565145f, z=0.0f), b=Point3F(x=0.2695615f, y=0.17423396f, z=0.0f)), LineSegment3F(a=Point3F(x=0.2695615f, y=0.17423396f, z=0.0f), b=Point3F(x=0.26979503f, y=0.17278175f, z=0.0f)), LineSegment3F(a=Point3F(x=0.26979503f, y=0.17278175f, z=0.0f), b=Point3F(x=0.26958779f, y=0.17082275f, z=0.0f)), LineSegment3F(a=Point3F(x=0.26958779f, y=0.17082275f, z=0.0f), b=Point3F(x=0.27104878f, y=0.17178811f, z=0.0021180715f)), LineSegment3F(a=Point3F(x=0.27104878f, y=0.17178811f, z=0.0021180715f), b=Point3F(x=0.27250978f, y=0.17275347f, z=0.004236143f)), LineSegment3F(a=Point3F(x=0.27250978f, y=0.17275347f, z=0.004236143f), b=Point3F(x=0.27397075f, y=0.17371881f, z=0.0063542146f)), LineSegment3F(a=Point3F(x=0.27397075f, y=0.17371881f, z=0.0063542146f), b=Point3F(x=0.2734902f, y=0.17562251f, z=0.006487554f)), LineSegment3F(a=Point3F(x=0.2734902f, y=0.17562251f, z=0.006487554f), b=Point3F(x=0.27300963f, y=0.1775262f, z=0.0066208933f)), LineSegment3F(a=Point3F(x=0.27300963f, y=0.1775262f, z=0.0066208933f), b=Point3F(x=0.27252907f, y=0.1794299f, z=0.0067542326f)), LineSegment3F(a=Point3F(x=0.27252907f, y=0.1794299f, z=0.0067542326f), b=Point3F(x=0.2720485f, y=0.1813336f, z=0.006887572f)), LineSegment3F(a=Point3F(x=0.2720485f, y=0.1813336f, z=0.006887572f), b=Point3F(x=0.27156794f, y=0.1832373f, z=0.007020911f)), LineSegment3F(a=Point3F(x=0.27156794f, y=0.1832373f, z=0.007020911f), b=Point3F(x=0.27108735f, y=0.18514101f, z=0.0071542514f)), LineSegment3F(a=Point3F(x=0.27108735f, y=0.18514101f, z=0.0071542514f), b=Point3F(x=0.2687779f, y=0.18634954f, z=0.002348038f)), LineSegment3F(a=Point3F(x=0.2687779f, y=0.18634954f, z=0.002348038f), b=Point3F(x=0.2664685f, y=0.18755807f, z=0.01879533f)), LineSegment3F(a=Point3F(x=0.2664685f, y=0.18755807f, z=0.01879533f), b=Point3F(x=0.2647539f, y=0.18663633f, z=0.018929258f)), LineSegment3F(a=Point3F(x=0.2647539f, y=0.18663633f, z=0.018929258f), b=Point3F(x=0.26303932f, y=0.18571459f, z=0.019063186f)), LineSegment3F(a=Point3F(x=0.26303932f, y=0.18571459f, z=0.019063186f), b=Point3F(x=0.26132473f, y=0.18479285f, z=0.019197114f)), LineSegment3F(a=Point3F(x=0.26132473f, y=0.18479285f, z=0.019197114f), b=Point3F(x=0.25961015f, y=0.1838711f, z=0.019331042f)), LineSegment3F(a=Point3F(x=0.25961015f, y=0.1838711f, z=0.019331042f), b=Point3F(x=0.25789556f, y=0.18294936f, z=0.01946497f)), LineSegment3F(a=Point3F(x=0.25789556f, y=0.18294936f, z=0.01946497f), b=Point3F(x=0.25618097f, y=0.18202762f, z=0.019598898f)), LineSegment3F(a=Point3F(x=0.25618097f, y=0.18202762f, z=0.019598898f), b=Point3F(x=0.25446638f, y=0.18110588f, z=0.019732825f)), LineSegment3F(a=Point3F(x=0.25446638f, y=0.18110588f, z=0.019732825f), b=Point3F(x=0.2527518f, y=0.18018414f, z=0.019866753f)), LineSegment3F(a=Point3F(x=0.2527518f, y=0.18018414f, z=0.019866753f), b=Point3F(x=0.2510372f, y=0.1792624f, z=0.020000681f)), LineSegment3F(a=Point3F(x=0.2510372f, y=0.1792624f, z=0.020000681f), b=Point3F(x=0.24932264f, y=0.17834066f, z=0.02013461f)), LineSegment3F(a=Point3F(x=0.24932264f, y=0.17834066f, z=0.02013461f), b=Point3F(x=0.24760816f, y=0.17741895f, z=0.020268546f)), LineSegment3F(a=Point3F(x=0.24760816f, y=0.17741895f, z=0.020268546f), b=Point3F(x=0.24785551f, y=0.17599007f, z=0.0f)), LineSegment3F(a=Point3F(x=0.24785551f, y=0.17599007f, z=0.0f), b=Point3F(x=0.2489997f, y=0.17742132f, z=0.0f)))
            val riverSkeleton = arrayListOf(LineSegment3F(a=Point3F(x=0.2687779f, y=0.18634954f, z=0.002348038f), b=Point3F(x=0.2680214f, y=0.18517174f, z=0.0019567027f)), LineSegment3F(a=Point3F(x=0.2687779f, y=0.18634954f, z=0.002348038f), b=Point3F(x=0.2680214f, y=0.18517174f, z=0.0019567027f)), LineSegment3F(a=Point3F(x=0.2680214f, y=0.18517174f, z=0.0019567027f), b=Point3F(x=0.26731622f, y=0.183963f, z=0.001565486f)), LineSegment3F(a=Point3F(x=0.26731622f, y=0.183963f, z=0.001565486f), b=Point3F(x=0.26701456f, y=0.18259558f, z=0.001174019f)), LineSegment3F(a=Point3F(x=0.26701456f, y=0.18259558f, z=0.001174019f), b=Point3F(x=0.2667129f, y=0.18122816f, z=7.825519E-4f)), LineSegment3F(a=Point3F(x=0.2667129f, y=0.18122816f, z=7.825519E-4f), b=Point3F(x=0.26600772f, y=0.18001942f, z=3.9133517E-4f)), LineSegment3F(a=Point3F(x=0.26600772f, y=0.18001942f, z=3.9133517E-4f), b=Point3F(x=0.26525122f, y=0.17884162f, z=0.0f)), LineSegment3F(a=Point3F(x=0.26600772f, y=0.18001942f, z=3.9133517E-4f), b=Point3F(x=0.26525122f, y=0.17884162f, z=0.0f)))
            val globalVertices = PointSet2F(points=arrayListOf(Point3F(x=0.2489997f, y=0.17742132f, z=0.0f), Point3F(x=0.25112677f, y=0.17799503f, z=0.0f), Point3F(x=0.25325385f, y=0.17856875f, z=0.0f), Point3F(x=0.25453934f, y=0.17753592f, z=0.0f), Point3F(x=0.25586307f, y=0.17870252f, z=0.0f), Point3F(x=0.25704408f, y=0.18001348f, z=0.0f), Point3F(x=0.25836074f, y=0.18047026f, z=0.0f), Point3F(x=0.25974515f, y=0.18063025f, z=0.0f), Point3F(x=0.26105848f, y=0.1801641f, z=0.0f), Point3F(x=0.2609803f, y=0.17875561f, z=0.0f), Point3F(x=0.26077545f, y=0.17736012f, z=0.0f), Point3F(x=0.26184866f, y=0.17656477f, z=0.0f), Point3F(x=0.263184f, y=0.17659676f, z=0.0f), Point3F(x=0.2641694f, y=0.17776361f, z=0.0f), Point3F(x=0.26525122f, y=0.17884162f, z=0.0f), Point3F(x=0.26654974f, y=0.17865564f, z=0.0f), Point3F(x=0.2676694f, y=0.17797226f, z=0.0f), Point3F(x=0.26851392f, y=0.17696847f, z=0.0f), Point3F(x=0.26916882f, y=0.17565145f, z=0.0f), Point3F(x=0.2695615f, y=0.17423396f, z=0.0f), Point3F(x=0.26979503f, y=0.17278175f, z=0.0f), Point3F(x=0.26958779f, y=0.17082275f, z=0.0f), Point3F(x=0.27104878f, y=0.17178811f, z=0.0021180715f), Point3F(x=0.27250978f, y=0.17275347f, z=0.004236143f), Point3F(x=0.27397075f, y=0.17371881f, z=0.0063542146f), Point3F(x=0.2734902f, y=0.17562251f, z=0.006487554f), Point3F(x=0.27300963f, y=0.1775262f, z=0.0066208933f), Point3F(x=0.27252907f, y=0.1794299f, z=0.0067542326f), Point3F(x=0.2720485f, y=0.1813336f, z=0.006887572f), Point3F(x=0.27156794f, y=0.1832373f, z=0.007020911f), Point3F(x=0.27108735f, y=0.18514101f, z=0.0071542514f), Point3F(x=0.2687779f, y=0.18634954f, z=0.002348038f), Point3F(x=0.2664685f, y=0.18755807f, z=0.01879533f), Point3F(x=0.2647539f, y=0.18663633f, z=0.018929258f), Point3F(x=0.26303932f, y=0.18571459f, z=0.019063186f), Point3F(x=0.26132473f, y=0.18479285f, z=0.019197114f), Point3F(x=0.25961015f, y=0.1838711f, z=0.019331042f), Point3F(x=0.25789556f, y=0.18294936f, z=0.01946497f), Point3F(x=0.25618097f, y=0.18202762f, z=0.019598898f), Point3F(x=0.25446638f, y=0.18110588f, z=0.019732825f), Point3F(x=0.2527518f, y=0.18018414f, z=0.019866753f), Point3F(x=0.2510372f, y=0.1792624f, z=0.020000681f), Point3F(x=0.24932264f, y=0.17834066f, z=0.02013461f), Point3F(x=0.24760816f, y=0.17741895f, z=0.020268546f), Point3F(x=0.24785551f, y=0.17599007f, z=0.0f), Point3F(x=0.2680214f, y=0.18517174f, z=0.0019567027f), Point3F(x=0.26731622f, y=0.183963f, z=0.001565486f), Point3F(x=0.26701456f, y=0.18259558f, z=0.001174019f), Point3F(x=0.2667129f, y=0.18122816f, z=7.825519E-4f), Point3F(x=0.26600772f, y=0.18001942f, z=3.9133517E-4f)))
            buildMesh(edgeSkeleton, riverSkeleton, globalVertices)
        }

        val test3 = {
            val edgeSkeleton = arrayListOf(LineSegment3F(a=Point3F(x=0.40122813f, y=0.85538137f, z=0.0f), b=Point3F(x=0.40084934f, y=0.85751414f, z=6.4985354E-5f)), LineSegment3F(a=Point3F(x=0.40084934f, y=0.85751414f, z=6.4985354E-5f), b=Point3F(x=0.40047055f, y=0.859647f, z=1.2997071E-4f)), LineSegment3F(a=Point3F(x=0.40047055f, y=0.859647f, z=1.2997071E-4f), b=Point3F(x=0.40009177f, y=0.8617798f, z=6.4985354E-5f)), LineSegment3F(a=Point3F(x=0.40009177f, y=0.8617798f, z=6.4985354E-5f), b=Point3F(x=0.399713f, y=0.8639126f, z=0.0f)), LineSegment3F(a=Point3F(x=0.399713f, y=0.8639126f, z=0.0f), b=Point3F(x=0.39928204f, y=0.8641032f, z=0.0f)), LineSegment3F(a=Point3F(x=0.39928204f, y=0.8641032f, z=0.0f), b=Point3F(x=0.39855865f, y=0.8627764f, z=0.0f)), LineSegment3F(a=Point3F(x=0.39855865f, y=0.8627764f, z=0.0f), b=Point3F(x=0.39794087f, y=0.86139727f, z=0.0f)), LineSegment3F(a=Point3F(x=0.39794087f, y=0.86139727f, z=0.0f), b=Point3F(x=0.397187f, y=0.8600876f, z=0.0f)), LineSegment3F(a=Point3F(x=0.397187f, y=0.8600876f, z=0.0f), b=Point3F(x=0.39602897f, y=0.859139f, z=0.0f)), LineSegment3F(a=Point3F(x=0.39602897f, y=0.859139f, z=0.0f), b=Point3F(x=0.39499944f, y=0.85805243f, z=0.0f)), LineSegment3F(a=Point3F(x=0.39499944f, y=0.85805243f, z=0.0f), b=Point3F(x=0.39515457f, y=0.85680896f, z=0.0f)), LineSegment3F(a=Point3F(x=0.39515457f, y=0.85680896f, z=0.0f), b=Point3F(x=0.39552653f, y=0.85561234f, z=0.0f)), LineSegment3F(a=Point3F(x=0.39552653f, y=0.85561234f, z=0.0f), b=Point3F(x=0.3953955f, y=0.85382515f, z=0.0f)), LineSegment3F(a=Point3F(x=0.3953955f, y=0.85382515f, z=0.0f), b=Point3F(x=0.39514995f, y=0.85205007f, z=0.0f)), LineSegment3F(a=Point3F(x=0.39514995f, y=0.85205007f, z=0.0f), b=Point3F(x=0.39500955f, y=0.8509414f, z=0.0f)), LineSegment3F(a=Point3F(x=0.39500955f, y=0.8509414f, z=0.0f), b=Point3F(x=0.39498892f, y=0.8498168f, z=0.0f)), LineSegment3F(a=Point3F(x=0.39498892f, y=0.8498168f, z=0.0f), b=Point3F(x=0.39516914f, y=0.8498045f, z=4.1248175E-4f)), LineSegment3F(a=Point3F(x=0.39516914f, y=0.8498045f, z=4.1248175E-4f), b=Point3F(x=0.39672443f, y=0.85095674f, z=3.0936132E-4f)), LineSegment3F(a=Point3F(x=0.39672443f, y=0.85095674f, z=3.0936132E-4f), b=Point3F(x=0.39827973f, y=0.85210896f, z=2.0624089E-4f)), LineSegment3F(a=Point3F(x=0.39827973f, y=0.85210896f, z=2.0624089E-4f), b=Point3F(x=0.39983502f, y=0.8532612f, z=1.0312045E-4f)), LineSegment3F(a=Point3F(x=0.39983502f, y=0.8532612f, z=1.0312045E-4f), b=Point3F(x=0.40139028f, y=0.8544134f, z=0.0f)), LineSegment3F(a=Point3F(x=0.40139028f, y=0.8544134f, z=0.0f), b=Point3F(x=0.4013092f, y=0.8548974f, z=1.4722084E-5f)), LineSegment3F(a=Point3F(x=0.4013092f, y=0.8548974f, z=1.4722084E-5f), b=Point3F(x=0.40122813f, y=0.85538137f, z=0.0f)))
            val riverSkeleton = arrayListOf<LineSegment3F>()
            val globalVertices = PointSet2F(points=arrayListOf(Point3F(x=0.40122813f, y=0.85538137f, z=0.0f), Point3F(x=0.40084934f, y=0.85751414f, z=6.4985354E-5f), Point3F(x=0.40047055f, y=0.859647f, z=1.2997071E-4f), Point3F(x=0.40009177f, y=0.8617798f, z=6.4985354E-5f), Point3F(x=0.399713f, y=0.8639126f, z=0.0f), Point3F(x=0.39928204f, y=0.8641032f, z=0.0f), Point3F(x=0.39855865f, y=0.8627764f, z=0.0f), Point3F(x=0.39794087f, y=0.86139727f, z=0.0f), Point3F(x=0.397187f, y=0.8600876f, z=0.0f), Point3F(x=0.39602897f, y=0.859139f, z=0.0f), Point3F(x=0.39499944f, y=0.85805243f, z=0.0f), Point3F(x=0.39515457f, y=0.85680896f, z=0.0f), Point3F(x=0.39552653f, y=0.85561234f, z=0.0f), Point3F(x=0.3953955f, y=0.85382515f, z=0.0f), Point3F(x=0.39514995f, y=0.85205007f, z=0.0f), Point3F(x=0.39500955f, y=0.8509414f, z=0.0f), Point3F(x=0.39498892f, y=0.8498168f, z=0.0f), Point3F(x=0.39516914f, y=0.8498045f, z=4.1248175E-4f), Point3F(x=0.3967244f, y=0.85095674f, z=3.0936132E-4f), Point3F(x=0.3982797f, y=0.85210896f, z=2.0624087E-4f), Point3F(x=0.399835f, y=0.8532612f, z=1.0312044E-4f), Point3F(x=0.40139028f, y=0.8544134f, z=0.0f), Point3F(x=0.4013092f, y=0.8548974f, z=1.4722084E-5f)))
            buildMesh(edgeSkeleton, riverSkeleton, globalVertices)
        }

        val test4 = {
            val edgeSkeleton = arrayListOf(LineSegment3F(a=Point3F(x=0.1611595f, y=0.787697f, z=0.0f), b=Point3F(x=0.1622503f, y=0.78802294f, z=3.4153334E-5f)), LineSegment3F(a=Point3F(x=0.1622503f, y=0.78802294f, z=3.4153334E-5f), b=Point3F(x=0.16334109f, y=0.78834885f, z=0.0f)), LineSegment3F(a=Point3F(x=0.16334109f, y=0.78834885f, z=0.0f), b=Point3F(x=0.16438434f, y=0.78815126f, z=3.1853757E-5f)), LineSegment3F(a=Point3F(x=0.16438434f, y=0.78815126f, z=3.1853757E-5f), b=Point3F(x=0.1654276f, y=0.7879537f, z=0.0f)), LineSegment3F(a=Point3F(x=0.1654276f, y=0.7879537f, z=0.0f), b=Point3F(x=0.16337417f, y=0.7895193f, z=4.3451088E-4f)), LineSegment3F(a=Point3F(x=0.16337417f, y=0.7895193f, z=4.3451088E-4f), b=Point3F(x=0.16363734f, y=0.7917364f, z=2.1725544E-4f)), LineSegment3F(a=Point3F(x=0.16363734f, y=0.7917364f, z=2.1725544E-4f), b=Point3F(x=0.1639005f, y=0.79395354f, z=0.0f)), LineSegment3F(a=Point3F(x=0.1639005f, y=0.79395354f, z=0.0f), b=Point3F(x=0.16379434f, y=0.79339653f, z=1.7010927E-5f)), LineSegment3F(a=Point3F(x=0.16379434f, y=0.79339653f, z=1.7010927E-5f), b=Point3F(x=0.16368818f, y=0.7928395f, z=0.0f)), LineSegment3F(a=Point3F(x=0.16368818f, y=0.7928395f, z=0.0f), b=Point3F(x=0.16110942f, y=0.7914715f, z=8.757522E-5f)), LineSegment3F(a=Point3F(x=0.16110942f, y=0.7914715f, z=8.757522E-5f), b=Point3F(x=0.15853067f, y=0.79010344f, z=0.0f)), LineSegment3F(a=Point3F(x=0.15853067f, y=0.79010344f, z=0.0f), b=Point3F(x=0.15747873f, y=0.79242164f, z=7.637124E-5f)), LineSegment3F(a=Point3F(x=0.15747873f, y=0.79242164f, z=7.637124E-5f), b=Point3F(x=0.1564268f, y=0.79473984f, z=0.0f)), LineSegment3F(a=Point3F(x=0.1564268f, y=0.79473984f, z=0.0f), b=Point3F(x=0.15499648f, y=0.7941184f, z=4.6784557E-5f)), LineSegment3F(a=Point3F(x=0.15499648f, y=0.7941184f, z=4.6784557E-5f), b=Point3F(x=0.15356618f, y=0.79349697f, z=0.0f)), LineSegment3F(a=Point3F(x=0.15356618f, y=0.79349697f, z=0.0f), b=Point3F(x=0.15414879f, y=0.79208815f, z=4.573588E-5f)), LineSegment3F(a=Point3F(x=0.15414879f, y=0.79208815f, z=4.573588E-5f), b=Point3F(x=0.1547314f, y=0.79067934f, z=9.147176E-5f)), LineSegment3F(a=Point3F(x=0.1547314f, y=0.79067934f, z=9.147176E-5f), b=Point3F(x=0.155314f, y=0.7892705f, z=4.573588E-5f)), LineSegment3F(a=Point3F(x=0.155314f, y=0.7892705f, z=4.573588E-5f), b=Point3F(x=0.1558966f, y=0.7878617f, z=0.0f)), LineSegment3F(a=Point3F(x=0.1558966f, y=0.7878617f, z=0.0f), b=Point3F(x=0.15696207f, y=0.7879117f, z=3.1999076E-5f)), LineSegment3F(a=Point3F(x=0.15696207f, y=0.7879117f, z=3.1999076E-5f), b=Point3F(x=0.15802751f, y=0.7879617f, z=0.0f)), LineSegment3F(a=Point3F(x=0.15802751f, y=0.7879617f, z=0.0f), b=Point3F(x=0.15868813f, y=0.78790593f, z=1.9888943E-5f)), LineSegment3F(a=Point3F(x=0.15868813f, y=0.78790593f, z=1.9888943E-5f), b=Point3F(x=0.15934876f, y=0.7878501f, z=0.0f)), LineSegment3F(a=Point3F(x=0.15934876f, y=0.7878501f, z=0.0f), b=Point3F(x=0.16025412f, y=0.78777355f, z=2.725781E-5f)), LineSegment3F(a=Point3F(x=0.16025412f, y=0.78777355f, z=2.725781E-5f), b=Point3F(x=0.1611595f, y=0.787697f, z=0.0f)))
            val riverSkeleton = arrayListOf<LineSegment3F>()
            val globalVertices = PointSet2F(points=arrayListOf(Point3F(x=0.1611595f, y=0.787697f, z=0.0f), Point3F(x=0.1622503f, y=0.78802294f, z=3.4153334E-5f), Point3F(x=0.16334109f, y=0.78834885f, z=0.0f), Point3F(x=0.16438434f, y=0.78815126f, z=3.1853757E-5f), Point3F(x=0.1654276f, y=0.7879537f, z=0.0f), Point3F(x=0.16337417f, y=0.7895193f, z=4.3451088E-4f), Point3F(x=0.16363734f, y=0.7917364f, z=2.1725544E-4f), Point3F(x=0.1639005f, y=0.79395354f, z=0.0f), Point3F(x=0.16379434f, y=0.79339653f, z=1.7010927E-5f), Point3F(x=0.16368818f, y=0.7928395f, z=0.0f), Point3F(x=0.16110942f, y=0.7914715f, z=8.757522E-5f), Point3F(x=0.15853067f, y=0.79010344f, z=0.0f), Point3F(x=0.15747873f, y=0.79242164f, z=7.637124E-5f), Point3F(x=0.1564268f, y=0.79473984f, z=0.0f), Point3F(x=0.15499648f, y=0.7941184f, z=4.6784557E-5f), Point3F(x=0.15356618f, y=0.79349697f, z=0.0f), Point3F(x=0.15414879f, y=0.79208815f, z=4.573588E-5f), Point3F(x=0.1547314f, y=0.79067934f, z=9.147176E-5f), Point3F(x=0.155314f, y=0.7892705f, z=4.573588E-5f), Point3F(x=0.1558966f, y=0.7878617f, z=0.0f), Point3F(x=0.15696207f, y=0.7879117f, z=3.1999076E-5f), Point3F(x=0.15802751f, y=0.7879617f, z=0.0f), Point3F(x=0.15868813f, y=0.78790593f, z=1.9888943E-5f), Point3F(x=0.15934876f, y=0.7878501f, z=0.0f), Point3F(x=0.16025412f, y=0.78777355f, z=2.725781E-5f)))
            buildMesh(edgeSkeleton, riverSkeleton, globalVertices)
        }

        val test5 = {
            val edgeSkeleton = arrayListOf(LineSegment3F(a=Point3F(x=0.11116079f, y=0.6722978f, z=0.0f), b=Point3F(x=0.11202975f, y=0.6703384f, z=0.0010798032f)), LineSegment3F(a=Point3F(x=0.11202975f, y=0.6703384f, z=0.0010798032f), b=Point3F(x=0.112898715f, y=0.668379f, z=0.0021596064f)), LineSegment3F(a=Point3F(x=0.112898715f, y=0.668379f, z=0.0021596064f), b=Point3F(x=0.113767676f, y=0.6664196f, z=0.0032394095f)), LineSegment3F(a=Point3F(x=0.113767676f, y=0.6664196f, z=0.0032394095f), b=Point3F(x=0.11463664f, y=0.66446024f, z=0.004319213f)), LineSegment3F(a=Point3F(x=0.11463664f, y=0.66446024f, z=0.004319213f), b=Point3F(x=0.115505606f, y=0.6625009f, z=0.005399016f)), LineSegment3F(a=Point3F(x=0.115505606f, y=0.6625009f, z=0.005399016f), b=Point3F(x=0.11740114f, y=0.66259956f, z=0.005813131f)), LineSegment3F(a=Point3F(x=0.11740114f, y=0.66259956f, z=0.005813131f), b=Point3F(x=0.11929667f, y=0.6626982f, z=0.0062272465f)), LineSegment3F(a=Point3F(x=0.11929667f, y=0.6626982f, z=0.0062272465f), b=Point3F(x=0.1211922f, y=0.66279685f, z=0.006641362f)), LineSegment3F(a=Point3F(x=0.1211922f, y=0.66279685f, z=0.006641362f), b=Point3F(x=0.123087734f, y=0.6628955f, z=0.0070554772f)), LineSegment3F(a=Point3F(x=0.123087734f, y=0.6628955f, z=0.0070554772f), b=Point3F(x=0.124983266f, y=0.66299415f, z=0.0074695926f)), LineSegment3F(a=Point3F(x=0.124983266f, y=0.66299415f, z=0.0074695926f), b=Point3F(x=0.12687878f, y=0.6630927f, z=0.007883707f)), LineSegment3F(a=Point3F(x=0.12687878f, y=0.6630927f, z=0.007883707f), b=Point3F(x=0.12826525f, y=0.66461843f, z=0.0079036f)), LineSegment3F(a=Point3F(x=0.12826525f, y=0.66461843f, z=0.0079036f), b=Point3F(x=0.12965171f, y=0.6661442f, z=0.007923493f)), LineSegment3F(a=Point3F(x=0.12965171f, y=0.6661442f, z=0.007923493f), b=Point3F(x=0.12881748f, y=0.66779f, z=0.0063387947f)), LineSegment3F(a=Point3F(x=0.12881748f, y=0.66779f, z=0.0063387947f), b=Point3F(x=0.12798326f, y=0.6694358f, z=0.0047540963f)), LineSegment3F(a=Point3F(x=0.12798326f, y=0.6694358f, z=0.0047540963f), b=Point3F(x=0.12714903f, y=0.6710816f, z=0.0031693976f)), LineSegment3F(a=Point3F(x=0.12714903f, y=0.6710816f, z=0.0031693976f), b=Point3F(x=0.1263148f, y=0.6727274f, z=0.0015846989f)), LineSegment3F(a=Point3F(x=0.1263148f, y=0.6727274f, z=0.0015846989f), b=Point3F(x=0.12548059f, y=0.67437327f, z=0.0f)), LineSegment3F(a=Point3F(x=0.12548059f, y=0.67437327f, z=0.0f), b=Point3F(x=0.12483841f, y=0.674224f, z=1.9778881E-5f)), LineSegment3F(a=Point3F(x=0.12483841f, y=0.674224f, z=1.9778881E-5f), b=Point3F(x=0.12419624f, y=0.67407477f, z=0.0f)), LineSegment3F(a=Point3F(x=0.12419624f, y=0.67407477f, z=0.0f), b=Point3F(x=0.12318964f, y=0.67410284f, z=3.0209614E-5f)), LineSegment3F(a=Point3F(x=0.12318964f, y=0.67410284f, z=3.0209614E-5f), b=Point3F(x=0.12218305f, y=0.6741309f, z=0.0f)), LineSegment3F(a=Point3F(x=0.12218305f, y=0.6741309f, z=0.0f), b=Point3F(x=0.12128445f, y=0.6726229f, z=5.2662926E-5f)), LineSegment3F(a=Point3F(x=0.12128445f, y=0.6726229f, z=5.2662926E-5f), b=Point3F(x=0.12038585f, y=0.671115f, z=0.0f)), LineSegment3F(a=Point3F(x=0.12038585f, y=0.671115f, z=0.0f), b=Point3F(x=0.11836696f, y=0.67170113f, z=6.306779E-5f)), LineSegment3F(a=Point3F(x=0.11836696f, y=0.67170113f, z=6.306779E-5f), b=Point3F(x=0.11634807f, y=0.6722873f, z=0.0f)), LineSegment3F(a=Point3F(x=0.11634807f, y=0.6722873f, z=0.0f), b=Point3F(x=0.11640173f, y=0.6735259f, z=3.7194175E-5f)), LineSegment3F(a=Point3F(x=0.11640173f, y=0.6735259f, z=3.7194175E-5f), b=Point3F(x=0.11645539f, y=0.67476463f, z=0.0f)), LineSegment3F(a=Point3F(x=0.11645539f, y=0.67476463f, z=0.0f), b=Point3F(x=0.11549127f, y=0.67581743f, z=4.28266E-5f)), LineSegment3F(a=Point3F(x=0.11549127f, y=0.67581743f, z=4.28266E-5f), b=Point3F(x=0.11452716f, y=0.6768702f, z=0.0f)), LineSegment3F(a=Point3F(x=0.11452716f, y=0.6768702f, z=0.0f), b=Point3F(x=0.11437048f, y=0.6770413f, z=6.9591865E-6f)), LineSegment3F(a=Point3F(x=0.11437048f, y=0.6770413f, z=6.9591865E-6f), b=Point3F(x=0.1142138f, y=0.6772124f, z=0.0f)), LineSegment3F(a=Point3F(x=0.1142138f, y=0.6772124f, z=0.0f), b=Point3F(x=0.114040956f, y=0.6769342f, z=9.826554E-6f)), LineSegment3F(a=Point3F(x=0.114040956f, y=0.6769342f, z=9.826554E-6f), b=Point3F(x=0.1138681f, y=0.67665595f, z=0.0f)))
            val riverSkeleton = arrayListOf<LineSegment3F>()
            val globalVertices = PointSet2F(points=arrayListOf(Point3F(x=0.11116079f, y=0.6722978f, z=0.0f), Point3F(x=0.11202975f, y=0.6703384f, z=0.0010798032f), Point3F(x=0.112898715f, y=0.668379f, z=0.0021596064f), Point3F(x=0.113767676f, y=0.6664196f, z=0.0032394095f), Point3F(x=0.11463664f, y=0.66446024f, z=0.004319213f), Point3F(x=0.115505606f, y=0.6625009f, z=0.005399016f), Point3F(x=0.11740114f, y=0.66259956f, z=0.005813131f), Point3F(x=0.11929667f, y=0.6626982f, z=0.0062272465f), Point3F(x=0.1211922f, y=0.66279685f, z=0.006641362f), Point3F(x=0.123087734f, y=0.6628955f, z=0.0070554772f), Point3F(x=0.124983266f, y=0.66299415f, z=0.0074695926f), Point3F(x=0.12687878f, y=0.6630927f, z=0.007883707f), Point3F(x=0.12826525f, y=0.66461843f, z=0.0079036f), Point3F(x=0.12965171f, y=0.6661442f, z=0.007923493f), Point3F(x=0.1288175f, y=0.66779006f, z=0.0063387947f), Point3F(x=0.12798327f, y=0.66943586f, z=0.0047540963f), Point3F(x=0.12714905f, y=0.67108166f, z=0.0031693974f), Point3F(x=0.12631482f, y=0.67272747f, z=0.0015846987f), Point3F(x=0.12548059f, y=0.67437327f, z=0.0f), Point3F(x=0.12483841f, y=0.674224f, z=1.9778881E-5f), Point3F(x=0.12419624f, y=0.67407477f, z=0.0f), Point3F(x=0.12318964f, y=0.67410284f, z=3.0209614E-5f), Point3F(x=0.12218305f, y=0.6741309f, z=0.0f), Point3F(x=0.12128445f, y=0.6726229f, z=5.2662926E-5f), Point3F(x=0.12038585f, y=0.671115f, z=0.0f), Point3F(x=0.11836696f, y=0.67170113f, z=6.306779E-5f), Point3F(x=0.11634807f, y=0.6722873f, z=0.0f), Point3F(x=0.11640173f, y=0.6735259f, z=3.7194175E-5f), Point3F(x=0.11645539f, y=0.67476463f, z=0.0f), Point3F(x=0.11549127f, y=0.67581743f, z=4.28266E-5f), Point3F(x=0.11452716f, y=0.6768702f, z=0.0f), Point3F(x=0.11437048f, y=0.6770413f, z=6.9591865E-6f), Point3F(x=0.1142138f, y=0.6772124f, z=0.0f), Point3F(x=0.114040956f, y=0.6769342f, z=9.826554E-6f)))
            buildMesh(edgeSkeleton, riverSkeleton, globalVertices)
        }

        val test6 = {
            val edgeSkeleton = arrayListOf(LineSegment3F(a=Point3F(x=0.14894158f, y=0.6382998f, z=0.0f), b=Point3F(x=0.14963773f, y=0.63658816f, z=0.012095087f)), LineSegment3F(a=Point3F(x=0.14963773f, y=0.63658816f, z=0.012095087f), b=Point3F(x=0.15033388f, y=0.6348765f, z=0.024190174f)), LineSegment3F(a=Point3F(x=0.15033388f, y=0.6348765f, z=0.024190174f), b=Point3F(x=0.15233867f, y=0.63499945f, z=0.02186334f)), LineSegment3F(a=Point3F(x=0.15233867f, y=0.63499945f, z=0.02186334f), b=Point3F(x=0.15434346f, y=0.6351224f, z=0.019536505f)), LineSegment3F(a=Point3F(x=0.15434346f, y=0.6351224f, z=0.019536505f), b=Point3F(x=0.15634824f, y=0.6352454f, z=0.01720967f)), LineSegment3F(a=Point3F(x=0.15634824f, y=0.6352454f, z=0.01720967f), b=Point3F(x=0.15835305f, y=0.63536835f, z=0.014882832f)), LineSegment3F(a=Point3F(x=0.15835305f, y=0.63536835f, z=0.014882832f), b=Point3F(x=0.16035783f, y=0.6354913f, z=0.018104307f)), LineSegment3F(a=Point3F(x=0.16035783f, y=0.6354913f, z=0.018104307f), b=Point3F(x=0.16236262f, y=0.6356143f, z=0.021325782f)), LineSegment3F(a=Point3F(x=0.16236262f, y=0.6356143f, z=0.021325782f), b=Point3F(x=0.16436741f, y=0.63573724f, z=0.024547257f)), LineSegment3F(a=Point3F(x=0.16436741f, y=0.63573724f, z=0.024547257f), b=Point3F(x=0.16637221f, y=0.6358602f, z=0.027768733f)), LineSegment3F(a=Point3F(x=0.16637221f, y=0.6358602f, z=0.027768733f), b=Point3F(x=0.16719587f, y=0.63759714f, z=0.027924195f)), LineSegment3F(a=Point3F(x=0.16719587f, y=0.63759714f, z=0.027924195f), b=Point3F(x=0.16801953f, y=0.6393341f, z=0.028079657f)), LineSegment3F(a=Point3F(x=0.16801953f, y=0.6393341f, z=0.028079657f), b=Point3F(x=0.1688432f, y=0.641071f, z=0.028235119f)), LineSegment3F(a=Point3F(x=0.1688432f, y=0.641071f, z=0.028235119f), b=Point3F(x=0.16966686f, y=0.64280796f, z=0.02839058f)), LineSegment3F(a=Point3F(x=0.16966686f, y=0.64280796f, z=0.02839058f), b=Point3F(x=0.17049052f, y=0.6445449f, z=0.028546043f)), LineSegment3F(a=Point3F(x=0.17049052f, y=0.6445449f, z=0.028546043f), b=Point3F(x=0.17131418f, y=0.64628184f, z=0.028701505f)), LineSegment3F(a=Point3F(x=0.17131418f, y=0.64628184f, z=0.028701505f), b=Point3F(x=0.1721378f, y=0.6480188f, z=0.028856967f)), LineSegment3F(a=Point3F(x=0.1721378f, y=0.6480188f, z=0.028856967f), b=Point3F(x=0.1706944f, y=0.64900196f, z=0.026349433f)), LineSegment3F(a=Point3F(x=0.1706944f, y=0.64900196f, z=0.026349433f), b=Point3F(x=0.169251f, y=0.64998513f, z=0.023841899f)), LineSegment3F(a=Point3F(x=0.169251f, y=0.64998513f, z=0.023841899f), b=Point3F(x=0.1678076f, y=0.6509683f, z=0.021334365f)), LineSegment3F(a=Point3F(x=0.1678076f, y=0.6509683f, z=0.021334365f), b=Point3F(x=0.16636418f, y=0.6519515f, z=0.018826835f)), LineSegment3F(a=Point3F(x=0.16636418f, y=0.6519515f, z=0.018826835f), b=Point3F(x=0.16445367f, y=0.6512981f, z=0.016810145f)), LineSegment3F(a=Point3F(x=0.16445367f, y=0.6512981f, z=0.016810145f), b=Point3F(x=0.16254316f, y=0.6506447f, z=0.014793456f)), LineSegment3F(a=Point3F(x=0.16254316f, y=0.6506447f, z=0.014793456f), b=Point3F(x=0.16063266f, y=0.64999133f, z=0.012776766f)), LineSegment3F(a=Point3F(x=0.16063266f, y=0.64999133f, z=0.012776766f), b=Point3F(x=0.15872213f, y=0.649338f, z=0.010760078f)), LineSegment3F(a=Point3F(x=0.15872213f, y=0.649338f, z=0.010760078f), b=Point3F(x=0.15681162f, y=0.6486846f, z=0.012782538f)), LineSegment3F(a=Point3F(x=0.15681162f, y=0.6486846f, z=0.012782538f), b=Point3F(x=0.15490112f, y=0.64803123f, z=0.014804998f)), LineSegment3F(a=Point3F(x=0.15490112f, y=0.64803123f, z=0.014804998f), b=Point3F(x=0.15299061f, y=0.64737785f, z=0.016827459f)), LineSegment3F(a=Point3F(x=0.15299061f, y=0.64737785f, z=0.016827459f), b=Point3F(x=0.15108009f, y=0.6467246f, z=0.018849917f)), LineSegment3F(a=Point3F(x=0.15108009f, y=0.6467246f, z=0.018849917f), b=Point3F(x=0.15037647f, y=0.64498764f, z=0.012566611f)), LineSegment3F(a=Point3F(x=0.15037647f, y=0.64498764f, z=0.012566611f), b=Point3F(x=0.14967285f, y=0.6432507f, z=0.0062833056f)), LineSegment3F(a=Point3F(x=0.14967285f, y=0.6432507f, z=0.0062833056f), b=Point3F(x=0.14896923f, y=0.6415138f, z=0.0f)))
            val riverSkeleton = arrayListOf(LineSegment3F(a=Point3F(x=0.15835305f, y=0.63536835f, z=0.014882832f), b=Point3F(x=0.15805598f, y=0.63678384f, z=0.014470485f)), LineSegment3F(a=Point3F(x=0.15835305f, y=0.63536835f, z=0.014882832f), b=Point3F(x=0.15805598f, y=0.63678384f, z=0.014470485f)), LineSegment3F(a=Point3F(x=0.15805598f, y=0.63678384f, z=0.014470485f), b=Point3F(x=0.15841682f, y=0.6381846f, z=0.01405809f)), LineSegment3F(a=Point3F(x=0.15841682f, y=0.6381846f, z=0.01405809f), b=Point3F(x=0.15893775f, y=0.6395344f, z=0.013645598f)), LineSegment3F(a=Point3F(x=0.15893775f, y=0.6395344f, z=0.013645598f), b=Point3F(x=0.15880463f, y=0.64097476f, z=0.013233206f)), LineSegment3F(a=Point3F(x=0.15880463f, y=0.64097476f, z=0.013233206f), b=Point3F(x=0.15882641f, y=0.64239514f, z=0.01282821f)), LineSegment3F(a=Point3F(x=0.15882641f, y=0.64239514f, z=0.01282821f), b=Point3F(x=0.15921415f, y=0.6437618f, z=0.012423195f)), LineSegment3F(a=Point3F(x=0.15921415f, y=0.6437618f, z=0.012423195f), b=Point3F(x=0.15974058f, y=0.6451575f, z=0.011997919f)), LineSegment3F(a=Point3F(x=0.15974058f, y=0.6451575f, z=0.011997919f), b=Point3F(x=0.15928565f, y=0.64657795f, z=0.011572691f)), LineSegment3F(a=Point3F(x=0.15928565f, y=0.64657795f, z=0.011572691f), b=Point3F(x=0.15879104f, y=0.6479149f, z=0.011166284f)), LineSegment3F(a=Point3F(x=0.15879104f, y=0.6479149f, z=0.011166284f), b=Point3F(x=0.15872213f, y=0.649338f, z=0.010760078f)), LineSegment3F(a=Point3F(x=0.15879104f, y=0.6479149f, z=0.011166284f), b=Point3F(x=0.15872213f, y=0.649338f, z=0.010760078f)))
            val globalVertices = PointSet2F(points=arrayListOf(Point3F(x=0.14894158f, y=0.6382998f, z=0.0f), Point3F(x=0.14963773f, y=0.63658816f, z=0.012095087f), Point3F(x=0.15033388f, y=0.6348765f, z=0.024190174f), Point3F(x=0.15233867f, y=0.63499945f, z=0.02186334f), Point3F(x=0.15434346f, y=0.6351224f, z=0.019536505f), Point3F(x=0.15634824f, y=0.6352454f, z=0.01720967f), Point3F(x=0.15835305f, y=0.63536835f, z=0.014882832f), Point3F(x=0.16035783f, y=0.6354913f, z=0.018104307f), Point3F(x=0.16236262f, y=0.6356143f, z=0.021325782f), Point3F(x=0.16436741f, y=0.63573724f, z=0.024547257f), Point3F(x=0.16637221f, y=0.6358602f, z=0.027768733f), Point3F(x=0.16719587f, y=0.63759714f, z=0.027924195f), Point3F(x=0.16801953f, y=0.6393341f, z=0.028079657f), Point3F(x=0.1688432f, y=0.641071f, z=0.028235119f), Point3F(x=0.16966686f, y=0.64280796f, z=0.02839058f), Point3F(x=0.17049052f, y=0.6445449f, z=0.028546043f), Point3F(x=0.17131418f, y=0.64628184f, z=0.028701505f), Point3F(x=0.1721378f, y=0.6480188f, z=0.028856967f), Point3F(x=0.1706944f, y=0.64900196f, z=0.026349433f), Point3F(x=0.169251f, y=0.64998513f, z=0.023841899f), Point3F(x=0.1678076f, y=0.6509683f, z=0.021334365f), Point3F(x=0.16636418f, y=0.6519515f, z=0.018826835f), Point3F(x=0.16445367f, y=0.6512981f, z=0.016810145f), Point3F(x=0.16254316f, y=0.6506447f, z=0.014793456f), Point3F(x=0.16063266f, y=0.64999133f, z=0.012776766f), Point3F(x=0.15872213f, y=0.649338f, z=0.010760078f), Point3F(x=0.15681162f, y=0.6486846f, z=0.012782538f), Point3F(x=0.15490112f, y=0.64803123f, z=0.014804998f), Point3F(x=0.15299061f, y=0.64737785f, z=0.016827459f), Point3F(x=0.15108009f, y=0.6467246f, z=0.018849917f), Point3F(x=0.15037647f, y=0.64498764f, z=0.012566611f), Point3F(x=0.14967285f, y=0.6432507f, z=0.0062833056f), Point3F(x=0.15805598f, y=0.63678384f, z=0.014470485f), Point3F(x=0.15841682f, y=0.6381846f, z=0.01405809f), Point3F(x=0.15893775f, y=0.6395344f, z=0.013645598f), Point3F(x=0.15880463f, y=0.64097476f, z=0.013233206f), Point3F(x=0.15882641f, y=0.64239514f, z=0.01282821f), Point3F(x=0.15921415f, y=0.6437618f, z=0.012423195f), Point3F(x=0.15974058f, y=0.6451575f, z=0.011997919f), Point3F(x=0.15928565f, y=0.64657795f, z=0.011572691f), Point3F(x=0.15879104f, y=0.6479149f, z=0.011166284f)))
            buildMesh(edgeSkeleton, riverSkeleton, globalVertices)
        }

        val test7 = {
            val edgeSkeleton = arrayListOf(LineSegment3F(a=Point3F(x=0.45423353f, y=0.4629355f, z=0.0f), b=Point3F(x=0.45239055f, y=0.4633788f, z=4.9925124E-4f)), LineSegment3F(a=Point3F(x=0.45239055f, y=0.4633788f, z=4.9925124E-4f), b=Point3F(x=0.45095888f, y=0.46537393f, z=5.0595374E-4f)), LineSegment3F(a=Point3F(x=0.45095888f, y=0.46537393f, z=5.0595374E-4f), b=Point3F(x=0.4511534f, y=0.46726903f, z=0.0015522738f)), LineSegment3F(a=Point3F(x=0.4511534f, y=0.46726903f, z=0.0015522738f), b=Point3F(x=0.45134792f, y=0.46916413f, z=0.0025985937f)), LineSegment3F(a=Point3F(x=0.45134792f, y=0.46916413f, z=0.0025985937f), b=Point3F(x=0.45154244f, y=0.47105923f, z=0.0036449137f)), LineSegment3F(a=Point3F(x=0.45154244f, y=0.47105923f, z=0.0036449137f), b=Point3F(x=0.45173696f, y=0.47295433f, z=0.004691234f)), LineSegment3F(a=Point3F(x=0.45173696f, y=0.47295433f, z=0.004691234f), b=Point3F(x=0.45193148f, y=0.47484943f, z=0.005737554f)), LineSegment3F(a=Point3F(x=0.45193148f, y=0.47484943f, z=0.005737554f), b=Point3F(x=0.45212597f, y=0.47674453f, z=0.0067838733f)), LineSegment3F(a=Point3F(x=0.45212597f, y=0.47674453f, z=0.0067838733f), b=Point3F(x=0.45109624f, y=0.47802964f, z=0.008032686f)), LineSegment3F(a=Point3F(x=0.45109624f, y=0.47802964f, z=0.008032686f), b=Point3F(x=0.4493469f, y=0.47875568f, z=0.006259168f)), LineSegment3F(a=Point3F(x=0.4493469f, y=0.47875568f, z=0.006259168f), b=Point3F(x=0.44759756f, y=0.47948173f, z=0.00448565f)), LineSegment3F(a=Point3F(x=0.44759756f, y=0.47948173f, z=0.00448565f), b=Point3F(x=0.44584823f, y=0.48020777f, z=0.002712132f)), LineSegment3F(a=Point3F(x=0.44584823f, y=0.48020777f, z=0.002712132f), b=Point3F(x=0.4440989f, y=0.48093385f, z=9.3861413E-4f)), LineSegment3F(a=Point3F(x=0.4440989f, y=0.48093385f, z=9.3861413E-4f), b=Point3F(x=0.44234955f, y=0.4816599f, z=0.0017526392f)), LineSegment3F(a=Point3F(x=0.44234955f, y=0.4816599f, z=0.0017526392f), b=Point3F(x=0.44060022f, y=0.48238593f, z=0.0025666642f)), LineSegment3F(a=Point3F(x=0.44060022f, y=0.48238593f, z=0.0025666642f), b=Point3F(x=0.43885088f, y=0.48311198f, z=0.0033806893f)), LineSegment3F(a=Point3F(x=0.43885088f, y=0.48311198f, z=0.0033806893f), b=Point3F(x=0.43710154f, y=0.48383805f, z=0.0041947146f)), LineSegment3F(a=Point3F(x=0.43710154f, y=0.48383805f, z=0.0041947146f), b=Point3F(x=0.4365272f, y=0.48200053f, z=0.003146036f)), LineSegment3F(a=Point3F(x=0.4365272f, y=0.48200053f, z=0.003146036f), b=Point3F(x=0.43595284f, y=0.480163f, z=0.0020973575f)), LineSegment3F(a=Point3F(x=0.43595284f, y=0.480163f, z=0.0020973575f), b=Point3F(x=0.4353785f, y=0.4783255f, z=0.0010486789f)), LineSegment3F(a=Point3F(x=0.4353785f, y=0.4783255f, z=0.0010486789f), b=Point3F(x=0.4348041f, y=0.47648796f, z=0.0f)), LineSegment3F(a=Point3F(x=0.4348041f, y=0.47648796f, z=0.0f), b=Point3F(x=0.43603113f, y=0.47707114f, z=4.0756637E-5f)), LineSegment3F(a=Point3F(x=0.43603113f, y=0.47707114f, z=4.0756637E-5f), b=Point3F(x=0.43725815f, y=0.4776543f, z=0.0f)), LineSegment3F(a=Point3F(x=0.43725815f, y=0.4776543f, z=0.0f), b=Point3F(x=0.43720105f, y=0.47994596f, z=6.877082E-5f)), LineSegment3F(a=Point3F(x=0.43720105f, y=0.47994596f, z=6.877082E-5f), b=Point3F(x=0.43714395f, y=0.4822376f, z=0.0f)), LineSegment3F(a=Point3F(x=0.43714395f, y=0.4822376f, z=0.0f), b=Point3F(x=0.43932867f, y=0.4819981f, z=6.5934284E-5f)), LineSegment3F(a=Point3F(x=0.43932867f, y=0.4819981f, z=6.5934284E-5f), b=Point3F(x=0.44151336f, y=0.4817586f, z=0.0f)), LineSegment3F(a=Point3F(x=0.44151336f, y=0.4817586f, z=0.0f), b=Point3F(x=0.4412207f, y=0.47966385f, z=6.345272E-5f)), LineSegment3F(a=Point3F(x=0.4412207f, y=0.47966385f, z=6.345272E-5f), b=Point3F(x=0.44092804f, y=0.47756913f, z=0.0f)), LineSegment3F(a=Point3F(x=0.44092804f, y=0.47756913f, z=0.0f), b=Point3F(x=0.44127166f, y=0.4759739f, z=4.8954542E-5f)), LineSegment3F(a=Point3F(x=0.44127166f, y=0.4759739f, z=4.8954542E-5f), b=Point3F(x=0.4416153f, y=0.4743787f, z=0.0f)), LineSegment3F(a=Point3F(x=0.4416153f, y=0.4743787f, z=0.0f), b=Point3F(x=0.44226092f, y=0.47294044f, z=4.7295467E-5f)), LineSegment3F(a=Point3F(x=0.44226092f, y=0.47294044f, z=4.7295467E-5f), b=Point3F(x=0.44290656f, y=0.47150218f, z=0.0f)), LineSegment3F(a=Point3F(x=0.44290656f, y=0.47150218f, z=0.0f), b=Point3F(x=0.44267157f, y=0.47038218f, z=3.433163E-5f)), LineSegment3F(a=Point3F(x=0.44267157f, y=0.47038218f, z=3.433163E-5f), b=Point3F(x=0.44243658f, y=0.46926218f, z=0.0f)), LineSegment3F(a=Point3F(x=0.44243658f, y=0.46926218f, z=0.0f), b=Point3F(x=0.44457674f, y=0.4692114f, z=6.4223E-5f)), LineSegment3F(a=Point3F(x=0.44457674f, y=0.4692114f, z=6.4223E-5f), b=Point3F(x=0.4467169f, y=0.46916062f, z=0.0f)), LineSegment3F(a=Point3F(x=0.4467169f, y=0.46916062f, z=0.0f), b=Point3F(x=0.44648224f, y=0.46730232f, z=5.619156E-5f)), LineSegment3F(a=Point3F(x=0.44648224f, y=0.46730232f, z=5.619156E-5f), b=Point3F(x=0.44624758f, y=0.46544406f, z=0.0f)), LineSegment3F(a=Point3F(x=0.44624758f, y=0.46544406f, z=0.0f), b=Point3F(x=0.44638872f, y=0.4652444f, z=7.3349843E-6f)), LineSegment3F(a=Point3F(x=0.44638872f, y=0.4652444f, z=7.3349843E-6f), b=Point3F(x=0.44652987f, y=0.46504477f, z=0.0f)), LineSegment3F(a=Point3F(x=0.44652987f, y=0.46504477f, z=0.0f), b=Point3F(x=0.44781035f, y=0.46323365f, z=6.6541805E-5f)), LineSegment3F(a=Point3F(x=0.44781035f, y=0.46323365f, z=6.6541805E-5f), b=Point3F(x=0.4490908f, y=0.46142253f, z=0.0f)), LineSegment3F(a=Point3F(x=0.4490908f, y=0.46142253f, z=0.0f), b=Point3F(x=0.45027256f, y=0.46103266f, z=3.7332084E-5f)), LineSegment3F(a=Point3F(x=0.45027256f, y=0.46103266f, z=3.7332084E-5f), b=Point3F(x=0.4514543f, y=0.46064278f, z=0.0f)), LineSegment3F(a=Point3F(x=0.4514543f, y=0.46064278f, z=0.0f), b=Point3F(x=0.45150664f, y=0.46068597f, z=2.035484E-6f)), LineSegment3F(a=Point3F(x=0.45150664f, y=0.46068597f, z=2.035484E-6f), b=Point3F(x=0.45155898f, y=0.46072912f, z=0.0f)))
            val riverSkeleton = arrayListOf(LineSegment3F(a=Point3F(x=0.4440989f, y=0.48093385f, z=9.3861413E-4f), b=Point3F(x=0.44445243f, y=0.4791604f, z=7.82158E-4f)), LineSegment3F(a=Point3F(x=0.4440989f, y=0.48093385f, z=9.3861413E-4f), b=Point3F(x=0.44445243f, y=0.4791604f, z=7.82158E-4f)), LineSegment3F(a=Point3F(x=0.44445243f, y=0.4791604f, z=7.82158E-4f), b=Point3F(x=0.44487637f, y=0.4774219f, z=6.273381E-4f)), LineSegment3F(a=Point3F(x=0.44487637f, y=0.4774219f, z=6.273381E-4f), b=Point3F(x=0.44350272f, y=0.47621801f, z=4.6930704E-4f)), LineSegment3F(a=Point3F(x=0.44350272f, y=0.47621801f, z=4.6930704E-4f), b=Point3F(x=0.44212908f, y=0.47501412f, z=3.11276E-4f)), LineSegment3F(a=Point3F(x=0.44212908f, y=0.47501412f, z=3.11276E-4f), b=Point3F(x=0.442553f, y=0.47327563f, z=1.5645611E-4f)), LineSegment3F(a=Point3F(x=0.442553f, y=0.47327563f, z=1.5645611E-4f), b=Point3F(x=0.44290656f, y=0.47150218f, z=0.0f)), LineSegment3F(a=Point3F(x=0.442553f, y=0.47327563f, z=1.5645611E-4f), b=Point3F(x=0.44290656f, y=0.47150218f, z=0.0f)))
            val globalVertices = PointSet2F(points=arrayListOf(Point3F(x=0.45423353f, y=0.4629355f, z=0.0f), Point3F(x=0.45239055f, y=0.4633788f, z=4.9925124E-4f), Point3F(x=0.45095888f, y=0.46537393f, z=5.0595374E-4f), Point3F(x=0.4511534f, y=0.46726903f, z=0.0015522738f), Point3F(x=0.45134792f, y=0.46916413f, z=0.0025985937f), Point3F(x=0.45154244f, y=0.47105923f, z=0.0036449137f), Point3F(x=0.45173696f, y=0.47295433f, z=0.004691234f), Point3F(x=0.45193148f, y=0.47484943f, z=0.005737554f), Point3F(x=0.45212597f, y=0.47674453f, z=0.0067838733f), Point3F(x=0.45109624f, y=0.47802964f, z=0.008032686f), Point3F(x=0.4493469f, y=0.47875568f, z=0.006259168f), Point3F(x=0.44759756f, y=0.47948173f, z=0.00448565f), Point3F(x=0.44584823f, y=0.48020777f, z=0.002712132f), Point3F(x=0.4440989f, y=0.48093385f, z=9.3861413E-4f), Point3F(x=0.44234955f, y=0.4816599f, z=0.0017526392f), Point3F(x=0.44060022f, y=0.48238593f, z=0.0025666642f), Point3F(x=0.43885088f, y=0.48311198f, z=0.0033806893f), Point3F(x=0.43710154f, y=0.48383805f, z=0.0041947146f), Point3F(x=0.4365272f, y=0.48200053f, z=0.003146036f), Point3F(x=0.43595284f, y=0.480163f, z=0.0020973575f), Point3F(x=0.4353785f, y=0.4783255f, z=0.0010486789f), Point3F(x=0.4348041f, y=0.47648796f, z=0.0f), Point3F(x=0.43603113f, y=0.47707114f, z=4.0756637E-5f), Point3F(x=0.43725815f, y=0.4776543f, z=0.0f), Point3F(x=0.43720105f, y=0.47994596f, z=6.877082E-5f), Point3F(x=0.43714395f, y=0.4822376f, z=0.0f), Point3F(x=0.43932867f, y=0.4819981f, z=6.5934284E-5f), Point3F(x=0.44151336f, y=0.4817586f, z=0.0f), Point3F(x=0.4412207f, y=0.47966385f, z=6.345272E-5f), Point3F(x=0.44092804f, y=0.47756913f, z=0.0f), Point3F(x=0.44127166f, y=0.4759739f, z=4.8954542E-5f), Point3F(x=0.4416153f, y=0.4743787f, z=0.0f), Point3F(x=0.44226092f, y=0.47294044f, z=4.7295467E-5f), Point3F(x=0.44290656f, y=0.47150218f, z=0.0f), Point3F(x=0.44267157f, y=0.47038218f, z=3.433163E-5f), Point3F(x=0.44243658f, y=0.46926218f, z=0.0f), Point3F(x=0.44457674f, y=0.4692114f, z=6.4223E-5f), Point3F(x=0.4467169f, y=0.46916062f, z=0.0f), Point3F(x=0.44648224f, y=0.46730232f, z=5.619156E-5f), Point3F(x=0.44624758f, y=0.46544406f, z=0.0f), Point3F(x=0.44638872f, y=0.4652444f, z=7.3349843E-6f), Point3F(x=0.44652987f, y=0.46504477f, z=0.0f), Point3F(x=0.44781035f, y=0.46323365f, z=6.6541805E-5f), Point3F(x=0.4490908f, y=0.46142253f, z=0.0f), Point3F(x=0.45027256f, y=0.46103266f, z=3.7332084E-5f), Point3F(x=0.4514543f, y=0.46064278f, z=0.0f), Point3F(x=0.45155898f, y=0.46072912f, z=0.0f), Point3F(x=0.44445243f, y=0.4791604f, z=7.82158E-4f), Point3F(x=0.44487637f, y=0.4774219f, z=6.273381E-4f), Point3F(x=0.44350272f, y=0.47621801f, z=4.6930704E-4f), Point3F(x=0.44212908f, y=0.47501412f, z=3.11276E-4f), Point3F(x=0.442553f, y=0.47327563f, z=1.5645611E-4f)))
            buildMesh(edgeSkeleton, riverSkeleton, globalVertices)
        }

        val test8 = {
            val edgeSkeleton = arrayListOf(LineSegment3F(a=Point3F(x=0.6287814f, y=0.64292514f, z=0.0f), b=Point3F(x=0.6285789f, y=0.64310527f, z=8.130057E-6f)), LineSegment3F(a=Point3F(x=0.6285789f, y=0.64310527f, z=8.130057E-6f), b=Point3F(x=0.62837636f, y=0.64328545f, z=0.0f)), LineSegment3F(a=Point3F(x=0.62837636f, y=0.64328545f, z=0.0f), b=Point3F(x=0.6264154f, y=0.6450299f, z=7.8738405E-5f)), LineSegment3F(a=Point3F(x=0.6264154f, y=0.6450299f, z=7.8738405E-5f), b=Point3F(x=0.62445444f, y=0.64677435f, z=0.0f)), LineSegment3F(a=Point3F(x=0.62445444f, y=0.64677435f, z=0.0f), b=Point3F(x=0.62391686f, y=0.6477758f, z=3.4099096E-5f)), LineSegment3F(a=Point3F(x=0.62391686f, y=0.6477758f, z=3.4099096E-5f), b=Point3F(x=0.62337923f, y=0.6487773f, z=0.0f)), LineSegment3F(a=Point3F(x=0.62337923f, y=0.6487773f, z=0.0f), b=Point3F(x=0.621824f, y=0.6474259f, z=6.181009E-5f)), LineSegment3F(a=Point3F(x=0.621824f, y=0.6474259f, z=6.181009E-5f), b=Point3F(x=0.6202689f, y=0.6460744f, z=0.0f)), LineSegment3F(a=Point3F(x=0.6202689f, y=0.6460744f, z=0.0f), b=Point3F(x=0.62136686f, y=0.64497685f, z=4.6574376E-5f)), LineSegment3F(a=Point3F(x=0.62136686f, y=0.64497685f, z=4.6574376E-5f), b=Point3F(x=0.6224649f, y=0.64387935f, z=0.0f)), LineSegment3F(a=Point3F(x=0.6224649f, y=0.64387935f, z=0.0f), b=Point3F(x=0.6218558f, y=0.6428003f, z=3.717364E-5f)), LineSegment3F(a=Point3F(x=0.6218558f, y=0.6428003f, z=3.717364E-5f), b=Point3F(x=0.6212467f, y=0.6417212f, z=0.0f)), LineSegment3F(a=Point3F(x=0.6212467f, y=0.6417212f, z=0.0f), b=Point3F(x=0.6201482f, y=0.6419466f, z=3.364214E-5f)), LineSegment3F(a=Point3F(x=0.6201482f, y=0.6419466f, z=3.364214E-5f), b=Point3F(x=0.61904967f, y=0.64217204f, z=0.0f)), LineSegment3F(a=Point3F(x=0.61904967f, y=0.64217204f, z=0.0f), b=Point3F(x=0.61853933f, y=0.6435119f, z=4.3012584E-5f)), LineSegment3F(a=Point3F(x=0.61853933f, y=0.6435119f, z=4.3012584E-5f), b=Point3F(x=0.618029f, y=0.64485174f, z=0.0f)), LineSegment3F(a=Point3F(x=0.618029f, y=0.64485174f, z=0.0f), b=Point3F(x=0.61734927f, y=0.6471128f, z=7.0830174E-5f)), LineSegment3F(a=Point3F(x=0.61734927f, y=0.6471128f, z=7.0830174E-5f), b=Point3F(x=0.6166695f, y=0.6493738f, z=0.0f)), LineSegment3F(a=Point3F(x=0.6166695f, y=0.6493738f, z=0.0f), b=Point3F(x=0.6166311f, y=0.64934134f, z=1.5085803E-6f)), LineSegment3F(a=Point3F(x=0.6166311f, y=0.64934134f, z=1.5085803E-6f), b=Point3F(x=0.6165927f, y=0.6493089f, z=0.0f)), LineSegment3F(a=Point3F(x=0.6165927f, y=0.6493089f, z=0.0f), b=Point3F(x=0.6161271f, y=0.64752984f, z=1.2806458E-4f)), LineSegment3F(a=Point3F(x=0.6161271f, y=0.64752984f, z=1.2806458E-4f), b=Point3F(x=0.61566144f, y=0.64575076f, z=2.5612916E-4f)), LineSegment3F(a=Point3F(x=0.61566144f, y=0.64575076f, z=2.5612916E-4f), b=Point3F(x=0.6151958f, y=0.6439717f, z=3.8419373E-4f)), LineSegment3F(a=Point3F(x=0.6151958f, y=0.6439717f, z=3.8419373E-4f), b=Point3F(x=0.6147302f, y=0.6421926f, z=5.122583E-4f)), LineSegment3F(a=Point3F(x=0.6147302f, y=0.6421926f, z=5.122583E-4f), b=Point3F(x=0.61426455f, y=0.6404135f, z=6.403229E-4f)), LineSegment3F(a=Point3F(x=0.61426455f, y=0.6404135f, z=6.403229E-4f), b=Point3F(x=0.6137988f, y=0.6386344f, z=7.6838746E-4f)), LineSegment3F(a=Point3F(x=0.6137988f, y=0.6386344f, z=7.6838746E-4f), b=Point3F(x=0.61540574f, y=0.6375966f, z=7.709473E-4f)), LineSegment3F(a=Point3F(x=0.61540574f, y=0.6375966f, z=7.709473E-4f), b=Point3F(x=0.6170127f, y=0.63655883f, z=7.7350717E-4f)), LineSegment3F(a=Point3F(x=0.6170127f, y=0.63655883f, z=7.7350717E-4f), b=Point3F(x=0.6186196f, y=0.63552105f, z=7.76067E-4f)), LineSegment3F(a=Point3F(x=0.6186196f, y=0.63552105f, z=7.76067E-4f), b=Point3F(x=0.62022656f, y=0.6344833f, z=7.786269E-4f)), LineSegment3F(a=Point3F(x=0.62022656f, y=0.6344833f, z=7.786269E-4f), b=Point3F(x=0.62183356f, y=0.6334454f, z=7.8118686E-4f)), LineSegment3F(a=Point3F(x=0.62183356f, y=0.6334454f, z=7.8118686E-4f), b=Point3F(x=0.62306416f, y=0.6352961f, z=5.207912E-4f)), LineSegment3F(a=Point3F(x=0.62306416f, y=0.6352961f, z=5.207912E-4f), b=Point3F(x=0.62429476f, y=0.63714683f, z=2.6039558E-4f)), LineSegment3F(a=Point3F(x=0.62429476f, y=0.63714683f, z=2.6039558E-4f), b=Point3F(x=0.6255253f, y=0.63899755f, z=0.0f)), LineSegment3F(a=Point3F(x=0.6255253f, y=0.63899755f, z=0.0f), b=Point3F(x=0.6260718f, y=0.6402748f, z=4.1678326E-5f)), LineSegment3F(a=Point3F(x=0.6260718f, y=0.6402748f, z=4.1678326E-5f), b=Point3F(x=0.6266184f, y=0.6415521f, z=0.0f)), LineSegment3F(a=Point3F(x=0.6266184f, y=0.6415521f, z=0.0f), b=Point3F(x=0.6271422f, y=0.64188457f, z=1.8612376E-5f)), LineSegment3F(a=Point3F(x=0.6271422f, y=0.64188457f, z=1.8612376E-5f), b=Point3F(x=0.627666f, y=0.6422171f, z=0.0f)), LineSegment3F(a=Point3F(x=0.627666f, y=0.6422171f, z=0.0f), b=Point3F(x=0.62822366f, y=0.6425711f, z=1.9815816E-5f)), LineSegment3F(a=Point3F(x=0.62822366f, y=0.6425711f, z=1.9815816E-5f), b=Point3F(x=0.6287814f, y=0.64292514f, z=0.0f)))
            val riverSkeleton = arrayListOf<LineSegment3F>()
            val globalVertices = PointSet2F(points=arrayListOf(Point3F(x=0.6287814f, y=0.64292514f, z=0.0f), Point3F(x=0.6285789f, y=0.64310527f, z=8.130057E-6f), Point3F(x=0.62837636f, y=0.64328545f, z=0.0f), Point3F(x=0.6264154f, y=0.6450299f, z=7.8738405E-5f), Point3F(x=0.62445444f, y=0.64677435f, z=0.0f), Point3F(x=0.62391686f, y=0.6477758f, z=3.4099096E-5f), Point3F(x=0.62337923f, y=0.6487773f, z=0.0f), Point3F(x=0.621824f, y=0.6474259f, z=6.181009E-5f), Point3F(x=0.6202689f, y=0.6460744f, z=0.0f), Point3F(x=0.62136686f, y=0.64497685f, z=4.6574376E-5f), Point3F(x=0.6224649f, y=0.64387935f, z=0.0f), Point3F(x=0.6218558f, y=0.6428003f, z=3.717364E-5f), Point3F(x=0.6212467f, y=0.6417212f, z=0.0f), Point3F(x=0.6201482f, y=0.6419466f, z=3.364214E-5f), Point3F(x=0.61904967f, y=0.64217204f, z=0.0f), Point3F(x=0.61853933f, y=0.6435119f, z=4.3012584E-5f), Point3F(x=0.618029f, y=0.64485174f, z=0.0f), Point3F(x=0.61734927f, y=0.6471128f, z=7.0830174E-5f), Point3F(x=0.6166695f, y=0.6493738f, z=0.0f), Point3F(x=0.6165927f, y=0.6493089f, z=0.0f), Point3F(x=0.6161271f, y=0.64752984f, z=1.2806458E-4f), Point3F(x=0.61566144f, y=0.64575076f, z=2.5612916E-4f), Point3F(x=0.6151958f, y=0.6439717f, z=3.8419373E-4f), Point3F(x=0.6147302f, y=0.6421926f, z=5.122583E-4f), Point3F(x=0.61426455f, y=0.6404135f, z=6.403229E-4f), Point3F(x=0.6137988f, y=0.6386344f, z=7.6838746E-4f), Point3F(x=0.61540574f, y=0.6375966f, z=7.709473E-4f), Point3F(x=0.6170127f, y=0.63655883f, z=7.7350717E-4f), Point3F(x=0.6186196f, y=0.63552105f, z=7.76067E-4f), Point3F(x=0.62022656f, y=0.6344833f, z=7.786269E-4f), Point3F(x=0.62183356f, y=0.6334454f, z=7.8118686E-4f), Point3F(x=0.62306416f, y=0.6352961f, z=5.207912E-4f), Point3F(x=0.62429476f, y=0.63714683f, z=2.6039558E-4f), Point3F(x=0.6255253f, y=0.63899755f, z=0.0f), Point3F(x=0.6260718f, y=0.6402748f, z=4.1678326E-5f), Point3F(x=0.6266184f, y=0.6415521f, z=0.0f), Point3F(x=0.6271422f, y=0.64188457f, z=1.8612376E-5f), Point3F(x=0.627666f, y=0.6422171f, z=0.0f), Point3F(x=0.62822366f, y=0.6425711f, z=1.9815816E-5f)))
            buildMesh(edgeSkeleton, riverSkeleton, globalVertices)
        }

        val tests = listOf<() -> Any?>(
                test8
        )

        debug = true

        tests.forEach { test ->
            test()
            debugIteration.incrementAndGet()
        }
    }
}

private class CollinearPatch(val start: Point2F, val end: Point2F, val points: ArrayList<Point2F>)

fun triangulatePolygon(vertices: ArrayList<Point3F>, polygon: ArrayList<Pair<Int, Int>>): LinkedHashSet<Set<Int>> {
    var points = ArrayList<Point2F>(polygon.map { vertices[it.first] })
    if (areaOfPolygon(points) < 0) {
        points.reverse()
    }
    var collinearPatches = findCollinearPatches(points)
    collinearPatches.forEach {
        if (debug) {
            draw(debugResolution, "debug-triangulatePolygon1-${debugIteration.get()}-${debugCount.andIncrement}", "output", Color.WHITE, vertices) {
                graphics.color = Color.BLACK
                for (i in 1..points.size) {
                    val a = points[i - 1]
                    val b = points[i % points.size]
                    drawEdge(a, b)
                    drawPoint(a, 3)
                    drawPoint(b, 3)
                }
                graphics.color = Color.RED
                drawPoint(it.start, 4)
                drawPoint(it.end, 4)
                graphics.color = Color.GREEN
                it.points.forEach {
                    drawPoint(it, 2)
                }
            }
            breakPoint()
        }
        points.removeAll(it.points)
    }
    var reducedPoints = ArrayList(points)
    if (reducedPoints.size < 3) {
        points = ArrayList(polygon.map { vertices[it.first] })
        reducedPoints = points
        collinearPatches = ArrayList()
    }
    val newEdges = ArrayList<LineSegment2F>()
    while (points.size > 3) {
        val (ai, bi, ci) = findNextEar(points)
        try {
            newEdges.add(LineSegment2F(points[ai], points[ci]))
            if (debug) {
                draw(debugResolution, "debug-triangulatePolygon1-${debugIteration.get()}-${debugCount.andIncrement}", "output", Color.WHITE, vertices) {
                    graphics.color = Color.BLACK
                    for (i in 1..points.size) {
                        val a = points[i - 1]
                        val b = points[i % points.size]
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
                draw(debugResolution, "debug-triangulatePolygon2-${debugIteration.get()}-${debugCount.andIncrement}", "output", Color.WHITE, vertices) {
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
                        val a = vertices[it.first]
                        val b = vertices[it.second]
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
    val pointSet = PointSet2F(vertices)
    var reducedPolygon = polygonFromPoints(pointSet, reducedPoints)
    var meshMinusPatches = buildMesh(reducedPolygon + newEdges.map { Pair(pointSet[it.a], pointSet[it.b]) }, vertices.size)
    while (collinearPatches.isNotEmpty()) {
        val patch = collinearPatches.first()
        collinearPatches.remove(patch)
        val sid = pointSet[patch.start]
        val eid = pointSet[patch.end]
        val edge = listOf(sid, eid)
        for (tri in ArrayList(meshMinusPatches)) {
            if (tri.containsAll(edge)) {
                meshMinusPatches.remove(tri)
                val convergence = LinkedHashSet(tri)
                convergence.removeAll(edge)
                val focus = vertices[convergence.first()]
                patch.points.forEach {
                    newEdges.add(LineSegment2F(it, focus))
                }
                break
            }
        }
        addPatchPoints(reducedPoints, patch)
        reducedPolygon = polygonFromPoints(pointSet, reducedPoints)
        meshMinusPatches = buildMesh(reducedPolygon + newEdges.map { Pair(pointSet[it.a], pointSet[it.b]) }, vertices.size)
    }
    val triangles = buildMesh(polygon + newEdges.map { Pair(pointSet[it.a], pointSet[it.b]) }, vertices.size)
    if (debug) {
        draw(debugResolution, "debug-triangulatePolygon3-${debugIteration.get()}-${debugCount.andIncrement}", "output", Color.WHITE, vertices) {
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
    val flipped = flipEdges(vertices, triangles, polygon)
    if (debug) {
        draw(debugResolution, "debug-triangulatePolygon4-${debugIteration.get()}-${debugCount.andIncrement}", "output", Color.WHITE, vertices) {
            graphics.color = Color.BLACK
            flipped.forEach {
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
        if (debug) {
            draw(debugResolution, "debug-findCollinearPatches-${debugIteration.get()}-${debugCount.andIncrement}", "output", Color.WHITE, points) {
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
        val angle = angle(points, ai, bi, ci)
        if (angle < 0.08 && patchSum + angle < 0.16) {
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
            draw(debugResolution, "debug-anyPointWithin-${debugIteration.get()}-${debugCount.andIncrement}", "output", Color.WHITE, points) {
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

private fun halfAngle(points: List<Point2F>, ai: Int, bi: Int, ci: Int): Double {
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

private fun angle(points: List<Point2F>, ai: Int, bi: Int, ci: Int): Double {
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

private class EdgeNode(val index: Int, var p1: Int, var p2: Int, var t1: TriNode, var t2: TriNode)

private class TriNode(var p1: Int, var p2: Int, var p3: Int, val edges: ArrayList<EdgeNode> = ArrayList())

private fun flipEdges(vertices: ArrayList<Point3F>, triangles: LinkedHashSet<Set<Int>>, polygonIndices: List<Pair<Int, Int>>, modifiedEdges: LinkedHashSet<Set<Int>>? = null): LinkedHashSet<Set<Int>> {
    val polygon = Polygon2F.fromUnsortedEdges(polygonIndices.map { LineSegment2F(vertices[it.first], vertices[it.second]) })
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
    var edgeNodesToCheck: MutableCollection<Int> = ArrayList()
    edgeMap.filter { it.value.size == 2 }.entries.forEach {
        val edge = it.key.toList()
        val edgeNode = EdgeNode(edgeNodes.size, edge[0], edge[1], it.value[0], it.value[1])
        edgeNode.t1.edges.add(edgeNode)
        edgeNode.t2.edges.add(edgeNode)
        if (modifiedEdges == null || modifiedEdges.contains(it.key)) {
            edgeNodesToCheck.add(edgeNode.index)
        }
        edgeNodes.add(edgeNode)
    }
    var nextNodesToCheck: MutableCollection<Int> = LinkedHashSet()
    val nodesToCheckByAngle = LinkedHashSet<Int>(edgeNodesToCheck)
    var iterations = 0
    var flips = 1
    while (flips > 0 && iterations < 100) {
        flips = 0
        edgeNodesToCheck.forEach { nodeId ->
            val edgeNode = edgeNodes[nodeId]
            val tri1 = edgeNode.t1
            val tri2 = edgeNode.t2
            val peaks = mutableSetOf(tri1.p1, tri1.p2, tri1.p3, tri2.p1, tri2.p2, tri2.p3)
            peaks.remove(edgeNode.p1)
            peaks.remove(edgeNode.p2)
            val peakLineIds = peaks.toList()
            val baseLine = LineSegment2F(vertices[edgeNode.p1], vertices[edgeNode.p2])
            val peakLine = LineSegment2F(vertices[peakLineIds[0]], vertices[peakLineIds[1]])
            val check1 = baseLine.intersectsOrTouches(peakLine)
            if (debug) {
                val check2 = !polygon.isWithin(baseLine.interpolate(0.5f))
                val check3 = baseLineIntersectsPolygon(polygon, baseLine)
                val check4 = hasCollinearTriangle(vertices, tri1, tri2)
                val check5 = (!containsCollinearPoints(buildQuad(vertices, tri1, tri2)) && peakLine.length2 < baseLine.length2)
                draw(debugResolution, "debug-flipEdges2-1-${debugIteration.get()}-${debugCount.andIncrement}", "output", Color.WHITE, vertices) {
                    graphics.color = Color.BLACK
                    triNodes.forEach {
                        val a = vertices[it.p1]
                        val b = vertices[it.p2]
                        val c = vertices[it.p3]
                        drawEdge(a, b)
                        drawEdge(b, c)
                        drawEdge(c, a)
                        drawPoint(a, 3)
                        drawPoint(b, 3)
                        drawPoint(c, 3)
                    }
                    graphics.color = if ((check1 && check5) || ((check1 || check2 || check3) && check4)) Color.RED else Color.BLUE
                    listOf(tri1, tri2).forEach {
                        val a = vertices[it.p1]
                        val b = vertices[it.p2]
                        val c = vertices[it.p3]
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
            if ((check1 && (!containsCollinearPoints(buildQuad(vertices, tri1, tri2)) && peakLine.length2 < baseLine.length2)) || ((check1 || !polygon.isWithin(baseLine.interpolate(0.5f)) || baseLineIntersectsPolygon(polygon, baseLine)) && hasCollinearTriangle(vertices, tri1, tri2))) {
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
                    nextNodesToCheck.add(edge.index)
                    nodesToCheckByAngle.add(edge.index)
                }
                flips++
            }
        }
        edgeNodesToCheck = nextNodesToCheck
        nextNodesToCheck = LinkedHashSet()
        iterations++
    }
    nodesToCheckByAngle.forEach { nodeId ->
        val edgeNode = edgeNodes[nodeId]
        val tri1 = edgeNode.t1
        val tri2 = edgeNode.t2
        val peaks = mutableSetOf(tri1.p1, tri1.p2, tri1.p3, tri2.p1, tri2.p2, tri2.p3)
        peaks.remove(edgeNode.p1)
        peaks.remove(edgeNode.p2)
        val peakLineIds = peaks.toList()
        val baseLine = LineSegment2F(vertices[edgeNode.p1], vertices[edgeNode.p2])
        val peakLine = LineSegment2F(vertices[peakLineIds[0]], vertices[peakLineIds[1]])
        if (debug) {
            val check1 = baseLine.intersects(peakLine)
            val check2 = (!containsCollinearPoints(buildQuad(vertices, tri1, tri2)) || ((isCollinearTriangle(vertices, tri1) || isCollinearTriangle(vertices, tri2)) && (!isCollinearTriangle(vertices, peakLineIds[0], peakLineIds[1], edgeNode.p1) && !isCollinearTriangle(vertices, peakLineIds[0], peakLineIds[1], edgeNode.p2))))
            val check3 = anglesNeedFlipping(getMinAndMaxAngles(baseLine, peakLine))
            draw(debugResolution, "debug-flipEdges2-2-${debugIteration.get()}-${debugCount.andIncrement}", "output", Color.WHITE, vertices) {
                graphics.color = Color.BLACK
                triNodes.forEach {
                    val a = vertices[it.p1]
                    val b = vertices[it.p2]
                    val c = vertices[it.p3]
                    drawEdge(a, b)
                    drawEdge(b, c)
                    drawEdge(c, a)
                    drawPoint(a, 3)
                    drawPoint(b, 3)
                    drawPoint(c, 3)
                }
                graphics.color = if (check1 && check2 && check3) Color.RED else Color.BLUE
                listOf(tri1, tri2).forEach {
                    val a = vertices[it.p1]
                    val b = vertices[it.p2]
                    val c = vertices[it.p3]
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
        if (baseLine.intersects(peakLine) && (!containsCollinearPoints(buildQuad(vertices, tri1, tri2)) || ((isCollinearTriangle(vertices, tri1) || isCollinearTriangle(vertices, tri2)) && (!isCollinearTriangle(vertices, peakLineIds[0], peakLineIds[1], edgeNode.p1) && !isCollinearTriangle(vertices, peakLineIds[0], peakLineIds[1], edgeNode.p2)))) && anglesNeedFlipping(getMinAndMaxAngles(baseLine, peakLine))) {
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

private fun baseLineIntersectsPolygon(polygon: Polygon2F, baseLine: LineSegment2F): Boolean {
    var check3 = false
    for (polyEdge in polygon.edges) {
        if (!(polyEdge.a.epsilonEquals(baseLine.a) || polyEdge.a.epsilonEquals(baseLine.b) || polyEdge.b.epsilonEquals(baseLine.a) || polyEdge.b.epsilonEquals(baseLine.b))) {
            if (polyEdge.intersectsOrTouches(baseLine)) {
                check3 = true
                break
            }
        }
    }
    return check3
}

private fun buildQuad(vertices: ArrayList<Point3F>, tri1: TriNode, tri2: TriNode): ArrayList<Point2F> {
    val quadEdges = LinkedHashSet<Set<Int>>()
    quadEdges.add(setOf(tri1.p1, tri1.p2))
    quadEdges.add(setOf(tri1.p2, tri1.p3))
    quadEdges.add(setOf(tri1.p3, tri1.p1))
    var t2Edge = setOf(tri2.p1, tri2.p2)
    if (!quadEdges.add(t2Edge)) {
        quadEdges.remove(t2Edge)
    }
    t2Edge = setOf(tri2.p2, tri2.p3)
    if (!quadEdges.add(t2Edge)) {
        quadEdges.remove(t2Edge)
    }
    t2Edge = setOf(tri2.p3, tri2.p1)
    if (!quadEdges.add(t2Edge)) {
        quadEdges.remove(t2Edge)
    }
    val quad = ArrayList<Point2F>(orderSegment(quadEdges).map { vertices[it.first] })
    return quad
}

private fun anglesNeedFlipping(minMax: Pair<Double, Double>) = (minMax.first < 0.55 && minMax.second > 2.0)

private fun getMinAndMaxAngles(baseLine: LineSegment2F, peakLine: LineSegment2F): Pair<Double, Double> {
    val angle1 = angle(baseLine.a, peakLine.a, baseLine.b)
    val angle2 = angle(baseLine.b, peakLine.b, baseLine.a)
    val minAngle = min(angle1, angle2)
    val maxAngle = max(angle1, angle2)
    return Pair(minAngle, maxAngle)
}

private fun hasCollinearTriangle(vertices: ArrayList<Point3F>, tri1: TriNode, tri2: TriNode) = isCollinearTriangle(vertices, tri1) || isCollinearTriangle(vertices, tri2)

private fun isCollinearTriangle(vertices: ArrayList<Point3F>, triangle: TriNode) = isCollinearTriangle(vertices[triangle.p1], vertices[triangle.p2], vertices[triangle.p3])

private fun isCollinearTriangle(vertices: ArrayList<Point3F>, a: Int, b: Int, c: Int) = isCollinearTriangle(vertices[a], vertices[b], vertices[c])

private fun isCollinearTriangle(p1: Point2F, p2: Point2F, p3: Point2F) =  containsCollinearPoints(listOf(p1, p2, p3)) || isCollinearTriangleAltMethod(p1, p2, p3)

private fun isCollinearTriangleAltMethod(a: Point2F, b: Point2F, c:Point2F): Boolean {
    val angle1 = angle(a, b, c)
    val angle2 = angle(b, c, a)
    val angle3 = angle(c, a, b)
    return angle1 < 0.03 || angle2 < 0.03 || angle3 < 0.03
}

private fun containsCollinearPoints(points: List<Point2F>): Boolean {
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
    try {
        val edgeSkeleton = ArrayList(edgeSkeletonIn)
        val riverSkeleton = ArrayList(riverSkeletonIn)
        if (debug) {
            draw(debugResolution, "debug-buildMesh1-${debugIteration.get()}-${debugCount.andIncrement}", "output", Color.WHITE, edgeSkeleton.flatMap { listOf(it.a, it.b) } + riverSkeleton.flatMap { listOf(it.a, it.b) }) {
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
        globalMapEdges(globalVertices, edgeSkeleton)
        unTwistEdges(edgeSkeleton)
        moveRiverInsideBorder(globalVertices, edgeSkeleton, riverSkeleton)
        unTwistEdges(riverSkeleton)
        if (debug) {
            draw(debugResolution, "debug-buildMesh2-${debugIteration.get()}-${debugCount.andIncrement}", "output", Color.WHITE, edgeSkeleton.flatMap { listOf(it.a, it.b) } + riverSkeleton.flatMap { listOf(it.a, it.b) }) {
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
            draw(debugResolution, "debug-buildMesh3-${debugIteration.get()}-${debugCount.andIncrement}", "output", Color.WHITE, edgeSkeleton.flatMap { listOf(it.a, it.b) } + riverSkeleton.flatMap { listOf(it.a, it.b) }) {
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
        globalMapEdges(globalVertices, riverSkeleton)
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
            draw(debugResolution, "debug-buildMesh4-${debugIteration.get()}-${debugCount.andIncrement}", "output", Color.WHITE, edgeSkeleton.flatMap { listOf(it.a, it.b) } + riverSkeleton.flatMap { listOf(it.a, it.b) }) {
                graphics.color = Color.BLACK
                polygons.forEach {
                    it.forEach {
                        drawEdge(meshPoints[it.first]!!, meshPoints[it.second]!!)
                    }
                }
            }
            breakPoint()
        }
        if (debug) {
            draw(debugResolution, "debug-buildMesh5-${debugIteration.get()}-${debugCount.andIncrement}", "output", Color.WHITE, edgeSkeleton.flatMap { listOf(it.a, it.b) } + riverSkeleton.flatMap { listOf(it.a, it.b) }) {
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
        val triangles = LinkedHashSet<Set<Int>>()
        polygons.forEach { polygon ->
            triangles.addAll(buildRelaxedTriangles(vertices, polygon))
        }
        if (debug) {
            draw(debugResolution, "debug-buildMesh6-${debugIteration.get()}-${debugCount.andIncrement}", "output", Color.WHITE, edgeSkeleton.flatMap { listOf(it.a, it.b) } + riverSkeleton.flatMap { listOf(it.a, it.b) }) {
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
    } catch (e: Exception) {
        throw GeometryException("unable to build mesh for unknown reason", e)
    }
}

private fun buildRelaxedTriangles(vertices: ArrayList<Point3F>, polygon: ArrayList<Pair<Int, Int>>): Set<Set<Int>> {
    val triangles = triangulatePolygon(vertices, polygon)
    val internalVertexStart = vertices.size
    val maxTriArea = 2e-6f
    var lastSize = -1
    var count = 0
    while (lastSize != vertices.size && count < 100) {
        lastSize = vertices.size
        val edgesToCheck = LinkedHashSet<Set<Int>>()
        for (triangle in ArrayList(triangles)) {
            val tri = triangle.toList()
            val aid = tri[0]
            val bid = tri[1]
            val cid = tri[2]
            val a = vertices[aid]
            val b = vertices[bid]
            val c = vertices[cid]
            val area = area2d(a, b, c)
            if (area > maxTriArea) {
                val did = vertices.size
                vertices.add(Point3F((a.x + b.x + c.x) / 3.0f, (a.y + b.y + c.y) / 3.0f, (a.z + b.z + c.z) / 3.0f))
                triangles.remove(triangle)
                edgesToCheck.add(setOf(aid, bid))
                edgesToCheck.add(setOf(bid, cid))
                edgesToCheck.add(setOf(cid, aid))
                edgesToCheck.add(setOf(aid, did))
                edgesToCheck.add(setOf(bid, did))
                edgesToCheck.add(setOf(cid, did))
                triangles.add(setOf(aid, bid, did))
                triangles.add(setOf(bid, cid, did))
                triangles.add(setOf(cid, aid, did))
                if (debug) {
                    draw(debugResolution, "test-create-smaller-triangles${debugCount.andIncrement}", "output", Color.WHITE, vertices) {
                        graphics.color = Color.BLACK
                        triangles.forEach {
                            val one = it.toList()
                            val drawA = vertices[one[0]]
                            val drawB = vertices[one[1]]
                            val drawC = vertices[one[2]]
                            drawEdge(drawA, drawB)
                            drawEdge(drawB, drawC)
                            drawEdge(drawC, drawA)
                            drawPoint(drawA, 4)
                            drawPoint(drawB, 4)
                            drawPoint(drawC, 4)
                        }
                        graphics.color = Color.RED
                        drawEdge(a, b)
                        drawEdge(b, c)
                        drawEdge(c, a)
                        drawPoint(a, 3)
                        drawPoint(b, 3)
                        drawPoint(c, 3)
                        graphics.color = Color.GREEN
                        val d = vertices[did]
                        drawEdge(a, d)
                        drawEdge(b, d)
                        drawEdge(c, d)
                        drawPoint(d, 3)
                    }
                    breakPoint()
                }
            } else if (debug) {
                draw(debugResolution, "test-create-smaller-triangles${debugCount.andIncrement}", "output", Color.WHITE, vertices) {
                    graphics.color = Color.BLACK
                    triangles.forEach {
                        val one = it.toList()
                        val drawA = vertices[one[0]]
                        val drawB = vertices[one[1]]
                        val drawC = vertices[one[2]]
                        drawEdge(drawA, drawB)
                        drawEdge(drawB, drawC)
                        drawEdge(drawC, drawA)
                        drawPoint(drawA, 4)
                        drawPoint(drawB, 4)
                        drawPoint(drawC, 4)
                    }
                    graphics.color = Color.BLUE
                    drawEdge(a, b)
                    drawEdge(b, c)
                    drawEdge(c, a)
                    drawPoint(a, 3)
                    drawPoint(b, 3)
                    drawPoint(c, 3)
                }
                breakPoint()
            }
        }
        flipEdges(vertices, triangles, polygon, edgesToCheck)
        relaxTriangles(vertices, triangles, internalVertexStart, 1)
        count++
    }
    relaxTriangles(vertices, triangles, internalVertexStart, 3)
    if (debug) {
        draw(debugResolution, "test-triangles${debugCount.andIncrement}", "output", Color.WHITE, vertices) {
            graphics.color = Color.BLACK
            graphics.stroke = BasicStroke(1.0f)
            triangles.forEach {
                val tri = it.toList()
                val a = vertices[tri[0]]
                val b = vertices[tri[1]]
                val c = vertices[tri[2]]
                val normal = (b - a).cross(c - a)
                val pointSize = if (normal.c == 0.0f) {
                    graphics.color = Color.RED
                    graphics.stroke = BasicStroke(2.0f)
                    8
                } else {
                    4
                }
                drawEdge(a, b)
                drawEdge(b, c)
                drawEdge(c, a)
                drawPoint(a, pointSize)
                drawPoint(b, pointSize)
                drawPoint(c, pointSize)
                if (normal.c == 0.0f) {
                    graphics.color = Color.BLACK
                    graphics.stroke = BasicStroke(1.0f)
                }
            }
        }
        val xVals = vertices.map { it.x }
        val yVals = vertices.map { it.y }
        val xMin = xVals.min()!!
        val xMax = xVals.max()!!
        val yMin = yVals.min()!!
        val yMax = yVals.max()!!
        val xDelta = xMax - xMin
        val yDelta = yMax - yMin
        val delta = max(xDelta, yDelta)
        val multiplier = 0.98f / delta
        val heightMap = ArrayListMatrix(debugResolution) { -Float.MAX_VALUE }
        val revisedVertices = ArrayList(vertices.map { Point3F(((it.x - xMin) * multiplier) + 0.01f, ((it.y - yMin) * multiplier) + 0.01f, it.z) })
        renderTriangles(revisedVertices, triangles, heightMap)
        writeHeightData("test-heightMap", heightMap)
    }
    return triangles
}

fun relaxTriangles(vertices: ArrayList<Point3F>, triangles: LinkedHashSet<Set<Int>>, internalVertexStart: Int, iterations: Int) {
    for (iteration in 1..iterations) {
        for (i in internalVertexStart..vertices.size - 1) {
            val affectingPoints = LinkedHashSet<Int>()
            triangles.forEach {
                if (it.contains(i)) {
                    affectingPoints.addAll(it)
                }
            }
            affectingPoints.remove(i)
            val n = affectingPoints.size
            val b = if (n > 3) 3.0f / (8.0f * n) else (3.0f / 16.0f)
            val inb = 1.0f - (n * b)
            val initialPosition = vertices[i]
            var x = 0.0f
            var y = 0.0f
            var z = 0.0f
            affectingPoints.forEach {
                val point = vertices[it]
                x += point.x
                y += point.y
                z += point.z
            }
            x *= b
            y *= b
            z *= b
            x += initialPosition.x * inb
            y += initialPosition.y * inb
            z += initialPosition.z * inb
            vertices[i] = Point3F(x, y, z)
        }
    }
}

private fun area2d(a: Point3F, b: Point3F, c: Point3F): Float {
    val a2d = Point3F(a.x, a.y, 0.0f)
    val b2d = Point3F(b.x, b.y, 0.0f)
    val c2d = Point3F(c.x, c.y, 0.0f)
    return (b2d - a2d).cross(c2d - a2d).length / 2.0f
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
                draw(debugResolution, "debug-unTwistEdges-${debugIteration.get()}-${debugCount.andIncrement}", "output", Color.WHITE, skeleton.flatMap { listOf(it.a, it.b) }) {
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
    try {
        if (edges.first().a.epsilonEquals(edges.last().b)) {
            return
        }
        val unmodified = Polygon2F.fromUnsortedEdges(edges.map { LineSegment2F(it.a, it.b) })
        if (unmodified.isClosed) {
            return
        }
        val fillEdges = LineSegment3F(unmodified.edges.last().b as Point3F, unmodified.edges.first().a as Point3F).subdivided2d(0.002f)
        var newEdges = unmodified.edges.map { LineSegment3F(it.a as Point3F, it.b as Point3F) } + fillEdges
        val modified = Polygon2F.fromUnsortedEdges(newEdges.map { LineSegment2F(it.a, it.b) })
        if (!modified.isClosed) {
            newEdges = Polygon2F(modified.points, true).edges.map { LineSegment3F(it.a as Point3F, it.b as Point3F) }
        }
        if (debug) {
            draw(debugResolution, "debug-closeEdge-${debugIteration.get()}-${debugCount.andIncrement}", "output", Color.WHITE, edges.flatMap { listOf(it.a, it.b) }) {
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
    } catch (e: Exception) {
        throw GeometryException("unable to close empty polygon")
    }
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
                    newRiverSkeleton.add(LineSegment3F(globalVertices[findSuitableReplacement(globalVertices, polygon, borderPoints, dropMap, line.first, line.second)] as Point3F, globalVertices[line.second] as Point3F))
                } else {
                    newRiverSkeleton.add(LineSegment3F(globalVertices[line.first] as Point3F, globalVertices[findSuitableReplacement(globalVertices, polygon, borderPoints, dropMap, line.second, line.first)] as Point3F))
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

private fun findSuitableReplacement(globalVertices: PointSet2F, polygon: ArrayList<Pair<Int, Int>>, borderPoints: LinkedHashSet<Int>, dropMap: HashMap<Int, ArrayList<Int>>, toReplace: Int, cantUse: Int): Int {
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
    val polyEdges = polygon.map { LineSegment2F(globalVertices[it.first]!!, globalVertices[it.second]!!) }
    val orderedBorderPoints = ArrayList(borderPoints)
    orderedBorderPoints.sortBy { globalVertices[it]!!.distance2(globalVertices[toReplace]!!) }
    orderedBorderPoints.forEach {
        if (isValidInteriorEdge(polyEdges, LineSegment2F(globalVertices[cantUse]!!, globalVertices[it]!!))) {
            return it
        }
    }
    return orderedBorderPoints.first()
}

fun isValidInteriorEdge(polyEdges: List<LineSegment2F>, edge: LineSegment2F): Boolean {
    for (polyEdge in polyEdges) {
        if (polyEdge.epsilonEquals(edge)) {
            return false
        }
        if (polyEdge.a.epsilonEquals(edge.a) || polyEdge.b.epsilonEquals(edge.a) || polyEdge.a.epsilonEquals(edge.b) || polyEdge.b.epsilonEquals(edge.b)) {
            continue
        }
        if (polyEdge.intersectsOrTouches(edge)) {
            return false
        }
    }
    return true
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
                val segmentSet = LinkedHashSet(it)
                while (segmentSet.isNotEmpty()) {
                    val newSegment = orderSegment(segmentSet)
                    segmentSet.removeAll(newSegment.map { setOf(it.first, it.second) })
                    orderedNonCycleSegments.add(newSegment)
                }
            }
            if (putNonCyclesInCycles) {
                orderedNonCycleSegments.forEach {
                    val (splicePoint, containingCycle) = findContainingCycle(meshPoints, segmentPaths, it)
                    if (splicePoint != null && containingCycle != null) {
                        val spliceEdge = findSuitableSpliceEdge(meshPoints, orderedNonCycleSegments, containingCycle, it, splicePoint)
                        val spliceLine = LineSegment3F(meshPoints[spliceEdge.first]!! as Point3F, meshPoints[spliceEdge.second]!! as Point3F)
                        val newLines = spliceLine.subdivided2d(0.002f)
                        newLines.forEach {
                            meshPoints.add(it.a)
                            meshPoints.add(it.b)
                        }
                        newEdges.addAll(newLines.map { Pair(meshPoints[it.a], meshPoints[it.b]) }.filter { it.first != it.second })
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

private fun containsPoint(meshPoints: PointSet2F, polygon: List<Pair<Int, Int>>, id: Int): Boolean {
    return containsPoint(meshPoints, polygon, meshPoints[id]!!)
}

private fun containsPoint(meshPoints: PointSet2F, polygon: List<Pair<Int, Int>>, point: Point2F): Boolean {
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

private fun getConnectedSegments(edges: Collection<Pair<Int, Int>>): List<Set<Set<Int>>> {
    return getConnectedEdgeSegments(edges.map { setOf(it.first, it.second) })
}

private fun getConnectedEdgeSegments(setEdges: Collection<Set<Int>>): List<Set<Set<Int>>> {
    val fullSet = LinkedHashSet(setEdges.filter { it.size == 2 })
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
    try {
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
    } catch (e: Exception) {
        throw GeometryException("unable to order empty segment")
    }
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
                draw(debugResolution * 4, "debug-triangulatePolygon1-${debugIteration.get()}-${debugCount.andIncrement}", "output", Color.WHITE, vertices) {
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

fun renderTriangles(vertices: ArrayList<Point3F>, triangles: LinkedHashSet<Set<Int>>, heightMap: ArrayListMatrix<Float>) {
    triangles.forEach {
        val tri = it.toList()
        val a = vertices[tri[0]]
        val b = vertices[tri[1]]
        val c = vertices[tri[2]]
        val cross = (b - a).cross(c - a)
        if (cross.c < 0) {
            renderTriangle(a, b, c, heightMap)
        } else {
            renderTriangle(a, c, b, heightMap)
        }
    }
}

fun renderTriangle(a: Point3F, b: Point3F, c: Point3F, heightMap: ArrayListMatrix<Float>) {
    val stride = heightMap.width
    val strideF = stride.toFloat()
    val p1 = Point3F(a.x * strideF, a.y * strideF, a.z)
    val p2 = Point3F(b.x * strideF, b.y * strideF, b.z)
    val p3 = Point3F(c.x * strideF, c.y * strideF, c.z)
    val normal = (p2 - p1).cross(p3 - p1)
    val d = -normal.dot(p1)
    val na = normal.a
    val nb = normal.b
    val nc = normal.c
    val minZ = min(p1.z, p2.z, p3.z)
    val maxZ = max(p1.z, p2.z, p3.z)

    fun interpolateZ(x: Int, y: Int): Float {
        val height = clamp(minZ, maxZ, -((na * x) + (nb * y) + d) / nc)
        if (height.isNaN()) {
            throw GeometryException("collinear triangle").with {
                data.add("val a = $a")
                data.add("val b = $b")
                data.add("val c = $c")
            }
        }
        return height
    }

    val ax: Int = round(16.0f * p1.x)
    val bx: Int = round(16.0f * p2.x)
    val cx: Int = round(16.0f * p3.x)

    val ay: Int = round(16.0f * p1.y)
    val by: Int = round(16.0f * p2.y)
    val cy: Int = round(16.0f * p3.y)

    val dxAb: Int = ax - bx
    val dxBc: Int = bx - cx
    val dxCa: Int = cx - ax

    val dyAb: Int = ay - by
    val dyBc: Int = by - cy
    val dyCa: Int = cy - ay

    val fdxAb: Int = dxAb shl 4
    val fdxBc: Int = dxBc shl 4
    val fdxCa: Int = dxCa shl 4

    val fdyAb: Int = dyAb shl 4
    val fdyBc: Int = dyBc shl 4
    val fdyCa: Int = dyCa shl 4

    var minX: Int = (min(ax, bx, cx) + 0xF) shr 4
    val maxX: Int = (max(ax, bx, cx) + 0xF) shr 4
    var minY: Int = (min(ay, by, cy) + 0xF) shr 4
    val maxY: Int = (max(ay, by, cy) + 0xF) shr 4

    val q: Int = 8

    minX = minX and (q - 1).complement()
    minY = minY and (q - 1).complement()

    var c1: Int = dyAb * ax - dxAb * ay
    var c2: Int = dyBc * bx - dxBc * by
    var c3: Int = dyCa * cx - dxCa * cy

    if(dyAb < 0 || (dyAb == 0 && dxAb > 0)) c1++
    if(dyBc < 0 || (dyBc == 0 && dxBc > 0)) c2++
    if(dyCa < 0 || (dyCa == 0 && dxCa > 0)) c3++

    var blockYOffset: Int = minY * stride

    var y: Int = minY
    while (y < maxY) {
        var x: Int = minX
        while (x < maxX) {
            val x0: Int = x shl 4
            val x1: Int = (x + q - 1) shl 4
            val y0: Int = y shl 4
            val y1: Int = (y + q - 1) shl 4

            val a00: Int = if (c1 + dxAb * y0 - dyAb * x0 > 0) 1 else 0
            val a10: Int = if (c1 + dxAb * y0 - dyAb * x1 > 0) 1 else 0
            val a01: Int = if (c1 + dxAb * y1 - dyAb * x0 > 0) 1 else 0
            val a11: Int = if (c1 + dxAb * y1 - dyAb * x1 > 0) 1 else 0
            val hs1 = a00 or (a10 shl 1) or (a01 shl 2) or (a11 shl 3)

            val b00: Int = if (c2 + dxBc * y0 - dyBc * x0 > 0) 1 else 0
            val b10: Int = if (c2 + dxBc * y0 - dyBc * x1 > 0) 1 else 0
            val b01: Int = if (c2 + dxBc * y1 - dyBc * x0 > 0) 1 else 0
            val b11: Int = if (c2 + dxBc * y1 - dyBc * x1 > 0) 1 else 0
            val hs2: Int = b00 or (b10 shl 1) or (b01 shl 2) or (b11 shl 3)

            val c00: Int = if (c3 + dxCa * y0 - dyCa * x0 > 0) 1 else 0
            val c10: Int = if (c3 + dxCa * y0 - dyCa * x1 > 0) 1 else 0
            val c01: Int = if (c3 + dxCa * y1 - dyCa * x0 > 0) 1 else 0
            val c11: Int = if (c3 + dxCa * y1 - dyCa * x1 > 0) 1 else 0
            val hs3: Int = c00 or (c10 shl 1) or (c01 shl 2) or (c11 shl 3)

            if (hs1 == 0x0 || hs2 == 0x0 || hs3 == 0x0) {
                x += q
                continue
            }

            var yOffset: Int = blockYOffset

            if (hs1 == 0xF && hs2 == 0xF && hs3 == 0xF) {
                var iy: Int = y
                val endY = y + q
                while (iy < endY) {
                    var ix: Int = x
                    val endX = x + q
                    while (ix < endX) {
                        val index = yOffset + ix
                        if (index < heightMap.size) {
                            heightMap[index] = interpolateZ(ix, iy)
                        }
                        ix++
                    }
                    yOffset += stride
                    iy++
                }
            } else {
                var cy1: Int = c1 + dxAb * y0 - dyAb * x0
                var cy2: Int = c2 + dxBc * y0 - dyBc * x0
                var cy3: Int = c3 + dxCa * y0 - dyCa * x0

                var iy = y
                val endY = y + q
                while (iy < endY) {
                    var cx1: Int = cy1
                    var cx2: Int = cy2
                    var cx3: Int = cy3

                    var ix = x
                    val endX = x + q
                    while (ix < endX) {
                        if(cx1 > 0 && cx2 > 0 && cx3 > 0) {
                            val index = yOffset + ix
                            if (index < heightMap.size) {
                                heightMap[index] = interpolateZ(ix, iy)
                            }
                        }
                        cx1 -= fdyAb
                        cx2 -= fdyBc
                        cx3 -= fdyCa
                        ix++
                    }

                    cy1 += fdxAb
                    cy2 += fdxBc
                    cy3 += fdxCa

                    yOffset += stride
                    iy++
                }
            }
            x += q
        }
        blockYOffset += q * stride
        y += q
    }
}

fun writeHeightData(name: String, heightMap: ArrayListMatrix<Float>) {
    var minValue = Double.MAX_VALUE
    var maxValue = -Double.MAX_VALUE
    for (y in (0..heightMap.width - 1)) {
        for (x in (0..heightMap.width - 1)) {
            val valueF = heightMap[x, y]
            if (valueF == -Float.MAX_VALUE) {
                continue
            }
            val value = valueF.toDouble()
            minValue = min(minValue, value)
            maxValue = max(maxValue, value)
        }
    }
    val adjustedMinValue = minValue - ((maxValue - minValue) * 0.1)
    val range = 1.0 / (maxValue - adjustedMinValue)
    val output = BufferedImage(heightMap.width, heightMap.width, BufferedImage.TYPE_USHORT_GRAY)
    val raster = output.raster
    for (y in (0..heightMap.width - 1)) {
        for (x in (0..heightMap.width - 1)) {
            val value = heightMap[x, y].toDouble()
            val pixel = round(((min(max(value, adjustedMinValue), maxValue) - adjustedMinValue) * range) * 65535).toInt()
            raster.setSample(x, y, 0, pixel)
        }
    }
    ImageIO.write(output, "png", File("output/$name.png"))
}

fun max(a: Int, b: Int, c: Int) = max(max(a, b), c)

fun min(a: Int, b: Int, c: Int) = min(min(a, b), c)

fun min(a: Float, b: Float, c: Float) = min(min(a, b), c)

fun max(a: Float, b: Float, c: Float) = max(max(a, b), c)

fun clamp(min: Float, max: Float, f: Float) = min(max(min, f), max)

