package org.apache.spark.graphx

import java.util.{Arrays => JArrays}
import scala.reflect.ClassTag

import org.apache.spark.graphx.impl.EdgePartitionBuilder
import org.apache.spark.{Logging, SparkContext}
import org.apache.spark.graphx.impl.{EdgePartition, GraphImpl}
import org.apache.spark.util.collection.PrimitiveVector

/**
 * Provides utilities for loading [[Graph]]s from files.
 */
object GraphLoader extends Logging {

  /**
   * Loads a graph from an edge list formatted file where each line contains two integers: a source
   * id and a target id. Skips lines that begin with `#`.
   *
   * If desired the edges can be automatically oriented in the positive
   * direction (source Id < target Id) by setting `canonicalOrientation` to
   * true.
   *
   * @example Loads a file in the following format:
   * {{{
   * # Comment Line
   * # Source Id <\t> Target Id
   * 1   -5
   * 1    2
   * 2    7
   * 1    8
   * }}}
   *
   * @param sc
   * @param path the path to the file (e.g., /home/data/file or hdfs://file)
   * @param canonicalOrientation whether to orient edges in the positive
   *        direction
   * @param minEdgePartitions the number of partitions for the
   *        the edge RDD
   * @tparam ED
   */
  def edgeListFile(
      sc: SparkContext,
      path: String,
      canonicalOrientation: Boolean = false,
      minEdgePartitions: Int = 1): Graph[Int, Int] = {
    val startTime = System.currentTimeMillis

    // Parse the edge data table directly into edge partitions
    val edges = sc.textFile(path, minEdgePartitions).mapPartitionsWithIndex { (pid, iter) =>
      val builder = new EdgePartitionBuilder[Int]
      iter.foreach { line =>
        if (!line.isEmpty && line(0) != '#') {
          val lineArray = line.split("\\s+")
          if (lineArray.length < 2) {
            logWarning("Invalid line: " + line)
          }
          val srcId = lineArray(0).toLong
          val dstId = lineArray(1).toLong
          if (canonicalOrientation && dstId > srcId) {
            builder.add(dstId, srcId, 1)
          } else {
            builder.add(srcId, dstId, 1)
          }
        }
      }
      Iterator((pid, builder.toEdgePartition))
    }.cache()
    edges.count()

    logInfo("It took %d ms to load the edges".format(System.currentTimeMillis - startTime))

    GraphImpl.fromEdgePartitions(edges, defaultVertexAttr = 1)
  } // end of edgeListFile

}
