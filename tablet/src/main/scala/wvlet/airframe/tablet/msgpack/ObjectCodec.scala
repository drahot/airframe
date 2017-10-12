/*
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package wvlet.airframe.tablet.msgpack

import org.msgpack.core.{MessagePacker, MessageUnpacker}
import org.msgpack.value.ValueFactory
import wvlet.surface.Surface
import scala.collection.JavaConverters._

/**
  *
  */
case class ObjectCodec[A](surface: Surface, paramCodec: Seq[MessageCodec[_]]) extends MessageCodec[A] {
  override def pack(p: MessagePacker, v: A): Unit = {
    val numParams = surface.params.length
    // Use array format [p1, p2, ....]
    p.packArrayHeader(numParams)
    for ((param, codec) <- surface.params.zip(paramCodec)) {
      val paramValue = param.get(v)
      codec.asInstanceOf[MessageCodec[Any]].pack(p, paramValue)
    }
  }

  override def unpack(u: MessageUnpacker, v: MessageHolder): Unit = {
    val numParams = surface.params.length
    val numElems  = u.unpackArrayHeader()
    if (numParams != numElems) {
      u.skipValue(numElems)
      v.setNull
    } else {
      var index = 0
      val args  = Seq.newBuilder[Any]
      while (index < numElems && index < numParams) {
        // TODO reuse message holders
        val m = new MessageHolder
        paramCodec(index).unpack(u, m)
        // TODO handle null value?
        args += m.getLastValue
        index += 1
      }
      surface.objectFactory
        .map(_.newInstance(args.result()))
        .map(x => v.setObject(x))
        .getOrElse(v.setNull)
    }
  }

  def unpack(u: MessageUnpacker, v: MessageHolder, params: Seq[String]) {}
}

/**
  * Codec for recording name of the parameters in an object
  * @param surface
  */
case class SurfaceCodec(surface: Surface) extends MessageCodec[Surface] {
  import ValueFactory._

  override def pack(p: MessagePacker, v: Surface): Unit = {
    val m = newMapBuilder()
      .put(newString("fullName"), newString(surface.fullName))
      .put(newString("params"), newArray(surface.params.map(_.name).map(newString(_)).asJava))
      .build()
    p.packValue(m)
  }

  override def unpack(u: MessageUnpacker, v: MessageHolder): Unit = {
    val m = u.unpackValue().asMapValue().map.asScala
    m.get(newString("params")) match {
      case Some(arr) =>
        val columns = for (param <- arr.asArrayValue().list().asScala) yield {
          param.toString
        }
        v.setObject(columns.toIndexedSeq)
      case None =>
        v.setIncompatibleFormatException("")
    }
  }
}