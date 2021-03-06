package com.ak.collector

import cats.effect.{ExitCode, IO, IOApp}

object Main extends IOApp {
  def run(args: List[String]): IO[ExitCode] =
    CollectorServer.stream[IO].compile.drain.as(ExitCode.Success)
}
