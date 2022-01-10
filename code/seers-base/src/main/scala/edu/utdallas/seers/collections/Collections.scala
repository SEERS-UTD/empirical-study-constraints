package edu.utdallas.seers.collections

import scala.jdk.javaapi.CollectionConverters
import scala.util.Random

object Collections {

  /**
   * Samples without replacement.
   *
   * @param items      The items to sample.
   * @param sampleSize Must be between 0 and items.size
   * @tparam T Item type.
   * @return A sample of size sampleSize.
   */
  def randomSample[T](items: Iterable[T], sampleSize: Int): List[T] =
    randomSample(items, sampleSize, new Random())

  /**
   * Samples using the provided random.
   */
  def randomSample[T](items: Iterable[T], sampleSize: Int, random: Random): List[T] = {

    /**
     * Swaps items 0 and randomIndex. The new element at 0 is accumulated.
     */
    @scala.annotation.tailrec
    def randomSample(items: Vector[T], sampleSize: Int, acc: List[T], random: Random): List[T] = {
      if (sampleSize == 0) acc
      else {
        val index = random.nextInt(items.size)

        val newHead +: newItems = if (index == 0) {
          // No need to swap if the random index is 0
          items
        } else {
          // Swap head and the element at index
          val head = items.head
          val other = items(index)

          items.updated(0, other).updated(index, head)
        }

        randomSample(newItems, sampleSize - 1, newHead :: acc, random)
      }
    }

    require(sampleSize >= 0 && sampleSize <= items.size,
      "Sample size must be >= 0 and <= list.size")

    if (sampleSize == items.size) items.toList
    else if (sampleSize == 0) List.empty
    else randomSample(items.toVector, sampleSize, List.empty, random)
  }

  def toScala[T](iterable: java.lang.Iterable[T]): Iterable[T] = CollectionConverters.asScala(iterable)

  def toJava[T](seq: Seq[T]): java.util.Collection[T] = CollectionConverters.asJava(seq)

}
