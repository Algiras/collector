package com.ak.collector

import cats.effect.{ExitCode, IO, IOApp}
import scala.io.StdIn.readLine

object Main extends IOApp {
  def run(args: List[String]) = for {
    serverFiber <- CollectorServer.stream[IO].compile.drain.start
    _ <- IO(readLine())
    _ <- serverFiber.cancel
  } yield ExitCode.Success
}
