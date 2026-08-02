package br.com.mobilemind.sg4s

import sbt.*
import sbt.Keys.*
import scala.meta.*

object StructGeneratorPlugin extends AutoPlugin {
  override def trigger = allRequirements

  object autoImport {
    val structAnnotation    = settingKey[String]("Annotation name used to identify target case classes (e.g., `\"struct\"`)")
    val structPrefix        = settingKey[String]("Prefix or suffix string to be stripped from the source `case class` name when naming the output opaque type")
    val structTargetPackage = settingKey[String]("Target package name where the generated opaque types will reside")
    val structTargetDir     = settingKey[File]("Output destination directory where generated Scala source files will be written")
    val sourcesTargetDir     = settingKey[File]("Directory containing Scala source files to be scanned for annotated case classes")
    val generateStructs = taskKey[Seq[File]]("Task to generate Scala source files for opaque types based on annotated case classes")
  }

  import autoImport._

  override lazy val projectSettings: Seq[Setting[?]] = Seq(
    structAnnotation    := "struct",
    structPrefix        := "_",
    structTargetPackage := "generated.structs",
    structTargetDir     := (Compile / sourceManaged).value / "generated_structs",
    sourcesTargetDir := (Compile / scalaSource).value,

    Compile / generateStructs := Def.uncached {
      val srcDir = sourcesTargetDir.value
      val outDir = structTargetDir.value

      val targetAnn = structAnnotation.value.split('.').last
      val prefixStr = structPrefix.value
      val targetPkg = structTargetPackage.value

      val sourceFiles = (srcDir ** "*.scala").get()
      val generatedFiles = sourceFiles.flatMap { file =>
        val code = IO.read(file)

        dialects.Scala3(code).parse[Source] match {
          case Parsed.Success(sourceTree) =>
            sourceTree.collect {

              case cls @ Defn.Class.After_4_6_0(_, classNameTree, _, ctor, template) =>

                val hasAnnotation = cls.mods.exists {
                  case Mod.Annot(Init.After_4_6_0(tpe, _, _)) =>
                    val annotName = tpe.syntax.split('.').last
                    annotName == targetAnn
                  case _ => false
                }

                if (hasAnnotation && cls.mods.exists(_.is[Mod.Case])) {
                  val rawClassName = classNameTree.value

                  val className = if (rawClassName.startsWith(prefixStr)) {
                    rawClassName.stripPrefix(prefixStr)
                  } else if (rawClassName.endsWith(prefixStr)) {
                    rawClassName.stripSuffix(prefixStr)
                  } else {
                    rawClassName
                  }

                  val fields = ctor.paramClauses.flatMap(_.values).map { param =>
                    val fName = param.name.value
                    val fType = param.decltpe.map(_.syntax).getOrElse("Any")
                    (fName, fType)
                  }

                  val bodyStats = template.body.stats
                  val userMethods = if (bodyStats.nonEmpty) {
                    val adaptedBody = bodyStats.map(_.syntax).mkString("\n\n")
                      .replaceAll("\\bthis\\.", "p.")

                    adaptedBody.linesIterator.map(line => if (line.trim.nonEmpty) s"    $line" else line).mkString("\n")
                  } else ""

                  def toCType(t: String): String = t match {
                    case "String" | "CString" => "CString"
                    case other                => other
                  }

                  val structSize = fields.length
                  val cStructType = s"CStruct$structSize[" + fields.map(f => toCType(f._2)).mkString(", ") + "]"

                  // 1. Getters
                  val fieldAccessors = fields.zipWithIndex.map { case ((fName, fType), idx) =>
                    val cType = toCType(fType)
                    s"    inline def $fName: $cType = p._${idx + 1}"
                  }.mkString("\n")

                  // 2. Setters
                  val fieldSetters = fields.zipWithIndex.map { case ((fName, fType), idx) =>
                    val cType = toCType(fType)
                    s"    inline def ${fName}_=(value: $cType): Unit = p._${idx + 1} = value"
                  }.mkString("\n")

                  // 3. Conversores CString <-> String
                  val stringConverters = fields.collect {
                    case (fName, fType) if fType == "String" || fType == "CString" =>
                      val capitalized = fName.capitalize
                      s"""|    inline def ${fName}ToScalaString(using Zone): String = fromCString(p.$fName)
                          |    inline def set${capitalized}FromScalaString(value: String)(using Zone): Unit = p.$fName = toCString(value)
                          |""".stripMargin
                  }.mkString("\n")

                  // 4. Apply, Copy e Unapply
                  val applyParams = fields.map { case (fName, fType) =>
                    if (fType == "String") s"$fName: String | CString"
                    else s"$fName: $fType"
                  }.mkString(", ")

                  val applyAssignments = fields.zipWithIndex.map { case ((fName, fType), idx) =>
                    if (fType == "String")
                      s"    p._${idx + 1} = $fName match { case s: String => toCString(s)(using z); case cs: CString => cs }"
                    else
                      s"    p._${idx + 1} = $fName"
                  }.mkString("\n")

                  val copyParams = fields.map { case (fName, fType) =>
                    if (fType == "String") s"$fName: String | CString = p.$fName"
                    else s"$fName: $fType = p.$fName"
                  }.mkString(", ")
                  val copyArgs = fields.map(_._1).mkString(", ")

                  val tupleType = "(" + fields.map(f => toCType(f._2)).mkString(", ") + ")"
                  val tupleValues = "(" + fields.map(f => s"p.${f._1}").mkString(", ") + ")"

                  val generatedCode =
                    s"""|package $targetPkg
                        |
                        |import scala.scalanative.unsafe.*
                        |
                        |type ${className}Struct = $cStructType
                        |opaque type $className = Ptr[${className}Struct]

                        |object $className:
                        |  inline def apply($applyParams)(using z: Zone): $className =
                        |    val p = alloc[${className}Struct]()
                        |$applyAssignments
                        |    p
                        |
                        |  inline def unapply(p: $className): Option[$tupleType] =
                        |    Some($tupleValues)

                        |  extension (p: $className)
                        |$fieldAccessors
                        |$fieldSetters
                        |$stringConverters
                        |    inline def copy($copyParams)(using Zone): $className =
                        |      $className($copyArgs)
                        |
                        |$userMethods
                        |""".stripMargin

                  val outFile = outDir / s"$className.scala"
                  IO.write(outFile, generatedCode)
                  Some(outFile)
                } else None
            }.flatten

          case Parsed.Error(_, _, _) =>
            Seq.empty
        }
      }.toSeq

      generatedFiles
    },

    Compile / sourceGenerators += (Compile / generateStructs).taskValue
  )
}