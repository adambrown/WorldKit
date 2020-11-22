package wk.api

@PublicApi
interface BitMatrix<T> : Matrix<Boolean>, Iterable<Boolean> {

    val wordCount: Int

    val bitsPerWord: Int

    @PublicApi
    fun setWord(i: Int, word: T)

    @PublicApi
    fun getWord(i: Int): T

    @PublicApi
    fun setBitIterator(): Iterator<Int>

    @PublicApi
    fun unsetBitIterator(): Iterator<Int>
}

@PublicApi
inline fun BitMatrix<*>.forEachSetBit(apply: (Int) -> Unit) = setBitIterator().forEach(apply)

@PublicApi
inline fun BitMatrix<*>.forEachUnsetBit(apply: (Int) -> Unit) = unsetBitIterator().forEach(apply)

private const val maskSet64 = 0b1L shl 63

@PublicApi
class BitMatrix64(override val width: Int, override val height: Int = width, init: ((Int) -> Long)? = null) : BitMatrix<Long> {

    override val bitsPerWord = 64

    override val size = width * height

    override val wordCount = (size ushr 6) + if ((size and 0b111111) == 0) 0 else 1

    @PublicApi
    val array = if (init != null) LongArray(wordCount, init) else LongArray(size)

    override fun set(i: Int, value: Boolean) {
        val id = i ushr 6
        val mask = (maskSet64 ushr (i and 0b111111))
        array[id] = if (value) array[id] or mask else array[id] and mask.inv()
    }

    override fun get(i: Int) = (array[i ushr 6] and (maskSet64 ushr (i and 0b111111))) != 0L

    override fun setWord(i: Int, word: Long) {
        array[i] = word
    }

    override fun getWord(i: Int) = array[i]

    override fun iterator(): Iterator<Boolean> = BitMatrixIterator(this)

    override fun setBitIterator(): Iterator<Int> = SetBitIterator64(this)

    override fun unsetBitIterator(): Iterator<Int> = UnsetBitIterator64(this)
}

private class BitMatrixIterator(private val matrix: BitMatrix<*>) : Iterator<Boolean> {

    private var currentBit = 0

    override fun hasNext(): Boolean {
        return currentBit < matrix.size
    }

    override fun next(): Boolean {
        return matrix[currentBit++]
    }
}

private class SetBitIterator64(private val matrix: BitMatrix64) : Iterator<Int> {

    val lastWord = matrix.wordCount - 1

    private var currentWord = 0

    private var currentBit = 0

    private var buffer = matrix.getWord(currentWord)

    init {
        currentBit = primeNext()
    }

    override fun hasNext(): Boolean {
        return currentBit < matrix.size
    }

    override fun next(): Int {
        val tmp = currentBit
        currentBit = primeNext()
        return tmp
    }

    private fun primeNext(): Int {
        while (buffer == 0L && currentWord < lastWord) {
            buffer = matrix.getWord(++currentWord)
        }
        val unsetBits = java.lang.Long.numberOfLeadingZeros(buffer)
        buffer = if (unsetBits == 63) 0L else buffer and (-1L ushr (unsetBits + 1))
        return (currentWord shl 6) + unsetBits
    }
}

private class UnsetBitIterator64(private val matrix: BitMatrix64) : Iterator<Int> {

    val lastWord = matrix.wordCount - 1

    private var currentWord = 0

    private var currentBit = 0

    private var buffer = matrix.getWord(currentWord).inv()

    init {
        currentBit = primeNext()
    }

    override fun hasNext(): Boolean {
        return currentBit < matrix.size
    }

    override fun next(): Int {
        val tmp = currentBit
        currentBit = primeNext()
        return tmp
    }

    private fun primeNext(): Int {
        while (buffer == 0L && currentWord < lastWord) {
            buffer = matrix.getWord(++currentWord).inv()
        }
        val unsetBits = java.lang.Long.numberOfLeadingZeros(buffer)
        buffer = if (unsetBits == 63) 0L else buffer and (-1L ushr (unsetBits + 1))
        return (currentWord shl 6) + unsetBits
    }
}