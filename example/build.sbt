
lazy val app = project
  .in(file("."))
  .enablePlugins(ScalaNativePlugin, StructGeneratorPlugin)
  .settings(
    scalaVersion := "3.8.4",
    structTargetDir := baseDirectory.value / "src" / "main" / "scala" / "generated" / "structs",
    //structAnnotation := "struct",
    //structPrefix := "_"
    //structTargetPackage := "generated.structs"
  )