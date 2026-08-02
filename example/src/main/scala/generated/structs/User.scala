package generated.structs

import scala.scalanative.unsafe.*

type UserStruct = CStruct2[CInt, CString]
opaque type User = Ptr[UserStruct]

object User:
  inline def apply(id: CInt, name: CString)(using z: Zone): User =
    val p = alloc[UserStruct]()
    p._1 = id
    p._2 = name
    p

  inline def unapply(p: User): Option[(CInt, CString)] =
    Some((p.id, p.name))

  extension (p: User)
    inline def id: CInt = p._1
    inline def name: CString = p._2
    inline def id_=(value: CInt): Unit = p._1 = value
    inline def name_=(value: CString): Unit = p._2 = value
    inline def nameToScalaString(using Zone): String = fromCString(p.name)
    inline def setNameFromScalaString(value: String)(using Zone): Unit = p.name = toCString(value)

    inline def copy(id: CInt = p.id, name: CString = p.name)(using Zone): User =
      User(id, name)

    def sayHello = "Hello!!!!"
