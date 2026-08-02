
# Scala Native Struct Generator Plugin

An sbt plugin designed for **Scala Native** that automatically generates zero-allocation **Opaque Types** mapping directly to `Ptr[CStructN[...]]` from annotated Scala 3 `case class` definitions.

Using **Scalameta** for robust AST parsing, it seamlessly handles Scala 3 syntax variants (braces `{}` or indentation `:`) and automatically ports user-defined methods from the source `case class` directly to the generated opaque type extensions.


## Features

* **Zero-Heap Allocation Ergonomics**: Wraps underlying Scala Native `Ptr[CStructN[...]]` with Scala 3 `opaque type`.
* **Method Porting**: Automatically ports custom methods defined in your `case class` into extension methods on the generated opaque type.
* **Automatic CString Handling**: Generates ergonomic helpers (`toScalaString`, `setFromScalaString`) for fields defined as `String` or `CString`.
* **sbt Integration**: Automatically hooks into `Compile / sourceGenerators`.

---

## Installation

Add the plugin dependency to your `project/plugins.sbt`:

```scala
addSbtPlugin("br.com.mobilemind" % "struct-generator-plugin" % "0.1.0-SNAPSHOT")

```

---

## Configurations

You can customize the plugin behavior in your project's `build.sbt`:

| Setting Key | Type | Default Value | Description |
| --- | --- | --- | --- |
| `structAnnotation` | `String` | `"struct"` | Annotation name used to identify target case classes (e.g., `"struct"` or `"generated.structs.struct"`). |
| `structPrefix` | `String` | `"_"` | Prefix or suffix string to be stripped from the source `case class` name when naming the output opaque type. |
| `structTargetPackage` | `String` | `"generated.structs"` | Target package name where the generated opaque types will reside. |
| `sourcesTargetDir` | `File` | `(Compile / sourceManaged).value / "generated_structs"` | Directory containing Scala source files to be scanned for annotated case classes. |
| `structTargetDir` | `File` | `(Compile / sourceManaged).value / "generated_structs"` | Output destination directory where generated Scala source files will be written. |


### Example `build.sbt` Customization

```scala
enablePlugins(StructGeneratorPlugin)

// Optional custom settings
structAnnotation    := "struct"
structPrefix        := "_"
structTargetPackage := "myproject.native.structs"
structTargetDir     := baseDirectory.value / "src" / "main" / "scala" / "generated"
sourcesTargetDir     := baseDirectory.value / "src" / "main" / "scala" / "domain"

```

---

## Usage Example

### 1. Define your annotated `case class`

Mark your structure with `@struct` (or your configured annotation). You can use leading/trailing underscores in the name to avoid collision with the generated type name.

```scala
package myapp.model

import scala.scalanative.unsafe.*

// Dummy annotation for AST matching (or import your own)
class struct extends scala.annotation.StaticAnnotation

@struct
case class _Person(id: CInt, name: String | CString):
  def isAdult: Boolean = p.id >= 18

  def printDetails()(using Zone): Unit =
    println(s"ID: ${p.id}, Name:${p.nameToScalaString}")

```

### 2. Generated Code Overview

The plugin parses your definition and generates a Scala 3 opaque type under your configured package:

```scala
package generated.structs

import scala.scalanative.unsafe.*

type PersonStruct = CStruct2[CInt, CString]
opaque type Person = Ptr[PersonStruct]

object Person:
  inline def apply(id: CInt, name: CString)(using z: Zone): Person =
    val p = alloc[PersonStruct]()
    p._1 = id
    p._2 = name
    p

  inline def unapply(p: Person): Option[(CInt, CString)] =
    Some((p.id, p.name))

  extension (p: Person)
    inline def id: CInt = p._1
    inline def name: CString = p._2
    inline def id_=(value: CInt): Unit = p._1 = value
    inline def name_=(value: CString): Unit = p._2 = value
    inline def nameToScalaString(using Zone): String = fromCString(p.name)
    inline def setNameFromScalaString(value: String)(using Zone): Unit = p.name = toCString(value)

    inline def copy(id: CInt = p.id, name: CString = p.name)(using Zone): Person =
      Person(id, name)

    // Ported methods from original case class
    def isAdult: Boolean = p.id >= 18

    def printDetails()(using Zone): Unit =
      println(s"ID: ${p.id}, Name:${p.nameToScalaString}")

```

### 3. Using the Generated Opaque Type

```scala
import generated.structs.Person
import scala.scalanative.unsafe.*

Zone { implicit z =>
  // Allocate on native memory stack via Zone
  val person = Person(id = 25, name = c"Alice")

  // Use auto-generated accessors and ported methods
  if (person.isAdult) {
    person.printDetails() // Prints: ID: 25, Name: Alice
  }

  // Update CString field using Scala String helper
  person.setNameFromScalaString("Bob")
  println(s"Updated Name: ${person.nameToScalaString}")
    
  // 2. Atualização Mutável dos Campos (Setters)
  person.id = 42
  person.name = c"Bob" // Re-aloca CString na Zone atual e atualiza o ponteiro
  
  // 3. Atualização Imutável via Copy
  val person2 = person.copy(name = c"Charlie")
  
  // 4. Pattern Matching extraindo Strings
  person2 match
    case Person(id, name) =>
      println(s"Pattern Match extraiu: id=$id, name=$name")
}

```

```

```