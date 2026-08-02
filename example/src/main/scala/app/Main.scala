package app

import scala.scalanative.unsafe.*
import generated.structs.*
import generated.struct

@struct case class _User(id: CInt, name: CString):
  def sayHello = "Hello!!!!"


@main def main(args: String*): Unit =
  Zone {
    // 1. Passa String comum no apply (convertida automaticamente para CString no CStruct)
    val u1 = User(1, c"Alice")

    println(s"User 1: id=${u1.id}, name=${u1.name}")

    // 2. Atualização Mutável dos Campos (Setters)
    u1.id = 42
    u1.name = c"Bob" // Re-aloca CString na Zone atual e atualiza o ponteiro

    println(s"User 1 modificado: id=${u1.id}, name=${u1.name}")

    // 3. Atualização Imutável via Copy
    val u2 = u1.copy(name = c"Charlie")

    println(s"User 2 (copy): id=${u2.id}, name=${u2.name}")

    // 4. Pattern Matching extraindo Strings
    u2 match
      case User(id, name) =>
        println(s"Pattern Match extraiu: id=$id, name=$name")

    println(s"${u2.sayHello}")
  }