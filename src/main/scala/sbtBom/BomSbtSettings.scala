package sbtBom

import java.io.FileOutputStream
import java.nio.channels.Channels

import sbt._
import sbt.Keys._
import sbtBom.BomSbtPlugin.autoImport._
import Defaults.prefix

import scala.xml.{Elem, PrettyPrinter, XML}
import scala.util.control.Exception.ultimately

object BomSbtSettings {
  def projectSettings: Seq[Setting[_]] =
    inConfig(Compile)(bomSettings) ++
    inConfig(Test)(bomSettings)

  private def bomSettings = Seq(
    bomFileName := "bom.xml",
    targetBomFile := target.value / (prefix(configuration.value.name) + bomFileName.value),
    makeBom := makeBomTask.value,
    listBom := listBomTask.value,
    noCompileDependenciesInOtherReports := true
  )

  private def makeBomTask: Def.Initialize[Task[sbt.File]] = Def.task[File] {
    val log = sLog.value
    val bomFile = targetBomFile.value

    log.info(s"Creating bom file ${bomFile.getAbsolutePath}")

    writeXmlToFile(generateBom.value,"UTF-8", bomFile)

    log.info(s"Bom file ${bomFile.getAbsolutePath} created")

    bomFile
  }

  private def listBomTask: Def.Initialize[Task[String]] = Def.task[String] {
    val log = sLog.value
    log.info("Creating bom")

    val bomText =
      xmlToText(generateBom.value, "UTF-8")

    log.info("Bom created")

    bomText
  }

  private def generateBom = Def.task[Elem] {
    val report = Classpaths.updateTask.value
    val ignoreModules: Seq[ModuleReport] =
      if (configuration.value != Compile && noCompileDependenciesInOtherReports.value)
        report.configuration(Compile).map(_.modules).getOrElse(Seq.empty)
      else
        Seq.empty

    new OldBomBuilder(
      report.configuration(configuration.value),
      ignoreModules

    ).build
  }

  private def writeXmlToFile(xml: Elem,
                             encoding: String,
                             destFile: sbt.File): Unit =
    writeToFile(xmlToText(xml, encoding), encoding, destFile)

  private def xmlToText(bomContent: Elem, encoding: String): String =
    "<?xml version=\"1.0\" encoding=\"" + encoding + "\"?>\n" +
      new PrettyPrinter(80, 2).format(bomContent)

  private def writeToFile(content: String,
                          encoding: String,
                          destFile: sbt.File): Unit = {
    destFile.getParentFile().mkdirs()
    val fos = new FileOutputStream(destFile.getAbsolutePath)
    val writer = Channels.newWriter(fos.getChannel(), encoding)
    ultimately(writer.close())(
      writer.write(content)
    )
  }
}
