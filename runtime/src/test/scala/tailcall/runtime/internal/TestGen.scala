package tailcall.runtime.internal

import tailcall.runtime.http.Method
import tailcall.runtime.model.Config._
import tailcall.runtime.model.Steps.Step
import tailcall.runtime.model._
import zio.test.Gen

import java.net.URL

object TestGen {
  def genName: Gen[Any, String] = fromIterableRandom("body", "completed", "email", "id", "name", "title", "url")

  def genBaseURL: Gen[Any, URL] = Gen.const(new URL("http://localhost:8080"))

  def genVersion: Gen[Any, Int] = Gen.int(0, 10)

  def genScalar: Gen[Any, TSchema] = Gen.fromIterable(List(TSchema.string, TSchema.num, TSchema.bool))

  def genField: Gen[Any, (String, TSchema)] =
    for {
      name <- genName
      kind <- genScalar
    } yield (name, kind)

  def genObj: Gen[Any, TSchema] = Gen.listOfN(2)(genField).map(fields => TSchema.obj(fields.toMap))

  def genSchema: Gen[Any, TSchema] = genObj

  def genServer: Gen[Any, Server] = genBaseURL.map(baseURL => Server(Option(baseURL)))

  def genMethod: Gen[Any, Method] =
    Gen.oneOf(Gen.const(Method.GET), Gen.const(Method.POST), Gen.const(Method.PUT), Gen.const(Method.DELETE))

  def genMustache: Gen[Any, Mustache] =
    for { name <- Gen.chunkOfN(2)(genName) } yield Mustache(name: _*)

  def genSegment: Gen[Any, Path.Segment] =
    Gen.oneOf(genName.map(Path.Segment.Literal(_)), genMustache.map(Path.Segment.Param(_)))

  def genPath: Gen[Any, Path] = Gen.listOfN(2)(genSegment).map(Path(_))

  def genHttp: Gen[Any, Step.Http] =
    for {
      path   <- genPath
      method <- Gen.option(genMethod)
      input  <- Gen.option(genSchema)
      output <- Gen.option(genSchema)
    } yield Step.Http(path, method, input, output)

  def genStep: Gen[Any, Step] =
    for { http <- genHttp } yield http

  def genFieldDefinition: Gen[Any, Field] =
    for {
      typeName <- genTypeName
      steps    <- Gen.option(Gen.listOf(genStep).map(Steps(_)))
    } yield Field(typeOf = typeName, steps = steps)

  def fromIterableRandom[A](seq: A*): Gen[Any, A] =
    Gen.fromRandom { random =>
      val list = seq.toVector
      random.nextIntBetween(0, list.length - 1).map(list(_))
    }

  def genTypeName: Gen[Any, String] = {
    fromIterableRandom("Query", "User", "Post", "Comment", "Album", "Photo", "Todo")
  }

  def schemaDefinition: Gen[Any, RootSchema] = Gen.const(RootSchema(Option("Query"), Option("Mutation")))

  def genGraphQL: Gen[Any, Config.GraphQL] =
    for {
      map    <- Gen
        .mapOfN(2)(genTypeName, Gen.mapOfN(2)(genName, genFieldDefinition).map(fields => Config.Type(fields = fields)))
      schema <- schemaDefinition
    } yield Config.GraphQL(schema, map)

  def genConfig: Gen[Any, Config] =
    for {
      version <- genVersion
      server  <- genServer
      graphQL <- genGraphQL
    } yield Config(version, server, graphQL)
}
