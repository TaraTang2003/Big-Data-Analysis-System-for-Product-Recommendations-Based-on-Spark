package com.highcom.spark

import org.apache.lucene.analysis.TokenStream
import org.apache.lucene.analysis.tokenattributes.CharTermAttribute
import org.apache.spark.mllib.feature.{HashingTF, IDF, IDFModel}
import org.apache.spark.{SparkConf, SparkContext}
import org.wltea.analyzer.lucene.IKAnalyzer
import org.apache.spark.mllib.clustering.KMeans
import org.apache.spark.mllib.linalg.Vector
import org.apache.spark.rdd.RDD
import org.apache.spark.mllib.clustering.KMeansModel
import java.sql.{Connection, DriverManager, PreparedStatement}

import scala.collection.mutable.{ArrayBuffer, ListBuffer}
import scala.util.control.Breaks._


object avSpark {
  //初始化数据连接
  var connection: Connection =null
  def dbcon(): Unit ={
    val url = "jdbc:mysql:///shixi?useUnicode=true&characterEncoding=UTF8&serverTimezone=UTC"
    //驱动名称
    val driver = "com.mysql.cj.jdbc.Driver"
    //用户名
    val username = "shixi"
    //密码
    val password = "123456"

    try {
      //注册Driver
      Class.forName(driver)
      //得到连接
      connection = DriverManager.getConnection(url, username, password)
    } catch {
      case e: Exception => e.printStackTrace
    }
  }

  def avdb(uid: String, keywords: String, typeId: Int): Unit = {
    val statement = connection.createStatement
    //    执行插入操作 t_avdata
    val sql1 = "insert into t_avdata1(userid,keywords,typeId) values(?,?,?)"
    val ps1: PreparedStatement = connection.prepareStatement(sql1)
    ps1.setString(1, uid)
    ps1.setString(2, keywords)
    ps1.setInt(3, typeId)
    ps1.execute();
    println("插入数据完成")
  }

  def main(args: Array[String]): Unit = {
    dbcon()
    val conf = new SparkConf().setAppName("BlogKMeans").setMaster("local[*]")
    val sc = new SparkContext(conf)
    val rdd = sc.textFile("E:\\1_学校课程\\大三计科\\实习\\sparkpro1\\data\\advdata1_small.txt")
    val value = rdd.mapPartitions(iterator => {
      val list = new ListBuffer[(String, ArrayBuffer[String])]
      while (iterator.hasNext) {
        val line = iterator.next()
        val textArr = line.split("\t")
        breakable {
          if (textArr.length != 2) break
          val id = textArr(0)
          val text = textArr(1)

          val analyzer = new IKAnalyzer(true)
          val ts: TokenStream = analyzer.tokenStream("", text)
          //得到相应词汇的内容
          val term: CharTermAttribute = ts.getAttribute(classOf[CharTermAttribute])
          //重置分词器，使得tokenstream可以重新返回各个分词
          ts.reset()
          val arr = new ArrayBuffer[String]
          //遍历分词数据
          while (ts.incrementToken()) {
            arr.+=(term.toString())

          }
          list.append((id, arr))
          analyzer.close()

        }
      }
      list.iterator
    })
    var wordRDD = value
    wordRDD = wordRDD.cache()
    // wordRDD.foreach(println)

    //TF-IDF
    val hashingTF: HashingTF = new HashingTF(1000)
    val tfRDD = wordRDD.map(x => {
      (x._1, hashingTF.transform(x._2))
    })
    //        println("*********tfRDD***********************")
    ////        tfRDD.foreach(println)

    val idf: IDFModel = new IDF().fit(tfRDD.map(_._2))
    val tfIdfs: RDD[(String, Vector)] = tfRDD.mapValues(idf.transform(_))
    //            println("===========tfIdfs=================")
    //            tfIdfs.foreach(x=>{
    //              println("tfIdfs = "+x)
    //            })

    wordRDD = wordRDD.mapValues(buffer => {
      buffer.distinct.sortBy(item => {
        hashingTF.indexOf(item)
      })
    })
    //        println("===========wordRDD=================")
    //        wordRDD.foreach(print)
    //设置聚类个数
    val kcluster = 20
    val kmeans = new KMeans()
    kmeans.setK(kcluster)
    //使用的是kemans++算法来训练模型  "random"|"k-means||"
    kmeans.setInitializationMode("k-means||")
    //设置最大迭代次数
    kmeans.setMaxIterations(100)
    //训练模型
    val kmeansModel: KMeansModel = kmeans.run(tfIdfs.map(_._2))
    //    kmeansModel.save(sc, "d:/model001")
    //打印模型的20个中心点
    println("*****==============================")
    println(kmeansModel.clusterCenters)


    val modelBroadcast = sc.broadcast(kmeansModel)
    val predicetionRDD = tfIdfs.mapValues(vetor => {
      val model = modelBroadcast.value
      model.predict(vetor)
    })

    val tfIdfs2wordsRDD = tfIdfs.join(wordRDD)
    val result: RDD[(String, (Int, (Vector, ArrayBuffer[String])))] =
      predicetionRDD.join(tfIdfs2wordsRDD)
    //    println("=====*****==============================")
    //    result.foreach(println)

    var j = 0;
    for (j <- 0 to 19) {
      var wordslist = result
        .filter(x => x._2._1 == j) //0-19
        .flatMap(line => {
        val avid = line._1
        val tfIdfV: Vector = line._2._2._1
        val words: ArrayBuffer[String] = line._2._2._2
        val list = new ListBuffer[(Double, String, String)]
        for (i <- 0 until words.length) {
          //hashingTF.indexOf(words(i)) 当前单词在1000个向量中的位置
          list.append((tfIdfV(hashingTF.indexOf(words(i))), words(i), avid))
        }
        list
      })

      val words = wordslist.sortBy(x => x._1, false)
        .map(_._2).filter(_.length() > 1).distinct()
        //      .take(30).foreach(println)
        .take(30)

      //    println("===========================")
      //    words.foreach(println)
      //    println("====len==",wordslist.count())

      val idandword = wordslist.filter(x => words.contains(x._2))
      println("**************************")
      idandword.foreach(println)

      //调取数据库函数
      idandword.foreach(x => {
        avdb(x._3, x._2, j)
      })
    }
    sc.stop()
    connection.close
  }

}
