package edu.utdallas.seers.collections

import edu.utdallas.seers.collections.Collections.randomSample
import edu.utdallas.seers.tests.BaseSpec
import org.scalatest.AppendedClues.convertToClueful

import scala.util.Random

class CollectionsSpec extends BaseSpec {
  // Use the same seed every time to avoid non-deterministic failures
  val random = new Random(0)

  "randomSample" should "return an exact sample without replacement" in {
    for (listSize <- 0 to 100; sampleSize <- 0 to listSize) {
      val theSample = randomSample(Range(0, listSize), sampleSize, random)

      theSample should have size sampleSize withClue s"with list size $listSize"

      val deduplicatedSample = theSample.toSet

      theSample should contain theSameElementsAs deduplicatedSample
    }
  }

  it should "throw IllegalArgumentException for sampleSize < 0 and > listSize" in {
    for (listSize <- 0 to 10) {
      val list = Range(0, listSize)

      val sampleSizes = List(-100, -2, -1, listSize + 1, listSize + 2, listSize + 100)

      for (sampleSize <- sampleSizes)
        (an[IllegalArgumentException] should be thrownBy
          randomSample(list, sampleSize, random)
          withClue s"with list size $listSize")
    }
  }
}
